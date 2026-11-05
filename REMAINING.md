# SoluPlay Native App — what is still open, phase by phase

**2026-11-05.** The list of everything the phases have not finished, kept
beside the plan so the two do not drift. The plan (`PLAN.md`) says what each
phase is; this says what is still owed.

**Nothing here can be closed by writing more code.** Every line is either
something only a real device can show, something only the owner can supply,
or a phase that has not been started.

---

## Phase 0 — Spec + skeleton · **done**

| # | Open | Who | Why it is open |
|---|---|---|---|
| 0.1 | The release keystore | Owner | Generated with a passphrase only he sets (an env var — never printed, never committed). The build falls back to the debug key until it exists. |
| 0.2 | The keystore's backup off this PC | Owner | Two copies, off the machine, plus a password manager. Nothing else can replace it. |
| 0.3 | ~~`APP_DOWNLOAD_URL` reads `https://example.com/soluplay-app`~~ **closed 2026-11-03** | — | The download button on the site points at the live landing page, `https://soluplay-download.vercel.app` (FreeTV `59b2ea3`). The landing page reads the manifest from this repository, so the address never has to change again. |
| 0.4 | The CI release job has never run a release | Owner | The workflow builds a signed debug APK; the release path has not been exercised because there is nothing to publish yet. The same gap as 4.4. |

## Phase 1 — Browse · **done**

| # | Open | Who | Why it is open |
|---|---|---|---|
| 1.1 | **X2** — the fixture fields `choices` and `watchPath` are modelled and unused | — | They belong to the board's own cards and to playback. The app opens a channel by its `channelId`; the web routes are read but not acted on. |
| 1.2 | **X4** — `/api/fixtures` answered with zero matches on 2026-11-04 | — | No fixture card has yet been seen carrying real data. The strip is proved by unit tests against the shape the site publishes, not by a real board. |
| 1.3 | The guide on a real handset | Owner | Nothing in Phase 1 has run on a phone or a box. |

## Phase 2 — Playback · **done**

| # | Open | Who | Why it is open |
|---|---|---|---|
| 2.1 | Every playback claim on real hardware | Owner | The decoder path, the first frame, the failover onto a second mirror, the D-pad focus, the ten-second deadline, the race of three, the size of the picture on a cheap handset. Nothing was seen on a device. |
| 2.2 | The three kinds of source on a device | Owner | That a `.php` mirror plays, that a `.ts` mirror plays, that a YouTube live channel plays and reports its state. |
| 2.3 | The race of three is a judgement, not a measurement | — | Three decoders at once, plus a web view for YouTube, is more than a cheap handset has been asked for. |
| 2.4 | The YouTube handshake | Owner | The player page is proved by its contents and not by its behaviour. The Galaxy A21 has to show a YouTube live channel playing, and *"live"* on the status line. |
| 2.5 | The size of the shipping package | — | Only measurable from the release variant, which was not built. |
| 2.6 | **X2** and **X4** are unchanged from Phase 1 | — | See 1.1 and 1.2. |
| 2.7 | ~~The TV corner preview of §6 is not built~~ **closed 2026-11-05** | — | Built in Phase 5, in a player of its own so it never fights the watch screen for the decoder. What is still open about it is in 5.4. |
| 2.8 | `latest.json` names no build until one is published | — | Until a real `SoluPlay.apk` asset exists in GitHub Releases. The manifest says so rather than pointing at a file that is not there. |

## Phase 3 — Old-box TLS + resilience · **done**

| # | Open | Who | Why it is open |
|---|---|---|---|
| 3.1 | **The backup base URLs are still empty** (`ApiConfig.BACKUP_BASE_URLS`) | Owner | PLAN §14.3. The machinery tries them in order and is tested; the addresses are his to name. Until then a dead domain is survived by the saved copy and the baked seed, not by a second address. |
| 3.2 | The Android 9 box itself | Owner | The repair is proved against the chains the servers really send; the box has to be asked. What it must show: an aynaott channel playing a picture, with no certificate reason on the panel. |
| 3.3 | The old toolchain scripts still point at the deleted project | Owner | `android-tv-toolchain/e2e/run-oldbox.sh` and `run-tls.sh`. The in-repo harnesses replace them; the stale copies can go. |

