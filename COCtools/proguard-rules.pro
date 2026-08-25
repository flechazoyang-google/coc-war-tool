# COC War Tool ProGuard/R8 Rules

# === Gson：保留通过反射访问的 DTO 和模型类 ===
-keep class com.cocwar.data.model.** { *; }
-keepclassmembers class com.cocwar.data.model.** { *; }

# 备份/恢复数据类（BackupCodec 通过 Gson 反序列化）
-keep class com.cocwar.data.repository.BackupData { *; }
-keep class com.cocwar.data.repository.BackupEvent { *; }
-keep class com.cocwar.data.repository.BackupMember { *; }
-keep class com.cocwar.data.repository.BackupAttack { *; }

# === Room ===
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# === 枚举（Gson 反序列化需要） ===
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
