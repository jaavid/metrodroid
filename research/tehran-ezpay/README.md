# Tehran EZPay: decoded fields and diagnostics

This fork displays the currently confirmed EZPay MIFARE Classic fields as **read-only** data.

## Default Info tab

- Journey state from sector 3, block 1, byte 0 (when readable). Values 0 and 1 are interpreted from captured entry / exit sequences.
- Previous stored balance from the older of the two alternating records in sector 4, block 0 and sector 5, block 0.
- Balance change between the newest and older records. This is **not** always a fare: recharges, adjustments, and other transactions can also change the balance. The UI formats the values in Iranian rials and respects balance-obfuscation preferences.

## Raw diagnostics (enable the Raw level setting)

- Current and previous record counters, plus the sector containing the active record.
- Uninterpreted trailing bytes (8–15) of each balance record.
- Sector 3, block 0; sector 3, block 1 (if readable); sector 15, block 1 (if readable).

Raw values are **not obfuscated** by Metrodroid. Handle dumps and screenshots carefully: they can identify individual cards.

## What remains unknown

This implementation does not claim to identify metro station names, timestamps, trip history, fare products, or the meaning of the uninterpreted metadata bytes. Additional real-card observations would be needed to confirm these interpretations.

No writing, recharging, or modification of transit-card data is implemented. Do not publish live authentication keys as part of new fixtures.
