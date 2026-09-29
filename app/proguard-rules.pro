-dontobfuscate
-keepattributes SourceFile,LineNumberTable
-ignorewarnings

# NewPipe extractor (uses reflection in places)
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class com.grack.nanojson.** { *; }

# Rhino (used by the extractor to decipher YouTube streams)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

# Protobuf lite
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

# osmdroid
-dontwarn org.osmdroid.**

# JavaScript bridge in the browser
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
