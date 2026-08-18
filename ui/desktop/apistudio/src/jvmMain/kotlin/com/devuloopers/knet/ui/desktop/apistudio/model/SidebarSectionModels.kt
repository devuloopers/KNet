package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem

/**
 * Top-level section categories in the API Studio Collections Sidebar.
 *
 * @property label User-facing title string for the section header.
 */
enum class SidebarSectionType(val label: String) {
    UNSAVED("Unsaved Sessions"),
    SAVED("Saved Collections")
}

/**
 * State DTO holding collapse/expand state configuration for sidebar sections.
 *
 * @property isUnsavedExpanded True if the Unsaved Sessions dropdown section is expanded.
 * @property isSavedExpanded True if the Saved Collections dropdown section is expanded.
 */
data class SidebarSectionState(
    val isUnsavedExpanded: Boolean = true,
    val isSavedExpanded: Boolean = true
)
