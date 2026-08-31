# OpenCell ProGuard Rules

# Keep Ktor/Netty classes
-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }

# Keep Room entities
-keep class io.opencell.core.database.entity.** { *; }

# Keep serializable models
-keep class io.opencell.core.model.** { *; }

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * implements androidx.room.TypeConverter { *; }

# Bouncy Castle
-keep class org.bouncycastle.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
