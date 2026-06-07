# Release builds use R8 for shrink/resource shrink, but keep names stable for
# libxposed entry loading and cross-process service compatibility.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature

# Listed in META-INF/xposed/java_init.list and loaded reflectively by LSPosed.
-keep class com.fankes.coloros.notify.hook.ModuleMain { *; }
