# Receivers and activities are referenced from the manifest; keep their names.
-keep class com.remindly.app.alarm.** { *; }
-keep class com.remindly.app.App { *; }

# JSON persistence uses explicit keys, nothing reflective to keep.
