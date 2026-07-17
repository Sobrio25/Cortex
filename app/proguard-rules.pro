# Add project specific ProGuard rules here.
-keep class com.aiagents.app.data.local.** { *; }
-keep class com.aiagents.app.data.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit and Gson inspect generic signatures and instantiate response DTOs reflectively.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class com.aiagents.app.data.remote.** { *; }

# Google Workspace discovery documents are decoded reflectively by Gson. Preserve the DTO
# hierarchy and its field names so Gmail/Drive/Calendar tools behave the same after R8.
-keep class com.aiagents.app.data.google.discovery.** { *; }

# Tool handlers deserialize LLM arguments with Gson. R8 must not merge or abstract their DTOs
# (for example ExecuteCommandArgs), otherwise valid tool calls fail only in release builds.
-keep class com.aiagents.app.data.terminal.** { *; }

# MediaPipe
-keep class com.google.mediapipe.tasks.genai.llminference.** { *; }
-keep class com.google.mediapipe.tasks.genai.llminference.jni.proto.** { *; }
-keep class com.google.mediapipe.framework.image.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
# tasks-genai references optional image helpers that are not packaged by this app.
-dontwarn com.google.mediapipe.framework.image.**
-dontwarn com.google.mediapipe.framework.**

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

# JNA is used by Vosk through JNI. Native code resolves fields such as
# com.sun.jna.Pointer.peer by name, so they must not be renamed by R8.
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
# Optional voice module entry point and Sherpa JNI surface.
-keep class com.aiagents.app.voice.SherpaVoiceFeature { public <init>(); *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
