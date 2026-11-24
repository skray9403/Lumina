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
    val activeAlbumName: String? = null,
    val pendingIntentRequest: IntentSenderRequest? = null,
    val visibleMedia: List<Medium> = emptyList(),
    val indexToDatePill: List<String> = emptyList(),
    val mediaIdToGridIndex: Map<Long, Int> = emptyMap(),
    val albumMedia: List<Medium> = emptyList(),
    val albumIndexToDatePill: List<String> = emptyList(),
    val albumMediaIdToGridIndex: Map<Long, Int> = emptyMap(),
    val favoriteCount: Int = 0,
    val trashedMedia: List<Medium> = emptyList(),
    val isTrashOverlay: Boolean = false,
    val isSearchActive: Boolean = false
)

private data class FilterInputs(
    val selectedTab: GalleryTab = GalleryTab.PHOTOS,
    val selectedIds: Set<Long> = emptySet(),
    val columnCount: Int = 3,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val activeAlbumId: Long? = null,
    val activeAlbumName: String? = null,
    val pickerMimeType: String? = null,
    val pendingIntentRequest: IntentSenderRequest? = null
)

private data class FlatFeedProjection(
    val items: List<Medium>,
    val sections: List<TimelineSection>,
    val indexToPill: List<String>,
    val idToIndex: Map<Long, Int>
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val FAVORITES_BUCKET_ID = -1001L
        const val TRASH_BUCKET_ID = -1002L
    }

    val repository = LuminaMediaRepository(application)

    private val rawMedia = MutableStateFlow<List<Medium>>(emptyList())
    private val rawTrashed = MutableStateFlow<List<Medium>>(emptyList())
    private val inputs = MutableStateFlow(FilterInputs())

    init {
        viewModelScope.launch {
            repository.mediaFlow.collect { media ->
                rawMedia.value = media
            }
        }
        viewModelScope.launch {
            repository.trashedMediaFlow.collect { trashed ->
                rawTrashed.value = trashed
            }
        }
    }

    val uiState: StateFlow<GalleryUiState> = combine(rawMedia, rawTrashed, inputs) { media, trashed, inp ->
        val mimeFiltered = when {
            inp.pickerMimeType == null || inp.pickerMimeType == "*/*" -> media
            inp.pickerMimeType.startsWith("image/") -> media.filter { !it.isVideo }
            inp.pickerMimeType.startsWith("video/") -> media.filter { it.isVideo }
            else -> media
        }

        val albums = repository.extractDirectories(mimeFiltered)
        val favoriteCount = mimeFiltered.count { it.isFavorite }

        val mainFiltered = applySearch(
            media = mimeFiltered,
            query = if (inp.activeAlbumId == null) inp.searchQuery else ""
        )
        val mainProj = buildFlatProjection(mainFiltered)

        val isTrashOverlay = inp.activeAlbumId == TRASH_BUCKET_ID
        val albumFiltered = when (inp.activeAlbumId) {
            null -> emptyList()
            FAVORITES_BUCKET_ID -> applySearch(mimeFiltered.filter { it.isFavorite }, inp.searchQuery)
            TRASH_BUCKET_ID -> applySearch(trashed, inp.searchQuery)
            else -> applySearch(mimeFiltered.filter { it.bucketId == inp.activeAlbumId }, inp.searchQuery)
        }
        val albumProj = buildFlatProjection(albumFiltered)

        val resolvedAlbumName = when (inp.activeAlbumId) {
            FAVORITES_BUCKET_ID -> "Favorites"
            TRASH_BUCKET_ID -> "Trash"
            else -> inp.activeAlbumName
                ?: inp.activeAlbumId?.let { id -> albums.firstOrNull { it.bucketId == id }?.name }
        }

        GalleryUiState(
            allMedia = media,
            timelineSections = if (inp.activeAlbumId != null) albumProj.sections else mainProj.sections,
            albums = albums,
            selectedTab = inp.selectedTab,
            selectedIds = inp.selectedIds,
            isMultiSelectMode = inp.selectedIds.isNotEmpty(),
            columnCount = inp.columnCount,
            searchQuery = inp.searchQuery,
            activeAlbumId = inp.activeAlbumId,
            activeAlbumName = resolvedAlbumName,
            pendingIntentRequest = inp.pendingIntentRequest,
            visibleMedia = mainProj.items,
            indexToDatePill = mainProj.indexToPill,
            mediaIdToGridIndex = mainProj.idToIndex,
            albumMedia = albumProj.items,
            albumIndexToDatePill = albumProj.indexToPill,
            albumMediaIdToGridIndex = albumProj.idToIndex,
            favoriteCount = favoriteCount,
            trashedMedia = trashed,
            isTrashOverlay = isTrashOverlay,
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
            rawTrashed.value = repository.queryTrashedMedia()
        }
    }

    fun setTab(tab: GalleryTab) {
        inputs.update { current ->
            current.copy(
                selectedTab = tab,
                activeAlbumId = if (tab == GalleryTab.PHOTOS) null else current.activeAlbumId,
                activeAlbumName = if (tab == GalleryTab.PHOTOS) null else current.activeAlbumName
            )
        }
    }

    fun openAlbum(album: Directory) {
        inputs.update {
            it.copy(
                activeAlbumId = album.bucketId,
                activeAlbumName = album.name,
                searchQuery = "",
                isSearchActive = false,
                selectedIds = emptySet()
            )
        }
    }

    fun openFavorites() {
        inputs.update {
            it.copy(
                activeAlbumId = FAVORITES_BUCKET_ID,
                activeAlbumName = "Favorites",
                searchQuery = "",
                isSearchActive = false,
                selectedIds = emptySet()
            )
        }
    }

    fun openTrash() {
        inputs.update {
            it.copy(
                activeAlbumId = TRASH_BUCKET_ID,
                activeAlbumName = "Trash",
                searchQuery = "",
                isSearchActive = false,
                selectedIds = emptySet()
            )
        }
    }

    fun closeAlbum() {
        inputs.update {
            it.copy(
                activeAlbumId = null,
                activeAlbumName = null,
                searchQuery = "",
                isSearchActive = false,
                selectedIds = emptySet()
            )
        }
    }

    fun filterByAlbum(bucketId: Long?) {
        if (bucketId == null) {
            closeAlbum()
        } else {
            inputs.update {
                it.copy(
                    activeAlbumId = bucketId,
                    activeAlbumName = uiState.value.albums.firstOrNull { a -> a.bucketId == bucketId }?.name
                )
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

    fun setColumnCount(count: Int) {
        inputs.update { it.copy(columnCount = count.coerceIn(2, 5)) }
    }

    fun toggleSelection(id: Long) {
        inputs.update { current ->
            val next = if (id in current.selectedIds) current.selectedIds - id else current.selectedIds + id
            current.copy(selectedIds = next)
        }
    }

    fun selectAll() {
        val state = uiState.value
        val activeFeed = if (state.activeAlbumId != null) state.albumMedia else state.visibleMedia
        val visibleIds = activeFeed.mapTo(HashSet()) { it.id }
        inputs.update { it.copy(selectedIds = visibleIds) }
    }

    fun clearSelection() {
        inputs.update { it.copy(selectedIds = emptySet()) }
    }

    fun addMediaToAlbum(
        items: List<Medium>,
        albumName: String,
        targetRelativePath: String? = null,
        onComplete: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            val copied = repository.copyMediaToAlbum(items, albumName, targetRelativePath)
            if (copied > 0) {
                rawMedia.value = repository.queryAllMedia()
            }
            onComplete(copied)
        }
    }

    private fun selectedUrisFromActiveSource(): List<android.net.Uri> {
        val state = uiState.value
        val source = if (state.isTrashOverlay) state.trashedMedia else state.allMedia
        return source.filter { it.id in state.selectedIds }.map { it.uri }
    }

    fun deleteSelected() {
        val uris = selectedUrisFromActiveSource()
        if (uris.isNotEmpty()) {
            val req = repository.createDeleteRequest(uris)
            inputs.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun deleteSelectedPermanently() = deleteSelected()

    fun trashSelected(isTrash: Boolean = true) {
        val uris = selectedUrisFromActiveSource()
        if (uris.isNotEmpty()) {
            val req = repository.createTrashRequest(uris, isTrash)
            inputs.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun restoreSelected() = trashSelected(isTrash = false)

    fun favoriteSelected(isFavorite: Boolean) {
        val uris = selectedUrisFromActiveSource()
        if (uris.isNotEmpty()) {
            val req = repository.createFavoriteRequest(uris, isFavorite)
            inputs.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun clearPendingIntentRequest() {
        inputs.update { it.copy(pendingIntentRequest = null) }
    }

    private fun applySearch(media: List<Medium>, query: String): List<Medium> {
        if (query.isBlank()) return media
        val lower = query.trim().lowercase(Locale.ROOT)
        return media.filter {
            it.displayName.lowercase(Locale.ROOT).contains(lower) ||
                it.bucketName.lowercase(Locale.ROOT).contains(lower)
        }
    }

    private fun buildFlatProjection(media: List<Medium>): FlatFeedProjection {
        if (media.isEmpty()) {
            return FlatFeedProjection(emptyList(), emptyList(), emptyList(), emptyMap())
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val currentYear = today.year
        val locale = Locale.getDefault()
        val fmtSameYear = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
        val fmtOtherYear = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)

        val indexToPill = ArrayList<String>(media.size)
        val idToIndex = HashMap<Long, Int>(media.size)
        val grouped = LinkedHashMap<String, MutableList<Medium>>()

        var lastDay: LocalDate? = null
        var lastLabel = ""

        media.forEachIndexed { idx, item ->
            val day = Instant.ofEpochMilli(item.dateTakenMs).atZone(zone).toLocalDate()
            val label = if (day == lastDay) {
                lastLabel
            } else {
                lastDay = day
                val formatted = when {
                    day == today -> "Today"
                    day == yesterday -> "Yesterday"
                    day.year == currentYear -> day.format(fmtSameYear)
                    else -> day.format(fmtOtherYear)
                }
                lastLabel = formatted
                formatted
            }
            indexToPill.add(label)
            idToIndex[item.id] = idx
            grouped.getOrPut(label) { ArrayList() }.add(item)
        }

        val sections = grouped.map { (title, list) ->
            TimelineSection(title = title, pillLabel = title, items = list)
        }

        return FlatFeedProjection(
            items = media,
            sections = sections,
            indexToPill = indexToPill,
            idToIndex = idToIndex
        )
    }
}
