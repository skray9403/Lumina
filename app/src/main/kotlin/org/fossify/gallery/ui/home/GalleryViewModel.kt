package org.fossify.gallery.ui.home

import android.app.Application
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.data.LuminaMediaRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GalleryTab {
    PHOTOS,
    ALBUMS
}

@Immutable
data class TimelineSection(
    val title: String,
    val items: List<Medium>
)

@Immutable
data class GalleryUiState(
    val allMedia: List<Medium> = emptyList(),
    val timelineSections: List<TimelineSection> = emptyList(),
    val albums: List<Directory> = emptyList(),
    val selectedTab: GalleryTab = GalleryTab.PHOTOS,
    val selectedIds: Set<Long> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val columnCount: Int = 3,
    val searchQuery: String = "",
    val activeAlbumId: Long? = null,
    val pendingIntentRequest: IntentSenderRequest? = null
)

class GalleryViewModel(application: Application) : ViewModel() {
    private val repository = LuminaMediaRepository(application)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.mediaFlow.collect { media ->
                _uiState.update { current ->
                    val albums = repository.extractDirectories(media)
                    val filtered = filterMedia(media, current.searchQuery, current.activeAlbumId)
                    val sections = groupIntoTimeline(filtered)
                    current.copy(
                        allMedia = media,
                        timelineSections = sections,
                        albums = albums
                    )
                }
            }
        }
    }

    fun setTab(tab: GalleryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            val filtered = filterMedia(current.allMedia, query, current.activeAlbumId)
            current.copy(
                searchQuery = query,
                timelineSections = groupIntoTimeline(filtered)
            )
        }
    }

    fun filterByAlbum(bucketId: Long?) {
        _uiState.update { current ->
            val filtered = filterMedia(current.allMedia, current.searchQuery, bucketId)
            current.copy(
                activeAlbumId = bucketId,
                selectedTab = GalleryTab.PHOTOS,
                timelineSections = groupIntoTimeline(filtered)
            )
        }
    }

    fun setColumnCount(count: Int) {
        val clamped = count.coerceIn(2, 5)
        _uiState.update { it.copy(columnCount = clamped) }
    }

    fun toggleSelection(id: Long) {
        _uiState.update { current ->
            val next = current.selectedIds.toMutableSet()
            if (next.contains(id)) {
                next.remove(id)
            } else {
                next.add(id)
            }
            current.copy(
                selectedIds = next,
                isMultiSelectMode = next.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        _uiState.update { current ->
            val allIds = current.allMedia.map { it.id }.toSet()
            current.copy(
                selectedIds = allIds,
                isMultiSelectMode = allIds.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedIds = emptySet(),
                isMultiSelectMode = false
            )
        }
    }

    fun deleteSelected() {
        val state = _uiState.value
        val uris = state.allMedia.filter { it.id in state.selectedIds }.map { it.uri }
        if (uris.isNotEmpty()) {
            val req = repository.createDeleteRequest(uris)
            _uiState.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun trashSelected(isTrash: Boolean = true) {
        val state = _uiState.value
        val uris = state.allMedia.filter { it.id in state.selectedIds }.map { it.uri }
        if (uris.isNotEmpty()) {
            val req = repository.createTrashRequest(uris, isTrash)
            _uiState.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun favoriteSelected(isFavorite: Boolean) {
        val state = _uiState.value
        val uris = state.allMedia.filter { it.id in state.selectedIds }.map { it.uri }
        if (uris.isNotEmpty()) {
            val req = repository.createFavoriteRequest(uris, isFavorite)
            _uiState.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun clearPendingIntentRequest() {
        _uiState.update { it.copy(pendingIntentRequest = null) }
    }

    private fun filterMedia(media: List<Medium>, query: String, albumId: Long?): List<Medium> {
        var result = media
        if (albumId != null) {
            result = result.filter { it.bucketId == albumId }
        }
        if (query.isNotBlank()) {
            val lower = query.trim().lowercase(Locale.ROOT)
            result = result.filter {
                it.displayName.lowercase(Locale.ROOT).contains(lower) ||
                        it.bucketName.lowercase(Locale.ROOT).contains(lower)
            }
        }
        return result
    }

    private fun groupIntoTimeline(media: List<Medium>): List<TimelineSection> {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        return media.groupBy { dateFormat.format(Date(it.dateTakenMs)) }
            .map { (date, items) -> TimelineSection(date, items) }
    }
}
