# SoluPlay App — the Android app, its releases and the update manifest

This repository holds the SoluPlay Android app, the published builds the
download page serves, and the release manifest that page reads. It is a
**separate account** from the main SoluPlay project and shares nothing with it.

`PLAN.md` is the spec of record for the app: the phases, the gates and the
decisions, with every later change appended as a numbered amendment.

## What lives here

| Path | Purpose |
| --- | --- |
| `app/` | The Android application module. Both launchers live here. |
| `core/` | Shared code: API, models, the ladder, the player, the updater. |
| `feature-phone/` | The handset UI. |
| `feature-tv/` | The TV UI. It also ships inside the phone APK. |
| `app/src/main/assets/catalogue-channels.json` | The guide baked into the APK — the last resort when the site cannot be reached. |
| `scripts/` | The build helper and the release-key generator. |
| `.github/workflows/android.yml` | CI: tests, lint, and the signed release. |
| `latest.json` | The manifest. The single source of truth for the current version. |
| GitHub Releases | The APK assets themselves. The release must be **public**. |

`core/`, `feature-phone/` and `feature-tv/` are source directories of the one
Gradle module, not separate modules: the shared code is on every variant, the
phone UI only on the phone flavour, and the TV UI on both.

## Two flavours, one shipped APK

| Flavour | Application id | Shipped |
| --- | --- | --- |
| `phone` | `com.soluplay.app` | Yes — carries the handset **and** the TV launcher |
| `tv` | `com.soluplay.tv` | Not yet — prepared for the split |

One APK covers a phone and an Android TV box. The split becomes real when the
app is listed on the Play Store with a dedicated TV section, or when the two
release cadences diverge.

## Building

The build needs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0)
and downloads nothing:

```bash
scripts/build.sh
```

That builds both debug flavours and runs the unit tests. It points at the
portable toolchain on this machine; on another machine, set `JAVA_HOME` and
`ANDROID_HOME` first. `scripts/build.sh --online <task>` allows the network.

Everything is pinned in `gradle/libs.versions.toml`: AGP 8.5.2, Kotlin 2.1.0,
Compose BOM 2024.12.01, Media3 1.4.1, min SDK 21, target SDK 34.

## Where the app is now

| Phase | State |
| --- | --- |
| 0 — spec, skeleton, CI | Done. Both launchers, offline build, lint clean, unit tests green. |
| 1 — browse | Done. The live catalogue, the match board, the phone grid, the TV focus grid, the offline cache and the site-down mode. |
| 2 — playback | Not started. |

Phase 1 reads the live site through the app's own client, falls back to the
copy the device saved, and falls back again to the copy baked into the APK.
Every screen says which of the three it is showing.

**Reading the real site.** The unit tests never touch the network; the live
harness does, and only when it is asked for:

```bash
./gradlew test -Dsoluplay.live=1
```

**Refreshing the baked catalogue.** The seed is the guide as it looked on the
day the APK was packaged, so it is regenerated whenever the catalogue changes:

```bash
node scripts/make-seed.js
```

The seed carries the **guide only** — names, logos, categories and the order. The
mirror ladder is left out on purpose: a baked upstream address is a stale credential
in the APK and in the repository, and the app never plays from the seed. Playback
asks the live API for the ladder the site publishes.

## The release key

The key that signs a published APK can never be replaced without breaking
updates for everyone who already installed the app. It is created once, on the
machine that will publish, and it never enters the repository:

```bash
export SOLUPLAY_KEYSTORE_PASSWORD='...'
scripts/create-release-keystore.sh
```

The passphrase is read from the environment and handed to `keytool` through
`:env`, so it never reaches a command line or a log. The script writes
`keystore/release.jks` and `keystore.properties`, both gitignored.

**Back the keystore up to two places off this machine, and keep the passphrase
in a password manager.** Without both, no further update can ever be signed.

## CI

`.github/workflows/android.yml` runs on every push to `main` and on every pull
request: unit tests, lint for both flavours, and the debug APKs, with the phone
APK uploaded as a build artifact.

Pushing a `v*` tag additionally builds the signed release APK and attaches it
to a GitHub Release as `SoluPlay.apk`. That job needs four repository secrets,
none of which exist yet:

| Secret | Meaning |
| --- | --- |
| `KEYSTORE_BASE64` | The keystore file, base64 encoded |
| `KEYSTORE_PASSWORD` | The keystore password |
| `KEY_ALIAS` | The key alias |
| `KEY_PASSWORD` | The key password |

A manual release on your own machine stays supported, so a bad CI day cannot
block a release.

## `latest.json`

It is served over plain HTTPS from GitHub Raw and is read by the download page
on every visit:

```
https://raw.githubusercontent.com/johncarry301/soluplay-app/main/latest.json
```

No GitHub API is called and nothing polls. The page revalidates the file, so a
new release shows up within seconds of the file being committed.

### Fields

| Field | Meaning |
| --- | --- |
| `schemaVersion` | Manifest format version. Bump only if the shape changes. |
| `appId` | Application id, also the future updater's identity check. |
| `versionCode` | Monotonically increasing integer. The future updater compares this. |
| `versionName` | Human version, e.g. `1.2.0`. Shown on the download page. |
| `updatedAt` | ISO-8601 timestamp of the release. |
| `releaseNotes` | Short release note text. |
| `downloadUrl` | Direct release-asset URL for the combined APK. |
| `fileName` | File name the visitor's browser should save. |
| `sizeBytes` | Exact size of the APK in bytes. `0` hides the size on the page. |
| `sha256` | SHA-256 of the APK. A future in-app updater verifies this. |
| `android.minSdkVersion` | Lowest supported API level. |
| `android.minAndroidVersion` | Lowest Android version shown to visitors, e.g. `5.0`. |
| `android.targetSdkVersion` | API level the build targets. |
| `variants.phone` | Download entry for phones and tablets. |
| `variants.tv` | Download entry for Android TV and TV boxes. |

`variants.<id>.url` is a **direct asset URL**, never a release webpage:

```
https://github.com/johncarry301/soluplay-app/releases/latest/download/SoluPlay.apk
```

The `latest` path always points at the newest published release, so a new
release needs no change in this file for the URL to stay correct — only
`versionCode`, `versionName`, `sizeBytes`, `sha256` and the notes do.

### One APK, then two

While the phone and TV builds are the same file, both variants point at the
same URL. When the flavors split, give each variant its own asset name and URL
and the page follows automatically — no change to the website.

## Publishing a new release

1. Build the APK and upload it as a release asset named `SoluPlay.apk`
   (or one file per variant when they split). The release must be **public**,
   otherwise the download returns 404.
2. Fill in the manifest and commit it:

   ```powershell
   $apk = "C:\path\to\SoluPlay.apk"
   (Get-Item $apk).Length
   (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
   ```

   Put the size in `sizeBytes` and the hash in `sha256`, bump `versionCode`
   and `versionName`, set `updatedAt`, and write the notes.
3. Commit and push `latest.json` to `main`.

The download page then shows the new version and hands out the new file. The
website is never rebuilt and redeployed for this.

## While no APK is published

`sizeBytes` may stay `0` and `sha256` empty — the page simply omits the size.
Do not publish the manifest before the release asset exists: a manifest with a
live URL but no asset behind it gives visitors a 404 instead of the page's
"link will be available soon" notice.
