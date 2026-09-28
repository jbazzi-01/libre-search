package org.libre.search.search

data class WebResult(
    val title: String,
    val url: String,
    val description: String,
    val siteName: String,
    val favicon: String?,
    val thumbnail: String?,
    val age: String?,
    val extraSnippets: List<String> = emptyList(),
    val sitelinks: List<Pair<String, String>> = emptyList(),
    val price: String? = null,
    val rating: String? = null,
)

data class VideoResult(
    val title: String,
    val url: String,
    val thumbnail: String?,
    val duration: String?,
    val creator: String?,
    val publisher: String?,
    val age: String?,
)

data class ImageResult(
    val title: String,
    val pageUrl: String,
    val imageUrl: String,
    val thumbnail: String,
    val source: String,
    val width: Int = 0,
    val height: Int = 0,
)

data class Discussion(
    val title: String,
    val url: String,
    val forum: String,
    val snippet: String,
    val answers: String?,
    val score: String?,
    val age: String?,
)

data class Faq(val question: String, val answer: String, val url: String, val title: String)

data class Product(
    val title: String,
    val url: String,
    val image: String?,
    val price: String?,
    val store: String,
    val rating: String?,
)

data class WebPage(
    val results: List<WebResult> = emptyList(),
    val videos: List<VideoResult> = emptyList(),
    val discussions: List<Discussion> = emptyList(),
    val faq: List<Faq> = emptyList(),
    val products: List<Product> = emptyList(),
    val alteredQuery: String? = null,
    val moreAvailable: Boolean = false,
)
