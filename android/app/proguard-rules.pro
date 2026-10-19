# kotlinx.serialization keeps its generated serializers via its own consumer rules.
# Retrofit interfaces and Room/Hilt/WorkManager are covered by their libraries' consumer rules.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# The app's own enums are read by name from JSON and from Room (valueOf, entries): keep their names.
-keepclassmembers enum com.example.fitapp.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Release builds carry no debug or verbose logging.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}