# SoluPlay Native App — what is still open, phase by phase

**2026-11-06.** The list of everything the phases have not finished, kept
beside the plan so the two do not drift. The plan (`PLAN.md`) says what each
phase is; this says what is still owed.

**Nothing here can be closed by writing more code.** Every line is either
something only a real device can show, something only the owner can supply,
or a phase that has not been started.

**What changed on 2026-11-06.** Phase 7 was added: the phone's home, its
match board and its watch page were rebuilt to the site's own mobile view
(PLAN Amendment 18), and every row of it is a device question. That work was
then published as the second release, `v0.2.1` — the version moved to 3 /
0.2.1, the release notes live in the repository and the publishing job reads
them, and the manifest names the new build. The rows that had gone stale
after the first release were corrected — the manifest names the published
build, the repository is not empty any more, and the publication steps are
done.

---

## Phase 0 — Spec + skeleton · **done**

| # | Open | Who | Why it is open |
|---|---|---|---|
| 0.1 | The release keystore | Owner | Generated with a passphrase only he sets (an env var — never printed, never committed). The build falls back to the debug key until it exists. |
| 0.2 | The keystore's backup off this PC | Owner | Two copies, off the machine, plus a password manager. Nothing else can replace it. |
| 0.3 | ~~`APP_DOWNLOAD_URL` reads `https://example.com/soluplay-app`~~ **closed 2026-11-03** | — | The download button on the site points at the live landing page, `https://soluplay-download.vercel.app` (FreeTV `59b2ea3`). The landing page reads the manifest from this repository, so the address never has to change again. |
| 0.4 | ~~The CI release job has never run a release~~ **closed 2026-11-05** | — | The job ran on the tag `v0.2.0`: it signed the phone flavour with the key from the repository secrets, published `SoluPlay.apk` and wrote the manifest into `main`. It ran again on `v0.2.1`. The path is exercised, not assumed. |

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
| 2.8 | ~~`latest.json` names no build until one is published~~ **closed 2026-11-05** | — | The manifest names the newest published build: `versionCode` 3, `versionName` `0.2.1`, with the size and fingerprint the publishing job read from the bytes it published. The number is never typed by hand. |

## Phase 3 — Old-box TLS + resilience · **done**

| # | Open | Who | Why it is open |
|---|---|---|---|
| 3.1 | **The backup base URLs are still empty** (`ApiConfig.BACKUP_BASE_URLS`) | Owner | PLAN §14.3. The machinery tries them in order and is tested; the addresses are his to name. Until then a dead domain is survived by the saved copy and the baked seed, not by a second address. |
| 3.2 | The Android 9 box itself | Owner | The repair is proved against the chains the servers really send; the box has to be asked. What it must show: an aynaott channel playing a picture, with no certificate reason on the panel. |
| 3.3 | The old toolchain scripts still point at the deleted project | Owner | `android-tv-toolchain/e2e/run-oldbox.sh` and `run-tls.sh`. The in-repo harnesses replace them; the stale copies can go. |

## Phase 4 — Updater · **code done, the device half open**

| # | Open | Who | Why it is open |
|---|---|---|---|
| 4.1 | The gate on a device | Owner | The code half of the gate is measured — 290 unit tests per suite, both debug flavours and both release flavours built. *"A new build reaches a device with one tap"* needs a handset and a box, and there is neither attached to this PC. |
| 4.2 | The three FreeTV routes are not deployed | Owner | `/api/app/version`, `/api/app/bootstrap` and `/api/app/favourites` are committed in the local `freetv` repository only — nothing pushed, nothing deployed, the live site untouched. §13 says every addition to `freetv` needs its own review first, so they wait for that review. Until then the app reads the manifest from the GitHub mirror, the guide from the two older routes, and keeps its saved list on the device. |
| 4.3 | ~~The app repository has never been pushed~~ **closed 2026-11-05** | — | `johncarry301/soluplay-app` is public and carries the release: `main` is published and the tag `v0.2.0` is on it, with `SoluPlay.apk` as the release asset. The repository was read end to end before publication — no credential, no signing material, no personal file and no build output is in it, and every path the release job depends on was checked against the build. |
| 4.4 | ~~The release job has run once~~ **closed 2026-11-06** | — | It has run twice. `v0.2.0` published the first build; `v0.2.1` published the phone's new face and wrote a second manifest over the first — a new tag, a new commit, the same key. What is left is 4.5, which no build can answer. |
| 4.5 | A device on `0.2.0` being offered `0.2.1` | Owner | The manifest now names `0.2.1` and an installed `0.2.0` should be offered it, and the two are signed with the same key — the fingerprint the published `v0.2.0` carries is the one the new build carries. That the offer appears, downloads and installs over the old build can only be seen on a device. |
| 4.6 | The install prompt on a real box | Owner | The `PackageInstaller` session, the *install unknown apps* permission, the system's own prompt and the result coming back through the receiver can only be seen on a device. |
| 4.7 | The blocked-build panel on a device | Owner | A retired build and a mandatory update past its grace replace the home screen with a panel instead of showing a sheet. That the panel really keeps the guide from being composed is a claim about the screen, and screens are seen on devices. |

