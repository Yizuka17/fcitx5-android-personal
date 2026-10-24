/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core.data

import kotlinx.serialization.Serializable
import java.io.File

typealias SHA256 = String

/**
 * A list of files with sha256
 */
@Serializable
data class DataDescriptor(
    /**
     * Implementation dependent, will be used to quick check if two descriptors are the same
     */
    val sha256: SHA256,
    /**
     * path -> sha256
     * sha256 will be empty if the path is a directory
     */
    val files: Map<String, SHA256>,
    /**
     * Symbolic links from target -> source
     */
    val symlinks: Map<String, String> = mapOf()
)

/** Older Windows builds used backslashes for directories but slashes for files. */
internal fun DataDescriptor.normalizePaths(): DataDescriptor {
    if (files.keys.none { '\\' in it } &&
        symlinks.none { (path, source) -> '\\' in path || '\\' in source }
    ) return this
    return copy(
        files = files.mapKeys { (path, _) -> path.replace('\\', '/') },
        symlinks = symlinks.map { (path, source) ->
            path.replace('\\', '/') to source.replace('\\', '/')
        }.toMap()
    )
}

/** A cached descriptor is only valid while its installed files and links still exist. */
internal fun DataDescriptor.checkInstalledFiles(root: File): DataDescriptor {
    val installedFiles = files.filter { (path, checksum) ->
        val file = root.resolve(path)
        if (checksum.isEmpty()) file.isDirectory else file.isFile && file.canRead()
    }
    val installedSymlinks = symlinks.filter { (path, source) ->
        val target = root.resolve(path)
        target.exists() && runCatching {
            target.canonicalFile == root.resolve(source).canonicalFile
        }.getOrDefault(false)
    }
    return if (installedFiles.size == files.size && installedSymlinks.size == symlinks.size) {
        this
    } else {
        // Bypass the descriptor hash shortcut so diff() reinstalls the missing entries.
        copy(sha256 = "", files = installedFiles, symlinks = installedSymlinks)
    }
}
