package com.soluplay.core.fixtures

import java.text.Normalizer
import java.util.Locale

/**
 * Team recognition for the match board.
 *
 * A card names two teams, and the flag a viewer recognises them by should not
 * be something an admin has to find, crop and upload. This is the pure half
 * of that: it turns a name or a common abbreviation into a country code, and
 * it turns a title like "BAN vs IND" into the two teams the title stands for.
 * Nothing here touches the network or the database.
 *
 * A name that is not recognised is not an error: the card falls back to the
 * team's initials. Guessing a country for a club or a domestic side would put
 * the wrong flag on a real match, so the rule is strict — a name names a
 * national team, or it names nothing.
 *
 * This is the same table the site draws its flags from, so the app and the
 * website put the same flag on the same match.
 */
object TeamFlags {

    /** Where a country flag is drawn from. The card falls back to initials if it cannot load. */
    private const val FLAG_BASE = "https://flagcdn.com/w80"

    /**
     * Every name and abbreviation this panel can place a flag on.
     *
     * The key is the normalised name (lower case, no punctuation) and the value
     * is the country code the flag is drawn from. Codes are the ones the flag
     * service uses, which are ISO 3166-1 alpha-2 for countries and the four
     * Home Nations' own codes for the United Kingdom's parts — a card that says
     * "England" must not be given the Union flag.
     *
     * The cricket abbreviations come first because a card is usually a cricket
     * card, and `SA` means South Africa in that context while `KSA` names Saudi
     * Arabia. Two letters that could plausibly mean two different countries are
     * simply left out.
     */
    private val TEAM_ALIASES: Map<String, String> = mapOf(
        // --- cricket nations ---
        "bangladesh" to "bd",
        "india" to "in",
        "pakistan" to "pk",
        "england" to "gb-eng",
        "australia" to "au",
        "new zealand" to "nz",
        "south africa" to "za",
        "sri lanka" to "lk",
        "west indies" to "wi",
        "zimbabwe" to "zw",
        "afghanistan" to "af",
        "ireland" to "ie",
        "netherlands" to "nl",
        "scotland" to "gb-sct",
        "wales" to "gb-wls",
        "nepal" to "np",
        "oman" to "om",
        "qatar" to "qa",
        "bahrain" to "bh",
        "kuwait" to "kw",
        "saudi arabia" to "sa",
        "united arab emirates" to "ae",
        "namibia" to "na",
        "kenya" to "ke",
        "nigeria" to "ng",
        "tanzania" to "tz",
        "uganda" to "ug",
        "rwanda" to "rw",
        "singapore" to "sg",
        "malaysia" to "my",
        "hong kong" to "hk",
        "united states" to "us",
        "united states of america" to "us",
        "republic of ireland" to "ie",

        // --- football nations ---
        "algeria" to "dz",
        "angola" to "ao",
        "argentina" to "ar",
        "austria" to "at",
        "belgium" to "be",
        "bolivia" to "bo",
        "brazil" to "br",
        "bulgaria" to "bg",
        "cambodia" to "kh",
        "cameroon" to "cm",
        "canada" to "ca",
        "chile" to "cl",
        "china" to "cn",
        "colombia" to "co",
        "costa rica" to "cr",
        "cote divoire" to "ci",
        "ivory coast" to "ci",
        "croatia" to "hr",
        "czechia" to "cz",
        "czech republic" to "cz",
        "denmark" to "dk",
        "ecuador" to "ec",
        "egypt" to "eg",
        "el salvador" to "sv",
        "estonia" to "ee",
        "finland" to "fi",
        "france" to "fr",
        "germany" to "de",
        "greece" to "gr",
        "guatemala" to "gt",
        "honduras" to "hn",
        "hungary" to "hu",
        "iceland" to "is",
        "indonesia" to "id",
        "iran" to "ir",
        "iraq" to "iq",
        "israel" to "il",
        "italy" to "it",
        "jamaica" to "jm",
        "japan" to "jp",
        "korea republic" to "kr",
        "south korea" to "kr",
        "laos" to "la",
        "latvia" to "lv",
        "lithuania" to "lt",
        "luxembourg" to "lu",
        "mali" to "ml",
        "mexico" to "mx",
        "mongolia" to "mn",
        "morocco" to "ma",
        "myanmar" to "mm",
        "north macedonia" to "mk",
        "northern ireland" to "gb-nir",
        "norway" to "no",
        "panama" to "pa",
        "paraguay" to "py",
        "peru" to "pe",
        "poland" to "pl",
        "portugal" to "pt",
        "romania" to "ro",
        "russia" to "ru",
        "salvador" to "sv",
        "saudi" to "sa",
        "senegal" to "sn",
        "serbia" to "rs",
        "slovakia" to "sk",
        "slovenia" to "si",
        "spain" to "es",
        "sweden" to "se",
        "switzerland" to "ch",
        "taiwan" to "tw",
        "thailand" to "th",
        "tunisia" to "tn",
        "turkey" to "tr",
        "turkmenistan" to "tm",
        "ukraine" to "ua",
        "uruguay" to "uy",
        "venezuela" to "ve",
        "vietnam" to "vn",
        "zambia" to "zm",

        // --- cricket and football abbreviations ---
        "ban" to "bd",
        "bgd" to "bd",
        "ind" to "in",
        "pak" to "pk",
        "eng" to "gb-eng",
        "aus" to "au",
        "nz" to "nz",
        "nzl" to "nz",
        "sa" to "za",
        "rsa" to "za",
        "sl" to "lk",
        "sri" to "lk",
        "wi" to "wi",
        "win" to "wi",
        "zim" to "zw",
        "afg" to "af",
        "ire" to "ie",
        "ned" to "nl",
        "sco" to "gb-sct",
        "wal" to "gb-wls",
        "npl" to "np",
        "oma" to "om",
        "qat" to "qa",
        "brn" to "bh",
        "kuw" to "kw",
        "ksa" to "sa",
        "are" to "ae",
        "uae" to "ae",
        "nam" to "na",
        "sin" to "sg",
        "mas" to "my",
        "hkg" to "hk",
        "can" to "ca",
        "usa" to "us",
        "gbr" to "gb-eng",

        // --- football abbreviations ---
        "alg" to "dz",
        "ang" to "ao",
        "arg" to "ar",
        "aut" to "at",
        "bel" to "be",
        "bol" to "bo",
        "bra" to "br",
        "bul" to "bg",
        "cam" to "kh",
        "cmr" to "cm",
        "chi" to "cl",
        "chn" to "cn",
        "col" to "co",
        "crc" to "cr",
        "civ" to "ci",
        "cro" to "hr",
        "cze" to "cz",
        "den" to "dk",
        "ecu" to "ec",
        "egy" to "eg",
        "fin" to "fi",
        "fra" to "fr",
        "ger" to "de",
        "gre" to "gr",
        "hon" to "hn",
        "hun" to "hu",
        "ice" to "is",
        "idn" to "id",
        "irn" to "ir",
        "irq" to "iq",
        "isr" to "il",
        "ita" to "it",
        "jam" to "jm",
        "jpn" to "jp",
        "kor" to "kr",
        "lao" to "la",
        "mar" to "ma",
        "mex" to "mx",
        "myn" to "mm",
        "nor" to "no",
        "pan" to "pa",
        "par" to "py",
        "per" to "pe",
        "pol" to "pl",
        "por" to "pt",
        "rom" to "ro",
        "rus" to "ru",
        "sen" to "sn",
        "srb" to "rs",
        "slo" to "sk",
        "esp" to "es",
        "swe" to "se",
        "sui" to "ch",
        "swi" to "ch",
        "thai" to "th",
        "tun" to "tn",
        "tur" to "tr",
        "ukr" to "ua",
        "ury" to "uy",
        "ven" to "ve",
        "vie" to "vn",
        "zam" to "zm",
    )

