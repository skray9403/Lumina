package org.fossify.gallery.ui.home

import android.app.Application
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.data.LuminaMediaRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class GalleryTab {
    PHOTOS,
    ALBUMS
}

@Immutable
data class TimelineSection(
    val title: String,
    val pillLabel: String = title,
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
    val pendingIntentRequest: IntentSenderRequest? = null,
    val visibleMedia: List<Medium> = emptyList(),
    val indexToDatePill: List<String> = emptyList(),
    val mediaIdToGridIndex: Map<Long, Int> = emptyMap(),
    val isSearchActive: Boolean = false
)

private data class FilterInputs(
    val selectedTab: GalleryTab = GalleryTab.PHOTOS,
    val selectedIds: Set<Long> = emptySet(),
    val columnCount: Int = 3,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val activeAlbumId: Long? = null,
    val pickerMimeType: String? = null,
    val pendingIntentRequest: IntentSenderRequest? = null
)

private data class DerivedTimeline(
    val visibleMedia: List<Medium>,
    val sections: List<TimelineSection>,
    val albums: List<Directory>,
    val indexToDatePill: List<String>,
    val mediaIdToGridIndex: Map<Long, Int>
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    val repository = LuminaMediaRepository(application)

    private val rawMedia = MutableStateFlow<List<Medium>>(emptyList())
    private val inputs = MutableStateFlow(FilterInputs())

    init {
        viewModelScope.launch {
            repository.mediaFlow.collect { media ->
                rawMedia.value = media
            }
        }
    }

    val uiState: StateFlow<GalleryUiState> = combine(rawMedia, inputs) { media, inp ->
        val derived = deriveTimeline(
            media = media,
            query = inp.searchQuery,
            albumId = inp.activeAlbumId,
            pickerMimeType = inp.pickerMimeType
        )
        GalleryUiState(
            allMedia = media,
            timelineSections = derived.sections,
            albums = derived.albums,
            selectedTab = inp.selectedTab,
            selectedIds = inp.selectedIds,
            isMultiSelectMode = inp.selectedIds.isNotEmpty(),
            columnCount = inp.columnCount,
            searchQuery = inp.searchQuery,
            activeAlbumId = inp.activeAlbumId,
            pendingIntentRequest = inp.pendingIntentRequest,
            visibleMedia = derived.visibleMedia,
            indexToDatePill = derived.indexToDatePill,
            mediaIdToGridIndex = derived.mediaIdToGridIndex,
            isSearchActive = inp.isSearchActive
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GalleryUiState()
        )

    fun setPickerMimeType(mimeType: String?) {
        inputs.update { it.copy(pickerMimeType = mimeType) }
    }

    fun refresh() {
        viewModelScope.launch {
            rawMedia.value = repository.queryAllMedia()
        }
    }

    fun setTab(tab: GalleryTab) {
        inputs.update { current ->
            if (tab == GalleryTab.PHOTOS && current.activeAlbumId != null) {
                current.copy(selectedTab = tab, activeAlbumId = null)
            } else {
                current.copy(selectedTab = tab)
            }
        }
    }

    fun setSearchActive(active: Boolean) {
        inputs.update { current ->
            if (!active) {
                current.copy(isSearchActive = false, searchQuery = "")
            } else {
                current.copy(isSearchActive = true)
            }
        }
    }

    fun setSearchQuery(query: String) {
        inputs.update { it.copy(searchQuery = query) }
    }

    fun filterByAlbum(bucketId: Long?) {
        inputs.update {
            it.copy(
                activeAlbumId = bucketId,
                selectedTab = GalleryTab.PHOTOS
            )
        }
    }

    fun setColumnCount(count: Int) {
        val clamped = count.coerceIn(2, 5)
        inputs.update { it.copy(columnCount = clamped) }
    }

    fun toggleSelection(id: Long) {
        inputs.update { current ->
            val next = if (id in current.selectedIds) {
                current.selectedIds - id
            } else {
                current.selectedIds + id
            }
            current.copy(selectedIds = next)
        }
    }

    /** Scoped strictly to currently visible timeline (album/search filtered). */
    fun selectAll() {
        val visibleIds = uiState.value.visibleMedia.mapTo(HashSet()) { it.id }
        inputs.update { it.copy(selectedIds = visibleIds) }
    }

    fun clearSelection() {
        inputs.update { it.copy(selectedIds = emptySet()) }
    }

    fun deleteSelected() {
        val state = uiState.value
        val uris = state.allMedia.filter { it.id in state.selectedIds }.map { it.uri }
        if (uris.isNotEmpty()) {
            val req = repository.createDeleteRequest(uris)
            inputs.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun trashSelected(isTrash: Boolean = true) {
        val state = uiState.value
        val uris = state.allMedia.filter { it.id in state.selectedIds }.map { it.uri }
        if (uris.isNotEmpty()) {
            val req = repository.createTrashRequest(uris, isTrash)
            inputs.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun favoriteSelected(isFavorite: Boolean) {
        val state = uiState.value
        val uris = state.allMedia.filter { it.id in state.selectedIds }.map { it.uri }
        if (uris.isNotEmpty()) {
            val req = repository.createFavoriteRequest(uris, isFavorite)
            inputs.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun clearPendingIntentRequest() {
        inputs.update { it.copy(pendingIntentRequest = null) }
    }

    private fun deriveTimeline(
        media: List<Medium>,
        query: String,
        albumId: Long?,
        pickerMimeType: String?
    ): DerivedTimeline {
        val mimeFiltered = when {
            pickerMimeType == null || pickerMimeType == "*/*" -> media
            pickerMimeType.startsWith("image/") -> media.filter { !it.isVideo }
            pickerMimeType.startsWith("video/") -> media.filter { it.isVideo }
            else -> media
        }

        val albums = repository.extractDirectories(mimeFiltered)

        var filtered = mimeFiltered
        if (albumId != null) {
            filtered = filtered.filter { it.bucketId == albumId }
        }
        if (query.isNotBlank()) {
            val lower = query.trim().lowercase(Locale.ROOT)
            filtered = filtered.filter {
                it.displayName.lowercase(Locale.ROOT).contains(lower) ||
                    it.bucketName.lowercase(Locale.ROOT).contains(lower)
            }
        }

        val zone = ZoneId.systemDefault()
        val currentYear = LocalDate.now(zone).year
        val locale = Locale.getDefault()
        val headerFmtSameYear = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
        val headerFmtOtherYear = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)

        val grouped = LinkedHashMap<LocalDate, MutableList<Medium>>()
        for (item in filtered) {
            val day = Instant.ofEpochMilli(item.dateTakenMs).atZone(zone).toLocalDate()
            grouped.getOrPut(day) { ArrayList() }.add(item)
        }

        val sections = ArrayList<TimelineSection>(grouped.size)
        val indexToPill = ArrayList<String>(filtered.size + grouped.size)
        val idToIndex = HashMap<Long, Int>(filtered.size)

        var gridIndex = 0
        for ((day, items) in grouped) {
            val label = if (day.year == currentYear) {
                day.format(headerFmtSameYear)
            } else {
                day.format(headerFmtOtherYear)
            }
            sections.add(TimelineSection(title = label, pillLabel = label, items = items))
            // Header item index
            indexToPill.add(label)
            gridIndex++
            // Media item indices
            for (m in items) {
                indexToPill.add(label)
                idToIndex[m.id] = gridIndex
                gridIndex++
            }
        }

        return DerivedTimeline(
            visibleMedia = filtered,
            sections = sections,
            albums = albums,
            indexToDatePill = indexToPill,
            mediaIdToGridIndex = idToIndex
        )
    }
}
