# SoluPlay App — releases and update manifest

This repository holds the published SoluPlay Android builds and the release
manifest that the download page reads. It is a **separate account** from the
main SoluPlay project and shares nothing with it.

## What lives here

| Path | Purpose |
| --- | --- |
| `latest.json` | The manifest. The single source of truth for the current version. |
| GitHub Releases | The APK assets themselves. The release must be **public**. |

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
