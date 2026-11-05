package com.soluplay.app.player

/**
 * The page that carries YouTube's own player.
 *
 * The player is YouTube's own embedded player and it is driven by YouTube's
 * own player API. The API is not decoration: the embedded player only talks
 * to the page once the page has spoken to it first, and the API is what does
 * that speaking. A page that puts the player up without it has a video
 * playing and no word about it — the app is told nothing, keeps the mirror in
 * "tuning", and gives it up at its deadline while the picture is on the
 * screen. That is exactly what a phone showed: the video moving, the words
 * "tuning", and then the picture taken away.
 *
 * So the API is loaded and used, the way the site uses it, and the player is
 * made by it — with the video already in it, so that the picture starts on
 * its own. If the script never arrives, the page puts the embedded player up
 * by itself and says the same words to it, so that a slow network is not the
 * same as a player that cannot be reached at all.
 *
 * The player's state is read as well as heard. An event that is missed must
 * not leave the app waiting for a picture that is already on the screen, so
 * the state is asked for every half second and a change is reported even if
 * the change itself was never announced.
 *
 * The player is made once, for one video, and is never taken down and built
 * again while that video is being watched: a player that is rebuilt is a
 * player that starts again, and a loop of that never plays anything.
 *
 * The page is told which origin it is embedded from, because the embedded
 * player only posts its messages to the origin it was told about.
 */
object YouTubePlayerPage {

    /**
     * The page, for an embedding origin.
     */
    fun html(origin: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}
#mount{position:absolute;top:0;left:0;width:100%;height:100%}
#player{position:absolute;top:0;left:0;width:100%;height:100%;border:0}
iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}
</style>
</head>
<body>
<div id="mount"></div>
<script>
var ORIGIN = ${origin.asJavaScriptString()};
var apiReady = false;
var apiFailed = false;
var apiWaitOver = false;
var player = null;
var currentId = null;
var mounted = false;
var lastState = -1;
var playCalls = 0;
var nudges = 0;
var held = false;

/* The app is told what the player does. */
function report(name, value) {
  if (typeof AndroidYouTube === 'undefined') return;
  try { AndroidYouTube[name](value); } catch (err) { }
}

function reportReady() {
  if (typeof AndroidYouTube === 'undefined') return;
  try { AndroidYouTube.onReady(); } catch (err) { }
}

function reportState(state) {
  if (state === lastState) return;
  lastState = state;
  if (state === 1) { playCalls = 0; }
  report('onState', state);
}

/* The player's own state is asked for, not only waited for. */
function pollState() {
  if (player && player.getPlayerState) {
    try {
      var state = player.getPlayerState();
      reportState(state);
      /* A player that came up paused is told to play, again and again for a
         while: a paused player has no picture at all. A player the viewer is
         holding is left alone — the app asked for that, and the page must not
         argue with it. */
      if (!held && (state === 2 || state === 5) && playCalls < 20) {
        playCalls += 1;
        try { player.playVideo(); } catch (err) { }
      }
    } catch (err) { }
  }
  setTimeout(pollState, 500);
}

/* The player's own API is loaded, and the player is made when it is there. */
var tag = document.createElement('script');
tag.src = 'https://www.youtube.com/iframe_api';
tag.onerror = function () {
  apiFailed = true;
  if (currentId && !mounted) { embed(currentId); }
};
document.body.appendChild(tag);

function onYouTubeIframeAPIReady() {
  apiReady = true;
  if (currentId && !mounted) { create(currentId); }
}

/* The API is given a while to arrive. If it has not, the player is put up by
   hand — a slow script is not the same as no player at all. */
window.setTimeout(function () {
  apiWaitOver = true;
  if (!apiReady && currentId && !mounted) { embed(currentId); }
}, 6000);

