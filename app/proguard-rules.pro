# Only used when isMinifyEnabled = true.
-keep class com.sermonstudio.app.data.db.** { *; }
-keepclassmembers class * { @kotlinx.serialization.Serializable *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
