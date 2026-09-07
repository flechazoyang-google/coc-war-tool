# COC War Tool ProGuard/R8 Rules

# === Gson：保留通过反射访问的模型类（Room Converters 用 Gson 序列化 Attack） ===
-keep class com.cocwar.data.model.** { *; }
-keepclassmembers class com.cocwar.data.model.** { *; }

# === Room ===
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# === 枚举（Gson 反序列化需要） ===
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
