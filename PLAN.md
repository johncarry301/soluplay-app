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
- **Phase 0 must inventory `~/.gradle/caches/8.9` first** and pin every dependency to a version already cached. Anything missing = no offline build = the whole toolchain assumption breaks.

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
| 6 | ~~**`/tv` web layout** in `freetv` for Tizen/webOS, D-pad handling, no promos~~ **withdrawn 2026-11-05** | — | Superseded by Amendment 16: the browser ten-foot page is removed. Distribution is the Android phone app and the Android TV app only. |

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

### Amendment 6 — 2026-11-04, Phase 2: playback, the ladder walk, and the match cards

Phase 2 is: Media3 playback, server-priority mirror selection, automatic
failover, first-frame timeout, frozen-playback detection, and fixture-to-native-channel
navigation. The stream addresses come from the live API only. The baked seed is
never used for playback. The FreeTV / web-player / Direct HLS code is untouched.

**The machine is one small class, and it holds no player.** `PlaybackMachine`
knows three things: which mirror is being tried, whether that mirror has proved
a picture, and whether the picture is still moving. It has no clock of its own
(the time comes from an injected `now`) and no player of its own, so the whole
ladder — every failure, every deadline, every hop — is exercised on the JVM
with no device, no decoder and no network. `core/` therefore carries no Media3
dependency at all: the ExoPlayer wrapper and the `PlayerView` live in
`app/src/main/kotlin/com/soluplay/app/player/`, and `core/player/PlaybackHost.kt`
is the seam between the two.

**The deadlines are the web player's own.** `FIRST_FRAME_DEADLINE_MS = 10_000`
and `STALL_GRACE_MS = 1_200` — the two numbers `components/players/DirectHlsPlayer.tsx`
uses — so the app gives a mirror up at the same moment the site does, and for the
same reasons.

- A mirror being tuned has ten seconds to prove a picture. No picture by then
  and the mirror is given up for the next one in the published order. The app
  never shows a black screen and calls it tuning forever.
- A picture that was up and then stops: the frozen frame stays on the screen
  through 1.2 s of grace, and if the player's clock moves again the picture is
  alive and nothing happens.
- A picture still frozen after the grace gets one more full ten-second budget
  **on the same mirror**, because a freeze is often the feed's own hiccup and
  that mirror recovers. Only when that second budget also expires is the mirror
  given up, and the next one is tried.

**The ladder is the server's order and is never re-ranked.**
`core/ladder/Ladder.kt` offers exactly one operation — "give me the next
mirror" — and no way to sort. The app never chooses a mirror by latency, by
failed-attempt count or by anything of its own, and there is no manual
"Server N" control on either surface.

**The ladder is read live, from the site, and from nowhere else.**
`CatalogueRepository.liveChannelDetail(id)` calls `GET /api/channels/<id>` and
returns the published `streams[]` in the order the server ranked them. It has
no cache fallback and no seed fallback, deliberately: the guide may fall back
to the copy on the device or the copy baked into the APK, and the ladder may
not. A stale ladder walks a viewer into a link that died days ago, and the baked
copy carries no stream addresses at all. `LiveLadderTest` proves both halves of
that rule.

**A late answer is dropped.** The view model carries an `opening` token, so an
answer that arrives after the viewer has moved to another channel is not played.

**Exhaustion is a state, not a spinner.** When every published mirror has been
tried, the screen says so in one line and says which reason gave the last
mirror up: no picture, frozen, player error, or no mirror at all. The
weak-signal panel sits inside the frame the picture occupies, so a dead feed
never becomes a separate screen.

**A match card now opens a native channel.** `core/fixtures/BoardChoices.kt`
reads the board's own `choices[]`: a card with one openable choice opens it, a
card with several asks which one, and a card with none says it cannot be
watched from here instead of opening nothing. Only a choice the board marks
`ready` **and** that names a direct channel is openable — the secured tier is
not in Phase 2.

**What is on the screen while it plays.** The picture is the screen. A tap
brings the words in and out and never pauses the feed. The bottom line carries
the channel name, what the app is doing (the mirror list is coming / tuning
mirror 2 of 3 / live / waiting for the picture) and which mirror is playing.
The TV surface is the same at TV type sizes, with the back control focusable and
on the D-pad.

**Deliberately not built in Phase 2.**

- The PLAN section 5 gesture set — swipe to change channel, double-tap
  fullscreen, two-finger pause, horizontal swipe to the next mirror. The
  horizontal mirror swipe is excluded on purpose: a manual mirror control
  contradicts the "no manual server button" fence in section 3. Tap-to-reveal-controls
  is built, because it is in the plan and it does not touch the ladder.
- The YouTube path of section 4 (resolve → WebView + IFrame API).
  `YouTubeResolveEnvelope` exists in the model but no client method calls it
  yet. A mirror that is a YouTube page rather than a media URL fails in Media3
  and the machine hops to the next mirror — the honest outcome, but not the
  YouTube path.
- The player features of section 4 that need a UI of their own: aspect-ratio
  control, subtitles, audio-track selection, HD/SD toggle, PiP, background
  audio, the stats overlay. The `PlayerView` is `useController = false`, so no
  Media3 controller chrome is on the screen.