/* The player, made by the player's own API. */
function create(videoId) {
  var mount = document.getElementById('mount');
  mount.innerHTML = '';
  var host = document.createElement('div');
  host.id = 'player';
  mount.appendChild(host);

  player = new YT.Player('player', {
    videoId: videoId,
    playerVars: {
      autoplay: 1,
      playsinline: 1,
      rel: 0,
      controls: 0,
      iv_load_policy: 3,
      modestbranding: 1,
      origin: ORIGIN
    },
    events: {
      onReady: function (e) {
        reportReady();
        try { e.target.playVideo(); } catch (err) { }
      },
      onStateChange: function (e) { reportState(e.data); },
      onError: function (e) { report('onError', e.data); }
    }
  });
  mounted = true;
}

/* The player, put up by hand, for when the API does not arrive.
   The words below are the ones the API says to the player, and they are what
   makes the player answer at all. */
function embed(videoId) {
  var mount = document.getElementById('mount');
  mount.innerHTML = '';
  var frame = document.createElement('iframe');
  frame.id = 'youtube-frame';
  frame.setAttribute('allow', 'autoplay; encrypted-media; picture-in-picture');
  frame.setAttribute('allowfullscreen', 'true');
  frame.src = 'https://www.youtube.com/embed/' + encodeURIComponent(videoId) +
    '?autoplay=1&playsinline=1&enablejsapi=1&controls=0&rel=0&modestbranding=1' +
    '&iv_load_policy=3&origin=' + encodeURIComponent(ORIGIN);
  mount.appendChild(frame);
  mounted = true;
  keepTalking();
}

/* The player is spoken to, again and again, for a while: the words that start
   it listening, and the word that starts it playing. A player that was not
   listening yet did not hear them the first time. */
function keepTalking() {
  if (nudges >= 20) return;
  nudges += 1;
  var frame = document.getElementById('youtube-frame');
  if (frame && frame.contentWindow) {
    try {
      frame.contentWindow.postMessage(JSON.stringify({event: 'listening', id: 1, channel: 'widget'}), '*');
      frame.contentWindow.postMessage(JSON.stringify({event: 'command', func: 'playVideo', args: ''}), '*');
    } catch (err) { }
  }
  setTimeout(keepTalking, 500);
}

window.addEventListener('message', function (event) {
  if (event.origin !== 'https://www.youtube.com' && event.origin !== 'https://www.youtube-nocookie.com') return;
  var data = event.data;
  if (typeof data === 'string') { try { data = JSON.parse(data); } catch (err) { return; } }
  if (!data || !data.event) return;
  if (data.event === 'onReady') { reportReady(); }
  else if (data.event === 'onStateChange') { reportState(data.info); }
  else if (data.event === 'onError') { report('onError', data.info); }
});

function start(videoId) {
  if (!videoId) return;
  if (currentId === videoId && mounted) { nudge(); return; }
  currentId = videoId;
  mounted = false;
  lastState = -1;
  playCalls = 0;
  nudges = 0;
  held = false;
  if (apiReady) { create(videoId); }
  else if (apiFailed || apiWaitOver) { embed(videoId); }
  /* Otherwise the API is still on its way, and the player is made when it
     arrives — so the picture is never started twice. */
}

/* The viewer is holding the feed. The picture stays, and the page stops
   telling the player to play. */
function hold() {
  held = true;
  if (player && player.pauseVideo) {
    try { player.pauseVideo(); } catch (err) { }
  }
}

/* The viewer let the feed run again. */
function release() {
  held = false;
  nudge();
}

/* The player is told to play. */
function nudge() {
  if (player && player.playVideo) {
    try { player.playVideo(); } catch (err) { }
    return;
  }
  var frame = document.getElementById('youtube-frame');
  if (!frame || !frame.contentWindow) return;
  nudges += 1;
  try {
    frame.contentWindow.postMessage(JSON.stringify({event: 'command', func: 'playVideo', args: ''}), '*');
  } catch (err) { }
}

pollState();
</script>
</body>
</html>
"""
}
