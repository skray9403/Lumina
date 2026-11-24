package org.fossify.gallery.ui

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fossify.gallery.engine.share.RecentShareStore
import org.fossify.gallery.engine.vault.VaultManager

class LuminaActivity : ComponentActivity() {
    private var launchRequest by mutableStateOf(LuminaLaunchRequest())
    private var launchRevision = 0
    private var upnpRequestId by mutableStateOf<Long?>(null)
    private var upnpRequestRevision by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VaultManager.init(this)
        enableEdgeToEdge()
        updateLaunchRequest(intent)

        setContent {
            val request = launchRequest
            LuminaApp(
                pickerMode = request.pickerMode,
                pickerMimeType = request.mimeType,
                allowMultiple = request.allowMultiple,
                initialUri = request.initialUri,
                launchRevision = request.revision,
                initialTab = request.initialTab,
                upnpCastMediaId = upnpRequestId,
                upnpCastRevision = upnpRequestRevision,
                onMediaPicked = { uris ->
                    if (uris.isNotEmpty()) {
                        val resultIntent = Intent().apply {
                            data = uris.first()
                            val clip = ClipData.newUri(
                                contentResolver,
                                "Lumina selected media",
                                uris.first()
                            )
                            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                            clipData = clip
                            putParcelableArrayListExtra(
                                Intent.EXTRA_STREAM,
                                ArrayList(uris)
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                    } else {
                        setResult(Activity.RESULT_CANCELED)
                    }
                    finish()
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateLaunchRequest(intent)
    }

    override fun onStop() {
        super.onStop()
        // Zeroize in-memory vault DEK whenever Lumina leaves the foreground
        if (!isChangingConfigurations) {
            VaultManager.lock()
        }
    }

    private fun updateLaunchRequest(intent: Intent?) {
        val action = intent?.action
        if (action == RecentShareStore.ACTION_UPNP_CAST) {
            val mediaId = intent.getLongExtra(RecentShareStore.EXTRA_UPNP_MEDIA_ID, -1L)
            if (mediaId != -1L) {
                upnpRequestId = mediaId
                upnpRequestRevision += 1
            }
            return
        }

        launchRevision += 1
        val pickerMode = action == Intent.ACTION_PICK || action == Intent.ACTION_GET_CONTENT
        launchRequest = LuminaLaunchRequest(
            pickerMode = pickerMode,
            mimeType = if (pickerMode) intent?.type else null,
            allowMultiple = pickerMode &&
                intent?.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) == true,
            initialUri = intent?.data.takeIf { action == Intent.ACTION_VIEW },
            initialTab = if (action == "android.intent.action.APPLICATION_PREFERENCES") {
                LuminaTab.SETTINGS.name
            } else {
                LuminaTab.PHOTOS.name
            },
            revision = launchRevision
        )
    }
}

private data class LuminaLaunchRequest(
    val pickerMode: Boolean = false,
    val mimeType: String? = null,
    val allowMultiple: Boolean = false,
    val initialUri: Uri? = null,
    val initialTab: String = LuminaTab.PHOTOS.name,
    val revision: Int = 0
)
