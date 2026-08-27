# Firestore data classes are reflectively mapped - keep model fields.
-keepclassmembers class uk.co.rodrunners.raffles.data.model.** { *; }
-keep class uk.co.rodrunners.raffles.data.model.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }

# Stripe
-keep class com.stripe.android.** { *; }
-dontwarn com.stripe.android.**

# Firebase
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
