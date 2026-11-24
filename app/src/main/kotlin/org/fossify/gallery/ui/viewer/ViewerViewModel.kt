package org.fossify.gallery.ui.viewer

import android.content.Context
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fossify.gallery.engine.metadata.MediaMetadata
import org.fossify.gallery.engine.metadata.NativeMetadataEngine
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.data.LuminaMediaRepository

@Immutable
data class ViewerUiState(
    val currentIndex: Int = 0,
    val isChromeVisible: Boolean = true,
    val isDetailsSheetOpen: Boolean = false,
    val currentMetadata: MediaMetadata? = null,
    val isLoadingMetadata: Boolean = false,
    val pendingIntentRequest: IntentSenderRequest? = null
)

class ViewerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    fun setCurrentIndex(index: Int) {
        _uiState.update { it.copy(currentIndex = index, currentMetadata = null) }
    }

    fun toggleChrome() {
        _uiState.update { it.copy(isChromeVisible = !it.isChromeVisible) }
    }

    fun setChromeVisible(visible: Boolean) {
        _uiState.update { it.copy(isChromeVisible = visible) }
    }

    fun setDetailsSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isDetailsSheetOpen = open) }
    }

    fun loadMetadata(context: Context, medium: Medium) {
        _uiState.update { it.copy(isLoadingMetadata = true) }
        viewModelScope.launch {
            val metadata = NativeMetadataEngine.extractMetadata(context, medium)
            _uiState.update { it.copy(currentMetadata = metadata, isLoadingMetadata = false) }
        }
    }

    fun toggleFavorite(repository: LuminaMediaRepository, medium: Medium) {
        val req = repository.createFavoriteRequest(listOf(medium.uri), !medium.isFavorite)
        _uiState.update { it.copy(pendingIntentRequest = req) }
    }

    fun deleteCurrent(repository: LuminaMediaRepository, medium: Medium) {
        val req = repository.createTrashRequest(listOf(medium.uri), true)
        _uiState.update { it.copy(pendingIntentRequest = req) }
    }

    fun restoreCurrent(repository: LuminaMediaRepository, medium: Medium) {
        val req = repository.createTrashRequest(listOf(medium.uri), false)
        _uiState.update { it.copy(pendingIntentRequest = req) }
    }

    fun deletePermanently(repository: LuminaMediaRepository, medium: Medium) {
        val req = repository.createDeleteRequest(listOf(medium.uri))
        _uiState.update { it.copy(pendingIntentRequest = req) }
    }

    fun clearPendingIntentRequest() {
        _uiState.update { it.copy(pendingIntentRequest = null) }
    }
}