**Closed in this pass.** The manifest in this repository used to name a build
that was never published, with an empty fingerprint and a URL that answered
404 — the download page would have offered a dead download and the app would
have tried to fetch a file that was not there. It now names the published
build — `0.2.0`, with the size and fingerprint the publishing job read from
the bytes it published — so neither number is ever typed by hand, and the
publishing job writes the real ones again for the next release.

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
| — | **Nothing is owed.** The phase is withdrawn | — | The browser ten-foot page is removed: SoluPlay is distributed as the Android phone app and the Android TV app only (PLAN Amendment 16). The code was dropped from the local `freetv` repository — `main` is three commits ahead of `origin/main` (`6021d13`, `17d134c`, `ff585b9`), the tree is byte-identical to the commit before Phase 6, and the work is kept on the local branch `archive/tv-2026-11-05`, unpublished. The two gates of this phase — the old Samsung box (6.3) and the undeployed `/tv` route (6.4) — are void with the phase. |

## Phase 7 — The phone looks like the site · **code done, the device half open**

| # | Open | Who | Why it is open |
|---|---|---|---|
| 7.1 | The new home on a real handset | Owner | The grid, the pills and the tiles are rebuilt to the site's own markup (`PLAN` Amendment 18) and the build and the unit tests are green, but no screen has been seen on a device. What has to be seen: that four columns of 56 dp plates read on a real width, that the names are not all ellipses, and that the pills row behaves under a thumb. |
| 7.2 | The new match card on a real handset | Owner | The card is the site's card — title box, two 4:3 flags, the LIVE badge or the kick-off time, the countdown in the foot bar. Whether a card with a long title holds its height, and whether the flags load from `flagcdn.com` on a mobile connection, is a device question. |
| 7.3 | The sliding second column under a finger | Owner | The board slides every five seconds as the site does, and the app's own gestures are kept — a tap never pauses. Whether a swipe and the slide fight each other is only known on a device. |
| 7.4 | The channel sheet on the watch page | Owner | The site's "tap a channel in the grid to switch" is a sheet on the phone. That it opens above the picture, closes on Back and does not steal the picture's own gestures is a device question. |
| 7.5 | The watch page's own chrome against the picture | Owner | The band, the pills and the two buttons are drawn in the site's colours. Whether they sit clear of the video on a 19.5:9 handset is a device question. |
| 7.6 | The television was not changed | — | The rails on the television are the site's own rails and were left alone; only the phone's home was rebuilt. Nothing here is owed for the TV flavour. |

**Closed in this pass.** The phone's guide was a rail-based list with headings
and counts, and the rails — with `GuideLayout.kt` and its test — are gone. The
seven categories now filter one flat grid in place, the match card is the
site's card, the team-name→flag table is ported into the app so both put the
same flag on the same match, and the watch page wears the site's chrome. The
`freetv` repository was read and not touched.

---

## Things that only the owner can supply

