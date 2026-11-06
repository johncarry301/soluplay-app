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
)

/** The seven, and only seven, the catalogue holds. */
val CATEGORY_FILTERS: List<CategoryFilter> = listOf(
    CategoryFilter("sports", "Sports", "Sports"),
    CategoryFilter("bangla", "Bangla", "Bangla"),
    CategoryFilter("indian", "Indian", "Indian"),
    CategoryFilter("pakistani", "Pakistani", "Pakistani"),
    CategoryFilter("documentary", "Documentary", "Documentary"),
    CategoryFilter("kids", "Kids", "Kids"),
    CategoryFilter("global", "Global", "Global"),
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
