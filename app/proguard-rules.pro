# Keep kotlinx.serialization metadata for our @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.iptv.player.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
