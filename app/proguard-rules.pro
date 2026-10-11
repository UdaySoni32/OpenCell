# Shizuku instantiates the user service by class name in another process; the probe is run via app_process.
-keep class com.opencell.voice.shizuku.** { *; }

# Ktor server references optional JVM/logging classes that don't exist on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn io.netty.**
-dontwarn reactor.blockhound.**

# Ktor loads some plugins/engines through ServiceLoader.
-keep class io.ktor.server.config.** { *; }
-keep class io.ktor.serialization.kotlinx.** { *; }
-keepnames class io.ktor.** { *; }

# Wire DTOs are serialized by kotlinx.serialization (generated serializers are kept by the library's rules,
# this keeps the names readable in crash reports).
-keepnames class com.opencell.gateway.** { *; }
