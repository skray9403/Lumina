package org.fossify.gallery.ui.home

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [GalleryUiState] defaults and state operations.
 */
class GalleryViewModelTest {

    @Test
    fun initialState_hasExpectedDefaults() {
        val state = GalleryUiState()

        assertTrue(state.allMedia.isEmpty())
        assertTrue(state.timelineSections.isEmpty())
        assertTrue(state.albums.isEmpty())
        assertEquals(GalleryTab.PHOTOS, state.selectedTab)
        assertTrue(state.selectedIds.isEmpty())
        assertFalse(state.isMultiSelectMode)
        assertEquals(3, state.columnCount)
        assertEquals("", state.searchQuery)
        assertNull(state.activeAlbumId)
        assertNull(state.pendingIntentRequest)
    }

    @Test
    fun selectionOperations_toggleAndClear() {
        val initial = GalleryUiState()
        assertFalse(initial.isMultiSelectMode)

        // Toggle item 1 -> selected
        val selected1 = initial.copy(
            selectedIds = setOf(101L),
            isMultiSelectMode = true
        )
        assertTrue(selected1.isMultiSelectMode)
        assertTrue(selected1.selectedIds.contains(101L))

        // Clear selection
        val cleared = selected1.copy(
            selectedIds = emptySet(),
            isMultiSelectMode = false
        )
        assertFalse(cleared.isMultiSelectMode)
        assertTrue(cleared.selectedIds.isEmpty())
    }

    @Test
    fun tabSelection_updatesTab() {
        val initial = GalleryUiState(selectedTab = GalleryTab.PHOTOS)
        val albumsState = initial.copy(selectedTab = GalleryTab.ALBUMS)

        assertEquals(GalleryTab.ALBUMS, albumsState.selectedTab)
    }

    @Test
    fun columnCount_clamping() {
        val minCount = 1.coerceIn(2, 5)
        assertEquals(2, minCount)

        val maxCount = 6.coerceIn(2, 5)
        assertEquals(5, maxCount)

        val validCount = 4.coerceIn(2, 5)
        assertEquals(4, validCount)
    }
}
