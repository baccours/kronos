package com.baccours.kronos.engine

import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private fun work(s: Int) = Task(type = TaskType.WORK, durationSeconds = s)
    private fun rest(s: Int) = Task(type = TaskType.REST, durationSeconds = s)
    private fun EngineResult.spoken() = cues.filterIsInstance<Cue.Speak>().map { it.text }

    /** Ticks the engine every [step] ms until it stops running and returns everything spoken. */
    private fun run(engine: TimerEngine, until: Long = 600_000, step: Long = 10): List<String> {
        val spoken = ArrayList<String>()
        var t = 0L
        while (t <= until && engine.status == TimerStatus.RUNNING) {
            spoken += engine.tick(t).spoken()
            t += step
        }
        return spoken
    }

    @Test
    fun workIsNumberedPerRound_andRestIsPlain() {
        val engine = TimerEngine(
            TimerConfig(rounds = 2, tasks = listOf(work(5), rest(3), work(5)))
        )
        engine.start(0)
        val spoken = run(engine).filter { it.startsWith("Work") || it == "Rest" }
        assertEquals(listOf("Work 1", "Rest", "Work 2", "Rest", "Work 2"), spoken)
    }

    @Test
    fun zeroDurationItemsNeverStallTheTimer() {
        val engine = TimerEngine(
            TimerConfig(sets = 2, tasks = listOf(work(0), rest(2)))
        )
        engine.start(0)
        run(engine, until = 60_000)
        assertEquals(TimerStatus.FINISHED, engine.status)
    }

    @Test
    fun finishesExactlyAtPlannedTime_withoutDrift() {
        val config = TimerConfig(sets = 2, rounds = 3, setRestSeconds = 7, tasks = listOf(work(11), rest(4)))
        val engine = TimerEngine(config)
        engine.start(0)
        var t = 0L
        while (engine.status == TimerStatus.RUNNING) { engine.tick(t); t += 10 }
        val expected = (TimerEngine.DEFAULT_READY_SECONDS + config.totalDurationSeconds) * 1000L
        assertTrue("finished at $t, expected ~$expected", t in expected..(expected + 20))
    }

    @Test
    fun skipWhilePausedDoesNotCorruptTheNextItem() {
        val engine = TimerEngine(TimerConfig(tasks = listOf(work(30), rest(20))))
        engine.start(0)
        engine.tick(10_000)
        engine.pause(14_000)
        val skipped = engine.skip(20_000)
        assertEquals(Phase.REST, skipped.state.phase)
        engine.resume(50_000)
        assertEquals(20, engine.snapshot(50_000).remainingSeconds)
        assertEquals(15, engine.snapshot(55_000).remainingSeconds)
    }

    @Test
    fun lateTickAnnouncesOnlyTheItemItLandsOn() {
        val engine = TimerEngine(TimerConfig(tasks = listOf(work(5), rest(5))))
        engine.start(0)
        val late = engine.tick(15_900)
        assertEquals(Phase.REST, late.state.phase)
        assertEquals(900L, late.state.segmentElapsedMs)
        assertEquals(listOf("Rest"), late.spoken())
    }

    @Test
    fun forTime_lapsAndFinishSet() {
        val engine = TimerEngine(
            TimerConfig(mode = TimerMode.FOR_TIME, sets = 2, setRestSeconds = 5)
        )
        engine.start(0)
        engine.tick(10_000)
        engine.lap(70_000)
        engine.lap(130_000)
        assertEquals(3, engine.snapshot(131_000).currentRound)
        val finished = engine.skip(140_000)
        assertEquals(Phase.SET_REST, finished.state.phase)
        assertEquals(listOf(60_000L, 60_000L, 10_000L), finished.state.laps.map { it.durationMs })
    }

    @Test
    fun forTime_roundThenFinishDoesNotCreateGhostLap() {
        val engine = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME))
        engine.start(0)
        engine.tick(10_000)
        engine.lap(40_000)
        assertEquals(1, engine.skip(40_400).state.laps.size)
    }

    @Test
    fun forTime_timeCapEndsTheSet() {
        val engine = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME, timeCap = 20))
        engine.start(0)
        run(engine, until = 60_000)
        assertEquals(TimerStatus.FINISHED, engine.status)
        assertEquals(20_000L, engine.snapshot(60_000).laps.single().durationMs)
    }

    @Test
    fun lapIsIgnoredInTimerMode() {
        val engine = TimerEngine(TimerConfig())
        engine.start(0)
        assertTrue(engine.lap(1_000).state.laps.isEmpty())
    }

    @Test
    fun announcementsAreStructured() {
        val engine = TimerEngine(
            TimerConfig(sets = 2, rounds = 2, setRestSeconds = 3, tasks = listOf(work(5), rest(3)))
        )
        val parts = ArrayList<List<Speech>>()
        var t = 0L
        parts += engine.start(t).cues.filterIsInstance<Cue.Speak>().map { it.parts }
        while (engine.status == TimerStatus.RUNNING) {
            parts += engine.tick(t).cues.filterIsInstance<Cue.Speak>().map { it.parts }
            t += 10
        }
        val all = parts.filter { it.isNotEmpty() }.map { it.toList() }
        assertEquals(listOf(Speech.GetReady), all.first())
        assertTrue(listOf(Speech.Work(1)) in all)
        assertTrue(listOf(Speech.RoundNumber(2), Speech.Work(1)) in all)
        assertTrue(listOf(Speech.RestBetweenSets) in all)
        assertTrue(listOf(Speech.SetNumber(2), Speech.Work(1)) in all)
        assertEquals(listOf(Speech.WellDone), all.last())
    }

    @Test
    fun speakTextIsReadableEnglish() {
        val cue = Cue.Speak(Speech.SetNumber(2), Speech.RoundNumber(3), Speech.Work(4))
        assertEquals("Set 2. Round 3. Work 4", cue.text)
    }

    @Test
    fun speechNeededListsWhatTheWorkoutSaysInOrder() {
        val engine = TimerEngine(
            TimerConfig(sets = 2, rounds = 2, setRestSeconds = 3, tasks = listOf(work(5), work(5), rest(3)))
        )
        val needed = engine.speechNeeded()
        assertEquals(
            listOf(
                Speech.GetReady, Speech.Work(1), Speech.Work(2), Speech.Rest,
                Speech.RoundNumber(2), Speech.RestBetweenSets, Speech.SetNumber(2), Speech.WellDone
            ),
            needed
        )
    }

    @Test
    fun speechNeededCoversTheCountdownAnnouncements() {
        val short = TimerEngine(TimerConfig(tasks = listOf(work(20), rest(5)))).speechNeeded()
        assertTrue(Speech.TenSeconds !in short && Speech.Halfway !in short)

        val medium = TimerEngine(TimerConfig(tasks = listOf(work(30), rest(5)))).speechNeeded()
        assertTrue(Speech.TenSeconds in medium && Speech.Halfway !in medium)

        val long = TimerEngine(TimerConfig(tasks = listOf(work(60), rest(5)))).speechNeeded()
        assertTrue(Speech.TenSeconds in long && Speech.Halfway in long)
    }

    @Test
    fun speechNeededIsEverythingTheWorkoutActuallySays() {
        val engine = TimerEngine(
            TimerConfig(sets = 2, rounds = 2, setRestSeconds = 15, tasks = listOf(work(40), rest(10), work(65)))
        )
        val needed = engine.speechNeeded().toSet()
        val spoken = HashSet<Speech>()
        val runner = TimerEngine(
            TimerConfig(sets = 2, rounds = 2, setRestSeconds = 15, tasks = listOf(work(40), rest(10), work(65)))
        )
        var t = 0L
        spoken += runner.start(t).cues.filterIsInstance<Cue.Speak>().flatMap { it.parts }
        while (runner.status == TimerStatus.RUNNING) {
            spoken += runner.tick(t).cues.filterIsInstance<Cue.Speak>().flatMap { it.parts }
            t += 250
        }
        assertEquals(emptySet<Speech>(), spoken - needed)
    }

    @Test
    fun forTime_totalTimeCapEndsTheWholeWorkout() {
        val engine = TimerEngine(
            TimerConfig(mode = TimerMode.FOR_TIME, sets = 2, setRestSeconds = 5, totalTimeCap = 30)
        )
        engine.start(0)
        var t = 0L
        val said = ArrayList<List<Speech>>()
        while (engine.status == TimerStatus.RUNNING && t <= 120_000) {
            said += engine.tick(t).cues.filterIsInstance<Cue.Speak>().map { it.parts }
            t += 10
        }
        assertEquals(TimerStatus.FINISHED, engine.status)
        // "Get ready" phase excluded from cap
        assertTrue("finished at $t", t in 40_000..40_020)
        assertEquals(30_000L, engine.snapshot(t).laps.single().durationMs)
        assertTrue(said.any { it == listOf(Speech.TenSeconds) })
    }

    @Test
    fun pauseAndResumeDoNotRepeatACountdownBeep() {
        val engine = TimerEngine(TimerConfig(tasks = listOf(work(10))))
        var beeps = 0
        fun EngineResult.count() { beeps += cues.count { it == Cue.Beep } }
        engine.start(0).count()
        var t = 0L
        while (t < 17_500) { engine.tick(t).count(); t += 10 }
        engine.pause(17_500).count()
        t = 30_000
        engine.resume(t).count()
        while (engine.status == TimerStatus.RUNNING) { engine.tick(t).count(); t += 10 }
        // Countdown beeps before work phase and completion
        assertEquals(6, beeps)
    }

    @Test
    fun onlySegmentAnnouncementsInterruptTheVoice() {
        val engine = TimerEngine(TimerConfig(tasks = listOf(work(60), rest(20))))
        val speaks = ArrayList<Cue.Speak>()
        var t = 0L
        speaks += engine.start(t).cues.filterIsInstance<Cue.Speak>()
        while (engine.status == TimerStatus.RUNNING) {
            speaks += engine.tick(t).cues.filterIsInstance<Cue.Speak>()
            t += 10
        }
        val countdown = speaks.filter { it.parts == listOf(Speech.Halfway) || it.parts == listOf(Speech.TenSeconds) }
        assertTrue(countdown.isNotEmpty())
        assertTrue(countdown.none { it.interrupt })

        val segmentStarts = speaks.filter { cue -> cue.parts.any { it is Speech.Work || it == Speech.Rest || it == Speech.WellDone } }
        assertTrue(segmentStarts.size >= 3)
        assertTrue(segmentStarts.all { it.interrupt })
    }

    @Test
    fun theLapListIsTheSameObjectUntilALapIsLogged() {
        val engine = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME))
        engine.start(0)
        engine.tick(10_000)
        engine.lap(12_000)
        val a = engine.snapshot(13_000).laps
        val b = engine.snapshot(14_000).laps
        assertTrue(a === b)
        engine.lap(15_000)
        assertEquals(2, engine.snapshot(16_000).laps.size)
    }
}
