
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.annotation.**


-dontwarn jdk.dynalink.**



-allowaccessmodification
-repackageclasses






-keep class org.schabi.newpipe.extractor.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

-dontwarn androidx.media3.**

# Release logs are not user-visible; dropping the calls also drops every format string.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
