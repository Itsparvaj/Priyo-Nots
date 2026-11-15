# ProGuard / R8 rules for Priyo Nots
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Room entities & DAOs safely
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**
