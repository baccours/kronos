# ProGuard / R8 rules for Kronos

# Keep Parcelable implementations and CREATOR fields (required for @Parcelize and Intent extras)
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keep class **$Creator implements android.os.Parcelable$Creator {
    *;
}

# Keep model classes, data models, enums, and parcelables
-keep class com.baccours.kronos.model.** { *; }

# Keep kotlinx.serialization generated serializers and annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class com.baccours.kronos.model.** {
    *** Companion;
    *** serializer(...);
}

-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable *;
}