## Phase 4 — Updater · **code done, the device half open**

| # | Open | Who | Why it is open |
|---|---|---|---|
| 4.1 | The gate on a device | Owner | The code half of the gate is measured — 280 unit tests per suite, both debug flavours built. *"A new build reaches a device with one tap"* needs a handset and a box, and there is neither attached to this PC. |
| 4.2 | The three FreeTV routes are not deployed | Owner | `/api/app/version`, `/api/app/bootstrap` and `/api/app/favourites` are committed in the local `freetv` repository only — nothing pushed, nothing deployed, the live site untouched. §13 says every addition to `freetv` needs its own review first, so they wait for that review. Until then the app reads the manifest from the GitHub mirror, the guide from the two older routes, and keeps its saved list on the device. |
| 4.3 | The app repository has never been pushed | Owner | `johncarry301/soluplay-app` is public and **empty**: no commits, no releases. The manifest mirror answers 404, the app has nothing to read and the download page has nothing to offer. This repository holds ten local commits that have never been pushed. It has been read end to end before publication: no credential, no signing material, no personal file and no build output is in it, and every path the release job depends on was checked against the build. |
| 4.4 | The release job has never run a release | Owner | The job that publishes the build and writes the manifest is written and reviewed, but it has never run: it needs the four secrets (0.1) and a `v*` tag. |
| 4.5 | A real release to update to | Owner | The updater is proved against a manifest, not against a real build. It needs a real `SoluPlay.apk` in GitHub Releases, and **that APK must be signed with the same key as the installed app** — a debug-signed build cannot be replaced by a release-signed one and the system refuses it. The release keystore (0.1) is therefore on the path to the first real update, not only to the store listing. |
| 4.6 | The install prompt on a real box | Owner | The `PackageInstaller` session, the *install unknown apps* permission, the system's own prompt and the result coming back through the receiver can only be seen on a device. |
| 4.7 | The blocked-build panel on a device | Owner | A retired build and a mandatory update past its grace replace the home screen with a panel instead of showing a sheet. That the panel really keeps the guide from being composed is a claim about the screen, and screens are seen on devices. |

**Closed in this pass.** The manifest in this repository used to name a build
that was never published, with an empty fingerprint and a URL that answered
404 — the download page would have offered a dead download and the app would
have tried to fetch a file that was not there. It now names **no build at all**
until a release exists, and the publishing job writes the real size and
fingerprint from the bytes it publishes, so neither number is ever typed by
hand.

## Phase 5 — Polish · **code done, the device half open**

| # | Open | Who | Why it is open |
|---|---|---|---|
| 5.1 | The match board on a device | Owner | The board is built and grouped by the clock the site publishes (`core/fixtures/BoardSections.kt`), and both surfaces have a board screen. What has not happened is a board read on a handset or a box. |
| 5.2 | The saved list travelling between two devices | Owner | The list, the key and the sync are built and proved by thirteen unit tests, and the site's route `/api/app/favourites` is written and proved by twenty more — but that route is **not deployed** (4.2), so the second device cannot be given the first device's key yet. |
| 5.3 | The on-screen keyboard on a television | Owner | The keyboard is drawn and the remote walks a square grid of thirty keys, and the search screen uses it. Whether a channel name can be typed comfortably with a D-pad is only known on a box. |
| 5.4 | The corner preview on TV | Owner | The preview plays the channel the remote rests on, after 700 ms, in a player of its own. Whether the picture arrives fast enough on an old box, and whether the sound carries on while the guide is read, is a claim about hardware. |
| 5.5 | The gestures on a real finger | Owner | The rules are pure and tested (`core/gestures/Gestures.kt`), and the surface that reads them is built. What has to be seen: that a tap brings the words in **without pausing the picture**, and that a sideways swipe asks for another mirror. |
| 5.6 | The Bengali and English wording, read by a speaker of both | Owner | The copy is written by me and has not been read aloud. The switch is remembered on the device, and channel and category names are the site's own and are never translated. |

