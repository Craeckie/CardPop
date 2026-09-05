/*
 * Copyright (C) 2026 FloFla Dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.cardpop.app.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/** Shown when neither the provider nor the URI yields anything usable. */
private const val FALLBACK_NAME = "selected_file"

/**
 * Return the human-readable file name behind a SAF [uri].
 *
 * `uri.lastPathSegment` is a provider *document id*, not a file name: the Downloads
 * provider yields ids like `msf:1000012345`, and only the external-storage provider
 * happens to embed a real path. So we ask the provider for
 * [OpenableColumns.DISPLAY_NAME] first and treat the path segment as a fallback.
 */
fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String {
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) {
                cursor.getString(column)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: FALLBACK_NAME
}
