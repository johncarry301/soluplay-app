# SoluPlay Native App — Full Plan

> **Official development roadmap.** Saved 2026-11-03, exactly as documented, before any code was written. Work proceeds phase by phase; this document is not to be edited or replaced as work advances — amendments are added as clearly-marked notes at the end of the relevant section.

---

## 0. Ground rules

| Rule | Source |
|---|---|
| **$0** — no paid service, no store account, no paid API | your standing rule |
| Sibling project, **never inside `freetv`** | the Direct-HLS isolation policy |
| `freetv` changes are **additive only** — new routes, never edits to the frozen 18 paths | frozen zone |
| One keystore, generated once, gitignored, **backed up off this PC** | the lost `soluplay-tv.jks` |
| Nothing called "verified" without a physical device | no Android device attached here |
| Commit locally; push only when you say push | standing rule |
| Bengali-first, English secondary | your audience |
| No download-the-app promo anywhere in-app | the `SoluPlayApp` contract |

**Scope fences — the app will never:** relay video through Vercel, ship DRM, add an account system, re-rank the ladder, show manual server buttons, touch the web player, or source any provider-protected stream.

## 1. Architecture

```
SoluPlay-App/                    ← new repo: sulayman-a-khan/soluplay-app (public)
├─ core/                         ← platform-independent, shared by both surfaces
│   ├─ api/          catalogue, channel, fixtures, youtube-resolve clients
│   ├─ model/        Channel, StreamLink, Fixture, UpdateManifest
│   ├─ ladder/       the published-order walk + failover state machine
│   ├─ player/       Media3 wrapper, YouTube path, first-frame rule
│   ├─ tls/          ProviderTls rebuild (chain completion + bundled roots)
│   ├─ updater/      manifest check, download, sha256, install, rollout
│   └─ cache/        offline catalogue, last-good response
├─ feature-phone/                ← Compose phone UI
├─ feature-tv/                   ← Compose TV UI (androidx.tv.tvmaterial)
└─ app/                          ← manifests, launchers, DI, product flavors
```

- **One Gradle project, two product flavors** (`phone`, `tv`), separate `applicationId`s so both can sit on one device.
- **Ship one APK first** — a single build declaring both `LAUNCHER` and `LEANBACK_LAUNCHER`. Split into two flavors only on a trigger (§12).
- Kotlin + Jetpack Compose, `minSdk 21`, `compile/target 34`, Gradle 8.9 — matching the toolchain already on your PC.
- **Media3 ExoPlayer 1.4.1** — the version proven in the old app and present in your warm Gradle cache. A newer1.6.x needs a network fetch, which the offline `--offline` build forbids.
- **Phase 0 must inventory `C:\Users\sahil\.gradle\caches\8.9` first** and pin every dependency to a version already cached. Anything missing = no offline build = the whole toolchain assumption breaks.

## 2. Data contract

**Consumed, unchanged, already public:**

| Endpoint | Gives | Cache |
|---|---|---|
| `GET /api/channels` | pinned catalogue, `activeStreamCount`, `primaryStream` | 30 s edge |
| `GET /api/channels/[id]` | channel + `streams[]` **pre-ranked**, `playable` | 15 s edge |
| `GET /api/fixtures` | match cards + kickoff clock + openable channels | 15 s edge |
| `POST /api/youtube/resolve` | YouTube URL → live `videoId` | none |

**New in `freetv`, additive only:**

| Endpoint | Purpose |
|---|---|
| `GET /api/app/bootstrap` | catalogue + rails + fixtures + categories + catalogue version hash, one round trip, ETag |
| `GET /api/app/version` | the update manifest (tiny JSON) |
| `GET /api/app/playback` | *later phase* — signed per-stream headers for feeds needing Referer/Origin. Never raw provider credentials |

**Error semantics the app must honour:** `503` = outage, **not** an empty catalogue. The server already refuses to cache an empty catalogue; the app must refuse to render "No Channels Online" on a 503.

## 3. The ladder — one rule, one implementation

```
streams[] arrive in published priority order  →  walk them in that order
the app never re-ranks                           →  the server is the only truth
no manual "Server N" control exists →  same rule the web now follows
```

