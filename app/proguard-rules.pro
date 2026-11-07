# R8 / ProGuard keep rules for ShowMeOutfit (SMO-S4-05).
#
# The release build enables `isMinifyEnabled` and `isShrinkResources`, so every entry here protects
# a construct that R8 cannot see through statically. Keep the list narrow: each `-keep` costs APK
# size, and the release AAB is already dominated by Compose and Ktor.

# --- kotlinx.serialization ---------------------------------------------------------------------
# The compiler plugin emits a synthetic `Companion.serializer()` per @Serializable type. R8 must not
# rename or remove it, otherwise `response.body<T>()` fails at runtime with a missing-serializer
# error that only appears in release builds.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.smoh.showmeoutfit.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.smoh.showmeoutfit.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Koin --------------------------------------------------------------------------------------
# Module declarations resolve constructors reflectively; obfuscated names break `single { ... }`.
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# --- Ktor --------------------------------------------------------------------------------------
# Engines and content converters are discovered through ServiceLoader, and the mock engine is used
# by unit tests only. Keep the public surface so the service files keep resolving.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keepclassmembers class * implements io.ktor.client.engine.HttpClientEngineFactory {
    <init>(...);
}

# --- Room --------------------------------------------------------------------------------------
# Generated `*_Impl` classes extend RoomDatabase and are looked up by name at runtime.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- WorkManager -------------------------------------------------------------------------------
# Workers are instantiated reflectively from the persisted WorkSpec class name.
-keep class * extends androidx.work.ListenableWorker { *; }

# --- Domain readability ------------------------------------------------------------------------
# Domain data classes are not reflected on, but keeping their names makes release stack traces and
# the R8 mapping usable during incident triage.
-keepnames class com.smoh.showmeoutfit.domain.model.** { *; }
