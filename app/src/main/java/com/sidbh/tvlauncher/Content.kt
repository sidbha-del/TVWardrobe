package com.sidbh.tvlauncher

enum class CardKind(val label: String) {
    Trailer("Trailer"),
    Sport("Sports"),
}

/** One tile in the Discover row. [id] is the YouTube video id; [appPackage] is the streaming app it belongs to, if any. */
data class Card(
    val id: String,
    val title: String,
    val source: String,
    val kind: CardKind,
    val publishedMs: Long,
    val imageUrl: String,
    val appPackage: String?,
)
