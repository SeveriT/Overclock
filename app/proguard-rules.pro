# ── Stack traces ──────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }

# ── Retrofit + Gson ───────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*

-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Preserve generic type info on TypeToken<T>() {} anonymous subclasses we use
# to deserialize List<...> from cached JSON.
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken

-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation interface * extends retrofit2.Call
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ── OkHttp ────────────────────────────────────────────────────────────────────
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── App data models (Gson deserializes these) ─────────────────────────────────
-keep class com.serkka.tracker.AiWorkoutEntry { *; }
-keep class com.serkka.tracker.AiWorkoutResponse { *; }
-keep class com.serkka.tracker.Workout { *; }
-keep class com.serkka.tracker.BodyWeight { *; }
-keep class com.serkka.tracker.Note { *; }
-keep class com.serkka.tracker.WorkoutSession { *; }

# ── Strava API models ────────────────────────────────────────────────────────
-keep class com.serkka.tracker.StravaApi$* { *; }
-keep interface com.serkka.tracker.StravaApi { *; }
-keep class com.serkka.tracker.TokenResponse { *; }
-keep class com.serkka.tracker.StravaAthlete { *; }
-keep class com.serkka.tracker.StravaActivity { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Firebase ──────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── Google Play Billing ───────────────────────────────────────────────────────
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }

# ── Google Drive / Auth ───────────────────────────────────────────────────────
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.**

# ── Encrypted SharedPreferences ───────────────────────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ── Compose ───────────────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Coil ──────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── R8 full mode compatibility ────────────────────────────────────────────────
-dontwarn java.lang.invoke.StringConcatFactory

# ── Apache HTTP / Google API Client (javax.naming not on Android) ─────────────
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