- **Failover triggers:** player error · no first frame inside the deadline · playback clock frozen past the stall window.
- **On exhaustion:** the weak-signal state with one clear line of text — never an infinite spinner.
- **Same first-frame-proven rule** as the web: no picture shown until output is proven, no black gap between tuning and picture.

## 4. Playback engine

| Link type | Route |
|---|---|
| `.m3u8` / HLS | Media3 HLS |
| `.ts` / MPEG-TS | Media3 MPEG-TS — **one player, no separate TS path** the web needs |
| `.mp4`, `.mpd`, audio | Media3 generic / DASH |
| YouTube | `/api/youtube/resolve` → `videoId` → WebView + IFrame API |

**YouTube on TV:** the IFrame path, not the YouTube Android Player API — the latter needs Play Services, which many boxes lack, and the IFrame path is exactly what the web already does.

**Player features:** aspect-ratio control · subtitles (Media3 reads HLS subtitle tracks) · audio-track selection · HD/SD toggle (bandwidth + old-box decode) · PiP and background audio (phone only) · a stats overlay (buffer, decoder, bitrate, dropped frames).

**Old-box TLS repair — the rebuild.** `ProviderTls` (chain completion + bundled ISRG Root X1 / DigiCert G3 + full PKIX validation against the bundle) is what rescued aynaott / alixbd / sofast on Android 9–11 boxes. It returns with the same diagnostic reasons (`tls-issuer`, `tls-expired`, `tls-clock`, `tls-address`) and the same "this box's own store was asked first" ordering — the platform trust manager always answers first, the repair only runs in its refusal path.

## 5. Phone UI — mirrors the web, doesn't copy it

**Screens:** Home (match strip → rails → logo grid) · Category · Watch · Match board · Search · Favourites · Settings · Update sheet.

**Same visual language as the web:** `#00c978` accent, `#060b13` background, `#0a1222` panels, the pill row, the 4-column circular logo grid with the live dot, the match strip on top.

**Gestures** — note the last one is the rule you kept on the web:
- swipe up / down → change channel
- tap on picture → reveal controls (**never pause**)
- double-tap → fullscreen · two-finger tap → pause
- horizontal swipe → next mirror

**Bengali-first** with an English toggle. TalkBack, font scaling, high-contrast.

## 6. TV UI — a different app, visually

| | Phone | TV |
|---|---|---|
| Layout | vertical feed | rows + focus grid |
| Type | 9–12 sp | ≥18 sp |
| Input | touch, gestures | D-pad only |
| Preview | — | selected channel plays in a corner PiP while browsing |
| Search | phone keyboard | on-screen keyboard driven by the remote |

- Focus ring on every focusable item; up/down moves between rows, left/right within a row, OK opens, back exits, long-press opens quick actions.
- `LEANBACK_LAUNCHER` activity; `android.hardware.touchscreen` **not** required.
- Audio and the corner preview keep playing while the user browses — that "sound continues" feel is what makes a TV app feel premium.
- Screensaver handling, HDMI-CEC wake, resume-after-screensaver.

## 7. Update system

**Manifest** (`/api/app/version`, a few KB, static):

```json
{ "versionCode": 12, "versionName": "1.2.0", "apkUrl": "…", "sha256": "…",
  "sizeBytes": 4821331, "releaseNotes": "…", "mandatory": false,
  "minSupportedVersionCode": 1, "rolloutPercent": 100, "variants": { … } }
```

**Flow:** check at launch and on resume → compare `versionCode` → non-blocking sheet → background download → **sha256 verified** → one-tap `PackageInstaller` install.

**Also built in:** staged rollout (a % of installs, by a random per-install id) · kill switch (point the manifest back at the previous APK) · `mandatory` flag (blocks playback with a message after a date) · `minSupportedVersionCode` · the manifest itself fetched from a **GitHub Releases** mirror as backup, so a Vercel outage can't stop updates.

**The APK lives on GitHub Releases — $0, and not on Vercel** (Vercel bandwidth is for the site, not for 5 MB binaries).

