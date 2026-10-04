# Applied to apps that use the plugin. The code compiles against every ML Kit script model but
# only the ones enabled with a Gradle flag (nativeOcrChinese and so on) ship, so R8 must not
# fail on the others' missing classes. The code checks BuildConfig before touching them.
-dontwarn com.google.mlkit.vision.text.chinese.**
-dontwarn com.google.mlkit.vision.text.devanagari.**
-dontwarn com.google.mlkit.vision.text.japanese.**
-dontwarn com.google.mlkit.vision.text.korean.**
