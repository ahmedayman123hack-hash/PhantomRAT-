# Keep everything - extreme obfuscation
-keepattributes *
-keep class com.phantom.rat.** { *; }
-dontwarn
-dontoptimize
-dontpreverify
-verbose

# String encryption
-encryptstrings class com.phantom.rat.core.C2Client
-encryptstrings class com.phantom.rat.core.CommandHandler
-encryptstrings class com.phantom.rat.ui.BuilderFragment

# Remove logging
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Obfuscation
-repackageclasses 'a.b.c'
-allowaccessmodification
-overloadaggressively
-useuniqueclassmembernames
-flattenpackagehierarchy 'x'￼Enter
