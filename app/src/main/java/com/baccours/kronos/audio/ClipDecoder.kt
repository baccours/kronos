package com.baccours.kronos.audio

import android.content.res.AssetFileDescriptor
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import java.nio.ByteOrder

/** Interleaved 16-bit PCM, as it came out of the codec. */
class DecodedPcm(val samples: ShortArray, val sampleRate: Int, val channels: Int)

/** Decodes a compressed audio asset (ogg Vorbis or Opus) to PCM with MediaExtractor + MediaCodec. */
object ClipDecoder {

    private const val TAG = "ClipDecoder"
    private const val TIMEOUT_US = 10_000L
    private const val MAX_DECODE_MS = 5_000L

    /** Returns null (and logs why) when the clip cannot be decoded. Blocking: call off the main thread. */
    fun decode(afd: AssetFileDescriptor, name: String): DecodedPcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: run {
                Log.e(TAG, "$name: no audio track")
                return null
            }
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            var out = ShortArray(INITIAL_CAPACITY)
            var size = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val deadline = SystemClock.elapsedRealtime() + MAX_DECODE_MS

            while (!outputDone) {
                if (SystemClock.elapsedRealtime() > deadline) {
                    Log.e(TAG, "$name: decoding timed out")
                    return null
                }
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val read = extractor.readSampleData(buffer, 0)
                        if (read < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, read, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex >= 0 -> {
                        val buffer = decoder.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val count = shorts.remaining()
                            if (size + count > out.size) out = out.copyOf(maxOf(out.size * 2, size + count))
                            shorts.get(out, size, count)
                            size += count
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = decoder.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                }
            }
            if (encoding != AudioFormat.ENCODING_PCM_16BIT) {
                Log.e(TAG, "$name: unsupported PCM encoding $encoding")
                return null
            }
            return DecodedPcm(out.copyOf(size), sampleRate, channels)
        } catch (e: Exception) {
            Log.e(TAG, "$name: decoding failed", e)
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            codec?.release()
            extractor.release()
        }
    }

    private const val INITIAL_CAPACITY = 64 * 1024
}
