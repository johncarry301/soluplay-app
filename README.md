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
| 2 — playback | Done. The playback state machine, the detail fetch, the Media3 host, the phone and TV watch surfaces, and the YouTube Live player. |
| 3 — old boxes | Done. The bundled CA roots and the certificate repair, so a television whose trust store was never updated still plays. |
| 4 — the updater | Code done, device half open. The app can fetch a manifest, download a build, check its fingerprint and hand it to the system installer. |

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

Pushing a `v*` tag additionally builds the signed release APK, attaches it to
a GitHub Release as `SoluPlay.apk`, and writes the manifest from the bytes it
has just published. That job needs four repository secrets, none of which
exist yet:

| Secret | Meaning |
| --- | --- |
| `KEYSTORE_BASE64` | The keystore file, base64 encoded |
| `KEYSTORE_PASSWORD` | The keystore password |
| `KEY_ALIAS` | The key alias |
| `KEY_PASSWORD` | The key password |

The job refuses to publish without them: a build that is not signed with the
release key cannot be installed over the build that was, and a device that
refuses the update is worse than a device that was never offered one.

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
| `versionCode` | Monotonically increasing integer. The updater compares this. |
| `versionName` | Human version, e.g. `1.2.0`. Shown on the download page. |
| `updatedAt` | ISO-8601 timestamp of the release. |
| `releaseNotes` | Short release note text. |
| `apkUrl` | Direct release-asset URL for the phone build. |
| `downloadUrl` | The same URL under the other name; both are read. |
| `fileName` | File name the visitor's browser should save. |
| `sizeBytes` | Exact size of the APK in bytes. `0` hides the size on the page. |
| `sha256` | SHA-256 of the APK. The updater verifies the download against this. |
| `mandatory` | `true` asks the device for the update; after a day of asking, it stops playing. |
| `minSupportedVersionCode` | The oldest build that may still play. Raise it to retire a build. |
| `rolloutPercent` | The share of installs the release is offered to, `1`–`100`. |
| `android.minSdkVersion` | Lowest supported API level. |
| `android.minAndroidVersion` | Lowest Android version shown to visitors, e.g. `5.0`. |
| `android.targetSdkVersion` | API level the build targets. |
| `variants.phone` | Download entry for phones and tablets. |
| `variants.tv` | Download entry for Android TV and TV boxes. |

Each variant carries its own `url`, `fileName`, `sizeBytes`, `sha256` and
`minSdkVersion`, so a device is offered the file that belongs to the surface
it is on. Today both variants name the same file, because the phone flavour
carries the television launcher as well; the split writes a second file into
the `tv` variant and nothing else changes.

`variants.<id>.url` is a **direct asset URL**, never a release webpage:

```
https://github.com/johncarry301/soluplay-app/releases/download/v0.2.0/SoluPlay.apk
```

The publishing job writes the tag-scoped URL of the release it has just made,
so the address always names the file it was written for.

### The three switches

Three fields turn a release on and off without touching the app:

- `versionCode` — the build on offer. Point it back at the previous build and
  no device is offered the newer one.
- `minSupportedVersionCode` — the oldest build that may still play. Raise it
  and every device below it is retired and must take the update.
- `rolloutPercent` — the share of installs the release reaches, bucketed by
  the install id, so the same device is always in the same bucket and a
  release grown from ten percent to fifty reaches exactly the devices that
  were already in it.

## Publishing a new release

The publishing job does it, so the release is one tag:

```bash
git tag v0.2.0
git push origin v0.2.0
```

The job then builds the release APK, publishes it as `SoluPlay.apk`, and
writes `latest.json` from the bytes it published — the size and the SHA-256
are read off the file, never typed in. The manifest is committed back to
`main` by the same job.

The version number lives in `app/build.gradle.kts` (`versionCode` and
`versionName`); the tag and the two must agree, because the app compares the
`versionCode` in the manifest with the one it is running.

### The same key, always

A build can only be installed over a build signed with the **same key**. The
first release therefore has to be installed from scratch, and every release
after it must be signed with the keystore that signed the first one. A lost
keystore ends updates for every device that has the app; it does not end
installs.

## While no APK is published

The manifest names no build: `versionCode` is `0`, the version name is empty
and no URL is filled in. The download page shows its "link will be available
soon" notice and the app is offered nothing, so neither of them points at a
file that is not there.

A manifest with a live URL and no asset behind it is the one thing to avoid:
it gives visitors a 404 instead of the notice, and it makes every device try
to download a file that does not exist.
