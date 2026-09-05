package com.cardpop.app.util

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowContentResolver

/** Returns a display name for every query — stands in for a DocumentsProvider. */
class NamingProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
        addRow(arrayOf<Any>("flash2609051105.xml"))
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

/** Returns an empty cursor — stands in for a provider that exposes no display name. */
class SilentProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

@RunWith(RobolectricTestRunner::class)
class UriFileNameTest {

    private val resolver get() = RuntimeEnvironment.getApplication().contentResolver

    @Test
    fun `display name wins over an opaque document id`() {
        ShadowContentResolver.registerProviderInternal("naming.docs", NamingProvider())
        val uri = Uri.parse("content://naming.docs/document/msf%3A1000012345")

        assertEquals("flash2609051105.xml", resolveDisplayName(resolver, uri))
    }

    @Test
    fun `falls back to the last path segment when the provider has no display name`() {
        ShadowContentResolver.registerProviderInternal("silent.docs", SilentProvider())
        val uri = Uri.parse("content://silent.docs/document/primary%3ADownload%2Fdeck.csv")

        assertEquals("deck.csv", resolveDisplayName(resolver, uri))
    }

    @Test
    fun `falls back to a placeholder when there is nothing usable`() {
        ShadowContentResolver.registerProviderInternal("silent2.docs", SilentProvider())
        val uri = Uri.parse("content://silent2.docs")

        assertEquals("selected_file", resolveDisplayName(resolver, uri))
    }
}
