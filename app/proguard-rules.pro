# Media3 keeps its model classes by reflection; R8 must not rename them.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# kotlinx.serialization keeps the generated serializers through the companion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.soluplay.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.soluplay.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
