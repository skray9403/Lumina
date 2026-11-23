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

/**
 * Compose entry point for Lumina and Android's image/video picker intents.
 *
 * Lumina: modern, expressive Jetpack Compose gallery application.
 * The old media activities remain as compatibility services while their
 * behavior is migrated; normal launch and picker flows now enter Compose.
 */
class LuminaActivity : ComponentActivity() {
    private var launchRequest by mutableStateOf(LuminaLaunchRequest())
    private var launchRevision = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun updateLaunchRequest(intent: Intent?) {
        launchRevision += 1
        val action = intent?.action
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
