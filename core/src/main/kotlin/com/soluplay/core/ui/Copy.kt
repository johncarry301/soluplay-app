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

    @Composable fun loadingLadder() = text(
        "চ্যানেলের মিরর তালিকা আসছে…",
        "The channel's mirror list is coming…",
    )

    @Composable fun tuning(mirror: Int, of: Int) = text(
        "মিরর $mirror টিউন হচ্ছে ($of টির মধ্যে)…",
        "Tuning mirror $mirror of $of…",
    )

    @Composable fun racing(count: Int) = text(
        "একসাথে $count টি মিরর চেষ্টা হচ্ছে…",
        "Trying $count mirrors at once…",
    )

    @Composable fun recovering() = text("ছবি ফিরে আসছে…", "Waiting for the picture…")

    @Composable fun exhausted() = text(
        "কোনো মিরর থেকে ছবি আসছে না।",
        "No mirror is sending a picture.",
    )

    @Composable fun ladderUnreadable() = text(
        "চ্যানেলের তথ্য আনা যায়নি।",
        "The channel's details could not be read.",
    )

    @Composable fun back() = text("ফিরে যান", "Back")

    @Composable fun chooseChannel() = text("চ্যানেল বেছে নিন", "Choose a channel")

    @Composable fun nothingToOpen() = text(
        "এই ম্যাচটি এখান থেকে খোলা যাচ্ছে না।",
        "This match cannot be opened from here.",
    )

    @Composable fun close() = text("বন্ধ", "Close")

    @Composable fun newVersion(name: String) = text("নতুন সংস্করণ $name", "New version $name")

    @Composable fun updateRequired() = text(
        "নতুন সংস্করণ ইনস্টল করা জরুরি",
        "A new version has to be installed",
    )

    @Composable fun updateRequiredBody() = text(
        "এই সংস্করণ আর খেলতে পারবে না। নতুন সংস্করণটি ইনস্টল করুন।",
        "This version can no longer play. Install the new one.",
    )

    @Composable fun updateMandatoryBody() = text(
        "এই আপডেটটি এখন আবশ্যক। নতুন সংস্করণটি ইনস্টল করুন।",
        "This update is now required. Install the new version.",
    )

    @Composable fun updateAvailableBody() = text(
        "নতুন সংস্করণটি ইনস্টল করা যাচ্ছে।",
        "A new version can be installed.",
    )

    @Composable fun installNow() = text("এখনই ইনস্টল করুন", "Install now")

    @Composable fun allowInstall() = text("ইনস্টলের অনুমতি দিন", "Allow installation")

    @Composable fun later() = text("পরে", "Later")

    @Composable fun downloadingUpdate() = text("ডাউনলোড হচ্ছে…", "Downloading…")

    @Composable fun installingUpdate() = text("ইনস্টল হচ্ছে…", "Installing…")

    @Composable fun readyToInstall() = text("ইনস্টলের জন্য প্রস্তুত", "Ready to install")

    @Composable fun updateFailed() = text(
        "আপডেটটি নামানো যায়নি।",
        "The update could not be downloaded.",
    )

    @Composable fun updatePermissionNeeded() = text(
        "এই ডিভাইসে অ্যাপ ইনস্টল করার অনুমতি দিতে হবে।",
        "This device has to allow the app to install updates.",
    )

    @Composable fun tryAgain() = text("আবার চেষ্টা করুন", "Try again")

    @Composable fun home() = text("হোম", "Home")

    @Composable fun matchesTab() = text("ম্যাচ", "Matches")

    @Composable fun searchTab() = text("খুঁজুন", "Search")

    @Composable fun savedTab() = text("পছন্দ", "Saved")

    @Composable fun liveNow() = text("এখন খেলা হচ্ছে", "Live now")

    @Composable fun comingUp() = text("আসছে", "Coming up")

    @Composable fun over() = text("শেষ হয়ে গেছে", "Finished")

    @Composable fun noMatches() = text(
        "এখন বোর্ডে কোনো ম্যাচ নেই।",
        "There is no match on the board.",
    )

    @Composable fun searchHint() = text("চ্যানেল খুঁজুন", "Search the channels")

    @Composable fun nothingFound() = text(
        "এই নামে কোনো চ্যানেল নেই।",
        "No channel carries that name.",
    )

    @Composable fun favouritesTitle() = text("পছন্দের তালিকা", "Your saved list")

    @Composable fun yourCode() = text("আপনার কোড", "Your code")

    @Composable fun codeOfAnotherDevice() = text(
        "অন্য ডিভাইসের কোড",
        "The code of another device",
    )

    @Composable fun useThisCode() = text("এই কোড নিন", "Use this code")

    @Composable fun codeNotValid() = text(
        "এই কোডটি ঠিক নয়।",
        "That is not a code the app wrote.",
    )

    @Composable fun takeFromServer() = text("সার্ভার থেকে নিন", "Take from the site")

    @Composable fun giveToServer() = text("সার্ভারে দিন", "Give to the site")

    @Composable fun syncedNow() = text("সার্ভারের সাথে মিলেছে", "The site has the same list")

    @Composable fun syncFailed() = text(
        "সার্ভারের সাথে যোগাযোগ হয়নি। তালিকা এই ডিভাইসে আছে।",
        "The site could not be reached. The list is on this device.",
    )

    @Composable fun noFavourites() = text(
        "এখনো কোনো চ্যানেল পছন্দ করা হয়নি।",
        "No channel has been saved yet.",
    )

    @Composable fun saveChannel() = text("পছন্দে রাখুন", "Save this channel")

    @Composable fun savedChannel() = text("পছন্দে আছে", "Saved")

    @Composable fun removeChannel() = text("সরান", "Remove")

    @Composable fun nextMirror() = text("অন্য মিরর", "Another mirror")

    @Composable fun heldFeed() = text("ফিড থামানো হয়েছে", "The feed is held")

    @Composable fun holdFeed() = text("ফিড থামান", "Hold the feed")

    @Composable fun resumeFeed() = text("ফিড চালু করুন", "Resume the feed")

    @Composable fun englishToggle() = text("EN", "EN")

    @Composable fun bengaliToggle() = text("বাং", "বাং")

    @Composable fun preview() = text("প্রিভিউ", "Preview")

    @Composable fun spaceKey() = text("স্পেস", "Space")

    @Composable fun deleteKey() = text("মুছুন", "Delete")

    @Composable fun clearKey() = text("মুছে ফেলুন", "Clear")

    @Composable fun typeHere() = text("অক্ষর লিখুন", "Type here")

    @Composable fun sportsSection() = text("24/7 স্পোর্টস", "24/7 Sports")

    @Composable fun allChannels() = text("সব", "All")

    @Composable fun watchLive() = text(
        "এখানে ক্লিক করে সরাসরি দেখুন",
        "Click here to watch live",
    )

    @Composable fun liveNowBadge() = text("এখন খেলা হচ্ছে", "LIVE NOW")

    @Composable fun startsAt() = text("শুরু হবে", "Starts at")

    @Composable fun noChannelsOnline() = text(
        "এখন কোনো চ্যানেল নেই।",
        "There are no active streams right now.",
    )
}
