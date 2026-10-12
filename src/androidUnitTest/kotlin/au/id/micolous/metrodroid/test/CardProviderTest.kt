/*
 * Copyright 2026 Metrodroid contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package au.id.micolous.metrodroid.test

import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.net.Uri
import au.id.micolous.metrodroid.provider.CardDBHelper
import au.id.micolous.metrodroid.provider.CardProvider
import au.id.micolous.metrodroid.provider.CardsTableColumns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression for the side-by-side .dev build: its manifest uses a suffixed
 * ContentProvider authority, which must be recognized during card persistence.
 */
class CardProviderTest : BaseInstrumentedTest() {
    @Test
    fun testInstalledPackageUriMatcher() {
        val authority = context.packageName + ".cardprovider"
        val collection = Uri.parse("content://$authority/cards")
        val item = Uri.parse("content://$authority/cards/1")
        val matcher = CardProvider.createUriMatcher(authority)

        assertEquals(collection, CardProvider.CONTENT_URI_CARD)
        assertEquals(CardDBHelper.CARD_COLLECTION_URI_INDICATOR, matcher.match(collection))
        assertEquals(CardDBHelper.SINGLE_CARD_URI_INDICATOR, matcher.match(item))
        assertEquals(-1, matcher.match(Uri.parse("content://another.package.cardprovider/cards")))
    }

    @Test
    fun testInsertAndQueryWithVariantAuthority() {
        val provider = CardProvider()
        provider.attachInfo(context, ProviderInfo().apply {
            authority = context.packageName + ".cardprovider"
            exported = false
        })

        val collection = CardProvider.CONTENT_URI_CARD
        assertEquals(CardDBHelper.CARD_DIR_TYPE, provider.getType(collection))

        val values = ContentValues().apply {
            put(CardsTableColumns.TYPE, "test")
            put(CardsTableColumns.TAG_SERIAL, "TEST-UID")
            put(CardsTableColumns.DATA, "{}")
            put(CardsTableColumns.SCANNED_AT, 1L)
        }
        val item = assertNotNull(provider.insert(collection, values))
        assertEquals(CardDBHelper.CARD_ITEM_TYPE, provider.getType(item))

        provider.query(item, null, null, null, null).use { cursor ->
            assertNotNull(cursor)
            assertTrue(cursor.moveToFirst())
            assertEquals("TEST-UID",
                    cursor.getString(cursor.getColumnIndexOrThrow(CardsTableColumns.TAG_SERIAL)))
        }
    }
}