    /**
     * Words that qualify a team rather than name it. "Bangladesh Women" and
     * "Bangladesh" are the same country and get the same flag, so the
     * qualifier is dropped before the name is looked up.
     */
    private val QUALIFIERS: Set<String> = setOf(
        "women", "woman", "womens", "w", "men", "man", "mens", "m", "squad",
        "u19", "u20", "u21", "u22", "u23", "u16", "u17", "u18",
        "test", "odi", "t20", "t20i", "first", "second", "third", "xi", "a", "b",
    )

    private val marks = Regex("[\\u0300-\\u036f]")
    private val punctuation = Regex("[.'’_]")
    private val notAllowed = Regex("[^a-z0-9\\s-]")
    private val spaces = Regex("\\s+")

    /** The separator a title's two teams are divided by. */
    private val separator = Regex("\\s+(?:vs\\.?|v\\.?|x)\\s+|\\s*[-–—]\\s*", RegexOption.IGNORE_CASE)

    /**
     * A name as this module looks it up: lower case, without accents, without
     * punctuation, and with its spaces kept — "South Africa" and "South-Africa"
     * are one name, and "SouthAfrica" is not.
     */
    fun normalizeTeamName(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFKD)
        return decomposed
            .replace(marks, "")
            .lowercase(Locale.US)
            .replace(punctuation, "")
            .replace(notAllowed, " ")
            .replace('-', ' ')
            .replace(spaces, " ")
            .trim()
    }

    /**
     * The country a team name stands for, or null when the name is not one this
     * module knows.
     *
     * The lookup is tried in three steps, from the most specific to the least:
     * the whole name, the name without its qualifiers, and then the first word
     * of what is left — which is how "England Lions" still finds England and
     * "New Zealand" is found whole rather than by its first word.
     */
    fun resolveTeamCountry(raw: String): String? {
        val name = normalizeTeamName(raw)
        if (name.isEmpty()) return null

        TEAM_ALIASES[name]?.let { return it }

        val words = name.split(" ").filter { it.isNotEmpty() && it !in QUALIFIERS }
        if (words.isEmpty()) return null

        TEAM_ALIASES[words.joinToString(" ")]?.let { return it }

        // A single leading word is only tried when the whole thing did not
        // match, so a name that is itself an alias is never shortened into a
        // different country.
        if (words.size > 1) {
            TEAM_ALIASES[words[0]]?.let { return it }
        }
        return null
    }

    /**
     * The flag to draw for a team, or null when the team is not recognised.
     *
     * The URL is only a suggestion: the card asks the image whether it loaded
     * and falls back to the team's initials when it did not, so a flag service
     * that is slow or down costs the card a picture and nothing else.
     */
    fun teamFlagUrl(raw: String): String? {
        val code = resolveTeamCountry(raw) ?: return null
        return "$FLAG_BASE/$code.png"
    }

    /**
     * The two teams a title stands for, or null when the title does not name
     * two.
     *
     * A card's title is what an admin types first — "Bangladesh vs India",
     * "Brazil vs Argentina", "BAN vs IND" — so the split is deliberately
     * forgiving about the separator and strict about the result: both halves
     * have to be there, or the title is left alone.
     */
    fun parseMatchTitle(raw: String): Pair<String, String>? {
        val title = raw.trim()
        if (title.isEmpty()) return null

        val separated = separator.split(title)
        if (separated.size != 2) return null

        val teamA = separated[0].trim()
        val teamB = separated[1].trim()
        if (teamA.isEmpty() || teamB.isEmpty()) return null
        return teamA to teamB
    }
}
