package com.fankes.coloros.notify.hook.systemui

import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import com.fankes.coloros.notify.hook.reflect.Reflection
import java.lang.reflect.Field
import java.lang.reflect.Method

internal data class SystemUiMembers(
    val statusBarUpdateGrayScale: Method,
    val statusBarSetIsIconColorable: Method,
    val iconManagerGetIconDescriptor: Method,
    val iconManagerIconBuilderField: Field,
    val iconBuilderContextField: Field,
    val notificationEntryGetSbn: Method,
    val statusBarIconField: Field,
    val statusBarPreloadedIconField: Field,
) {
    companion object {
        fun resolve(
            classLoader: ClassLoader,
            warn: (String, String, Throwable?) -> Unit,
        ): SystemUiMembers? {
            fun warnMissing(key: String, message: String): SystemUiMembers? {
                warn(key, message, null)
                return null
            }

            fun load(name: String): Class<*>? = Reflection.loadClassOrNull(name, classLoader) {
                warn("class:$name", "未找到类：$name", it)
            }

            val statusBarIconViewClass = load("com.android.systemui.statusbar.StatusBarIconView")
                ?: return null
            val statusBarIconControllerClass = load("com.oplus.systemui.statusbar.phone.StatusBarIconControllerExImpl")
                ?: return null
            val iconManagerClass = load("com.android.systemui.statusbar.notification.icon.IconManager")
                ?: return null
            val iconBuilderClass = load("com.android.systemui.statusbar.notification.icon.IconBuilder")
                ?: return null
            val notificationEntryClass = load("com.android.systemui.statusbar.notification.collection.NotificationEntry")
                ?: return null
            val statusBarIconClass = load("com.android.internal.statusbar.StatusBarIcon")
                ?: return null

            val statusBarUpdateGrayScale = Reflection.findMethod(
                statusBarIconControllerClass,
                "updateStatusBarIconGrayScale",
                Drawable::class.java,
                statusBarIconViewClass,
                StatusBarNotification::class.java,
            ) ?: return warnMissing(
                "member:statusbar.gray",
                "未找到 StatusBarIconControllerExImpl.updateStatusBarIconGrayScale"
            )
            val statusBarSetIsIconColorable = Reflection.findMethod(
                statusBarIconViewClass,
                "setIsIconColorable",
                Boolean::class.javaPrimitiveType!!,
            ) ?: return warnMissing("member:statusbar.colorable", "未找到 StatusBarIconView.setIsIconColorable(boolean)")
            val iconManagerGetIconDescriptor = Reflection.findMethod(
                iconManagerClass,
                "getIconDescriptor",
                notificationEntryClass,
                Boolean::class.javaPrimitiveType!!,
            ) ?: return warnMissing(
                "member:iconmanager.getIconDescriptor",
                "未找到 IconManager.getIconDescriptor(NotificationEntry, boolean)"
            )
            val iconManagerIconBuilderField = Reflection.findField(iconManagerClass, "iconBuilder")
                ?: return warnMissing("member:iconmanager.iconBuilder", "未找到 IconManager.iconBuilder")
            val iconBuilderContextField = Reflection.findField(iconBuilderClass, "context")
                ?: return warnMissing("member:iconbuilder.context", "未找到 IconBuilder.context")
            val notificationEntryGetSbn = Reflection.findMethod(notificationEntryClass, "getSbn")
                ?: return warnMissing("member:entry.getSbn", "未找到 NotificationEntry.getSbn()")
            val statusBarIconField = Reflection.findField(statusBarIconClass, "icon")
                ?: return warnMissing("member:statusbar.icon", "未找到 StatusBarIcon.icon")
            val statusBarPreloadedIconField = Reflection.findField(statusBarIconClass, "preloadedIcon")
                ?: return warnMissing("member:statusbar.preloaded", "未找到 StatusBarIcon.preloadedIcon")

            return SystemUiMembers(
                statusBarUpdateGrayScale = statusBarUpdateGrayScale,
                statusBarSetIsIconColorable = statusBarSetIsIconColorable,
                iconManagerGetIconDescriptor = iconManagerGetIconDescriptor,
                iconManagerIconBuilderField = iconManagerIconBuilderField,
                iconBuilderContextField = iconBuilderContextField,
                notificationEntryGetSbn = notificationEntryGetSbn,
                statusBarIconField = statusBarIconField,
                statusBarPreloadedIconField = statusBarPreloadedIconField,
            )
        }
    }
}