- TLS repair (section 4's `ProviderTls`), the update system (section 7), and
  every phase after this one.

**Still open.**

- *X2.* The fixture `watchPath` fields are modelled and are still not what
  navigates. The app opens a channel by its `channelId`;
  `FixtureChoice.watchPath` and `Fixture.watchPath` are web routes and are
  read but not acted on. That is a deliberate choice — the app opens the
  channel itself, so a web route is not what decides — but it is unproven
  against the board's own rules: a card whose only openable choice is
  `delisted` or `secured` is refused, and the board's own `watchPath` might
  have said otherwise.
- *X4.* `/api/fixtures` answered with zero matches on 2026-11-04, so no match
  card has been seen carrying real data. The card→channel navigation is proven
  by unit tests against the shape the site publishes, not by a real board.
- *No physical device.* Nothing in Phase 2 has run on a phone or a box. The
  decoder path, the first frame on real hardware, the failover onto a second
  mirror, the D-pad focus and the ten-second deadline are all unproven in the
  only way that counts.
- *The TV corner preview* of section 6 is not built: browsing the grid plays
  nothing.

**The gate.** `scripts/build.sh` is green: both debug flavours and both release
flavours compiled, 72 unit tests, no failures, and the three live-harness
tests skipped because they only run when they are asked for
(`./gradlew test -Dsoluplay.live=1`). Twenty of the 72 are new in Phase 2 —
twelve for the playback machine, six for the fixture choices, two for the live
ladder. Nothing was committed, pushed, released or deployed, and `latest.json`
stays unpublished until a real `SoluPlay.apk` asset exists in GitHub Releases.

### Amendment 7 — 2026-11-04, Phase 2 correction pass: the guards, and the second chance

A read-only audit of Phase 2 raised eleven findings. Four of them are fixed
here, one is aligned with the web player, two are recorded as decisions, and
the rest are left alone on purpose.

**F1 — a stale player event can no longer change what is on screen.** Every
try at playing now has a number. `PlaybackCommand.Prepare` carries it, the
host puts it on every answer it reports, and the machine ignores any answer
whose number is not the one it is waiting for. A picture, a stall, a recovery
or an error from a mirror the walk has already left therefore changes
nothing: it cannot mark a new mirror live, and it cannot disarm the
ten-second deadline. The number is carried in the core, so the guard can be
tested without a device.

**F1, at the player.** Each try at playing also gets its own listener on the
player, and the listener of the try that is over is taken off before the next
one goes on. The answer of a mirror the walk has left has nowhere to go.

**F2 — the listener that was put on is the one that comes off.** The host
holds the listener it registered and removes that instance when it releases
the player. The old code built a second listener to remove, which removed
nothing.

**F8 — a ladder for a channel the viewer has left is refused.** The machine
now owns a session number. Opening a channel begins one, the ladder has to be
handed back with the session it belongs to, and a ladder that arrives late —
or after the watch screen was closed — is refused instead of played. The
view model's old `opening` token is gone; the machine's session is the one
guard.

**F10 — Back puts the phone's choice sheet away first.** The sheet is closed
by Back before the screen is left, the same way the TV surface already does
it and the same way the sheet's own button does.

**F4 — a temporary failure is given a second chance, as on the web.** The web
player re-reads the manifest of the same mirror before it gives that mirror
up, so a healthy mirror is not retired over a hiccup. The app does the same:
a network failure re-tries the same published address, up to the web's own
two re-reads, and only then does the walk move to the next published mirror.
A failure that is not a network failure means the mirror itself is broken and
the walk moves on at once. The ladder is never re-ranked — the mirror that is
tried again is the one the server published in this place.

*Intentional differences from the web player, and why.*

- The web's last resort is an in-place recovery window of three seconds after
  the re-reads are used up. The app uses the same three seconds, but
  re-prepares the mirror to get there, because Media3 cannot resume a load
  that has already failed. The window is never allowed to run past the budget
  the mirror was already given, so the mirror is given up at the earlier of
  the two — which is the web's own rule.
- The web recovers a media error in place, with no cap and no counter. Media3
  has no equivalent that is worth the risk of re-preparing a decoder that has
  already failed, so anything that is not a network failure moves the walk on
  at once. This is the one place where the app is stricter than the site.
- The web's recovery of a feed that was already on air has no deadline at
  all. The app gives the mirror the same ten seconds a first frame gets, so a
  broken feed cannot sit on the screen for ever.

**F5 — the signed headers are still to come.** The app sends no provider
`Referer` or `Origin` of its own, and none is invented here. The web player
sends what the card publishes; the app will do the same once the signed
header work lands, and until then a mirror that needs a header is a mirror
that fails. That work is planned, not started.

**F3 — audio-only streams are out of scope by decision.** The app is for
video: live TV, sports, films and video streaming services. Radio and
audio-only streams are permanently out of scope, so no audio-only detection
is built and the video first frame stays the signal that a mirror has proved
itself.

**What was not touched.** The published ladder order, the live-only ladder
loading, the saved copy and the baked seed (neither carries a stream address
and neither is on the playback path), the FreeTV repository, the Direct HLS
panel, the web player and the eighteen protected paths. No provider header
was invented, and no feature outside this list was started.

**The gate.** `scripts/build.sh` is green: both debug flavours and both
release flavours compiled, 84 unit tests, no failures, and the three
live-harness tests skipped because they only run when they are asked for
(`./gradlew test -Dsoluplay.live=1`). Twelve of the 84 are new in this pass,
and all twelve are about the guards: a late picture, a late error and a late
stall from a try that is over, a ladder from a session that is over, a ladder
that arrives after the channel was closed, the number of each try at playing,
the two second chances, the three-second window, and the two ways a broken
mirror is given up.

**Still open.**

- *No physical device.* The correction pass has not run on a phone or a box.
  What still needs the real thing: that a real network failure is reported
  as a network failure and not as a broken mirror (the fault is read from the
  player's error code, and only the player can tell the two apart); that the
  second chance on a real failing mirror behaves as the tests say; that Back
  closes the sheet on a real handset; and that the listener really is the one
  that is removed, seen from the player's own state.
- *X2 and X4* are unchanged: the fixture `watchPath` fields are still read
  and not acted on, and no match card has yet been seen carrying real data.
- *The TV corner preview* of section 6 is still not built.
- `latest.json` stays unpublished until a real `SoluPlay.apk` asset exists in
  GitHub Releases.

Nothing in this pass was committed, pushed, released or deployed, and no
keystore was touched. The working tree holds the changes for review.

### Amendment 8 — 2026-11-04, Phase 2: the race — parallel stream startup

The mirrors of a channel are raced now, not walked one at a time.

**What the app does.** When a channel is opened and the site publishes its
ladder, the first three published mirrors are tuned together, each by a player
of its own. The first of them to put a picture on the screen keeps it, and the
ones that lost are let go of at once — decoder, buffer and surface together.
The walk moves on to the next published mirrors only when the whole batch has
failed, in batches of up to three, and the mirrors that are left are tried
together when fewer than three are left. The published order is never
changed: the race only decides how many of the published mirrors are in
flight at the same moment.

**The numbers are the web's own.** The ten-second first-frame deadline, the
1.2-second stall grace, the two re-reads of a failing mirror and the
three-second recovery window are all unchanged. A mirror that *fails* is given
up after them, exactly as before. A mirror that merely *loses a race* is not,
because a mirror that was slower is not a broken mirror.

**A losing mirror is not thrown away.** It keeps its place in the published
order and is raced again if the picture on the screen dies, so the failover the
web player does is still there: the walk continues with the mirrors published
next, in the order the site published them.

**Three players, and the one that wins.** The host now holds a player per try
at playing and a surface per player — a Media3 player only reports a rendered
first frame when it has a real surface attached, so a race needs one view per
mirror inside the stage. The winner's view is brought to the front; the
losers' views are taken out of the stage and their players are released.

**No stale callbacks, no competing players.** The attempt number of
Amendment 7 does this work too: the losers are removed from the machine's
list of mirrors in flight the moment the winner is chosen, so a picture that
arrives late from a loser is ignored. The listener of a loser is taken off its
player and the player is released before anything else is put on, so a loser
cannot say anything afterwards at all.

**The screen tells the truth.** While more than one mirror is being tried the
status says how many ("Trying 3 mirrors at once…") instead of claiming that
one mirror is being tuned.

**What was not touched.** The server-provided order of the URLs, the FreeTV
web player, the site's APIs and the backend, the published ladder order, the
live-only ladder loading, and the eighteen protected paths. Nothing outside
the Android app was changed.

**The gate.** `scripts/build.sh` is green: both debug flavours built, 94 unit
tests, no failures, and the three live-harness tests skipped because they only
run when they are asked for. Ten of the 94 are new in this pass and all ten
are about the race: the first three mirrors tuned together, the winner keeping
the picture, the losers being let go of at once, the batch moving on to the
next three, the mirrors that are left being tried together, the walk ending
when no mirror is left, a broken mirror leaving the race, and the mirrors that
lost being raced again when the picture dies.

**Still open.**

- *No physical device.* The race has not run on a phone or a box. What still
  needs the real thing: that three decoders at once do not upset a cheap
  handset; that the first picture really is the one that is kept; that the
  losers' players really are released; and that the stage shows one picture
  and not three.
- *The race size of three is a judgement, not a measurement.* It is the
  number that was asked for, and it has not been tried against a real device
  under load.
- *X2 and X4* are unchanged, the TV corner preview is still not built, and
  `latest.json` stays unpublished until a real `SoluPlay.apk` asset exists in
  GitHub Releases.

Nothing in this pass was committed, pushed, released or deployed, and no
keystore was touched. The working tree holds the changes for review.

### Amendment 9 — 2026-11-04, Phase 2 correction pass: the four findings from the Galaxy A21

The first APK was put on a real phone, and four things were wrong with it. This
amendment records what each of them actually was, what was changed, and what
still needs the real thing to be proved.

#### 1. The logos were clipped and the plates did not match

**What was wrong.** The logo was drawn inside a circle and scaled to fill it,
so a wordmark wider than it was tall was cut off at the sides and a square
logo lost its corners. A logo that could not be fetched was replaced by a
letter cut out of the middle of the name, so a name that begins with a
character written in two pieces showed half a letter.

**What it is now.** Every logo stands on the same plate: the same background,
the same rounded shape, the same room inside it. The logo itself is fitted
rather than cropped — what was published is shown whole, in the shape it was
published in. A logo that cannot be fetched is replaced by the first letter of
the channel's name, taken whole.

**The name and the category.** The category the site filed a channel under is
now carried from the channel detail into the playback state, and it is shown
under the channel name on the phone and on the television.

#### 2. The app lagged

**What was wrong.** Two things, and both of them were in the guide.

The logos were fetched at the size they were published at and kept at that
size in memory, forever, with no limit on how many were fetched at the same
time and nothing kept on the device between runs. The guide was drawn a whole
rail at a time — one list item per rail — so every channel of a rail was
composed and every logo of a rail was fetched the moment the rail came on the
screen, however long that rail was.

**What it is now.** A logo is decoded no larger than it is ever drawn (320 px
on the long edge), at most four are fetched at the same time, a logo that is
being fetched is never fetched twice, the ones kept in memory are bounded and
the oldest are let go of first, and a copy is kept in the app's own cache so
the next run does not ask the network for it again.

The guide is now a flat list: a heading, then one row of four channels at a
time. The list draws only the rows that are on the screen, so a rail of forty
channels fetches four logos at a time and not forty. The pill that jumps to a
rail still works, and the order is still the server's.

#### 3. The package was bigger than it needed to be

**What was wrong.** Nothing was broken, but two dependencies were carried for
nothing: `compose.ui.tooling.preview` (the preview tooling, which the app
never uses) and `media3-ui` (the `PlayerView`, which the app never uses — the
picture is drawn on a `TextureView` of its own, so that the winning mirror of
a race can be brought in front of the others). The dependency metadata was
also being packaged, and the app shows no list of what went into it.

**What it is now.** Those two dependencies are gone, the dependency metadata is
no longer packaged, and nothing else was touched: Media3 is whole, both ABIs
are still built, and no supported video format was dropped.

**The numbers.** The phone debug package went from 12,646,014 bytes to
12,326,772 bytes — 319 KB smaller, and that is with the WebView YouTube player
added in this same pass. The television debug package is 12,273,616 bytes.

**The number that matters is not this one.** The 12 MB that was measured is the
*debug* package, and it is mostly unminified dex: `classes.dex` alone is
16.9 MB and `classes13.dex` 11.2 MB before compression. The release variant
already has R8 and resource shrinking turned on, so the shipping package is a
different and much smaller thing. It was not measured in this pass, because
building the release variant was not allowed here.

#### 4. Three kinds of source, and only one of them was playable

**This was the real bug.** The site publishes an address and nothing else — no
field says "this one is YouTube" — so the app has to read the kind of a source
out of the address, exactly as the web player does. Three kinds are in the
live catalogue today: an HLS playlist (151 channels; 7 of the mirrors are
published as `stream.php?id=…&e=.m3u8`), a raw MPEG-TS transport stream (3
mirrors), and a YouTube live channel (13 mirrors).

**Why the playlist behind the `.php` page failed.** Media3 decides what a
stream is from the *extension of the path*, and its own table knows `m3u8`,
`mpd`, `ism` and `isml` and nothing else. The path of
`…/stream.php?id=sony_max_hd&e=.m3u8` ends in `.php`, so Media3 played it as
a plain file and never as a playlist. The web player does not have this
problem because hls.js reads what the address *returns* rather than what the
address is called. The app now reads the kind the same way and builds the
right source for it: an HLS source for a playlist, whatever the file is
called.

**Why the `.ts` mirror failed.** Two reasons. Media3 has no `ts` in its
extension table either, so a transport stream was never opened as one. And the
manifest said `usesCleartextTraffic="false"`, which on API 28 and later — the
Galaxy A21 is API 30 — blocks every plain-HTTP origin outright, and 14 of the
published mirrors are plain HTTP.

**What it is now.** The kind of a mirror is decided in `core` by
`StreamKinds.kindOf`, out of the address alone, and it is carried on every
prepare command so the surface never has to guess. A playlist is played with
Media3's HLS source, a transport stream with a progressive source over the
default extractor set (which carries the TS extractor), and a YouTube address
with YouTube's own player.

**YouTube is not an HLS stream.** YouTube live is played with YouTube's IFrame
Player API, in a web view, with the same options the web player uses
(`autoplay`, `playsinline`, `rel=0`, `controls=0`, `iv_load_policy=3`,
`modestbranding=1`) and the same three events reported back: the player is
ready, its state changed, it could not play. Nothing is extracted from
YouTube and no manifest is fetched — the stream is played by YouTube's own
player, with its advertising, the way the site plays it. A YouTube address
that names a video is answered on the device, with no network call at all; an
address that names only a channel is asked about through the site's own
resolver, the same route the site already uses.

**A YouTube mirror is given longer.** It has to load a page, a player and its
own advertising before anything is on the screen, so its first-frame deadline
is 25 seconds rather than the ten every other mirror gets.

**The mirror's own address is not proxied.** Nothing goes through Vercel. The
mirror is fetched by the device, from the address the site published, in the
order the site published it. The failover is untouched.

**Cleartext.** A network security config permits plain HTTP in general —
because a mirror that the browser player can read is a mirror this app has to
be able to read too — and denies it for the addresses that carry the app's own
traffic: the site and the update manifest.

**What was not touched.** The server-provided order of the URLs, the published
ladder order, the FreeTV web player, the site's APIs and the backend, the
Direct HLS and Secured streaming separation, the eighteen protected paths, and
the rules about provider credentials. Nothing outside the Android app was
changed, and no provider-specific header was invented.

**The gate.** `scripts/build.sh` is green: both debug flavours built, 134 unit
tests, no failures, and the three live-harness tests skipped because they only
run when they are asked for. Forty of the 134 are new in this pass: how an
address is read into a kind (`.php` playlist, `.ts` stream, YouTube live, a
relay that wraps either), how a YouTube address is turned into a video (a
video named in the address answered without a round trip, a channel address
asked through the site's route, the answer kept, a failed lookup answering
nothing, a quote in the address not breaking the body); how a logo is sized
and what stands in for one; how the guide's list is built a row at a time; and
that a prepare carries the kind of the source, that a YouTube mirror is given
the longer budget, and that a feed the app fetches itself is still given ten
seconds.

**Still open.**

- *Everything here needs the real thing.* The logos, the scrolling, the three
  kinds of source and the size of the picture on a cheap handset have not been
  seen on a device since these changes. What still needs the Galaxy A21: that
  a `.php` mirror plays, that a `.ts` mirror plays, that a YouTube live
  channel plays and reports its state, that the guide scrolls smoothly while
  the logos come in, and that a logo is neither cropped nor missing.
- *The size of the shipping package is still unmeasured.* It can only be
  measured from the release variant, which was not built here.
- *The race of Amendment 8 and the kinds of this amendment have not met yet
  on a device.* A race that contains a YouTube mirror now holds a web view as
  well as three decoders, and that is more than a cheap handset has been asked
  for.
- *X2 and X4* are unchanged, the TV corner preview is still not built, and
  `latest.json` stays unpublished until a real `SoluPlay.apk` asset exists in
  GitHub Releases.

Nothing in this pass was committed, pushed, released or deployed, and no
keystore was touched. The working tree holds the changes for review.

### Amendment 10 — 2026-11-04, Phase 2 correction pass two: the Galaxy A21 says it again

The device test of the Amendment 9 build came back with three things: YouTube
links are still not playing, the logos are not all in the same rounded shape,
and they do not all have the same white background. The first of these was
the finding of the previous pass and it did not work; the other two were
missed by it.

**1. The logos.** The plate a logo stood on was `Panel` (`0xFF0A1222`) and the
app's background is `0xFF060B13` — a plate that is nearly the background, so
there was no plate to see. Logos published with a white background of their
own therefore looked like white squares, and logos published transparent sat
on near-black, and the grid showed two kinds of tile. Three things were
wrong with it, and all three are fixed:

- The plate is white for every channel (`LogoPlateBackground`), so a logo
  published with a white background of its own and a logo published
  transparent look the same. The letter that stands in for a logo that could
  not be fetched is drawn in `OnLogoPlate` on the same white.
- The corner of the plate is one share of its short side (`PLATE_CORNER_PERCENT
  = 24`) for every size, so a 40 dp plate in the choice sheet and a 96 dp
  plate on the TV have the same shape. The TV focus ring used to be a separate
  96 dp box around an 84 dp plate, whose radius therefore did not match the
  plate's; the ring is now drawn on the plate itself, through the `border`
  argument of `ChannelLogo` and `LogoPlate`.
- The team logos in the match strip were drawn with no plate at all, in a
  circle, over the app background. They use `LogoPlate` now, like everything
  else.

A logo is fitted, not cropped: it keeps the proportions it was published
with, and the plate only says where the logo is.

**A fourth cause, and the reason so many logos were missing.** The loader
asked for logos without naming itself, and Wikimedia answers `403` to a
client that names no one. The loader now asks with a browser's name
(`LOGO_USER_AGENT`), the same way the streams are asked for.

**2. YouTube.** The player was not playing, and the reasons were in the
implementation, not in the address:

- *The player was rebuilt in a loop.* The surface kept the video it wanted
  forever, and `onPageFinished` fires again when the YouTube iframe finishes
  loading — so every iframe load re-ran `start()`, whose page script destroyed
  the player and built a new one, which loaded a new iframe. The player was
  recreated endlessly and never reached a playing state.
- *Everything hung on one script.* The page waited for
  `https://www.youtube.com/iframe_api` before it would do anything. If that
  script was slow or blocked, `apiReady` stayed false, `start()` did nothing
  at all, and nothing was ever said. The page now has no script of YouTube's
  in it: the embedded player posts what it is doing to the page it is in, and
  the page hears it and tells it to play.
- *The web view asked as a web view.* The default Android WebView name
  carries the token that says it is a web view, and YouTube treats such a
  player as an unverified one and refuses to play it. The web view now asks
  with a browser's name.
- *The page was embedded from YouTube's own origin.* The embedded player only
  posts its messages to the origin it was told about, so the page is now
  loaded from the site's own origin — the origin the web player embeds from —
  and that origin is in the embed's `origin` parameter.
- *The player was never told to play.* The embed is now told to play, and
  told again for as long as it has not started, because a player left to
  itself can come up paused and a paused player has no picture.
- *Nothing was ever said.* Every failure ended in silence, so a device test
  could only report "not playing".

So the player is YouTube's own embedded player, put in an iframe with the
same options the web player uses (`autoplay`, `playsinline`, `rel=0`,
`controls=0`, `iv_load_policy=3`, `modestbranding=1`) and `enablejsapi=1` so
that it reports its state, its errors and its readiness. The player is made
once, for one video, and is never taken down and built again while that video
is being watched. Nothing is extracted from YouTube and no manifest is
fetched.

**3. A failure that says something.** The player's own reason is carried from
the player to the screen: `PlaybackState.detail` holds a short code, and the
code is put into words by `failureDetail` in the chrome — "YouTube would not
play this video (error 150)", "The channel's current video could not be
found", "The YouTube page could not be loaded", "The YouTube player did not
start". A code nobody knows is shown as it is, because naming it is better
than hiding it. The reason a YouTube mirror never showed a picture now names
the player that was waited on.

**The gate.** `scripts/build.sh` is green: both debug flavours built, 152 unit
tests per suite, no failures, and the three live-harness tests skipped because
they only run when they are asked for. Eighteen of them are new in this pass:
five that a player's own reason reaches the screen and is forgotten when the
next channel is opened, six that the codes are put into words, and seven that
the YouTube page is what it has to be — the embedded player, autoplay, the
state reports, the origin, no script of YouTube's, no rebuild, and a video id
that cannot break the page.

**Still open.**

- *YouTube can only be proved on the device.* There is no emulator and no
  attached handset on this machine, so the player page is proved by its
  contents and not by its behaviour. What the Galaxy A21 has to show: that a
  YouTube live channel plays, and — if it does not — the line under the
  reason on the panel, which now names what the player said.
- *The logos are proved by their shape, not by their pixels.* A white plate
  with one corner and a fitted logo is the same for every channel by
  construction; whether every logo host answers the device is another
  question, and the name the loader asks with is now the browser's.
- *The size of the shipping package is still unmeasured*, and the release
  variant was not built here.

Nothing in this pass was committed, pushed, released or deployed, and no
keystore was touched. The working tree holds the changes for review.

### Amendment 11 — 2026-11-04, Phase 2 correction pass three: the YouTube player that plays but says nothing

The Galaxy A21 was asked again, and the report was exact: the YouTube video
**plays for a few seconds**, the status line says *"tuning mirror 1"* instead of
*"live"*, and then the picture is taken away on its own and the panel says *"no
mirror is sending a picture"*.

**The root cause: the player was never spoken to.** The embedded player only
answers the page that spoke to it first, and the speaking is YouTube's own
IFrame Player API. Without it the page put the iframe up, the video moved, and
the player said nothing at all — so the app was told nothing, held the mirror in
"tuning", and gave it up at its deadline. The deadline did what it was built to
do: it took away a picture it had never been told about. The page in Amendment
10 was right that a script of YouTube's must not be *waited for*, and wrong that
it must not be *used*: the script is the handshake, and without the handshake
there is no conversation.

**What changed.**

- *The player is driven by YouTube's own player API.* The page loads
  `https://www.youtube.com/iframe_api` and makes the player with
  `new YT.Player(...)`, with the video already in it and `origin` set to the
  site's own origin. The player's `onReady`, `onStateChange` and `onError` are
  reported to the app.
- *The state is read as well as heard.* The player's own state is asked for
  every half second (`getPlayerState()`), so a change that was never announced
  is still seen, and a player that comes up paused is told to play.
- *The player is never put up twice.* While the API is on its way the page
  waits; if the script has not arrived after six seconds the player is put up
  by hand, and the words that start it listening
  (`{"event":"listening","channel":"widget"}`) and the word that starts it
  playing are said to it, again and again, for a while. A player put up by
  hand and then made again by the API is a picture that starts twice.
- *A failed request no longer takes the picture away.* The web view reported
  every failed request for the embed as "the page failed", which was a fatal
  error: the mirror left the race and the web view was destroyed. Now a
  request that was called off, a request in a scheme the web view does not
  know, and any failure at all while the player is already playing are all left
  alone. Only a player that has never said anything is given up.
- *YouTube is given longer to prove a picture.* The first-frame deadline for a
  YouTube mirror was 25 seconds; the player has to fetch a page, a player API
  and its own advertising on a phone network, so it is now 40 seconds. The
  ten-second deadline for the feeds the app fetches itself is unchanged.

**The gate.** `scripts/build.sh` is green: both debug flavours built, 160 unit
tests per suite, no failures, three live-harness tests skipped because they only
run when they are asked for. Eight are new in this pass: four that the YouTube
page speaks to the player first, tells it the origin, reads its state and does
not put it up twice, and four that a failed request does not take a playing
picture away.

**Still open.**

- *The YouTube handshake can only be proved on the device.* There is no
  emulator and no attached handset on this machine, so the page is proved by
  its contents and not by its behaviour. What the Galaxy A21 has to show: that
  the status line says *"live"* while the video is moving, and — if it does
  not — the line under the reason on the panel, which names what the player
  said.

Nothing in this pass was committed, pushed, released or deployed, and no
keystore was touched. The working tree holds the changes for review.

### Amendment 12 — 2026-11-05, Phase 3: the television that was never given the roots

Phase 3 is the phase the old television app was written for: **a box that never
got a CA update, playing a mirror whose certificate chain stops one certificate
short of a root it has never heard of.** Its gate is *"An aynaott/alixbd
channel plays on an Android 9 box"*. Half of that can be measured here and half
of it cannot; what was measured is written down below, and what is left for the
box is named at the end rather than dressed up.

**The problem, as it is on the wire.** The chains are not broken — they are
short:

- `tvsen5.aynaott.com` sends `leaf ← YR1 ← Root YR` and stops. The next
  certificate in that chain is ISRG Root X1, which a 2019 television never
  received. A browser on a current machine completes it; the box cannot.
- `alixbd.com` sends **its leaf only**. The issuer is not on the wire at all,
  so nothing can be completed from what arrived — the issuer has to come from
  somewhere else.
- The same shape runs through the families the app leads to: `aynascope`,
  `sscsott`, `ottplus`, `bbaria`, `klowdtv`, `sonarbanglatv`, and one short of
  DigiCert Global Root G3 for the `sofast`/`akamai` chain.

**What changed.**

- *The repair is written from the ground up* (`core/…/tls/ProviderTls.kt`) in
  the ordering the plan demands: **the box's own store is asked first and every
  one of its checks is kept**; only in its refusal path is the chain completed,
  by walking issuer to subject and adding **only a certificate that actually
  signed the one on top** — never a flat bundle handed over for a verifier to
  build a path through; the completed chain is **offered back to the platform
  first**, because a platform that can finish it itself should be the one to
  say so; and only then is it validated properly — full PKIX validation
  anchored at a certificate this app carries, with the signatures, the dates,
  the CA flag and the server-auth usage all checked.
- *Trust still ends at a root this app deliberately carries.* Verification is
  never switched off, no certificate is accepted on its word, and no host is
  pinned: the address a playlist names is not always the address the player
  dials.
- *The bundle is three certificates* (`app/src/main/res/raw/provider_roots.pem`):
  **Sectigo Public Server Authentication CA DV R36** (not self-signed — it is
  used as an anchor, and it is the certificate that proves the old app really
  did work on Android 9–11 television boxes), **DigiCert Global Root G3**, and
  **ISRG Root X1**. The same three are declared in
  `network_security_config.xml`.
- *The four reasons are kept and they reach the screen.* `tls-issuer`,
  `tls-expired`, `tls-clock` and `tls-address` are read out of the platform's
  own refusal, carried through `PlaybackDetails` → `PlaybackState.detail` →
  the panel, and printed in Bengali and English. A failure that says nothing is
  a failure the viewer cannot act on.
- *A certificate refusal is a broken mirror, not a bad moment.* It is FATAL, so
  the ladder walk moves to the next mirror at once instead of retrying a mirror
  that can never play.
- *The old-box proof is in the repo* (`ProviderTlsTest`, 15 tests) and it is a
  proof, not a demonstration: a store built to be a 2018 television refuses the
  captured aynaott chain **as sent**, and the same bytes are accepted once the
  bundle completes and anchors them; a self-signed certificate claiming
  `CN=alixbd.com` is still turned away; a certificate that has run out is still
  turned away, with the reason `EXPIRED`, while the identical bytes are
  accepted at the pinned date of 2026-10-20.
- *The toolchain's `run-oldbox.sh` is recreated in the repo*
  (`LiveOldBoxHarnessTest`, run with `-Dsoluplay.live=1`), so the measurement
  no longer needs the deleted Java project. It dials every host the live
  catalogue leads to, takes the chain the host really sends, and puts it to
  this machine's store, to a simulated 2018 store, and to that same 2018 store
  through the repair.
- *The resilience ladder is complete and honest*: the live site first, then the
  copy saved on the device, then the copy baked into the APK — and **every
  screen says which copy it is showing** (`BrowseChrome`: live / saved copy /
  built-in copy / no copy). The saved copy is written **whole** (an atomic
  write, so a crash mid-write cannot destroy it) and **only from an answer that
  is a guide**: an answer that reports a failure, or that carries no channels,
  never touches it. The seed carries names and logos and **no stream addresses
  at all**, and the ladder of mirrors is read from the site only — so a stale
  guide can never walk a viewer into a link that died days ago.

**The measurement, live.** 37 hosts were dialled from the live catalogue.
**10 were turned away by the simulated 2018 box, and the bundle rescued all
10** — `tvsen5` and `tvsen6.aynaott.com`, `stream.ottplus.live`,
`box.bbaria.net`, `a-cdn.klowdtv.com`, `app24.jagobd.com.bd`,
`livetv.shamon-cable.net`, `mtv.sunplex.live`,
`g4wlkwx8l23a-hls-live.5centscdn.com` and `raw.githubusercontent.com`. **None
was left refused**, and none was refused for a date. The aynaott certificate has
meanwhile been renewed — the captured one ran out on 3 November 2026, the live
one is good until 25 January 2027 and is issued by `YR1` now — and it still
needs the bundle: that chain still stops one short of ISRG Root X1.

**The gate.** `scripts/build.sh` is green: both debug flavours built, **191
unit tests per suite**, no failures, **4 skipped** — the live harnesses, which
only run when they are asked for. Thirty-one of the tests are new in this
phase: fifteen that prove the repair against a 2018 store, eight that keep the
saved copy honest, five that name the four certificate reasons, two that carry
a refusal to the screen and on to the next mirror, and one live harness.

**Still open.**

- *The backup base URLs are still empty* (`ApiConfig.BACKUP_BASE_URLS`). The
  machinery tries them in order and is tested; the addresses are yours to name
  (§14.3). Until they are named, "your domain dying" is survived by the saved
  copy and the baked seed, not by a second address.
- *The box itself is still the last mile.* Nothing here proves that an
  Android 9 television plays an aynaott channel: the repair has been proved
  against the chains the servers really send, and the box has to be asked. What
  the box has to show: an aynaott channel playing a picture, with no
  certificate reason on the panel.
- *The old toolchain scripts still point at the deleted project*
  (`android-tv-toolchain/e2e/run-oldbox.sh`, `run-tls.sh`). The in-repo
  harnesses replace them; the stale copies can go.

Nothing in this pass was committed, pushed, released or deployed, and no
keystore was touched. The working tree holds the changes for review.

### Amendment 13 — 2026-11-05, Phase 4: the app that can install the next version of itself

A television box in a living room does not open the web app to notice that a
new build exists. It has to be told. Phase 4 is the telling: the app asks the
site what the newest build is, decides for itself whether that build is for
this device, downloads it, checks it, and hands it to the system installer.
Its gate is *"a new build reaches a device with one tap"* — which is half a
statement about code and half a statement about a device, and the code half is
written below while the device half is named at the end.

**The decisions are pure, and they live in `core`.**

- *The offer.* `UpdatePolicy.offerOf` reads the manifest and hands back the
  build for **this surface**: the phone build on a phone and the television
  build on a television, chosen from the manifest's `variants` and not by
  guessing. A manifest with no build number in it is not an offer.
- *Staged rollout.* The share of installs a release is for is a hundred
  buckets, and an install lands in one of them by an id of its own
  (`UpdatePolicy.bucketOf`). The same device is always in the same bucket, so
  a release that grows from ten per cent to fifty reaches **exactly the
  devices that were already in it** and no others.
- *The kill switch.* Pointing the manifest back at the previous build is
  enough: an offer that is not newer than the running build is nothing at all.
- *The mandatory flag.* A mandatory update does **not** stop the app the
  minute it appears — that reads as a broken app. The device is given a day
  (`MANDATORY_GRACE_MS`), the moment the build was **first offered to that
  device** is remembered per version number, and after the grace the app
  stops.
- *Retiring a build.* `minSupportedVersionCode` names the oldest build that
  may still play. A device below it is stopped at once, with no grace, because
  the build it is running has been withdrawn.

**The download is checked before it is installed.**

- The bytes are written into `<name>.part` and the **sha256 is computed while
  they are being written**. Only a digest that matches the manifest is moved
  into place; a mismatch, or a failure, deletes the part file and the app says
  the update could not be downloaded.
- **A manifest with no fingerprint is refused outright.** An update that
  cannot be checked is not an update, and the refusal is its own reason
  (`NO_FINGERPRINT`) rather than a download that quietly failed.
- The downloader asks for **no credentials and no provider headers**. The APK
  is a public GitHub Releases asset.
- **The file is fetched over the app's own repaired transport.** A television
  whose trust store was never updated could read the manifest and then fail to
  fetch the file that carries the repair, so the download is opened with the
  same secure context as everything else the app reaches.

**The install is handed to the system, and the system is asked back.**

- The APK is installed with a `PackageInstaller` session
  (`MODE_FULL_INSTALL`): the file is written into the session, `fsync` is
  called, and the session is committed with an `IntentSender`. The legacy
  `commit(Intent)` form — taken out of the platform in Android 10 — is called
  reflectively for the old boxes and nowhere else.
- The result comes back through a **manifest-declared receiver**
  (`InstallResultReceiver`), because the system instantiates the receiver, not
  the app. The result is published through a flow the ViewModel is listening
  to; a failure that came back from the installer is a failure the viewer is
  told about.
- `REQUEST_INSTALL_PACKAGES` is declared, and a device that has not allowed
  this app to install packages is sent to the system's own
  *install unknown apps* screen. **The downloaded file is kept**, so the second
  press installs without downloading again.

**The screen is honest about where the app is in the update.**

- A new build is offered in a **sheet**: the version, the release notes, and
  *Install now* / *Later*. *Later* is only offered while the update is still
  optional.
- A **blocked** build — a retired one, or a mandatory update past its grace —
  does not get a sheet. It gets a **panel that replaces the home screen**, so
  the guide is never composed and no channel can be opened. The playback
  machine is not touched, and the Direct HLS and Secured separation is not
  touched: the app simply stops being a player until it is updated.
- Every word of it is in **Bengali and English** (`core/ui/Copy.kt`), in both
  the phone and the television sizes — the television sheet is the same sheet
  at a larger scale, not a second design.

**One round trip for the guide and the board.**

- The app now asks `/api/app/bootstrap` first: the catalogue and the match
  board in one body, so the guide on the screen and the board on the screen
  are the same pair of answers instead of two reads taken minutes apart.
- **A site without that route is not a failure.** The app falls back to the
  two routes it already used (`/api/channels`, `/api/fixtures`), and behaves
  exactly as it did before — which matters, because the route does not exist
  on the live site yet.
- A board that could not be read is **said so** in the bootstrap body
  (`fixturesAvailable`), so the board saved on the device is not overwritten by
  an empty board.

**The two routes in `freetv`, written but not deployed.**

| Route | What it does | Where it lives |
|---|---|---|
| `/api/app/version` | Serves the release manifest verbatim, from the site's own copy or from the GitHub raw mirror, with the last copy kept when the address does not answer | `lib/appRelease.ts`, `app/api/app/version/route.ts` |
| `/api/app/bootstrap` | The catalogue and the board in one body, read from the two existing routes on the server | `app/api/app/bootstrap/route.ts` |

Neither route changes an existing route. `/api/channels` and `/api/fixtures`
are read as they were, and the bootstrap route calls them rather than
duplicating their queries. **Both are committed in the local `freetv`
repository only** — nothing was pushed, nothing was deployed, and the live site
is untouched — because §13 says every addition to `freetv` needs its own review
first.

**The release path: one tag, and the manifest writes itself.**

- The release job builds the signed phone flavour, publishes it as
  `SoluPlay.apk`, and **writes `latest.json` from the bytes it has just
  published**: the size is read off the file and the fingerprint is
  `sha256sum`ed from it. A number typed by hand is a number that can be wrong,
  and a wrong fingerprint makes every device refuse the update.
- **One file, two variants.** The published file is the phone flavour, which
  carries the television launcher as well, so one download covers a handset and
  a TV box. Both manifest variants are given the same file, and the split in
  §12 writes a second file into the `tv` variant and changes nothing else.
- **Before the first release the manifest names no build**: `versionCode` is
  `0`, the version name is empty and no URL is filled in. The download page
  shows its *"link will be available soon"* notice and the app is offered
  nothing, so neither of them points at a file that is not there. A test reads
  the real file in this repository and proves that it is inert.
- The job commits the manifest back to `main` **on top of the branch as it is
  at that moment**, not on top of the tag, so a branch that moved on while the
  release was building does not refuse the push.
- The job **refuses to publish without the four secrets**, because a build
  that is not signed with the release key cannot be installed over the build
  that was.

**The manifest mirror does not exist yet.** `johncarry301/soluplay-app` is
public and **empty**: it has no commits and no releases, so
`https://raw.githubusercontent.com/johncarry301/soluplay-app/main/latest.json`
answers 404 and the app's mirror read falls over. Not one of this repository's
commits has ever been pushed. Until the repository is pushed there is no
manifest for the app to read and no release for the page to offer.

**§14.2 is closed.** The download button on the site points at the live
landing page, `https://soluplay-download.vercel.app` (FreeTV `59b2ea3`), and
the landing page reads the manifest from this repository — so the address
never has to change again, and a new release needs no website change at all.

**The gate.** `scripts/build.sh` is green: both debug flavours built, **231
unit tests per suite** (phone debug, phone release, TV debug, TV release),
**no failures**, **4 skipped** — the live harnesses, which only run when they
are asked for. **Thirty-nine of the tests are new in this phase**: fourteen
that decide an update (the variant for the surface wins, a manifest with
nothing in it, only a newer build is taken, the same install lands in the same
bucket every time, a staged release reaches only its share, a growing release
reaches the installs it already had, a retired build stops the app at once, a
mandatory update is given a day, an optional update never stops the app);
six that read the manifest (the site is asked first and the mirror is not
asked at all, an address that answers with rubbish is passed over, no address
answering is no update and not a failure); six that download (a matching
fingerprint is kept, a mismatched one is thrown away, a manifest with no
fingerprint is refused, a fingerprint that is not a fingerprint is refused, a
failed download is a failure and not an empty build); nine that make the
bootstrap route work and fall back when it is not there; and four that tie
this repository's own `latest.json` to the updater (the published file parses
into the updater's model, it names no build before the first release, the
document the publishing job writes is the one the app reads, and a device on
the older build is offered the new one while a device already on it is not).

**The build number is now 2.** The updater compares build numbers, so the
first published release has to be newer than anything that is already
installed; `versionCode` is 2 and `versionName` is `0.2.0`.

The APKs are in `artifacts/phase4-updater/`, signed with the debug key (the
release keystore is still open — §0.1), carrying the new permission and the
new receiver:

| Artifact | sha256 |
|---|---|
| `SoluPlay-Phase4-Updater-phone-debug-0.2.0.apk` | `a7e6c8f77c40c74a383d244b21e7d99df8d34c4af89e3f01603d7b340a29f096` |
| `SoluPlay-Phase4-Updater-tv-debug-0.2.0.apk` | `0036def0fbfa31103c07eaa202c20df5d8de50baf650fdaa0253b739d1e79a26` |

Both are signed with the same debug certificate
(`12e85bad1887edc2c4a9215658b5ef54978c1eec05bb757b1675137fcfba4153`), so one
can be installed over the other — which is how the update path can be walked
on a device before the release key exists. The phone build is
`com.soluplay.app` and the television build `com.soluplay.tv`, both at build
number 2.

**Still open.**

- *The two `freetv` routes are not live.* They are written and reviewed here
  but not deployed, so the app today reads the manifest from the GitHub mirror
  and the guide from the two older routes. Each route needs its own review
  before it goes live.
- *The repository has never been pushed.* `johncarry301/soluplay-app` is empty
  on GitHub, so the manifest mirror answers 404 and there is no release for the
  download page to offer. Pushing the repository is the owner's word to give,
  and it is the first thing that has to happen for the manifest chain to exist
  at all.
- *The release job has never run a release.* It needs the four secrets
  (§0.1) and a `v*` tag. What it then does is written down above and proved
  by the shape test, but the job itself has never been executed against a
  signed build.
- *A real build to update to.* The updater can be proved against a manifest,
  but "a new build reaches a device with one tap" needs a real
  `SoluPlay.apk` in GitHub Releases and a device to install it on. **The APK
  in the manifest must be signed with the same key as the installed app** —
  a debug-signed build cannot be replaced by a release-signed one, and the
  system will refuse it. The release keystore (§0.1) is therefore on the path
  to the first real update, not only on the path to the store listing.
- *The install prompt on a real box.* The `PackageInstaller` path, the
  *install unknown apps* permission, and the prompt itself can only be seen on
  a device. What the device has to show: a new version offered, one press, and
  the app replaced by the build that was offered.

This pass is committed in the local repository and **nothing was pushed,
released or deployed**; no keystore was touched. The working tree holds the
changes for review.

### Amendment 14 — 2026-11-05, Phase 5: the polish the plan asked for

Phase 5 is the one row of §12 that has no gate: *"Match board, favourites +
sync, TV on-screen search, corner preview, gestures, Bengali/English"* — done
when *—*. A phase without a gate cannot be measured, so this amendment says
what was built, what it was proved by, and what is left for a device.

**The saved list, and the key it travels under.**

The plan's fence (§0) is that the app has no accounts and never will, so the
**key is the identity**. The key is eight characters from a thirty-two letter
alphabet with the characters that are read wrong over the phone left out —
no `I`, no `O`, no `0`, no `1` — so a key that is read aloud is the key that
was written. It is written on the device, shown to the viewer, and typed into
the second device that is to carry the same list.

The rules live in `core/favourites/` and are stated plainly, because a list is
the one thing a viewer would notice losing:

- **The device's copy is the one the screen shows, and it is written the
  moment a channel is marked.** A viewer who marks a channel on a train with
  no signal must not lose it.
- **The site is asked only when the viewer asks for it.** The server is never
  pushed to in the background.
- **A site that cannot be reached never empties the list.** A `null` answer
  and an empty list are two different things, and the app tells them apart:
  a list that is emptied because the network was down is worse than a list
  that is a day old.
- **The list that was written last is the list that is kept.** There is no
  account, so there is no merge and no conflict screen to fall back on.
- **A key that is not one is refused and nothing is written.** Repairing a
  key would put a viewer's list under a key nobody typed.

The copy travels as ids, and the names stay on the device: a favourite whose
channel is not in today's catalogue is still a favourite, and it is still
named.

**The site answers under that key, and only that key.**

`/api/app/favourites` is a new route in `freetv`: `GET ?key=…` answers the
ids the site holds, `POST {key, ids}` puts them there. It is written in the
house style — a Mongo model, a store that refuses rather than answering from
nowhere, a body read under a cap, a key that is not one refused whole — and
**it is not deployed**. §13 says every addition to `freetv` needs its own
review before it goes live, so it waits with the two Phase 4 routes beside
it. Until then the app keeps the list on the device and says the site could
not be reached.

**The board, the search and the words.**

- *The board* (`core/fixtures/BoardSections.kt`) groups the matches into the
  three the plan names — live now, coming up, finished — from the clock the
  site publishes, not from a second clock of our own.
- *The search* (`core/search/ChannelSearch.kt`) matches a name or a category
  against the guide that is already on the device, so it answers while
  offline.
- *The gestures* (`core/gestures/Gestures.kt`) are rules, not code in a
  surface: a finger that travelled is a swipe and a finger that stayed is a
  tap, the swipe up changes the channel, the swipe sideways asks for another
  mirror, two fingers hold the feed and one tap only brings the words in.
  **A tap never pauses the picture** — that is the rule the plan kept from
  the web.
- *The words* (`core/ui/Copy.kt`) are Bengali first with English beside them,
  and the choice is **remembered on the device**, because a viewer who turned
  the words into English once expects to find them in English next time.
  Channel and category names are the site's own and are never translated.

**The phone has four places now.** Home (the strip, the rails, the logo
grid), the match board, the search and the saved list, behind a pill row of
four icons. The choice sheet is a sheet, the back key closes it, and a
channel is marked with a star on the watch screen and in the saved list.

**The television is a different app, and it is now built as one.**

- One screen at a time, under a chrome bar that is **row zero of every
  screen's focus grid**, so the remote can always get back to it.
- One key handler per screen root owns the arrows and Enter, and the choice
  sheet is modal: the handler swallows the arrows while the sheet is open and
  the back key closes it.
- *The on-screen keyboard* (`TvKeyboard.kt`) is drawn, not typed: three rows
  of ten, the last row holding the space, the delete, the clear and the
  search, so the remote walks a square grid and never a ragged one.
- *The corner preview* plays the channel the remote is resting on in a
  380 × 214 dp box in the corner, after the remote has rested on it for 700
  ms. **It is a second player and a second view model**, so the picture of
  the guide and the picture of the watch screen never fight over one decoder,
  and the sound carries on while the guide is read.

**The gate.** `scripts/build.sh` is green: both debug flavours built and
**280 unit tests per suite**, no failures, 4 skipped — the live harnesses,
which only run when they are asked for. **Forty-nine of those tests are new
in this phase**: thirteen that keep a saved list (a key that is read right, a
key that is not, a list that is never lost because the site could not be
reached, the site that is the truth when it answers, a name the device knows
is kept when the site holds only ids, a list that could not be pushed stays on
the device, another device's key is taken on); nine that match a search; nine
that group a board; twelve that turn a gesture into an intent; and six that
step to the next mirror.

The site half is measured too: `npm run test:all` in `freetv` is green at
**184 tests** (twenty of them new, for the favourites route alone), and
`npm run typecheck` and `npm run build` are both green, with
`/api/app/favourites` registered as a dynamic route.

The APKs are in `artifacts/phase5-polish/`, signed with the same debug key as
Phase 4 (`12e85bad…`), so one can be installed over the other:

| Artifact | sha256 |
|---|---|
| `SoluPlay-Phase5-Polish-phone-debug-0.2.0.apk` | `58c7b18d793ec5bd567e3966be132f2865f96c743d39a547aabcb5332851f6d5` |
| `SoluPlay-Phase5-Polish-tv-debug-0.2.0.apk` | `128676db72786d991399e8d660af00cff0edce09e8463b5e74ddcccdb3eb2fe2` |

The build number is still **2** and the name still **0.2.0**: no release has
been published, so there is nothing for the number to be newer than. Raising
it is the owner's call when the first release is cut.

**Still open.**

- *Everything that is a claim about a screen.* The corner preview, the focus
  ring, the drawn keyboard, the gestures on a real finger, the sheet's
  animation — none of it has been seen on a handset or a box.
- *The saved list has never travelled.* The site route is written and
  proved, but it is not deployed, so the second device cannot be given the
  first device's key yet.
- *The words have not been read aloud.* They were written by me and the
  Bengali has not been spoken by a speaker of it.
- *The last-good catalogue, the seed catalogue and the backup base URLs* are
  unchanged from Phase 3 (3.1).

This pass is committed in the local repository and **nothing was pushed,
released or deployed**; no keystore was touched. The `freetv` additions are
in that repository's working tree for review, and the site is untouched.

### Amendment 15 — 2026-11-05, Phase 6: the ten-foot page for the boxes that run a browser and nothing else

Phase 6 is the last row of §12: **`/tv` web layout** in `freetv` for
Tizen/webOS, D-pad handling, no promos — done when *"An old Samsung box gets
a 10-foot page"*. The code half is finished and measured here. The box is
the owner's, so the gate stays open until he answers it.

**One new route, and one guarded line in an old file.**

The page is `/tv`, and it is **additive**: no existing route was edited, no
API changed, and the two players and the watch page were not touched. The
single change to an existing file is a guard in `components/AdManager.tsx` —
the 24-hour ad modal's Skip button can only be pressed with a mouse, so a box
would sit behind it with no way out, and the modal is therefore never raised
on `/tv`. That is the whole of the change to a file that already existed.

**The guide is the site's, in the site's order.**

The rails are the catalogue's own seven, in the catalogue's own order, and
the order inside a rail is left exactly as the catalogue answered it — pinned
first, then the admin's priority, then the name. **Nothing on this page
re-ranks a channel.** A channel whose category the site does not know goes
into an "Other" rail at the end rather than being dropped, because a guide
that quietly loses a channel is worse than a guide with one more row.

**The remote's rules are the app's rules.**

The walk is the one `TvFocus.kt` walks its guide with, copied rather than
invented: up and down move between rows, left and right move inside a row, a
row that holds nothing is stepped over rather than stopping the walk, the
walk **stops at the ends** instead of wrapping round to the other side, and
the column is clamped into the row it lands on. The bar is the first row of
the walk, so the remote can always get back to it.

The keys are read **by their number before their name**. The engines in the
field — Tizen 2016–2019 and webOS 1–3, all of them Chrome 38 or older — do
not always fill in `event.key`, so a page that only reads the name of the key
hears nothing on half the boxes that exist. The Tizen BACK key (`10009`),
the arrows and Enter are read from `keyCode`; the name is the fallback for
the engines that do fill it in.

Three more old-engine hazards are respected: `Array.prototype.sort` is **not
stable** on Chrome 38, so every order in this page is settled by an explicit
index tie-break rather than by the sort's own idea of a tie; `scrollIntoView`
with options is unreliable, so the focused tile is brought into view by
measuring `getBoundingClientRect` and moving the scroll by the difference;
and `padStart`, `find` and `includes` are avoided in the pure modules, which
use `indexOf` instead.

**The search is drawn, because a television has no keyboard.**

The keyboard is the app's own: three rows of ten, the last row holding the
space, the delete, the clear and the search, so the remote walks a square
grid and never a ragged one. The ranking is the app's too — a channel whose
name *begins* with what was typed comes before one that merely contains it,
and a channel that matches only because of its category or its tags comes
after both.

**Nothing is promoted, and the picture is the picture.**

There is no app-download banner, no news ticker, no fixture promo, no admin
link, no download CTA and no ad modal on `/tv`. A channel that opens takes
the whole screen: the same `DirectHlsPlayer` and `MpegTsPlayer` the watch
page uses, **unchanged**, fed by `buildLadderFromMirrors` so the ladder is
the server's own ranking and the automatic failover is the players' own. The
only thing added over the picture is the channel's own name, which fades
after six seconds.

A channel whose every mirror failed **comes off the guide** — a tile a
viewer can pick must be a tile that plays — and the remote lands on the next
channel in the same row. That is the app's rule too, and it is the one the
plan kept from the web.

**The gate.** In `freetv`:

| Check | Result |
|---|---|
| `npx tsc --noEmit` | clean |
| `npm run test:tv` | 24 tests, 0 failures (all new) |
| `npm run test:all` | 208 tests, 0 failures |
| `npm run build` | green — `/tv` is a static route, 7.88 kB |

The twenty-four new tests hold the rules a box depends on: that a key is read
by its number before its name, that the Tizen BACK key is understood, that
the walk steps over an empty row and stops at the ends, that the column is
clamped into the row it lands on, that the rails are the site's seven in the
site's order, that the search's ranking is name-first, that the order inside
a rail is never re-sorted, and that the keyboard is three rows of ten.

**The proof that was possible here, and what it was not.**

The production build was run locally against the in-memory catalogue and
read in a real browser at 1920 × 1080. The guide drew its rails in the
site's order with the site's own logos; the walk was measured off the live
DOM, not off the code: **Guide → right → Search → right → Refresh → down →
the third tile of the first rail → left → the second tile → up → back on
the bar, in the same column → up → no move → left → Guide → left → no
move**. The search typed a name on the drawn keyboard and ranked the answers
name-first; BACK on the search tab returned to the guide while BACK on the
guide was left to the browser, so a viewer can still leave the page. A
mirror whose origin answered 404 was walked to the end of the ladder, and
the channel came off the guide with the focus on the next one.

The catalogue was read from the in-memory store and **nothing was written**:
`data/store.json` is byte-identical afterwards
(`28400f8f06888cdf9537681f4c75b60e6bfdbcc468f53d41eba3564ef28a4c2e`).

**Still open.**

- *The old Samsung box.* Everything above is a claim about a screen, and
  screens are seen on boxes. §10 is honest about this: the last mile of
  every phase is the owner's.
- *The `/tv` route is not deployed.* It is in the local `freetv` repository
  only. §13 says every addition to `freetv` needs its own review before it
  goes live, so it waits for that review beside the three Phase 4 routes.

This pass is committed in the local repositories and **nothing was pushed,
released or deployed**; no keystore was touched and the live site is
untouched.

### Amendment 16 — 2026-11-05, Phase 6 withdrawn: the browser ten-foot page is removed

**The decision.** The browser interface is no longer part of the product.
SoluPlay is distributed as the **Android phone app** and the **Android TV
app** only, so the `/tv` web layout — the ten-foot page built for Tizen and
webOS sets — is withdrawn and its code is removed from `freetv`. Amendment 15
stays above as the record of what was built and measured; this amendment
records why it is gone.

**Why.** The page was built for boxes that run a browser and nothing else.
Every one of those boxes is a television the app already reaches, and the
app's own television surface — the focus grid, the drawn keyboard, the saved
list, the ladder walk and the failover — is the same product without a second
implementation to keep alive. One distribution means one set of rules.

**What was removed** (in `freetv`, one commit, `ca57735`, 14 files):

| Removed | What it was |
|---|---|
| `app/tv/page.tsx`, `app/tv/layout.tsx` | the route itself |
| `components/tv/` (5 files) | bar, guide rows, channel tile, drawn keyboard, watch screen |
| `lib/tvFocus.ts`, `lib/tvKeyboard.ts`, `lib/tvRows.ts` | the focus walk, the keyboard, the rails |
| `tests/tvFocus.test.ts` (24 tests) | the twenty-four tests that held the walk's rules |
| the `/tv` guard in `components/AdManager.tsx` | the ad modal's exception, which existed only for this page |
| the `test:tv` script and four `tsconfig.tests.json` lines | the test wiring |

The commit was dropped from `freetv`'s `main` — the branch is now
`6021d13` and `17d134c` ahead of `origin/main`, and the tree is byte-identical
to the commit before Phase 6. The work itself is kept on the local branch
`archive/tv-2026-11-05`, so it is recoverable and can be read; it is not
published.

**What was not touched.** The two Phase 4 commits are exactly as they were,
still unpushed and still awaiting their own review. The site's own behaviour
is unchanged: the catalogue, the watch page, the players, the ranking, the
server-provided ladder, the automatic failover, the admin gate and every
existing route are as they were, and the ad modal is byte-identical to the
published file — it simply has no `/tv` exception any more. The Android phone
app and the **Android TV app** are untouched; they never used the web page.
`lib/tvPolyfills.ts` stays, because the phone layout, the watch page and the
admin dashboard have always used it.

**The gate.**

| Check | Result |
|---|---|
| `git diff 17d134c HEAD` | empty — the tree is the pre-Phase-6 tree |
| `git status` | clean |
| `npx tsc --noEmit` | clean |
| `npm run test:all` | 184 tests, 0 failures (208 − the 24 `/tv` tests) |
| `npm run build` | green — no `/tv` route in the output; the three `/api/app/*` routes are in it |
| `data/store.json` | byte-identical (`28400f8f06888cdf9537681f4c75b60e6bfdbcc468f53d41eba3564ef28a4c2e`) |

**Still open.** Unchanged by this amendment, and all of it still the owner's:
the two Phase 4 routes are committed locally and wait for their review (4.2),
the saved list cannot travel between two devices until that route is live
(5.2), the release keystore passphrase is still wanted (Phase 0), and the
device half of every phase is still the owner's.

This pass is committed in the local repositories and **nothing was pushed,
released or deployed**; no keystore was touched and the live site is
untouched.

### Amendment 17 — 2026-11-05, the release path is made publishable

**What changed.** The path that publishes the build and writes the manifest
was read end to end before this repository goes public, and four things were
hardened. The release itself is unchanged: the same job still publishes the
same asset and writes the same manifest.

| Change | Why |
|---|---|
| The Android SDK is installed by name in both jobs (`platforms;android-34`, `build-tools;34.0.0`) | The build took whatever the runner image happened to carry, so a change in the image could have changed the build. |
| `gradle-wrapper.properties` carries `distributionSha256Sum` | The wrapper jar was validated by the CI action; the Gradle distribution itself was not pinned to a checksum. |
| `scripts/build.sh` and `scripts/create-release-keystore.sh` derive the toolchain from the account's home folder | The account's name was written into two files that are about to be public. |
| `REMAINING.md` carries the true commit count and the publication steps | The list of what is owed should be the truth on the day it is read. |

**The gate.** The release job's paths were checked against the build rather
than assumed: the flavour's output
(`app/build/outputs/apk/phone/release/app-phone-release.apk`), the version
file (`app/build/version-info.json`), the keystore paths (the repository root,
read by `rootProject.file`) and the manifest's `jq` program, whose output was
produced and parsed. The git sequence the job uses to write the manifest into
`main` was run in a scratch copy of this repository, so that step is known to
work rather than assumed to.

**What was not touched.** The app code, the release job's steps, the manifest
format and the phases themselves: the release path is the same path with its
sharp edges filed. The release key was not created and no secret was
changed — the passphrase is still the owner's.

**Still open.** Everything in `REMAINING.md` is still open: the four secrets,
the first push, the `v0.2.0` tag, and the device half of every phase.

This pass is committed in the local repository and **nothing was pushed,
released or deployed**; no keystore was touched.