## 8. Resilience — the app must survive your domain dying

That was your stated reason for building a TV app, so it's a first-class feature:

1. **Multiple base URLs** tried in order (`https://soluplay.vercel.app` + backups **you still need to name**).
2. **Last-good catalogue cached on-device** (`filesDir`).
3. **A baked seed catalogue** in assets as the last resort — regenerated by the toolchain's `make-seed.js`.
4. **Media URLs are third-party origins**, so a stale guide still plays.
5. Every screen states which copy it is showing.

## 9. Permissions, privacy, data safety

`INTERNET` · `ACCESS_NETWORK_STATE` · `POST_NOTIFICATIONS` (phone, opt-in, for kickoff reminders) · `REQUEST_INSTALL_PACKAGES` (the updater) · `WAKE_LOCK` · `FOREGROUND_SERVICE` (phone background audio only).

**Never requested:** location, contacts, storage, camera, microphone. No analytics by default; opt-in diagnostics only, no PII. The Play **privacy policy + data safety form** come later with the store listing, not now.

## 10. Verification — what each layer can actually prove

| Layer | Provable here? |
|---|---|
| Gradle build (offline) + lint | ✅ |
| Unit tests: catalogue parsing, ladder walk, failover state machine, manifest compare, rollout decision | ✅ |
| JVM harnesses against the live site (recreate `logiccheck` / `e2e` / `run-live` / `run-tls` / `run-oldbox` from the toolchain) | ✅ — but **not** on-screen playback |
| D-pad focus behaviour, PiP, install flow, old-box TLS on real hardware | ❌ — your phone and your TV only |

**Nothing will be reported as "playback verified" without a device.** The toolchain's `run-oldbox.sh` can simulate a 2018 CA store, which tells us *which roots to bundle* — it does not prove a given box plays.

## 11. Release engineering

- `versionCode` monotonic, `versionName` user-facing.
- **Keystore:** created in Phase 0, gitignored, backed up to two places off this PC (and a password manager).
- **CI:** GitHub Actions on the new public repo → signed APK artifact → GitHub Release → manifest updated. Free, and it's the $0 answer to "auto update as soon as a new update is available".
- Manual release path also supported, so a bad CI day can't block you.

## 12. Phases and gates

| # | Phase | Done when |
|---|---|---|
| 0 | **Spec + skeleton.** Full plan written into the repo as the spec of record; Gradle cache inventory; keystore created; project skeleton; both launchers; CI green | A signed debug APK builds offline on this PC |
| 1 | **Browse.** Catalogue + fixtures + 7 rails; phone grid + TV focus grid; offline cache; site-down mode | Both surfaces show the real catalogue, live and cached |
| 2 | **Playback.** Media3 for all types; YouTube path; published ladder walk; automatic failover; first-frame rule | A channel plays on a real device and steps mirrors by itself |
| 3 | **Old-box TLS + resilience.** `ProviderTls` rebuild; multiple base URLs; last-good cache; seed catalogue | An aynaott/alixbd channel plays on an Android 9 box |
| 4 | **Updater.** Manifest + bootstrap endpoints in `freetv`; check/download/sha256/install; staged %; kill switch; mandatory | A new build reaches a device with one tap |
| 5 | **Polish.** Match board, favourites + sync, TV on-screen search, corner preview, gestures, Bengali/English | — |
| 6 | **`/tv` web layout** in `freetv` for Tizen/webOS, D-pad handling, no promos | An old Samsung box gets a 10-foot page |

**Split into two packages** (`com.soluplay.app` + `com.soluplay.tv`) when: you list on Play and want a dedicated TV section · you want both installed on one device · TV RAM/startup turns out to be a problem on old boxes · the two release cadences diverge. Until then, one APK.

## 13. Risks worth naming now

