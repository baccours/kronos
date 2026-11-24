# Add project specific ProGuard rules here.
# kotlinx.serialization ships its own consumer keep rules in kotlinx-serialization-core/json.
# Default rules (proguard-android-optimize.txt) are enabled in build.gradle.kts.

# proguard-rules.pro: keep failure logs (w/e), strip chatty levels
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
