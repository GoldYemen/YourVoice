# Keep the JNI-facing audio bridge: methods are resolved by name from C++.
-keep class com.sotak.app.audio.AudioEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Compose runtime metadata.
-keepattributes *Annotation*
-dontwarn org.jetbrains.annotations.**