- **Gradle cache gaps** — the single most likely reason Phase 0 stalls. Mitigated by the inventory-first rule.
- **ABIs** — Media3 ships native libs; old boxes are `armeabi-v7a`, new ones `arm64-v8a`. Two ABIs roughly doubles the APK. `x86_64` only if you ever attach an emulator.
- **Verification ceiling** — no device here means every phase ends at "builds and the harness agrees", and the last mile is yours.
- **The site's own certificate** — if it expires, the update check fails. The GitHub Releases mirror is the answer.
- **`freetv` stays frozen** — Phase 4 adds two routes to it and changes nothing else. Every one of those additions needs its own review.

## 14. What I need from you

1. **Keystore** — I'll generate it with a passphrase you set through an env var (never printed, never committed); you then back it up off this PC.
2. **A real APK download URL** — `APP_DOWNLOAD_URL` is still `https://example.com/soluplay-app`.
3. **Backup base URLs** — the old app shipped this empty.
4. **A device** — your phone and your TV, for the phases that can only be proven there.
5. **Header-carrying feeds** — include (needs a new signed endpoint) or defer past Phase 2? I'd defer.
6. **New repo name** — `soluplay-app` on `sulayman-a-khan`, public, for free CI + Releases.

---

## Amendments

*(Amendments are appended here as clearly-marked notes; nothing above is edited retroactively.)*

### Amendment 1 — 2026-11-03, Phase 0: repository identity and the shipped package names

Sections 1 and 14 name the repository `sulayman-a-khan/soluplay-app` on the main
account. **The app repository is `johncarry301/soluplay-app`**, the same separate
account that serves the download landing page: the release assets and the manifest
that the landing page reads must be published from the account that owns the page,
and the main account must not appear anywhere in this work. The plan's own
repository layout is otherwise unchanged — one Gradle project, two flavours, the
APK on GitHub Releases.

**Application ids.** The phone flavour ships as `com.soluplay.app` and the TV
flavour as `com.soluplay.tv`, as sections 1 and 12 intend. `latest.json` carried
an earlier placeholder id (`com.soloplay.freetv`); it now reads `com.soluplay.app`
in `appId` and `android.packageName`, so the manifest's identity check and the
built package agree. The manifest also gained the updater fields of section 7
(`apkUrl`, `mandatory`, `minSupportedVersionCode`, `rolloutPercent`) so that the
same document can be served from the site and from this repository as the
GitHub Releases mirror. `downloadUrl` stays beside `apkUrl` because the landing
page already reads that name.

**The single shipped APK is the `phone` flavour.** It declares both the
handset `LAUNCHER` entry and the `LEANBACK_LAUNCHER` entry, so one file covers a
phone and a TV box. The `tv` flavour exists in the build for the split described
in section 12, but it is not published.

### Amendment 2 — 2026-11-03, Phase 0: the dependency cache was warmed for a different stack

Section 1 requires the Kotlin + Jetpack Compose stack, and the offline build to
use only what the warm cache holds. The cache on the build machine was warmed by
the **old, Java-and-Views** TV app: it holds AGP 8.5.2, Media3 1.4.1 and a thin
set of AndroidX, but **no Kotlin Gradle plugin and no Compose artifacts at all**.

The agreed stack was therefore fetched once, over the network, to warm the cache —
the toolchain's own `--offline` rule is a property of the build, not a reason to
change the stack. Every dependency is now pinned in `gradle/libs.versions.toml` to
a version present in the cache, Media3 to 1.4.1 as section 1 requires, and the
offline build works from here on. The choices this forced:

| | |
|---|---|
| AGP | 8.5.2 (already cached) |
| Kotlin | 2.1.0 (Compose compiler 2.1.0) |
| Compose | BOM 2024.12.01, Material 3 |
| Media3 | 1.4.1 (already cached) |
| TV UI | `androidx.tv` 1.0.0 (Material for TV) — **superseded by Amendment 3** |
| min / target SDK | 21 / 34, as section 1 states |

Nothing else in this plan changes.

### Amendment 3 — 2026-11-03, Phase 0: the TV UI no longer uses `androidx.tv`

Amendment 2's TV UI row does not hold, and the reason is the build machine's
Maven mirror rather than a change of mind.

