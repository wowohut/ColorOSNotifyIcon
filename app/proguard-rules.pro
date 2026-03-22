# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-ignorewarnings
-optimizationpasses 10
-dontusemixedcaseclassnames
-dontoptimize
# 仅启用 R8 的压缩/裁剪（shrink），不做任何类/方法/字段重命名（obfuscate）。
# 这样可以最大化避免：
# - AIDL/Binder DESCRIPTOR 与外部进程不一致（例如 libxposed.service）
# - 反射/框架入口（Xposed/LibXposed）依赖类名导致的兼容性问题
-dontobfuscate
-verbose
-overloadaggressively
-allowaccessmodification
-adaptclassstrings
-adaptresourcefilenames
-adaptresourcefilecontents

-renamesourcefileattribute P
-keepattributes SourceFile,Signature,LineNumberTable

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static *** throwUninitializedProperty(...);
    public static *** throwUninitializedPropertyAccessException(...);
}

-keep class * extends android.app.Activity
-keep class * implements androidx.viewbinding.ViewBinding {
    <init>();
    *** inflate(android.view.LayoutInflater);
}

############################################
# LibXposed / LSPosed 模块入口
############################################
# 框架会读取 META-INF/xposed/java_init.list 中的类名并通过反射加载模块入口。
# 开启 R8 混淆/压缩后，若入口类被重命名或被裁剪，将出现“App 能打开但模块不生效”的现象。
-keep class com.fankes.coloros.notify.hook.ModuleMain { *; }

# 说明：当前 Release 已使用 -dontobfuscate（不重命名），因此无需额外保留 libxposed.service。
# 若你未来重新开启混淆（移除 -dontobfuscate），再考虑添加：
#   -keep class io.github.libxposed.service.** { *; }
