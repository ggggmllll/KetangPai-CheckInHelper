# OpenCV/WeChat QR classes are accessed through JNI and model-backed native code.
# Keep their public bridge methods when R8 optimizes the release APK.
-keep class org.opencv.** { *; }
