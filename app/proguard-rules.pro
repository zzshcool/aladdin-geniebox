# Keep JNI native entry points
-keepclasseswithmembernames class * {
    native <methods>;
}

# Room
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
