# Personal Tehran EZPay test builds

Metrodroid already supports static MIFARE Classic keys bundled as Android
assets: `assets/keys/static/*.json`. The **dev build** can use this existing
read-only authentication path without asking the tester to import a key file
after installation. This does **not** enable writing or recharging cards.

## Security boundary

**Do not commit authentication keys to this repository, or publish an APK
containing them.** This fork is public. Secrets stored in GitHub Actions
can be extracted from any APK that includes them; using an Actions secret
does not make the distributed APK secure.

The file `src/dev/assets/keys/` is ignored by Git. Ordinary public CI builds
never inject personal keys, and the workflow fails if the expected private
EZPay asset accidentally appears in an unapproved build.

## Local personal build

Copy your own properly authorized static-key JSON file to:

```bash
mkdir -p src/dev/assets/keys/static
cp /path/to/Metrodroid-Keys.json src/dev/assets/keys/static/tehran-ezpay.json
./gradlew assembleDev
```

The JSON must be in Metrodroid's `MifareClassicStatic` format. The
existing `ClassicAndroidReader` / `CardKeysFromFiles` flow automatically
loads all `assets/keys/static/*.json` files.

This asset is included in **dev only**. Do not share the resulting APK.
The unkeyed standard APK, whose build does not use the personal file, can
still import keys via the app's Keys screen.

## Automated GitHub Actions personal build

Use a **separate private repository** with the same workflow and a trusted
branch, rather than this public fork, for APKs with embedded keys. In that
private repository set the Actions secret `EZPAY_STATIC_KEYS_B64` to
the Base64-encoded *entire* JSON file:

```bash
base64 < /path/to/Metrodroid-Keys.json | tr -d '\n' |
  gh secret set EZPAY_STATIC_KEYS_B64 -R YOUR_ACCOUNT/YOUR_PRIVATE_REPO
```

On push/merge to a trusted branch or manual `workflow_dispatch`, the
workflow injects the secret file into `src/dev/assets/keys/static/` before
`assembleDev`. It validates the file without printing key values and then
checks that the asset is present in the built APK. This injection is
disabled for pull requests and public repositories.

**Never share a keyed artifact**, even from a private repository. Anyone who
obtains the APK may extract its bundled static keys. Rotate/revoke keys if
their confidentiality is compromised, when that is feasible.

## Troubleshooting

- If the dev APK only displays the manufacturer and raw card memory,
  inspect whether sectors 3–5 were read and whether this was a *keyed*
  personal build.
- If `assets/keys/static/tehran-ezpay.json` is absent from the APK, the
  workflow correctly made an unkeyed build.
- If an older dev APK uses a different debug signing certificate, uninstall
  that dev package before installing the new personal build.
