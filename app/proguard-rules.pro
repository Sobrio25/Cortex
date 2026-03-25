# Add project specific ProGuard rules here.
-keep class com.aiagents.app.data.local.** { *; }
-keep class com.aiagents.app.data.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# MediaPipe
-keep class com.google.mediapipe.tasks.genai.llminference.** { *; }
-keep class com.google.mediapipe.tasks.genai.llminference.jni.proto.** { *; }
-keep class com.google.mediapipe.framework.image.** { *; }
-keep class com.google.mediapipe.framework.** { *; }

# Protobuf
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# AutoValue
-keep class com.google.auto.value.** { *; }
-dontwarn com.google.auto.value.**

# sherpa-onnx (local STT via ONNX Whisper)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# Vosk (lightweight offline STT)
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**