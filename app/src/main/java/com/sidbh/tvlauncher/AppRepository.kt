package com.sidbh.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Tile artwork. Banners fill the tile; plain icons are centered on a card. [tint] is the artwork's average color. */
class AppImage(val bitmap: ImageBitmap, val isBanner: Boolean, val tint: Color)

private class Art(val bitmap: Bitmap, val tint: Color)

class AppRepository(private val context: Context) {
    private val pm = context.packageManager

    // Sized in bytes so a few dozen banners stay well under the memory budget.
    private val images = object : LruCache<String, Art>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Art) = value.bitmap.byteCount
    }

    /** TV apps first; phone-style apps with no TV entry point are added after, one entry per package. */
    fun loadApps(): List<Item> {
        val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phone = query(Intent.CATEGORY_LAUNCHER)
        return (tv + phone)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                val info = it.activityInfo
                Item(
                    id = info.packageName,
                    label = it.loadLabel(pm).toString(),
                    category = categorize(info.packageName, info.applicationInfo.category),
                    target = Target.App(ComponentName(info.packageName, info.name)),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun query(category: String) =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun categorize(pkg: String, appCategory: Int) = when {
        pkg in TV_APPS -> Category.TvInputs
        pkg in STREAMING || appCategory == ApplicationInfo.CATEGORY_VIDEO || appCategory == ApplicationInfo.CATEGORY_AUDIO ->
            Category.Streaming
        pkg in GAMES || appCategory == ApplicationInfo.CATEGORY_GAME -> Category.Games
        else -> Category.Utilities
    }

    fun invalidateImages() = images.evictAll() // an update may have changed icons

    /** Blocking; call off the main thread. Null for items that are not apps. */
    fun loadImage(item: Item): AppImage? {
        val component = (item.target as? Target.App)?.component ?: return null
        images.get("b:${item.id}")?.let { return AppImage(it.bitmap.asImageBitmap(), true, it.tint) }
        images.get("i:${item.id}")?.let { return AppImage(it.bitmap.asImageBitmap(), false, it.tint) }

        val info = pm.getActivityInfo(component, 0)
        val banner = info.loadBanner(pm)
        val art = if (banner != null) {
            store("b:${item.id}", banner, BANNER_W, BANNER_H)
        } else {
            store("i:${item.id}", info.loadIcon(pm), ICON_PX, ICON_PX)
        }
        return AppImage(art.bitmap.asImageBitmap(), banner != null, art.tint)
    }

    /** Big, sharp icon for the Glass top shelf; the normal 192 px icon looks pixelated at that size. Blocking. */
    fun loadLargeIcon(item: Item): ImageBitmap? {
        val component = (item.target as? Target.App)?.component ?: return null
        images.get("L:${item.id}")?.let { return it.bitmap.asImageBitmap() }
        return store("L:${item.id}", pm.getActivityInfo(component, 0).loadIcon(pm), LARGE_ICON_PX, LARGE_ICON_PX)
            .bitmap.asImageBitmap()
    }

    /** Small square icon for text lists (List Style); always the app icon, never the wide banner. Blocking. */
    fun loadSmallIcon(item: Item): ImageBitmap? {
        val component = (item.target as? Target.App)?.component ?: return null
        images.get("s:${item.id}")?.let { return it.bitmap.asImageBitmap() }
        return store("s:${item.id}", pm.getActivityInfo(component, 0).loadIcon(pm), SMALL_ICON_PX, SMALL_ICON_PX)
            .bitmap.asImageBitmap()
    }

    private fun store(key: String, drawable: Drawable, w: Int, h: Int): Art {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bitmap))
        return Art(bitmap, averageColor(bitmap)).also { images.put(key, it) }
    }

    fun launch(component: ComponentName): Boolean = context.startSafely(
        Intent(Intent.ACTION_MAIN)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
    )

    fun launchPackage(pkg: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return context.startSafely(intent)
    }

    fun openAppInfo(pkg: String): Boolean =
        context.startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    /** Tries each intent in turn and stops at the first one that starts. */
    fun startFirst(vararg intents: Intent): Boolean = intents.any { context.startSafely(it) }

    private companion object {
        const val BANNER_W = 480
        const val BANNER_H = 270
        const val ICON_PX = 192
        const val SMALL_ICON_PX = 96
        const val LARGE_ICON_PX = 384

        val TV_APPS = setOf("com.tcl.tv", "com.tcl.ui_mediaCenter")
        val STREAMING = setOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvmusic",
            "com.google.android.videos",
            "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar",
            "com.sonyliv",
            "com.fancode.tv",
        )
        val GAMES = setOf("com.google.android.play.games")
    }
}
