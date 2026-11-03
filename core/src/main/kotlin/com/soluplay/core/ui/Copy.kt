package com.soluplay.core.ui

import androidx.compose.runtime.Composable

/** The word in the language the app is currently speaking. */
@Composable
fun text(bengali: String, english: String): String =
    when (LocalUiLanguage.current) {
        UiLanguage.BENGALI -> bengali
        UiLanguage.ENGLISH -> english
    }

/**
 * The words the chrome of both surfaces uses.
 *
 * Channel names and category names are the server's own and are never
 * translated here — only the furniture around them is.
 */
object Copy {

    @Composable fun live() = text("লাইভ", "Live")

    @Composable fun savedCopy() = text("সংরক্ষিত তালিকা", "Saved list")

    @Composable fun builtInCopy() = text("অ্যাপের সাথে আসা তালিকা", "List built into the app")

    @Composable fun noCopy() = text("কোনো তালিকা নেই", "No list available")

    @Composable fun refresh() = text("রিফ্রেশ", "Refresh")

    @Composable fun refreshing() = text("রিফ্রেশ হচ্ছে", "Refreshing")

    @Composable fun siteDown() = text(
        "সাইটে পৌঁছানো যাচ্ছে না।",
        "The site cannot be reached.",
    )

    @Composable fun siteDownNoCopy() = text(
        "সাইটে পৌঁছানো যাচ্ছে না এবং এই ডিভাইসে কোনো সংরক্ষিত তালিকা নেই।",
        "The site cannot be reached and this device has no saved list yet.",
    )

    @Composable fun siteGaveNothing() = text(
        "সাইটে পৌঁছানো গেছে, কিন্তু কোনো তালিকা দেওয়া হয়নি।",
        "The site answered but could not give a list.",
    )

    @Composable fun siteGaveNothingNoCopy() = text(
        "সাইটে পৌঁছানো গেছে, কিন্তু কোনো তালিকা দেওয়া হয়নি এবং এই ডিভাইসে কোনো সংরক্ষিত তালিকা নেই।",
        "The site answered but could not give a list, and this device has no saved list yet.",
    )

    @Composable fun todaysMatches() = text("আজকের ম্যাচ", "Today's matches")

    @Composable fun channels() = text("চ্যানেল", "Channels")

    @Composable fun mirrors(count: Int) = text("${count}টি মিরর", "$count mirrors")

    @Composable fun noMirror() = text("এখন কোনো মিরর নেই", "No mirror available")

    @Composable fun mirrorsUnknown() = text(
        "মিররের তালিকা সাইট থেকে আসে",
        "The mirror list comes from the site",
    )

    @Composable fun playbackLater() = text("প্লেব্যাক আসছে Phase 2-এ", "Playback arrives in Phase 2")

    @Composable fun close() = text("বন্ধ", "Close")
}
