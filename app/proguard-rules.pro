# R8 is currently OFF for release builds (isMinifyEnabled = false in app/build.gradle.kts),
# so these rules are not applied today. They are kept minimal and correct so that turning
# minification on later works without broad "keep everything" rules.
#
# Not needed here: Room, Firebase, Google Play Billing, Compose, Coil and Kotlin ship their
# own consumer rules inside their AARs.

# ── Readable stack traces ─────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Google Drive API client (backups) ─────────────────────────────────────────
# The client maps JSON to fields annotated with @Key via reflection, using generic type
# info, so keep those fields and the signatures/annotations they rely on.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}

# Optional JVM-only dependencies of the Google API / HTTP client that don't exist on Android
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn com.google.api.client.extensions.appengine.**

# ── R8 full mode ──────────────────────────────────────────────────────────────
-dontwarn java.lang.invoke.StringConcatFactory
