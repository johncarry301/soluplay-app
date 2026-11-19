package com.soluplay.core.browse

import com.soluplay.core.model.Channel

/**
 * The catalogue's seven categories, in the order the site shows them.
 *
 * The site names them once and every surface reads that list, so the app
 * reads the same seven in the same order: a filter that sits in a different
 * place on the phone than on the site is a filter a viewer has to look for.
 */
data class CategoryFilter(
    val slug: String,
    val name: String,
    /** The exact `Channel.category` value this filter lists. */
    val category: String,
    /** The rail's heading on the site's watch page, e.g. "Sports TV". */
    val title: String = name,
)

/** The seven, and only seven, the catalogue holds. */
val CATEGORY_FILTERS: List<CategoryFilter> = listOf(
    CategoryFilter("sports", "Sports", "Sports", "Sports TV"),
    CategoryFilter("bangla", "Bangla", "Bangla", "Bangla TV"),
    CategoryFilter("indian", "Indian", "Indian", "Indian TV"),
    CategoryFilter("pakistani", "Pakistani", "Pakistani", "Pakistani TV"),
    CategoryFilter("documentary", "Documentary", "Documentary", "Documentary TV"),
    CategoryFilter("kids", "Kids", "Kids", "Kids TV"),
    CategoryFilter("global", "Global", "Global", "Global TV"),
)

/**
 * The channels one filter shows, in the order the server published them.
 *
 * A channel sits on exactly one filter — the one whose category equals its
 * own — so the filters never show the same channel twice. A null filter is
 * "all", which is every channel the server sent.
 */
fun channelsInFilter(channels: List<Channel>, filter: CategoryFilter?): List<Channel> =
    if (filter == null) channels else channels.filter { it.category == filter.category }
