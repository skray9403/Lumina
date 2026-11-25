package org.fossify.gallery.ui.home

import android.app.Application
import android.net.Uri
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
import org.fossify.gallery.engine.vault.VaultManager
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

enum class MediaFilter {
    ALL,
    PHOTOS,
    VIDEOS
}

enum class MediaGroupBy {
    DATE,
    SIZE,
    FOLDER
}

enum class SortOrder {
    DESCENDING,
    ASCENDING
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
    val visibleSections: List<TimelineSection> = emptyList(),
    val albumSections: List<TimelineSection> = emptyList(),
    val albums: List<Directory> = emptyList(),
    val selectedTab: GalleryTab = GalleryTab.PHOTOS,
    val mediaFilter: MediaFilter = MediaFilter.ALL,
    val groupBy: MediaGroupBy = MediaGroupBy.DATE,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
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
    val vaultMedia: List<Medium> = emptyList(),
    val isHiddenShown: Boolean = false,
    val isTrashOverlay: Boolean = false,
    val isSearchActive: Boolean = false
)

private data class FilterInputs(
    val selectedTab: GalleryTab = GalleryTab.PHOTOS,
    val mediaFilter: MediaFilter = MediaFilter.ALL,
    val groupBy: MediaGroupBy = MediaGroupBy.DATE,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
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
        VaultManager.init(application)
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

    val uiState: StateFlow<GalleryUiState> = combine(
        rawMedia,
        rawTrashed,
        VaultManager.vaultMedia,
        VaultManager.isUnlocked,
        inputs
    ) { media, trashed, vault, vaultUnlocked, inp ->
        // When unlocked, merge encrypted hidden items inline into the unified feed (never during external picker)
        val combinedRaw = if (vaultUnlocked && vault.isNotEmpty() && inp.pickerMimeType == null) {
            (media + vault).sortedByDescending { it.dateTakenMs }
        } else {
            media
        }

        val pickerFiltered = when {
            inp.pickerMimeType == null || inp.pickerMimeType == "*/*" -> combinedRaw
            inp.pickerMimeType.startsWith("image/") -> combinedRaw.filter { !it.isVideo }
            inp.pickerMimeType.startsWith("video/") -> combinedRaw.filter { it.isVideo }
            else -> combinedRaw
        }

        val typeFiltered = applyMediaFilter(pickerFiltered, inp.mediaFilter)
        val albums = repository.extractDirectories(typeFiltered)
        val favoriteCount = typeFiltered.count { it.isFavorite }

        val mainFiltered = applySearch(
            media = typeFiltered,
            query = if (inp.activeAlbumId == null) inp.searchQuery else ""
        )
        val effectiveMainGroup = if (inp.activeAlbumId == null) inp.groupBy else MediaGroupBy.DATE
        val mainProj = buildProjection(mainFiltered, effectiveMainGroup, inp.sortOrder)

        val isTrashOverlay = inp.activeAlbumId == TRASH_BUCKET_ID
        val albumFiltered = when (inp.activeAlbumId) {
            null -> emptyList()
            FAVORITES_BUCKET_ID -> applySearch(typeFiltered.filter { it.isFavorite }, inp.searchQuery)
            TRASH_BUCKET_ID -> applySearch(applyMediaFilter(trashed, inp.mediaFilter), inp.searchQuery)
            else -> applySearch(typeFiltered.filter { it.bucketId == inp.activeAlbumId }, inp.searchQuery)
        }
        val effectiveAlbumGroup = if (inp.groupBy == MediaGroupBy.SIZE) MediaGroupBy.SIZE else MediaGroupBy.DATE
        val albumProj = buildProjection(albumFiltered, effectiveAlbumGroup, inp.sortOrder)

        val resolvedAlbumName = when (inp.activeAlbumId) {
            FAVORITES_BUCKET_ID -> "Favorites"
            TRASH_BUCKET_ID -> "Trash"
            else -> inp.activeAlbumName
                ?: inp.activeAlbumId?.let { id -> albums.firstOrNull { it.bucketId == id }?.name }
        }

        GalleryUiState(
            allMedia = combinedRaw,
            timelineSections = if (inp.activeAlbumId != null) albumProj.sections else mainProj.sections,
            visibleSections = mainProj.sections,
            albumSections = albumProj.sections,
            albums = albums,
            selectedTab = inp.selectedTab,
            mediaFilter = inp.mediaFilter,
            groupBy = inp.groupBy,
            sortOrder = inp.sortOrder,
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
            vaultMedia = vault,
            isHiddenShown = vaultUnlocked,
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

    fun setMediaFilter(filter: MediaFilter) {
        inputs.update { it.copy(mediaFilter = filter) }
    }

    fun setGroupBy(groupBy: MediaGroupBy) {
        inputs.update { it.copy(groupBy = groupBy) }
    }

    fun setSortOrder(order: SortOrder) {
        inputs.update { it.copy(sortOrder = order) }
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

    fun setSelectedIds(ids: Set<Long>) {
        inputs.update { it.copy(selectedIds = ids) }
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
            val publicOnly = items.filterNot { VaultManager.isVaultMedium(it) }
            val copied = repository.copyMediaToAlbum(publicOnly, albumName, targetRelativePath)
            if (copied > 0) {
                rawMedia.value = repository.queryAllMedia()
            }
            onComplete(copied)
        }
    }

    fun requestPermanentDeleteForUris(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            val req = repository.createDeleteRequest(uris)
            inputs.update { it.copy(pendingIntentRequest = req) }
        }
    }

    private fun selectedPublicUris(): List<Uri> {
        val state = uiState.value
        val source = if (state.isTrashOverlay) state.trashedMedia else state.allMedia
        return source
            .filter { it.id in state.selectedIds && !VaultManager.isVaultMedium(it) }
            .map { it.uri }
    }

    fun deleteSelected() {
        val uris = selectedPublicUris()
        if (uris.isNotEmpty()) {
            val req = repository.createDeleteRequest(uris)
            inputs.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun deleteSelectedPermanently() = deleteSelected()

    fun trashSelected(isTrash: Boolean = true) {
        val uris = selectedPublicUris()
        if (uris.isNotEmpty()) {
            val req = repository.createTrashRequest(uris, isTrash)
            inputs.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun restoreSelected() = trashSelected(isTrash = false)

    fun favoriteSelected(isFavorite: Boolean) {
        val uris = selectedPublicUris()
        if (uris.isNotEmpty()) {
            val req = repository.createFavoriteRequest(uris, isFavorite)
            inputs.update { it.copy(pendingIntentRequest = req) }
        }
    }

    fun clearPendingIntentRequest() {
        inputs.update { it.copy(pendingIntentRequest = null) }
    }

    private fun applyMediaFilter(media: List<Medium>, filter: MediaFilter): List<Medium> = when (filter) {
        MediaFilter.ALL -> media
        MediaFilter.PHOTOS -> media.filter { !it.isVideo }
        MediaFilter.VIDEOS -> media.filter { it.isVideo }
    }

    private fun applySearch(media: List<Medium>, query: String): List<Medium> {
        if (query.isBlank()) return media
        val lower = query.trim().lowercase(Locale.ROOT)
        return media.filter {
            it.displayName.lowercase(Locale.ROOT).contains(lower) ||
                it.bucketName.lowercase(Locale.ROOT).contains(lower)
        }
    }

    private fun buildProjection(
        media: List<Medium>,
        groupBy: MediaGroupBy,
        sortOrder: SortOrder
    ): FlatFeedProjection {
        if (media.isEmpty()) {
            return FlatFeedProjection(emptyList(), emptyList(), emptyList(), emptyMap())
        }
        return when (groupBy) {
            MediaGroupBy.DATE -> buildDateProjection(media, sortOrder)
            MediaGroupBy.SIZE -> buildSizeProjection(media, sortOrder)
            MediaGroupBy.FOLDER -> buildFolderProjection(media, sortOrder)
        }
    }

    private fun buildDateProjection(media: List<Medium>, sortOrder: SortOrder): FlatFeedProjection {
        val ordered = if (sortOrder == SortOrder.ASCENDING) {
            media.sortedBy { it.dateTakenMs }
        } else {
            media.sortedByDescending { it.dateTakenMs }
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val currentYear = today.year
        val locale = Locale.getDefault()
        val fmtSameYear = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
        val fmtOtherYear = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)

        val indexToPill = ArrayList<String>(ordered.size)
        val idToIndex = HashMap<Long, Int>(ordered.size)
        val grouped = LinkedHashMap<String, MutableList<Medium>>()

        var lastDay: LocalDate? = null
        var lastLabel = ""

        ordered.forEachIndexed { idx, item ->
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
            items = ordered,
            sections = sections,
            indexToPill = indexToPill,
            idToIndex = idToIndex
        )
    }

    private fun buildSizeProjection(media: List<Medium>, sortOrder: SortOrder): FlatFeedProjection {
        val ordered = if (sortOrder == SortOrder.ASCENDING) {
            media.sortedBy { it.sizeBytes }
        } else {
            media.sortedByDescending { it.sizeBytes }
        }

        val buckets = LinkedHashMap<String, MutableList<Medium>>()
        for (item in ordered) {
            val bucket = when {
                item.sizeBytes >= 100L * 1024L * 1024L -> "Huge (> 100 MB)"
                item.sizeBytes >= 10L * 1024L * 1024L -> "Large (10–100 MB)"
                item.sizeBytes >= 1L * 1024L * 1024L -> "Medium (1–10 MB)"
                else -> "Small (< 1 MB)"
            }
            buckets.getOrPut(bucket) { ArrayList() }.add(item)
        }

        val flatItems = ArrayList<Medium>(ordered.size)
        val indexToPill = ArrayList<String>(ordered.size + buckets.size)
        val idToGridIndex = HashMap<Long, Int>(ordered.size)
        val sections = ArrayList<TimelineSection>(buckets.size)

        var gridIndex = 0
        for ((bucket, list) in buckets) {
            sections.add(TimelineSection(title = "$bucket · ${list.size}", pillLabel = bucket, items = list))
            // Account for full-span section header slot in LazyVerticalGrid
            indexToPill.add(bucket)
            gridIndex++
            for (item in list) {
                flatItems.add(item)
                indexToPill.add(bucket)
                idToGridIndex[item.id] = gridIndex
                gridIndex++
            }
        }

        return FlatFeedProjection(
            items = flatItems,
            sections = sections,
            indexToPill = indexToPill,
            idToIndex = idToGridIndex
        )
    }

    private fun buildFolderProjection(media: List<Medium>, sortOrder: SortOrder): FlatFeedProjection {
        val grouped = LinkedHashMap<String, MutableList<Medium>>()
        for (item in media) {
            val folder = item.bucketName.ifBlank { "Internal Storage" }
            grouped.getOrPut(folder) { ArrayList() }.add(item)
        }

        val sortedFolders = if (sortOrder == SortOrder.ASCENDING) {
            grouped.entries.sortedBy { it.key.lowercase(Locale.ROOT) }
        } else {
            grouped.entries.sortedByDescending { it.key.lowercase(Locale.ROOT) }
        }

        val flatItems = ArrayList<Medium>(media.size)
        val indexToPill = ArrayList<String>(media.size + sortedFolders.size)
        val idToGridIndex = HashMap<Long, Int>(media.size)
        val sections = ArrayList<TimelineSection>(sortedFolders.size)

        var gridIndex = 0
        for ((folder, list) in sortedFolders) {
            val orderedItems = if (sortOrder == SortOrder.ASCENDING) {
                list.sortedBy { it.dateTakenMs }
            } else {
                list.sortedByDescending { it.dateTakenMs }
            }
            sections.add(TimelineSection(title = "$folder · ${orderedItems.size}", pillLabel = folder, items = orderedItems))
            indexToPill.add(folder)
            gridIndex++
            for (item in orderedItems) {
                flatItems.add(item)
                indexToPill.add(folder)
                idToGridIndex[item.id] = gridIndex
                gridIndex++
            }
        }

        return FlatFeedProjection(
            items = flatItems,
            sections = sections,
            indexToPill = indexToPill,
            idToIndex = idToGridIndex
        )
    }
}
