package com.fankes.coloros.notify.hook.icon

import android.app.Notification
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import com.fankes.coloros.notify.rules.IconRule
import com.fankes.coloros.notify.rules.RuleStore

internal class NotificationIconResolver(
    private val config: RuleStore.ModuleConfig,
    private val rules: Map<String, IconRule>,
) {

    data class PanelIconRenderPlan(
        val drawable: Drawable,
        val tintColor: Int?,
        val clipToOutline: Boolean,
        val source: PanelIconSource,
    )

    enum class PanelIconSource {
        Theme,
        Rule,
        Placeholder,
        Original,
    }

    fun resolveStatusBarIcon(
        context: Context,
        sbn: StatusBarNotification,
        originalSmallIcon: Icon?,
        currentStatusBarIcon: Icon?,
    ): Icon? {
        if (sbn.shouldKeepColorOsDefault()) return null
        resolveThemeIconReplacement(context, sbn)?.let { return it.toIcon() }
        val originalDrawable = originalSmallIcon?.loadDrawableOrNull(context) ?: return null
        val originalIsGrayscale = IconBitmapClassifier.isGrayscaleDrawable(originalDrawable)
        resolveRuleIconReplacement(sbn, originalIsGrayscale)?.let { return it.toIcon() }

        if (originalIsGrayscale && currentStatusBarIcon?.isGrayscale(context) == false) return originalSmallIcon
        return null
    }

    fun resolvePanelIconPlan(
        context: Context,
        sbn: StatusBarNotification,
        originalSmallIcon: Icon?,
        currentDrawable: Drawable?,
    ): PanelIconRenderPlan? {
        if (!config.panelIconReplacementEnabled || sbn.isMediaNotification()) return null
        if (sbn.shouldKeepColorOsDefault()) return null
        resolveThemeIconReplacement(context, sbn)?.let { return it.toPanelIconRenderPlan(context) }

        val originalDrawable = originalSmallIcon?.loadDrawableOrNull(context) ?: return null
        val originalIsGrayscale = IconBitmapClassifier.isGrayscaleDrawable(originalDrawable)
        resolveRuleIconReplacement(sbn, originalIsGrayscale)?.let { return it.toPanelIconRenderPlan(context) }

        if (originalIsGrayscale && currentDrawable?.let(IconBitmapClassifier::isGrayscaleDrawable) == false) {
            return PanelIconRenderPlan(
                drawable = originalDrawable,
                tintColor = context.defaultPanelIconTint,
                clipToOutline = false,
                source = PanelIconSource.Original,
            )
        }
        return null
    }

    private fun resolveThemeIconReplacement(
        context: Context,
        sbn: StatusBarNotification,
    ): IconReplacement? {
        if (!config.rulesEnabled) return null
        if (config.iconSourceMode != RuleStore.IconSourceMode.DesktopTheme) return null
        return ThemeIconProvider.resolve(context, sbn)?.let(IconReplacement::ThemeIcon)
    }

    private fun resolveRuleIconReplacement(
        sbn: StatusBarNotification,
        originalIsGrayscale: Boolean,
    ): IconReplacement? {
        if (!config.rulesEnabled) return null
        if (config.iconSourceMode != RuleStore.IconSourceMode.RuleLibrary) return null
        rules[sbn.rulePackageName()]?.takeIf { it.isEnabled }?.let { rule ->
            if (rule.isEnabledAll || !originalIsGrayscale) return IconReplacement.RuleIcon(rule)
        }
        return IconReplacement.Placeholder.takeIf {
            config.placeholderIconEnabled && !originalIsGrayscale
        }
    }

    private fun IconReplacement.toIcon(): Icon = when (this) {
        is IconReplacement.ThemeIcon -> Icon.createWithBitmap(bitmap)
        is IconReplacement.RuleIcon -> Icon.createWithBitmap(rule.iconBitmap)
        IconReplacement.Placeholder -> Icon.createWithBitmap(PlaceholderIconFactory.createBitmap())
    }

    private fun IconReplacement.toPanelIconRenderPlan(context: Context): PanelIconRenderPlan = when (this) {
        is IconReplacement.ThemeIcon -> PanelIconRenderPlan(
            drawable = BitmapDrawable(context.resources, bitmap),
            tintColor = null,
            clipToOutline = false,
            source = PanelIconSource.Theme,
        )
        is IconReplacement.RuleIcon -> PanelIconRenderPlan(
            drawable = BitmapDrawable(context.resources, rule.iconBitmap),
            tintColor = rule.iconColor.takeIf { it != 0 } ?: context.defaultPanelIconTint,
            clipToOutline = false,
            source = PanelIconSource.Rule,
        )
        IconReplacement.Placeholder -> PanelIconRenderPlan(
            drawable = BitmapDrawable(context.resources, PlaceholderIconFactory.createBitmap()),
            tintColor = context.defaultPanelIconTint,
            clipToOutline = false,
            source = PanelIconSource.Placeholder,
        )
    }

    private val Context.defaultPanelIconTint: Int
        get() {
            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                DARK_PANEL_ICON_TINT
            } else {
                LIGHT_PANEL_ICON_TINT
            }
        }

    private fun Icon.isGrayscale(context: Context): Boolean? =
        loadDrawableOrNull(context)?.let(IconBitmapClassifier::isGrayscaleDrawable)

    private fun Icon.loadDrawableOrNull(context: Context): Drawable? =
        runCatching { loadDrawable(context)?.mutate() }.getOrNull()

    private fun StatusBarNotification.rulePackageName(): String =
        packageName.orEmpty()

    private fun StatusBarNotification.shouldKeepColorOsDefault(): Boolean =
        config.iconSourceMode == RuleStore.IconSourceMode.RuleLibrary &&
            !config.oplusPushSpecialHandlingEnabled &&
            isOplusPush()

    private fun StatusBarNotification.isOplusPush(): Boolean =
        opPkg == SYSTEM_FRAMEWORK_PACKAGE && opPkg != packageName

    private fun StatusBarNotification.isMediaNotification(): Boolean {
        if (notification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) return true
        return notification.category == Notification.CATEGORY_TRANSPORT
    }

    private sealed class IconReplacement {
        data class ThemeIcon(val bitmap: Bitmap) : IconReplacement()
        data class RuleIcon(val rule: IconRule) : IconReplacement()
        object Placeholder : IconReplacement()
    }

    private companion object {
        const val SYSTEM_FRAMEWORK_PACKAGE = "android"
        const val LIGHT_PANEL_ICON_TINT = 0xFF707173.toInt()
        const val DARK_PANEL_ICON_TINT = 0xFFDCDCDC.toInt()
    }
}
