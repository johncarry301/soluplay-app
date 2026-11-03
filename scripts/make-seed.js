// Bakes the public catalogue into the APK's assets, so a build carries a copy of the
// guide from the day it was packaged.
//
//   node scripts/make-seed.js [outDir]
//
// The catalogue is the site's own public list and nothing else. The mirror ladder is
// deliberately left out: a baked copy of a channel's upstream URL is a stale credential
// in the APK and in the repository, and the app never plays from the seed — playback
// asks the live API for the ladder it publishes. What is left is the guide a viewer can
// browse when the site cannot be reached: names, logos, categories and the order.
const fs = require("fs");
const path = require("path");

const SERVER = process.env.BASE_URL || "https://soluplay.vercel.app";
const OUT = process.argv[2] || path.join(__dirname, "..", "app", "src", "main", "assets");

/** The endpoint the guide comes from. The match board is never baked: it is time-bound. */
const ENDPOINT = "/api/channels";

/** The fields of a channel that carry a stream. None of them belong in a baked copy. */
const STREAM_FIELDS = ["primaryStream", "activeStreamCount"];

/**
 * A baked catalogue must not contain any of these anywhere. The check is the last line
 * of defence against a credential finding its way into the APK.
 */
const FORBIDDEN = [/m3u8/i, /token=/i, /nimblesessionid/i];

/**
 * The only absolute address a baked channel may keep is its logo: an image the viewer
 * sees, served by whoever already serves it to the website. Every other field must be
 * free of addresses, so no upstream line can hide in one.
 */
const ABSOLUTE = /https?:\/\//i;

function stripStreams(channels) {
  return channels.map((channel) => {
    const copy = { ...channel };
    for (const field of STREAM_FIELDS) delete copy[field];
    return copy;
  });
}

function assertClean(text, channels) {
  for (const pattern of FORBIDDEN) {
    const found = pattern.exec(text);
    if (found) throw new Error(`the baked catalogue still carries "${found[0]}"`);
  }
  for (const channel of channels) {
    for (const [field, value] of Object.entries(channel)) {
      if (field === "logo") continue;
      if (typeof value === "string" && ABSOLUTE.test(value)) {
        throw new Error(`the baked catalogue still carries an address in "${field}"`);
      }
    }
  }
}

(async () => {
  const response = await fetch(SERVER + ENDPOINT, {
    headers: { accept: "application/json", "user-agent": "SoluPlay-Seed/1.0" },
  });
  if (!response.ok) throw new Error(`${ENDPOINT} answered HTTP ${response.status}`);

  const payload = await response.json();
  if (!Array.isArray(payload.channels)) throw new Error(`${ENDPOINT} carried no channel array`);

  const channels = stripStreams(payload.channels);
  const envelope = JSON.stringify({ cachedAt: Date.now(), payload: { ...payload, channels } });
  assertClean(envelope, channels);

  fs.mkdirSync(OUT, { recursive: true });
  const file = path.join(OUT, "catalogue-channels.json");
  fs.writeFileSync(file, envelope, "utf8");

  const rails = new Set(channels.map((c) => c.category || "(none)"));
  console.log(`${file}`);
  console.log(`  ${channels.length} channels, ${rails.size} rails, ${Math.round(envelope.length / 1024)} KB`);
  console.log(`  no stream ladder was baked: ${STREAM_FIELDS.join(", ")} removed`);
})().catch((e) => {
  console.error("FAIL " + e.message);
  process.exit(1);
});
