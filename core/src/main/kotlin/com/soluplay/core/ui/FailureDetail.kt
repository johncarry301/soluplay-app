package com.soluplay.core.ui

import com.soluplay.core.player.PlaybackDetails

/**
 * A sentence in both of the app's languages.
 */
data class BilingualText(val bengali: String, val english: String)

/**
 * The player's own reason for the last failure, in words.
 *
 * The reason a player gives is a short code — the number YouTube refused a
 * video with, for instance — and a code is not something a viewer can act on.
 * So the codes the app knows are put into words here, and a code it does not
 * know is shown as it is, because naming it is better than hiding it.
 */
fun failureDetail(detail: String): BilingualText = when {
    detail.isBlank() -> BilingualText("", "")

    detail == PlaybackDetails.YOUTUBE_NO_PICTURE -> BilingualText(
        "YouTube-এর প্লেয়ার চালু হয়নি।",
        "The YouTube player did not start.",
    )

    detail == PlaybackDetails.YOUTUBE_RESOLVE -> BilingualText(
        "চ্যানেলের চলমান ভিডিওটি খুঁজে পাওয়া যায়নি।",
        "The channel's current video could not be found.",
    )

    detail == PlaybackDetails.YOUTUBE_PAGE -> BilingualText(
        "YouTube-এর পাতা আসেনি।",
        "The YouTube page could not be loaded.",
    )

    detail == PlaybackDetails.TLS_ISSUER -> BilingualText(
        "এই মিররের সার্টিফিকেটের চেইনটি এই ডিভাইসের পরিচিত নয়।",
        "This mirror's certificate chain is not known to this device.",
    )

    detail == PlaybackDetails.TLS_EXPIRED -> BilingualText(
        "এই মিররের সার্টিফিকেটের মেয়াদ শেষ হয়ে গেছে।",
        "This mirror's certificate has expired.",
    )

    detail == PlaybackDetails.TLS_CLOCK -> BilingualText(
        "এই ডিভাইসের দিন-সময় সার্টিফিকেটের সঙ্গে মিলছে না।",
        "This device's clock does not match the certificate.",
    )

    detail == PlaybackDetails.TLS_ADDRESS -> BilingualText(
        "এই মিররের সার্টিফিকেটটি অন্য ঠিকানার জন্য।",
        "This mirror's certificate is for another address.",
    )

    PlaybackDetails.isYouTubeError(detail) -> {
        val code = PlaybackDetails.youTubeErrorCode(detail)
        BilingualText(
            "YouTube এই ভিডিওটি বাজাতে দেনি ($code)।",
            "YouTube would not play this video (error $code).",
        )
    }

    else -> BilingualText(detail, detail)
}