**Closed in this pass.** The app had one screen on the phone and one on the
television, both of them browsing. It now has four places on the phone (home,
matches, search, saved) and one screen at a time under a chrome bar on the
television, with a drawn keyboard, a corner preview and a saved list. The
gestures the plan named are rules in `core` rather than code in a surface,
and the Bengali/English switch is remembered instead of resetting.

## Phase 6 — `/tv` web layout · **withdrawn 2026-11-05**

| # | Open | Who | Why it is open |
|---|---|---|---|
| — | **Nothing is owed.** The phase is withdrawn | — | The browser ten-foot page is removed: SoluPlay is distributed as the Android phone app and the Android TV app only (PLAN Amendment 16). The code was dropped from the local `freetv` repository — `main` is `6021d13` and `17d134c` ahead of `origin/main`, the tree is byte-identical to the commit before Phase 6, and the work is kept on the local branch `archive/tv-2026-11-05`, unpublished. The two gates of this phase — the old Samsung box (6.3) and the undeployed `/tv` route (6.4) — are void with the phase. |

---

## Things that only the owner can supply

1. **The release keystore passphrase** (Phase 0).
2. **The backup base URLs** (Phase 3, PLAN §14.3).
3. **A push of this repository** (Phase 4, 4.3) — the manifest mirror and the
   release assets live on GitHub, and the repository is empty.
4. **The four release secrets and a `v*` tag** (Phase 0, 0.1; Phase 4, 4.4) —
   the keystore, its passwords, the alias, and the tag that starts the release.
5. **A device** — the phone and the television (every phase, PLAN §10).
6. **A decision on header-carrying feeds** — include or defer past Phase 2 (PLAN §14.5). Currently deferred.

The download URL asked for in PLAN §14.2 was named on 2026-11-03: the site's
download button points at the live landing page, and the landing page reads the
manifest from this repository.

## Things that were asked for and answered

- *Header-carrying feeds* — deferred past Phase 2 (PLAN §14.5).
- *The real APK download URL* — named on 2026-11-03: the site's download
  button points at the live landing page, and the landing page reads the
  manifest from this repository.
- *Splitting into two packages* — not yet. One APK covers a handset and an
  Android TV box; the split is prepared as the `tv` flavour and waits for the
  day the two need separate packages (PLAN §12).

## The first publication, in order

1. **Create the release key.**
   `export SOLUPLAY_KEYSTORE_PASSWORD='…'` and then
   `scripts/create-release-keystore.sh`. The passphrase is yours alone: it is
   never written to this repository and never printed.
2. **Back the key up** to two places off this machine and keep the passphrase
   in a password manager. Without both, no update can ever be signed for the
   people who already installed the app.
3. **Add the four secrets** to this repository: `KEYSTORE_BASE64` (the base64
   of `keystore/release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`soluplay`)
   and `KEY_PASSWORD`. The job refuses to run without them rather than
   publishing an unsigned or wrongly-signed build.
4. **Push `main`.** The branch has never been on the remote, and the release
   job needs it to exist there before it can write the manifest into it.
5. **Watch the `build` job go green** on that push. The release job waits for
   it, so a red build means no release and no manifest.
6. **Push the tag `v0.2.0`.** That runs the release job: it builds the signed
   phone APK, publishes it as the release asset, and writes `latest.json` into
   `main` with the real version number, size and fingerprint.

Nothing else is needed for the manifest to become valid and reachable.

## The rule behind the list

- A phase is **not** done until its gate is measured, and a gate that needs a
  device stays open until a device measures it.
- Nothing is pushed, released or deployed without the owner's word.
- This file and `PLAN.md` are kept together: the plan says what each phase is,
  this says what is still owed on it.
