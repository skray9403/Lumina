package org.fossify.gallery.engine.share

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.chooser.ChooserAction
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.LuminaActivity

@Immutable
data class QuickShareTarget(
    val componentName: ComponentName,
    val label: String,
    val icon: ImageBitmap
)

class ShareTargetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chosen = intent.getParcelableExtra(
            Intent.EXTRA_CHOSEN_COMPONENT,
            ComponentName::class.java
        ) ?: return
        RecentShareStore.recordChosenComponent(context, chosen)
    }
}

object RecentShareStore {
    const val ACTION_UPNP_CAST = "org.fossify.gallery.ACTION_UPNP_CAST"
    const val EXTRA_UPNP_MEDIA_ID = "extra_upnp_media_id"

    private const val PREFS_NAME = "lumina_recent_share_targets"
    private const val KEY_COMPONENTS = "ordered_components"
    private const val MAX_RECENTS = 4

    fun recordChosenComponent(context: Context, component: ComponentName) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val flattened = component.flattenToShortString()
        val current = prefs.getString(KEY_COMPONENTS, "")
            .orEmpty()
            .split('|')
            .filter { it.isNotBlank() && it != flattened }
        val updated = (listOf(flattened) + current).take(MAX_RECENTS * 2)
        prefs.edit().putString(KEY_COMPONENTS, updated.joinToString("|")).apply()
    }

    fun launchNativeShareSheet(context: Context, medium: Medium) {
        launchNativeShareSheet(context, listOf(medium))
    }

    fun launchNativeShareSheet(context: Context, items: List<Medium>) {
        if (items.isEmpty()) return
        val sendIntent = if (items.size == 1) {
            val medium = items.first()
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, medium.uri)
                type = medium.mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            val resolvedMime = resolveSharedMimeType(items)
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
                type = resolvedMime
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val callbackIntent = Intent(context.applicationContext, ShareTargetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            1001,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val chooser = Intent.createChooser(sendIntent, null, pendingIntent.intentSender)

        // Inject native "UPnP Cast" action directly into Android 14+ Sharesheet
        val primary = items.first()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val castIntent = Intent(context.applicationContext, LuminaActivity::class.java).apply {
                action = ACTION_UPNP_CAST
                data = primary.uri
                putExtra(EXTRA_UPNP_MEDIA_ID, primary.id)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val castPending = PendingIntent.getActivity(
                context.applicationContext,
                1002,
                castIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val castAction = ChooserAction.Builder(
                Icon.createWithResource(context, android.R.drawable.ic_menu_share),
                "UPnP Cast",
                castPending
            ).build()
            chooser.putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, arrayOf(castAction))
        }

        context.startActivity(chooser)
    }

    fun shareDirectlyTo(context: Context, medium: Medium, target: QuickShareTarget) {
        shareDirectlyTo(context, listOf(medium), target)
    }

    fun shareDirectlyTo(context: Context, items: List<Medium>, target: QuickShareTarget) {
        if (items.isEmpty()) return
        recordChosenComponent(context, target.componentName)
        val directIntent = if (items.size == 1) {
            val medium = items.first()
            Intent(Intent.ACTION_SEND).apply {
                component = target.componentName
                putExtra(Intent.EXTRA_STREAM, medium.uri)
                type = medium.mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                component = target.componentName
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
                type = resolveSharedMimeType(items)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        try {
            context.startActivity(directIntent)
        } catch (_: Exception) {
            launchNativeShareSheet(context, items)
        }
    }

    private fun resolveSharedMimeType(items: List<Medium>): String {
        val allImages = items.all { !it.isVideo }
        val allVideos = items.all { it.isVideo }
        return when {
            allImages -> "image/*"
            allVideos -> "video/*"
            else -> "*/*"
        }
    }

    suspend fun loadRecentTargets(
        context: Context,
        mimeType: String,
        limit: Int = MAX_RECENTS
    ): List<QuickShareTarget> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "*/*" }
        }
        val resolved = pm.queryIntentActivities(
            probe,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        ).filter { it.activityInfo.packageName != context.packageName }

        if (resolved.isEmpty()) return@withContext emptyList()

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedOrder = prefs.getString(KEY_COMPONENTS, "")
            .orEmpty()
            .split('|')
            .mapNotNull { ComponentName.unflattenFromString(it) }

        val byComponent = resolved.associateBy {
            ComponentName(it.activityInfo.packageName, it.activityInfo.name)
        }

        val ordered = LinkedHashMap<ComponentName, android.content.pm.ResolveInfo>()
        for (comp in savedOrder) {
            byComponent[comp]?.let { ordered[comp] = it }
            if (ordered.size >= limit) break
        }
        if (ordered.size < limit) {
            val usedPackages = ordered.keys.mapTo(HashSet()) { it.packageName }
            for (info in resolved) {
                val comp = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                if (comp.packageName !in usedPackages) {
                    ordered[comp] = info
                    usedPackages.add(comp.packageName)
                    if (ordered.size >= limit) break
                }
            }
        }

        ordered.entries.mapNotNull { (comp, info) ->
            try {
                val label = info.loadLabel(pm).toString()
                val drawable = info.loadIcon(pm)
                QuickShareTarget(
                    componentName = comp,
                    label = label,
                    icon = drawable.toSquareImageBitmap(96)
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun Drawable.toSquareImageBitmap(sizePx: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, sizePx, sizePx)
        draw(canvas)
        return bmp.asImageBitmap()
    }
}
