/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.core.data.DataDescriptor
import org.fcitx.fcitx5.android.core.data.DataHierarchy
import org.fcitx.fcitx5.android.core.data.FileAction
import org.fcitx.fcitx5.android.core.data.FileSource
import org.fcitx.fcitx5.android.core.data.checkInstalledFiles
import org.fcitx.fcitx5.android.core.data.normalizePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataHierarchyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun unavailablePluginDoesNotDeleteSharedDirectoriesFromWindowsBuild() {
        val main = DataDescriptor(
            "main",
            mapOf(
                "usr" to "",
                "usr\\share" to "",
                "usr\\share\\fcitx5" to "",
                "usr\\share\\fcitx5\\addon" to "",
                "usr/share/fcitx5/addon/androidfrontend.conf" to "frontend"
            )
        )
        val old = DataDescriptor(
            "main-with-plugin",
            main.files + mapOf(
                "usr/share" to "",
                "usr/share/fcitx5" to "",
                "usr/share/fcitx5/addon" to "",
                "usr/share/fcitx5/addon/plugin.conf" to "plugin"
            )
        )

        // Before the fix, removing a plugin schedules deletion of the whole addon directory.
        val legacy = DataHierarchy().apply { install(main, FileSource.Main) }
        assertTrue(DataHierarchy.diff(old, legacy).contains(FileAction.DeleteDir("usr/share/fcitx5/addon")))

        val normalized = DataHierarchy().apply { install(main.normalizePaths(), FileSource.Main) }
        val actions = DataHierarchy.diff(old.normalizePaths(), normalized)
        assertFalse(actions.any { it is FileAction.DeleteDir })
        assertEquals(listOf(FileAction.DeleteFile("usr/share/fcitx5/addon/plugin.conf")), actions)
    }

    @Test
    fun missingAssetsAreRestoredEvenWhenDescriptorHashHasNotChanged() {
        val main = DataDescriptor(
            "main", mapOf("androidfrontend.conf" to "frontend", "keyboard.conf" to "keyboard")
        )
        val hierarchy = DataHierarchy().apply { install(main, FileSource.Main) }
        val cached = hierarchy.downToDataDescriptor()
        temporaryFolder.newFile("keyboard.conf").writeText("keyboard")

        // An unchanged descriptor alone used to skip installation of the missing frontend.
        assertTrue(DataHierarchy.diff(cached, hierarchy).isEmpty())
        val actions = DataHierarchy.diff(cached.checkInstalledFiles(temporaryFolder.root), hierarchy)
        assertEquals(listOf(FileAction.CreateFile("androidfrontend.conf", FileSource.Main)), actions)
    }
}