`androidx.tv` has exactly one release, `tv-material`/`tv-foundation` 1.0.0, and
in this environment that artifact has been **republished against Compose
1.10.3**. Its metadata therefore demands `compileSdk 35` and AGP 8.6 or
newer, and it drags the whole graph up with it: Compose 1.10.3 pulls
`androidx.core` 1.16.0 and `androidx.lifecycle` 2.9.4, and the AAR metadata
check fails the build with 28 errors before a single line of Kotlin is
compiled.

Keeping `androidx.tv` would have meant changing what section 1 fixes —
`compile/target 34`, AGP 8.5.2, the cached toolchain — or downloading an SDK
platform the machine does not have. The TV UI is therefore built on **plain
Jetpack Compose**: Material 3 from the pinned Compose BOM, and Compose's own
focus system for the D-pad (`Modifier.focusable()` plus a `FocusRequester` for
the first target). Compose's focus chain already walks the arrows between
focusables, which is what a TV grid needs.

| | |
|---|---|
| TV UI | Jetpack Compose (Material 3) + Compose focus, no `androidx.tv` |
| Everything else | unchanged — Compose BOM 2024.12.01, AGP 8.5.2, Kotlin 2.1.0, Media3 1.4.1, min/target 21/34 |

**What this changes in the plan.** Section 1's layout line "Compose TV UI
(androidx.tv.tvmaterial)" now reads "Compose TV UI". The TV surface keeps
every behaviour section 5 asks for — the focus grid, the corner preview, the
on-screen search — and Phase 5 is where the TV-specific polish is judged on a
real box.

**What this does not change.** The shipped APK is still the `phone` flavour
with both launchers; the `tv` flavour is still the prepared split; the version
pins, the two ABIs and the offline build gate are untouched.

### Amendment 4 — 2026-11-04, Phase 1: the guide as the site actually publishes it

Phase 1 reads the live site. On 2026-11-04 `/api/channels` answered with
**167 channels** and `/api/fixtures` with **zero** matches, so the facts below
are the ones the app was built against.

**The seven rails are the site's own categories, not a list of ours.** The
catalogue carries seven categories — Sports, Bangla, Indian, Pakistani,
Documentary, Kids, Global — and the app reads them as seven rails. Their order
is not written anywhere in the app: `priorityOrder` is global across the whole
catalogue, so a rail's place is decided by the order the server published and
nothing else. The app never re-ranks the guide.

**A failed catalogue is not an empty catalogue.** A route that answers
`success: false`, an empty channel list, or an unreachable site is a failure,
never a guide. The app falls back in the order of trust — live site, then the
copy this device saved under `filesDir`, then the copy baked into
`app/src/main/assets/catalogue-channels.json` — and every screen says which
copy it is showing.

**The phone home is match strip, then pill row, then the rails.** Each rail is
a 4-column grid of circular logos with the live dot, exactly the visual
language of section 5. The pill row above the rails holds the categories and
jumps to the rail it names. The match strip renders nothing at all while the
board is empty, because an empty strip is worse than no strip.

**The TV D-pad is handled by the screen, not by the focus chain.** Amendment 3
left the arrows to Compose's own focus search. On a grid of seven rows that is
not enough: left and right must stay inside one row and up and down must move
between rows, and the refresh control in the top bar is a row of its own. The
TV surface therefore reads the arrow keys itself and moves a `FocusRequester`
to the right cell; every focusable item draws a focus ring, and OK opens the
channel it is on. Type on the TV stays at 18 sp and above, as section 6 asks.

**Logos are fetched by a small loader of our own.** No image library: the
logos are fetched over plain HTTP, kept in memory for the life of the process,
and a channel whose logo cannot be fetched still shows the first letter of its
name.

**Kick-off times are parsed by hand.** The board publishes `startsAt` as a
string whose shape has changed before, and `java.time` needs API 26 while the
app runs on API 21, so the clock is read with a regex and `Calendar`.

**One command proves the live path.** The unit tests never touch the network;
the live harness does, and only when it is asked for:

```
./gradlew test -Dsoluplay.live=1
```

It reads the real site through the app's own client and repository, and it
proves the site-down path by pointing the client at an address that cannot
answer.

### Amendment 5 — 2026-11-04, Phase 1 correction pass: the findings of the read-only review

