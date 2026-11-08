# Keep line numbers so crash logs from release builds point at real lines.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit document scanner and text recognition (Scan a card): the release build crashed starting the scanner
# (NullPointerException inside com.google.mlkit.vision.documentscanner, emulator 2026-11-08) until these were kept.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
