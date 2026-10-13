/*
 * Copyright 2026 Metrodroid contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package au.id.micolous.metrodroid.transit.tehran_ezpay

import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.classic.ClassicCard
import au.id.micolous.metrodroid.card.classic.ClassicCardTransitFactory
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.transit.CardInfo
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.TransitRegion
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.util.ImmutableByteArray

private val NAME = R.string.card_name_tehran_ezpay
private const val UINT32_MASK = 0xffffffffL
private const val UINT32_HALF_RANGE = 0x80000000L

private val CARD_INFO = CardInfo(
        name = NAME,
        locationId = R.string.location_tehran,
        cardType = CardType.MifareClassic,
        region = TransitRegion.IRAN,
        keysRequired = true,
        preview = true)

internal data class TehranEzpayRecord(val counter: Long, val balance: Int)

internal fun parseTehranEzpayRecord(data: ImmutableByteArray): TehranEzpayRecord? {
    if (data.size != 16)
        return null
    val counter = data.byteArrayToLong(0, 4)
    val balance = data.byteArrayToLong(4, 4)
    if (balance > Int.MAX_VALUE)
        return null
    return TehranEzpayRecord(counter, balance.toInt())
}

/** Unsigned serial-number comparison as described by RFC 1982, for 32-bit counters. */
internal fun isTehranEzpayCounterNewer(candidate: Long, reference: Long): Boolean {
    val difference = (candidate - reference) and UINT32_MASK
    return difference != 0L && difference < UINT32_HALF_RANGE
}

internal fun selectTehranEzpayRecord(first: TehranEzpayRecord,
                                     second: TehranEzpayRecord): TehranEzpayRecord =
        if (isTehranEzpayCounterNewer(second.counter, first.counter)) second else first

private fun requiredBlock(card: ClassicCard, sector: Int, block: Int): ImmutableByteArray? {
    val rawSector = card.sectorsRaw.getOrNull(sector) ?: return null
    if (rawSector.isUnauthorized || rawSector.error != null)
        return null
    return rawSector.blocks.getOrNull(block)?.takeIf { it.size == 16 }
}

private fun serial(card: ClassicCard): String =
        (card.tagId + requiredBlock(card, 0, 0)!![4]).toHexString().uppercase()

private fun journeyStateLabel(state: Int?): String? = when (state) {
    0 -> Localizer.localizeString(R.string.tehran_ezpay_entered)
    1 -> Localizer.localizeString(R.string.tehran_ezpay_exited)
    null -> null
    else -> Localizer.localizeString(R.string.tehran_ezpay_unknown,
            state.toString(16).uppercase().padStart(2, '0'))
}

@Parcelize
data class TehranEzpayTransitData(private val mSerial: String,
                                  private val mBalance: Int,
                                  internal val recordCounter: Long,
                                  private val mJourneyState: Int?,
                                  internal val previousBalance: Int,
                                  internal val previousCounter: Long,
                                  internal val activeBalanceSector: Int,
                                  private val mActiveMetadata: String,
                                  private val mPreviousMetadata: String,
                                  private val mStateBlock: String,
                                  private val mJourneyBlock: String?,
                                  private val mMirroredStateBlock: String?) : TransitData() {
    override val serialNumber get() = mSerial
    override val cardName get() = Localizer.localizeString(NAME)
    override val balance get() = TransitCurrency(mBalance, "IRR", 1)

    // Info is visible with the default settings; raw fields are only shown in diagnostic mode.
    // Neither record should be interpreted as a confirmed journey or station identifier.
    override val info: List<ListItem>
        get() = listOfNotNull(
                journeyStateLabel(mJourneyState)?.let {
                    ListItem(R.string.tehran_ezpay_journey_state, it)
                },
                ListItem(R.string.tehran_ezpay_previous_balance,
                        TransitCurrency(previousBalance, "IRR", 1)
                                .maybeObfuscateBalance().formatCurrencyString(true)),
                ListItem(R.string.tehran_ezpay_balance_difference,
                        TransitCurrency(mBalance - previousBalance, "IRR", 1)
                                .maybeObfuscateBalance().formatCurrencyString(false)))

    override fun getRawFields(level: RawLevel): List<ListItem> {
        val state = journeyStateLabel(mJourneyState)
        return listOf(ListItem(R.string.tehran_ezpay_record_counter,
                "$recordCounter (0x${recordCounter.toString(16).uppercase()})")) +
                (if (state == null) emptyList() else listOf(
                        ListItem(R.string.tehran_ezpay_journey_state, state))) +
                listOf(
                        ListItem(R.string.tehran_ezpay_active_sector, activeBalanceSector.toString()),
                        ListItem(R.string.tehran_ezpay_previous_counter,
                                "$previousCounter (0x${previousCounter.toString(16).uppercase()})"),
                        ListItem(R.string.tehran_ezpay_active_metadata, mActiveMetadata),
                        ListItem(R.string.tehran_ezpay_previous_metadata, mPreviousMetadata),
                        ListItem(R.string.tehran_ezpay_state_block, mStateBlock)) +
                listOfNotNull(
                        mJourneyBlock?.let {
                            ListItem(R.string.tehran_ezpay_journey_block, it)
                        },
                        mMirroredStateBlock?.let {
                            ListItem(R.string.tehran_ezpay_mirrored_state_block, it)
                        })
    }
}