1. **The release keystore passphrase** (Phase 0).
2. **The backup base URLs** (Phase 3, PLAN §14.3).
3. **A device** — the phone and the television (every phase, PLAN §10).
4. **A decision on header-carrying feeds** — include or defer past Phase 2 (PLAN §14.5). Currently deferred.
5. **A backup of the release key off this machine** (Phase 0, 0.2) — the
   passphrase is the owner's alone, and the key itself is the only thing that
   can sign the update that replaces the app people already have.

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

## The first publication — **done 2026-11-05**

This is the order it was done in, kept so the next release can follow it.

1. **Create the release key.**
   `export SOLUPLAY_KEYSTORE_PASSWORD='…'` and then
   `scripts/create-release-keystore.sh`. The passphrase is yours alone: it is
   never written to this repository and never printed.
2. **Back the key up** to two places off this machine and keep the passphrase
   in a password manager. Without both, no update can ever be signed for the
   people who already installed the app. — *still open, 0.2*.
3. **Add the four secrets** to this repository: `KEYSTORE_BASE64` (the base64
   of `keystore/release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`soluplay`)
   and `KEY_PASSWORD`. The job refuses to run without them rather than
   publishing an unsigned or wrongly-signed build.
4. **Push `main`.** The release job needs the branch to exist on the remote
   before it can write the manifest into it.
5. **Watch the `build` job go green** on that push. The release job waits for
   it, so a red build means no release and no manifest.
6. **Push the tag `v0.2.0`.** That runs the release job: it builds the signed
   phone APK, publishes it as the release asset, and writes `latest.json` into
   `main` with the real version number, size and fingerprint.

Steps 1 and 3–6 are done; the release is published and the manifest names it.
Step 2 is the owner's and is still open.

## The second publication — **done 2026-11-06**

The first release published the app as it stood on 2026-11-05. The phone's
home, its match board and its watch page were then rebuilt to the site's own
mobile view (PLAN Amendment 18), and a release has to carry what changed.

1. **The version moved.** `versionCode` 2 → 3 and `versionName` `0.2.0` →
   `0.2.1` in `app/build.gradle.kts`. The application id, the signing key and
   the two flavours were not touched.
2. **The notes were written down.** `release-notes/v0.2.1.md` holds what the
   release says. The publishing job reads that file: the release page shows it
   in full, and the manifest carries its first line, so the line under the
   update button in the app and the page it opens cannot disagree. A release
   with no notes file still falls back to GitHub's generated notes.
3. **The gate ran.** Both debug flavours, both release flavours, lint and the
   unit tests, on the portable toolchain, offline.
4. **The build was checked before it was published.** The signed phone APK was
   verified locally — the certificate is the release certificate and not the
   debug key, and the fingerprint was read from the file itself.
5. **`main` was pushed, and the tag `v0.2.1` with it.** The tag is what runs
   the release job: it signs the phone flavour with the key from the
   repository secrets, publishes `SoluPlay.apk`, and writes `latest.json` into
   `main` with the real version number, size and fingerprint.
6. **The published file was checked afterwards** — the signature, the sha256,
   the version in the manifest and the download URL.

Step 2 of the first list — the backup of the release key off this machine — is
still the owner's and is still open. Nothing else about the release is.

## The first attempt

The first run of the release job failed at "Restore the release key", and the
fault was in the workflow rather than in the secrets: `base64 --decode` reads
its argument as a *file name*, so the job asked for a file whose name was the
whole key. The key is decoded from standard input now, and a decode that
produces nothing is refused before the signer sees it.

The error printed that argument back, and the argument is the base64 of the
release key — but the log GitHub serves shows it as `***`, because a registered
repository secret is masked wherever it appears in log output. The key was never
published, and the mask is also proof that the pasted secret was byte-exact.

The passphrase stays as it is: it is the owner's to choose, it is never written
here, and nothing has been published that could weaken it.

The tag `v0.2.0` was pushed once and the job failed on it, so the tag is moved
to the commit that carries the fix rather than left pointing at the broken one.

## The rule behind the list

- A phase is **not** done until its gate is measured, and a gate that needs a
  device stays open until a device measures it.
- Nothing is pushed, released or deployed without the owner's word.
- This file and `PLAN.md` are kept together: the plan says what each phase is,
  this says what is still owed on it.
