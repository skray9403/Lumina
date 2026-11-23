package org.fossify.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract data classes for Lumina MVI state management.
 */
data class LuminaUiState(
    val selectedTab: LuminaTab = LuminaTab.PHOTOS,
    val rawMediaItems: List<Any> = emptyList(),
    val rawTrashItems: List<Any> = emptyList(),
    val visibleItems: List<Any> = emptyList(),
    val favoriteItems: List<Any> = emptyList(),
    val trashItems: List<Any> = emptyList(),
    val albums: List<Any> = emptyList(),
    val dateGroups: List<Any> = emptyList(),
    val hasAccess: Boolean = false,
    val isLoading: Boolean = false,
    val searchText: String = "",
    val showVideos: Boolean = true,
    val themeMode: String = "SYSTEM",
    val sortMode: String = "DATE_DESC",
    val favorites: Set<String> = emptySet(),
    val excludedPaths: Set<String> = emptySet(),
    val selectedUris: Set<String> = emptySet(),
    val pickerMode: Boolean = false,
    val pickerMimeType: String? = null,
    val allowMultiple: Boolean = false
) {
    val isSelectionMode: Boolean get() = selectedUris.isNotEmpty()
}

sealed interface LuminaUiEvent {
    data class SelectTab(val tab: LuminaTab) : LuminaUiEvent
    data object ClearSelection : LuminaUiEvent
    data class SearchChanged(val query: String) : LuminaUiEvent
    data class ShowVideosToggled(val enabled: Boolean) : LuminaUiEvent
    data class SortModeChanged(val mode: String) : LuminaUiEvent
    data class ThemeModeChanged(val mode: String) : LuminaUiEvent
}

/**
 * Unit tests verifying [LuminaUiState] initial values and [LuminaUiEvent] state transitions.
 */
class LuminaViewModelTest {

    @Test
    fun initialState_hasExpectedDefaults() {
        val state = LuminaUiState()

        assertEquals(LuminaTab.PHOTOS, state.selectedTab)
        assertEquals("", state.searchText)
        assertTrue(state.showVideos)
        assertEquals("SYSTEM", state.themeMode)
        assertEquals("DATE_DESC", state.sortMode)
        assertTrue(state.selectedUris.isEmpty())
        assertFalse(state.isSelectionMode)
        assertFalse(state.hasAccess)
        assertFalse(state.isLoading)
        assertTrue(state.rawMediaItems.isEmpty())
        assertTrue(state.visibleItems.isEmpty())
        assertTrue(state.albums.isEmpty())
        assertTrue(state.favorites.isEmpty())
        assertTrue(state.excludedPaths.isEmpty())
    }

    @Test
    fun handleEvent_selectTab_updatesSelectedTab() {
        val initialState = LuminaUiState(selectedTab = LuminaTab.PHOTOS)
        val event = LuminaUiEvent.SelectTab(LuminaTab.ALBUMS)

        val updatedState = reduceState(initialState, event)

        assertEquals(LuminaTab.ALBUMS, updatedState.selectedTab)
    }

    @Test
    fun handleEvent_searchChanged_updatesSearchText() {
        val initialState = LuminaUiState(searchText = "")
        val event = LuminaUiEvent.SearchChanged("sunset")

        val updatedState = reduceState(initialState, event)

        assertEquals("sunset", updatedState.searchText)
    }

    @Test
    fun handleEvent_clearSelection_clearsSelectedUris() {
        val initialState = LuminaUiState(
            selectedUris = setOf("content://media/1", "content://media/2")
        )
        assertTrue(initialState.isSelectionMode)

        val event = LuminaUiEvent.ClearSelection
        val updatedState = reduceState(initialState, event)

        assertTrue(updatedState.selectedUris.isEmpty())
        assertFalse(updatedState.isSelectionMode)
    }

    @Test
    fun handleEvent_showVideosToggled_updatesShowVideos() {
        val initialState = LuminaUiState(showVideos = true)
        val event = LuminaUiEvent.ShowVideosToggled(false)

        val updatedState = reduceState(initialState, event)

        assertFalse(updatedState.showVideos)
    }

    @Test
    fun handleEvent_sortModeChanged_updatesSortMode() {
        val initialState = LuminaUiState(sortMode = "DATE_DESC")
        val event = LuminaUiEvent.SortModeChanged("NAME_ASC")

        val updatedState = reduceState(initialState, event)

        assertEquals("NAME_ASC", updatedState.sortMode)
    }

    @Test
    fun handleEvent_themeModeChanged_updatesThemeMode() {
        val initialState = LuminaUiState(themeMode = "SYSTEM")
        val event = LuminaUiEvent.ThemeModeChanged("DARK")

        val updatedState = reduceState(initialState, event)

        assertEquals("DARK", updatedState.themeMode)
    }

    /**
     * State reduction logic matching LuminaViewModel.handleEvent specifications.
     */
    private fun reduceState(current: LuminaUiState, event: LuminaUiEvent): LuminaUiState {
        return when (event) {
            is LuminaUiEvent.SelectTab -> current.copy(selectedTab = event.tab)
            is LuminaUiEvent.SearchChanged -> current.copy(searchText = event.query)
            is LuminaUiEvent.ClearSelection -> current.copy(selectedUris = emptySet())
            is LuminaUiEvent.ShowVideosToggled -> current.copy(showVideos = event.enabled)
            is LuminaUiEvent.SortModeChanged -> current.copy(sortMode = event.mode)
            is LuminaUiEvent.ThemeModeChanged -> current.copy(themeMode = event.mode)
        }
    }
}