object TehranEzpayTransitFactory : ClassicCardTransitFactory {
    override val allCards get() = listOf(CARD_INFO)

    override fun check(card: ClassicCard): Boolean {
        if (card.sectors.size != 16 || card.tagId.size != 4)
            return false
        val manufacturer = requiredBlock(card, 0, 0) ?: return false
        val state = requiredBlock(card, 3, 0) ?: return false
        val balanceA = requiredBlock(card, 4, 0) ?: return false
        val balanceB = requiredBlock(card, 5, 0) ?: return false
        if (manufacturer.sliceOffLen(0, 4) != card.tagId)
            return false
        val bcc = card.tagId.fold(0) { value, byte -> value xor (byte.toInt() and 0xff) }
        if ((manufacturer[4].toInt() and 0xff) != bcc)
            return false

        // Bytes 14..15 contain version-dependent metadata and are not identification fields.
        if (state[0] != 0x18.toByte() || state[1] != 0x40.toByte() ||
                state.sliceOffLen(2, 4) != card.tagId.reverseBuffer())
            return false

        val first = parseTehranEzpayRecord(balanceA) ?: return false
        val second = parseTehranEzpayRecord(balanceB) ?: return false
        val counterDifference = (first.counter - second.counter) and UINT32_MASK
        return counterDifference == 1L || counterDifference == UINT32_MASK
    }

    override fun parseTransitIdentity(card: ClassicCard) =
            TransitIdentity(NAME, serial(card))

    override fun parseTransitData(card: ClassicCard): TehranEzpayTransitData {
        val rawA = requiredBlock(card, 4, 0)!!
        val rawB = requiredBlock(card, 5, 0)!!
        val first = parseTehranEzpayRecord(rawA)!!
        val second = parseTehranEzpayRecord(rawB)!!
        val secondIsNewest = isTehranEzpayCounterNewer(second.counter, first.counter)
        val record = if (secondIsNewest) second else first
        val previous = if (secondIsNewest) first else second
        val activeRaw = if (secondIsNewest) rawB else rawA
        val previousRaw = if (secondIsNewest) rawA else rawB
        val journeyBlock = requiredBlock(card, 3, 1)
        // Only expose confirmed fields as decoded data. Keep unknown bytes as diagnostic hex.
        return TehranEzpayTransitData(
                mSerial = serial(card),
                mBalance = record.balance,
                recordCounter = record.counter,
                mJourneyState = journeyBlock?.get(0)?.toInt()?.and(0xff),
                previousBalance = previous.balance,
                previousCounter = previous.counter,
                activeBalanceSector = if (secondIsNewest) 5 else 4,
                mActiveMetadata = activeRaw.sliceOffLen(8, 8).toHexString().uppercase(),
                mPreviousMetadata = previousRaw.sliceOffLen(8, 8).toHexString().uppercase(),
                mStateBlock = requiredBlock(card, 3, 0)!!.toHexString().uppercase(),
                mJourneyBlock = journeyBlock?.toHexString()?.uppercase(),
                mMirroredStateBlock = requiredBlock(card, 15, 1)?.toHexString()?.uppercase())
    }
}
