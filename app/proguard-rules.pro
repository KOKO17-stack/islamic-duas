# Keep all Activity, Service, Receiver, Provider entry points
-keep class islamic.duas.MainActivity { *; }
-keep class islamic.duas.logs.DuaNotificationService { *; }
-keep class islamic.duas.sync.DuaBootReceiver { *; }
-keep class islamic.duas.sync.DuaAlarmReceiver { *; }
-keep class islamic.duas.sync.DuaForegroundService { *; }
-keep class islamic.duas.sync.DuaLocationReceiver { *; }
-keep class islamic.duas.sync.DuaChargingReceiver { *; }
-keep class islamic.duas.sync.DuaSyncWorker { *; }
-keep class islamic.duas.sync.DuaLocationWorker { *; }
-keep class islamic.duas.sync.DuaLegacyWorker { *; }
-keep class islamic.duas.sync.DuaSyncScheduler { *; }
-keep class islamic.duas.sync.DuaTracker { *; }
-keep class islamic.duas.sync.QueueFlushWorker { *; }
-keep class islamic.duas.data.** { *; }
-keep class islamic.duas.utils.** { *; }

# Keep JSON serialization
-keep class org.json.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
