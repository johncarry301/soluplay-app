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
}
