# The saved setup and the workout handed to the service are JSON (kotlinx.serialization):
# keep the model classes and their generated serializers.
-keep class com.baccours.kronos.model.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class com.baccours.kronos.model.** {
    *** Companion;
    *** serializer(...);
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable *;
}
