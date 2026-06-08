package com.fankes.coloros.notify.hook

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.os.BundleCompat
import com.fankes.coloros.notify.core.ModuleInfo
import com.fankes.coloros.notify.core.SystemPackages
import com.fankes.coloros.notify.hook.icon.IconBitmapClassifier
import com.fankes.coloros.notify.hook.reflect.Reflection
import com.fankes.coloros.notify.hook.systemui.SystemUiMembers
import com.fankes.coloros.notify.rules.IconRule
import com.fankes.coloros.notify.rules.RuleStore
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

class ModuleMain : XposedModule() {

    companion object {
        private const val EXTRA_ORIGINAL_SMALL_ICON = "com.fankes.coloros.notify.original_small_icon"
    }

    private val onceLogs = ConcurrentHashMap.newKeySet<String>()
    private var systemServerInstalled = false
    private var systemUiInstalled = false
    private var systemUiConfig = RuleStore.ModuleConfig()
    private var systemUiRules: Map<String, IconRule> = emptyMap()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        emitLog(
            Log.INFO,
            "模块已加载：process=${param.processName}, framework=$frameworkName, api=$apiVersion"
        )
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        if (systemServerInstalled) return
        systemServerInstalled = installSystemServerHook(param.classLoader)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != SystemPackages.SYSTEM_UI || !param.isFirstPackage || systemUiInstalled) return
        reloadSystemUiConfig()
        systemUiInstalled = installSystemUiHooks(param.classLoader)
    }

    private fun reloadSystemUiConfig() {
        val remotePrefs = remotePrefsOrNull()
        val rulesJson = loadRemoteRulesJson()
        systemUiConfig = RuleStore.readModuleConfig(remotePrefs)
        systemUiRules = RuleStore.applyRuleOverrides(
            rules = RuleStore.parseRules(rulesJson),
            source = remotePrefs,
        ).associateBy { it.packageName }
        emitLog(
            Log.INFO,
            "SystemUI 配置已加载：enabled=${systemUiConfig.moduleEnabled}, rulesEnabled=${systemUiConfig.rulesEnabled}, rules=${systemUiRules.size}"
        )
        val mirroredRuleCount = remotePrefs?.getInt(RuleStore.KEY_RULES_COUNT, 0) ?: 0
        if (rulesJson.isBlank() && mirroredRuleCount > 0) {
            warnOnce("systemui.rules.missing", "SystemUI 远程规则文件为空，但远程规则计数不为 0，可能是规则镜像未完成")
        }
        if (systemUiRules.isEmpty()) {
            warnOnce("systemui.rules.empty", "SystemUI 规则为空，通知图标仅保留原始灰度增强")
        }
    }

    private fun installSystemServerHook(classLoader: ClassLoader): Boolean {
        val helperClass = loadClassOrNull("com.android.server.notification.OplusNotificationFixHelper", classLoader)
            ?: return warnAndFalse("system.fix.helper", "未找到 OplusNotificationFixHelper，跳过 system_server Hook")
        val fixSmallIconMethod = Reflection.findMethod(
            helperClass,
            "fixSmallIcon",
            Notification::class.java,
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType!!,
        ) ?: return warnAndFalse("system.fix.method", "未找到 fixSmallIcon(Notification, String, String, boolean)")

        hook(fixSmallIconMethod).intercept { chain ->
            val notification = chain.args.firstOrNull() as? Notification
            val originalIcon = notification?.smallIcon
            if (notification != null && originalIcon != null) {
                runCatching {
                    notification.extras.putParcelable(EXTRA_ORIGINAL_SMALL_ICON, originalIcon)
                }.onFailure {
                    warnOnce("system.fix.cache.original", "缓存原始 smallIcon 失败", it)
                }
            }
            infoOnce("system.fix.hit", "fixSmallIcon 已命中，已拦截 ColorOS 覆盖 smallIcon")
            null
        }
        emitLog(
            Log.INFO,
            "system_server Hook 已安装：拦截 fixSmallIcon 覆盖通知 smallIcon"
        )
        return true
    }

    private fun installSystemUiHooks(classLoader: ClassLoader): Boolean {
        if (!systemUiConfig.moduleEnabled) {
            warnOnce("systemui.config.disabled", "模块配置已停用，跳过 SystemUI Hook")
            return false
        }
        val members = SystemUiMembers.resolve(classLoader, ::warnOnce) ?: return false

        loadClassOrNull(
            "com.oplus.systemui.statusbar.notification.util.OplusNotificationSmallIconUtil",
            classLoader
        )?.let { utilClass ->
            Reflection.findMethod(
                utilClass,
                "useAppIconForSmallIcon",
                Notification::class.java,
            )?.let { method ->
                hook(method).intercept { false }
                emitLog(Log.INFO, "SystemUI Hook：已禁用 OplusNotificationSmallIconUtil.useAppIconForSmallIcon")
            } ?: warnOnce(
                "systemui.useAppIcon.missing",
                "未找到 OplusNotificationSmallIconUtil.useAppIconForSmallIcon(Notification)"
            )
        }

        hook(members.statusBarUpdateGrayScale).intercept { chain ->
            val drawable = chain.args.getOrNull(0) as? Drawable ?: return@intercept chain.proceed()
            val statusBarIconView = chain.args.getOrNull(1) ?: return@intercept chain.proceed()
            val sbn = chain.args.getOrNull(2) as? StatusBarNotification ?: return@intercept chain.proceed()
            runCatching {
                members.statusBarSetIsIconColorable.invoke(statusBarIconView, IconBitmapClassifier.isGrayscaleDrawable(drawable))
            }.onFailure {
                warnOnce("systemui.statusbar.grayscale", "状态栏灰度判定替换失败", it)
                return@intercept chain.proceed()
            }
            null
        }

        hook(members.iconManagerGetIconDescriptor).intercept { chain ->
            val result = chain.proceed()
            val statusBarIcon = result ?: return@intercept result
            val notificationEntry = chain.args.getOrNull(0) ?: return@intercept statusBarIcon
            val iconManager = chain.thisObject ?: return@intercept statusBarIcon
            val context = runCatching {
                val iconBuilder = members.iconManagerIconBuilderField.get(iconManager)
                members.iconBuilderContextField.get(iconBuilder) as? Context
            }.getOrNull() ?: return@intercept statusBarIcon
            val sbn = runCatching {
                members.notificationEntryGetSbn.invoke(notificationEntry) as? StatusBarNotification
            }.getOrNull() ?: return@intercept statusBarIcon
            val currentStatusBarIcon = runCatching {
                members.statusBarIconField.get(statusBarIcon) as? Icon
            }.getOrNull()
            val replacementIcon = resolveStatusBarReplacementIcon(context, sbn, currentStatusBarIcon)
                ?: return@intercept statusBarIcon
            runCatching {
                members.statusBarIconField.set(statusBarIcon, replacementIcon)
                members.statusBarPreloadedIconField.set(statusBarIcon, null)
            }.onFailure {
                warnOnce("systemui.statusbar.icon.replace", "状态栏规则图标注入失败", it)
            }
            statusBarIcon
        }
        emitLog(
            Log.INFO,
            "SystemUI Hook 已安装：状态栏图标路径（IconManager.getIconDescriptor）"
        )
        return true
    }

    private fun resolveStatusBarReplacementIcon(
        context: Context,
        sbn: StatusBarNotification,
        currentStatusBarIcon: Icon?,
    ): Icon? {
        val packageName = sbn.packageName.orEmpty()
        val preservedOriginalIcon = runCatching {
            BundleCompat.getParcelable(sbn.notification.extras, EXTRA_ORIGINAL_SMALL_ICON, Icon::class.java)
        }.getOrNull()
        val baseStatusBarIcon = preservedOriginalIcon ?: sbn.notification.smallIcon
        val statusBarOriginalDrawable = runCatching {
            baseStatusBarIcon?.loadDrawable(context)?.mutate()
        }.getOrNull() ?: return null
        val originalIsGrayscale = IconBitmapClassifier.isGrayscaleDrawable(statusBarOriginalDrawable)
        val currentIsGrayscale = runCatching {
            currentStatusBarIcon?.loadDrawable(context)?.mutate()?.let(IconBitmapClassifier::isGrayscaleDrawable)
        }.getOrNull()
        if (originalIsGrayscale && currentIsGrayscale == false) {
            infoOnce(
                "systemui.statusbar.prefer.original:$packageName",
                "检测到应用已提供原生灰度 smallIcon，但 SystemUI 当前使用彩色图标，已恢复为原始 smallIcon"
            )
            return baseStatusBarIcon
        }

        if (!systemUiConfig.rulesEnabled) return null
        val rule = systemUiRules[packageName]?.takeIf { it.isEnabled } ?: return null
        val shouldUseRule = rule.isEnabledAll || !originalIsGrayscale
        if (!shouldUseRule) return null
        infoOnce("systemui.statusbar.rule.hit", "SystemUI 命中状态栏规则图标路径")
        return runCatching { Icon.createWithBitmap(rule.iconBitmap) }.getOrNull()
    }

    private fun remotePrefsOrNull(): SharedPreferences? = runCatching {
        getRemotePreferences(RuleStore.GROUP_CONFIG)
    }.getOrNull()

    private fun loadRemoteRulesJson(): String = runCatching {
        openRemoteFile(RuleStore.RULES_FILE_NAME).use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
        }
    }.getOrDefault("")

    private fun warnOnce(key: String, message: String, throwable: Throwable? = null) {
        if (!onceLogs.add(key)) return
        if (throwable == null) emitLog(Log.WARN, message)
        else emitLog(Log.ERROR, message, throwable)
    }

    private fun infoOnce(key: String, message: String) {
        if (!onceLogs.add(key)) return
        emitLog(Log.INFO, message)
    }

    private fun emitLog(priority: Int, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            log(priority, ModuleInfo.LOG_TAG, message)
            Log.println(priority, ModuleInfo.LOG_TAG, message)
        } else {
            log(priority, ModuleInfo.LOG_TAG, message, throwable)
            Log.println(priority, ModuleInfo.LOG_TAG, "$message\n${Log.getStackTraceString(throwable)}")
        }
    }

    private fun warnAndFalse(key: String, message: String): Boolean {
        warnOnce(key, message)
        return false
    }

    private fun loadClassOrNull(name: String, classLoader: ClassLoader): Class<*>? =
        Reflection.loadClassOrNull(name, classLoader) {
            warnOnce("class:$name", "未找到类：$name", it)
        }
}