A review of Phase 1 against the live contracts found two risks (R1, R2), five
faults (F1–F5), one phone defect (N1), five TV defects (T1–T5) and two
defects in the match clock (X1, X3). This amendment records what changed. The
API contracts, the order of trust, the architecture and the scope fences are
untouched; the changes are corrections, not new ground.

**R2 — the baked catalogue carries no ladder.** The seed is the guide and
nothing else: 167 channels, each with `_id`, `name`, `logo`, `category`,
`isPinned`, `priorityOrder` and `tags`. `primaryStream` and
`activeStreamCount` are removed by the generator, and the generator refuses to
write a seed that carries `m3u8`, `token=` or `nimblesessionid` anywhere, or
that carries an absolute address in any field other than `logo`. A baked
upstream line is a stale credential inside the APK and inside the repository,
so it is not there. The consequence is honest copy: the details card of a
baked copy says that the mirror list comes from the site, not that no mirror
is available. Playback asks the live API for the ladder it publishes.

**R1 — the generator lives in this repository.** `scripts/make-seed.js` reads
the public catalogue from `BASE_URL` and writes
`app/src/main/assets/catalogue-channels.json`. It names no path outside this
repository and no path on this PC.

**F1–F5 — a failed guide is never an empty guide.**
- *F1.* A board that could not be read leaves no board behind: the cached
  board file is deleted instead of leaving a stale one behind, while a board
  the site answered as empty is kept as an empty board.
- *F2.* Each address is asked and each answer is read before it is accepted.
  An address that answers with something that is not a guide is passed over
  rather than believed.
- *F3.* "The site could not be reached" and "the site answered with no list"
  are now two different facts, not one. The transport says which, the snapshot
  carries it, and the banner says it.
- *F4.* A guide that is already on the screen is not relabelled live when a
  refresh fails: what the viewer is looking at stays, and its badge says
  cached.
- *F5.* The live dot is gone. The catalogue publishes mirror counts, not
  broadcast state, so a dot on every logo said something the site never said.
  Amendment 4's phone description no longer stands on this point.

**N1 — the pill jump lands clear of the pinned row.** The pill row is a sticky
header, so a rail that landed flush with the top of the list hid its own title
underneath it. The jump now carries a negative offset, because a positive
offset is a *forward* scroll and would push the rail further up, under the
header.

**T1–T5 — the TV remote.**
- *T1.* The details card is modal: the arrows went to the grid behind it. The
  card takes them now, OK closes it, back closes it.
- *T2.* A row that was off the window stayed off the window, because a
  scrolling column does not follow the focus. The column is measured as it was
  drawn and scrolled by the difference.
- *T3.* The same, along a row: a tile off the right edge of its rail is
  brought into the rail.
- *T4.* A refresh rebuilt the grid and threw the focus away. The cell the
  viewer was on is asked for again, and the first cell answers when that one
  is gone.
- *T5.* The top bar is a row of its own, and going back to it returns the
  guide to its beginning.

**X1 — the offset sign.** `-0530` was read as `+0530`, so a kick-off 5½ hours
behind UTC was placed 5½ hours ahead. A `-` offset now subtracts.

**X3 — the phase comes from the server.** The site computes the phase of a
match — `upcoming`, `live`, `finished` — and the app reads it through a
`BoardPhase` seam instead of deciding for itself. The countdown is used only
for a match that has not started.

**Still open.**

- *X2.* The fixture fields `choices` and `watchPath` are modelled and unused.
  They belong to the board's own cards and to playback, and Phase 1 neither
  draws a choice nor opens a match.
- *X4.* `/api/fixtures` answered with zero matches on 2026-11-04, so no
  fixture card has been seen carrying real data. The match strip is proven by
  unit tests against the shape the site publishes, not by a real board.

**The gate.** `scripts/build.sh` is green: both debug flavours, both release
flavours compiled, 52 unit tests, no failures, and the three live-harness
tests skipped because they only run when they are asked for
(`./gradlew test -Dsoluplay.live=1`). Nothing was committed, pushed, released
or deployed.
