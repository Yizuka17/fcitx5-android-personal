/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataDescriptorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun windowsDirectoriesMergeWithPluginDirectories() {
        val descriptor = DataDescriptor(
            "cached",
            mapOf(
                "usr\\share\\fcitx5" to "",
                "usr/share/fcitx5" to "",
                "usr/share/fcitx5/frontend.conf" to "hash"
            ),
            mapOf("usr\\share\\link" to "usr\\share\\fcitx5")
        )

        val normalized = descriptor.normalizePaths()

        assertEquals("cached", normalized.sha256)
        assertEquals(
            mapOf("usr/share/fcitx5" to "", "usr/share/fcitx5/frontend.conf" to "hash"),
            normalized.files
        )
        assertEquals(mapOf("usr/share/link" to "usr/share/fcitx5"), normalized.symlinks)
    }

    @Test
    fun normalizedDescriptorKeepsItsHash() {
        val descriptor = DataDescriptor("cached", mapOf("usr/share/frontend.conf" to "hash"))

        assertSame(descriptor, descriptor.normalizePaths())
    }

    @Test
    fun intactInstallationKeepsCachedDescriptor() {
        temporaryFolder.newFolder("usr")
        temporaryFolder.newFile("usr/frontend.conf").writeText("[Addon]")
        val cached = DataDescriptor("cached", mapOf("usr" to "", "usr/frontend.conf" to "hash"))

        assertSame(cached, cached.checkInstalledFiles(temporaryFolder.root))
    }

    @Test
    fun missingFileInvalidatesCacheWithoutDiscardingOtherFiles() {
        temporaryFolder.newFile("present.conf").writeText("[Addon]")
        val cached = DataDescriptor(
            "cached", mapOf("present.conf" to "present", "missing.conf" to "missing")
        )

        val installed = cached.checkInstalledFiles(temporaryFolder.root)

        assertEquals("", installed.sha256)
        assertEquals(mapOf("present.conf" to "present"), installed.files)
    }

    @Test
    fun deletedResourceTreeInvalidatesUnchangedDescriptor() {
        val cached = DataDescriptor(
            "cached", mapOf("usr" to "", "usr/frontend.conf" to "hash")
        )

        val installed = cached.checkInstalledFiles(temporaryFolder.root)

        assertEquals("", installed.sha256)
        assertTrue(installed.files.isEmpty())
    }

    @Test
    fun directoryCannotStandInForAnAssetFile() {
        temporaryFolder.newFolder("frontend.conf")
        val cached = DataDescriptor("cached", mapOf("frontend.conf" to "hash"))

        assertFalse(cached.checkInstalledFiles(temporaryFolder.root).files.containsKey("frontend.conf"))
    }

    @Test
    fun missingOrReplacedLinkIsRecreated() {
        temporaryFolder.newFile("source").writeText("asset")
        temporaryFolder.newFile("replaced-link").writeText("wrong file")
        val cached = DataDescriptor(
            "cached", mapOf("source" to "hash"),
            mapOf("missing-link" to "source", "replaced-link" to "source")
        )

        val installed = cached.checkInstalledFiles(temporaryFolder.root)

        assertEquals("", installed.sha256)
        assertTrue(installed.symlinks.isEmpty())
        assertEquals(cached.files, installed.files)
    }
}
