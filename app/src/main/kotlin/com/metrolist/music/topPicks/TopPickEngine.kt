package com.metrolist.music.topPicks

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.HomePage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime
import java.util.Locale
import kotlin.random.Random

/**
 * Builds the "Top picks for you" row from YouTube Music data, ported from
 * BitChord's TopPicks. Only fetches when Home loads and queues when a card is
 * tapped. Nothing here touches playback or the automix engine.
 */
object TopPickEngine {
    private const val ARTISTS = 3
    private const val ARTIST_POOL = 10
    private const val MIN_FILTERED_QUEUE = 12
    private const val SEED_POOL = 40
    private const val SEED_RADIOS = 3
    private const val STATION_FROM_MIX = 30
    private const val STATION_SIZE = 60
    private const val MOOD_PLAYLISTS = 3
    private const val MOOD_QUEUE = 60

    private class ArtistCard(val page: ArtistPage, val browseId: String, val photo: String? = null)

    /** Mix kinds found on Home's shelves, matched by title. */
    private enum class Mix { SUPERMIX, DISCOVER, NEW_RELEASE }

    /**
     * [history] is the listener's recent songs (YouTube history). [homeSections]
     * is Home's loaded shelves, used to find the account's mixes.
     */
    suspend fun build(
        history: List<SongItem>,
        homeSections: List<HomePage.Section>,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<TopPick> = coroutineScope {
        val mixes = findMixes(homeSections)
        val ranked = weightedSample(rankArtists(history).take(ARTIST_POOL), ARTISTS)
        val pages = ranked.map { ref ->
            async { YouTube.artist(ref.second).getOrNull() }
        }.awaitAll()
        val artists = ranked.zip(pages).mapNotNull { (ref, page) ->
            page?.let { ArtistCard(it, ref.second) }
        }.distinctBy { it.page.artist.title.lowercase(Locale.ROOT) }
            .let { cards ->
                val photos = cards.map { card ->
                    async { AppleArtistPhotos.photoFor(card.page.artist.title, card.page) }
                }.awaitAll()
                cards.zip(photos) { card, photo -> ArtistCard(card.page, card.browseId, photo) }
            }

        val lead = latestRelease(artists)
        val rest = buildList {
            personalStation(mixes, history)?.let(::add)
            add(moodPick(now))
            artists.getOrNull(0)?.let(::andSimilar)?.let(::add)
            artists.getOrNull(1)?.let(::featured)?.let(::add)
            mixes[Mix.NEW_RELEASE]?.let { item ->
                add(
                    TopPick(
                        kind = TopPickKind.NEW_RELEASE_MIX,
                        title = item.title,
                        subtitle = item.author?.name.orEmpty(),
                        images = listOfNotNull(item.thumbnail),
                        watch = item.watchTarget(),
                    ),
                )
            }
            artists.getOrNull(2)?.let(::andSimilar)?.let(::add)
            discoveryStation(mixes, artists, history)?.let(::add)
        }.shuffled()
        listOfNotNull(lead) + rest
    }

    /** The queue a card starts. LATEST_RELEASE opens a page, so it has none. */
    suspend fun queue(pick: TopPick): Result<List<SongItem>> {
        if (pick.kind == TopPickKind.MOOD) return moodQueue(pick)
        val target = pick.watch ?: return Result.failure(IllegalStateException("no queue behind ${pick.kind}"))
        if (pick.kind == TopPickKind.PERSONAL_STATION || pick.kind == TopPickKind.DISCOVERY_STATION) {
            return freshStation(pick, target)
        }
        return YouTube.next(target.toEndpoint()).map { it.items }
    }

    private fun WatchTarget.toEndpoint() = WatchEndpoint(videoId = videoId, playlistId = playlistId, params = params)

    private suspend fun freshStation(pick: TopPick, target: WatchTarget): Result<List<SongItem>> = coroutineScope {
        val mixAsync = async { YouTube.next(target.toEndpoint()).getOrNull()?.items.orEmpty() }
        val history = YouTube.musicHistory().getOrNull()?.sections.orEmpty().flatMap { it.songs }
        val seeds = history.take(SEED_POOL).shuffled().take(SEED_RADIOS)
        val radios = seeds.map { seed ->
            async {
                YouTube.next(WatchEndpoint(videoId = seed.id, playlistId = "RDAMVM${seed.id}"))
                    .getOrNull()?.items.orEmpty()
            }
        }
        val mix = mixAsync.await()
        val around = radios.awaitAll().flatten()

        val discovery = pick.kind == TopPickKind.DISCOVERY_STATION
        val heard = if (discovery) history.mapTo(HashSet()) { it.id } + pick.exclude else emptySet<String>()
        val seedArtist = pick.artistName?.takeIf { discovery }?.lowercase(Locale.ROOT)
        fun keep(song: SongItem): Boolean =
            song.id !in heard &&
                (seedArtist == null || song.artists.none { it.name.lowercase(Locale.ROOT).contains(seedArtist) })
        val core = mix.filter(::keep).shuffled().take(STATION_FROM_MIX)
        val coreIds = core.mapTo(HashSet()) { it.id }
        val extra = around.filter { keep(it) && it.id !in coreIds }
            .distinctBy { it.id }
            .shuffled()
            .take(STATION_SIZE - core.size)
        val station = (core + extra).shuffled()
        when {
            station.size >= MIN_FILTERED_QUEUE -> Result.success(station)
            mix.isNotEmpty() -> Result.success(mix.shuffled())
            else -> Result.failure(IllegalStateException("${pick.kind} came back empty"))
        }
    }

    private fun <T> weightedSample(ranked: List<T>, count: Int): List<T> {
        val pool = ranked.withIndex().toMutableList()
        val picked = mutableListOf<T>()
        while (picked.size < count && pool.isNotEmpty()) {
            val weights = pool.map { (ranked.size - it.index).toDouble() }
            var roll = Random.nextDouble() * weights.sum()
            val index = weights.indexOfFirst { roll -= it; roll <= 0 }.coerceAtLeast(0)
            picked += pool.removeAt(index).value
        }
        return picked
    }

    /** Artists from history, best first: (name, channel id). Each row counts once, recent rows a little more. */
    private fun rankArtists(history: List<SongItem>): List<Pair<String, String>> {
        val scores = LinkedHashMap<String, Pair<String, Double>>()
        history.forEachIndexed { index, song ->
            val recency = 1.0 - index.toDouble() / history.size.coerceAtLeast(1)
            val lead = song.artists.firstOrNull { it.id != null }
            val id = lead?.id ?: return@forEachIndexed
            val name = lead.name
            if (!id.startsWith("UC") || name.isBlank() || name.equals("Various Artists", true)) return@forEachIndexed
            val (_, score) = scores[id] ?: (name to 0.0)
            scores[id] = name to score + 1.0 + recency * 0.5
        }
        return scores.entries.sortedByDescending { it.value.second }.map { it.value.first to it.key }
    }

    private fun latestRelease(artists: List<ArtistCard>): TopPick? {
        data class Candidate(val rank: Int, val artist: ArtistCard, val album: AlbumItem)
        val candidates = artists.flatMapIndexed { rank, artist ->
            artist.page.sections.flatMap { section ->
                section.items.filterIsInstance<AlbumItem>().map { Candidate(rank, artist, it) }
            }
        }
        val best = candidates.maxWithOrNull(
            compareBy<Candidate> { it.album.year ?: 0 }.thenByDescending { it.rank },
        ) ?: return null
        return TopPick(
            kind = TopPickKind.LATEST_RELEASE,
            title = best.album.title,
            subtitle = best.album.artists.orEmpty().joinToString { it.name },
            artistName = best.artist.page.artist.title,
            images = listOfNotNull(best.album.thumbnail),
            browseId = best.album.browseId,
        )
    }

    private fun personalStation(mixes: Map<Mix, PlaylistItem>, history: List<SongItem>): TopPick? {
        val watch = mixes[Mix.SUPERMIX]?.watchTarget()
            ?: history.firstOrNull()?.let { song ->
                WatchTarget(playlistId = "RDAMVM${song.id}", videoId = song.id)
            }
            ?: return null
        return TopPick(kind = TopPickKind.PERSONAL_STATION, watch = watch)
    }

    private fun moodPick(now: LocalDateTime): TopPick =
        TopPick(kind = TopPickKind.MOOD, mood = moodFor(now), hints = regionHints())

    private fun andSimilar(artist: ArtistCard): TopPick? {
        val radio = artist.page.artist.radioEndpoint ?: return null
        val face = artist.photo ?: artist.page.artist.thumbnail ?: return null
        val related = relatedArtists(artist).mapNotNull { it.thumbnail }.filter { it != face }.distinct().take(2)
        return TopPick(
            kind = TopPickKind.ARTIST_AND_SIMILAR,
            title = artist.page.artist.title,
            artistName = artist.page.artist.title,
            artistBrowseId = artist.browseId,
            images = listOf(face) + related,
            watch = radio.toTarget(),
        )
    }

    private fun featured(artist: ArtistCard): TopPick? {
        val shuffle = artist.page.artist.shuffleEndpoint ?: artist.page.artist.radioEndpoint ?: return null
        val face = artist.photo ?: artist.page.artist.thumbnail ?: return null
        return TopPick(
            kind = TopPickKind.FEATURED_ARTIST,
            title = artist.page.artist.title,
            subtitle = artist.page.monthlyListenerCount ?: artist.page.subscriberCountText.orEmpty(),
            artistName = artist.page.artist.title,
            artistBrowseId = artist.browseId,
            images = listOf(face),
            watch = shuffle.toTarget(),
        )
    }

    /** "Fans might also like": the artist shelf made only of other artists, without the artist. */
    private fun relatedArtists(artist: ArtistCard): List<ArtistItem> =
        artist.page.sections
            .firstOrNull { it.items.isNotEmpty() && it.items.all { item -> item is ArtistItem } }
            ?.items.orEmpty()
            .filterIsInstance<ArtistItem>()
            .filter { it.id != artist.browseId }
            .distinctBy { it.title.lowercase(Locale.ROOT) }
            .take(4)

    private fun discoveryStation(
        mixes: Map<Mix, PlaylistItem>,
        artists: List<ArtistCard>,
        history: List<SongItem>,
    ): TopPick? {
        mixes[Mix.DISCOVER]?.watchTarget()?.let { watch ->
            return TopPick(kind = TopPickKind.DISCOVERY_STATION, watch = watch)
        }
        val seed = artists.firstOrNull() ?: return null
        val radio = seed.page.artist.radioEndpoint ?: return null
        return TopPick(
            kind = TopPickKind.DISCOVERY_STATION,
            artistName = seed.page.artist.title,
            watch = radio.toTarget(),
            exclude = history.take(200).map { it.id },
        )
    }

    private fun findMixes(homeSections: List<HomePage.Section>): Map<Mix, PlaylistItem> {
        val mixes = homeSections.flatMap { it.items }
            .filterIsInstance<PlaylistItem>()
            .filter { it.id.removePrefix("VL").startsWith("RDTMAK") }
        val found = mutableMapOf<Mix, PlaylistItem>()
        mixes.forEach { item ->
            val title = item.title.lowercase(Locale.ROOT)
            val kind = when {
                "supermix" in title -> Mix.SUPERMIX
                "discover" in title -> Mix.DISCOVER
                "new release" in title -> Mix.NEW_RELEASE
                else -> null
            }
            if (kind != null) found.putIfAbsent(kind, item)
        }
        return found
    }

    private fun PlaylistItem.watchTarget(): WatchTarget =
        WatchTarget(playlistId = id.removePrefix("VL"))

    private fun WatchEndpoint.toTarget(): WatchTarget? =
        playlistId?.let { WatchTarget(playlistId = it, videoId = videoId, params = params) }

    fun moodFor(now: LocalDateTime): TopPickMood {
        val suited = when (now.hour) {
            in 5..8 -> listOf(TopPickMood.ENERGY, TopPickMood.FEEL_GOOD, TopPickMood.FOCUS)
            in 9..11 -> listOf(TopPickMood.FOCUS, TopPickMood.FEEL_GOOD, TopPickMood.ENERGY)
            in 12..16 -> listOf(TopPickMood.FEEL_GOOD, TopPickMood.FOCUS, TopPickMood.ENERGY, TopPickMood.RELAX)
            in 17..19 -> listOf(TopPickMood.ENERGY, TopPickMood.FEEL_GOOD, TopPickMood.RELAX)
            in 20..22 -> listOf(TopPickMood.LOVE, TopPickMood.RELAX, TopPickMood.HEARTBREAK, TopPickMood.FEEL_GOOD)
            else -> listOf(TopPickMood.RELAX, TopPickMood.FEELING_BLUE, TopPickMood.HEARTBREAK, TopPickMood.LOVE)
        }
        return if (Random.nextFloat() < 0.7f) suited.random()
        else TopPickMood.entries.filter { it.isCoreMood }.random()
    }

    private suspend fun moodQueue(pick: TopPick): Result<List<SongItem>> = coroutineScope {
        val mood = pick.mood ?: return@coroutineScope Result.failure(IllegalStateException("mood card without a mood"))
        val shelves = YouTube.categoryShelves(TopPickMood.CATEGORY_BROWSE_ID, mood.params)
            .getOrElse { return@coroutineScope Result.failure(it) }
        val playlists = { shelf: HomePage.Section -> shelf.items.filterIsInstance<PlaylistItem>() }
        val candidates = shelves.filter { playlists(it).isNotEmpty() }
        val score = { shelf: HomePage.Section ->
            val title = shelf.title.lowercase(Locale.ROOT)
            2 * mood.shelfWords.count { it in title } + pick.hints.count { it.isNotBlank() && it in title }
        }
        val best = candidates.maxOfOrNull(score)
            ?: return@coroutineScope Result.failure(IllegalStateException("no playlists for $mood"))
        val shelf = candidates.filter { score(it) == best }.random()
        val chosen = playlists(shelf).take(6).shuffled().take(MOOD_PLAYLISTS)
        val songs = chosen.map { item ->
            async { YouTube.playlist(item.id.removePrefix("VL")).getOrNull()?.songs.orEmpty() }
        }.awaitAll().flatten()
            .distinctBy { it.id }
            .shuffled()
            .take(MOOD_QUEUE)
        if (songs.isEmpty()) Result.failure(IllegalStateException("$mood playlists were empty"))
        else Result.success(songs)
    }

    /** The card's title line. Stations and moods name themselves; the rest use their own title. */
    fun labelOf(pick: TopPick): String = when (pick.kind) {
        TopPickKind.PERSONAL_STATION -> "My Supermix"
        TopPickKind.DISCOVERY_STATION -> pick.artistName?.let { "$it Discovery" } ?: "Discovery Station"
        TopPickKind.MOOD -> pick.mood?.name?.lowercase(Locale.ROOT)?.replace('_', ' ')
            ?.replaceFirstChar { it.uppercase() } ?: "Mood"
        TopPickKind.NEW_RELEASE_MIX -> pick.title.ifBlank { "New Release Mix" }
        else -> pick.title
    }

    /** The card's second line. */
    fun subtitleOf(pick: TopPick): String = when (pick.kind) {
        TopPickKind.LATEST_RELEASE -> pick.subtitle.ifBlank { pick.artistName.orEmpty() }
        TopPickKind.ARTIST_AND_SIMILAR -> "Artist & Similar"
        TopPickKind.FEATURED_ARTIST -> pick.subtitle.ifBlank { "Featured Artist" }
        else -> pick.subtitle
    }

    fun moodStation(mood: TopPickMood, hints: List<String> = regionHints()): TopPick =
        TopPick(kind = TopPickKind.MOOD, mood = mood, hints = hints)

    fun regionHints(): List<String> = when (Locale.getDefault().country) {
        "IN" -> listOf("bollywood", "indian", "hindi", "punjabi", "desi")
        "PK" -> listOf("pakistani", "urdu", "punjabi")
        "KR" -> listOf("k-pop", "korean")
        "JP" -> listOf("j-pop", "japanese")
        "BR" -> listOf("brazil", "sertanejo", "funk")
        "MX", "ES", "AR", "CO", "CL", "PE" -> listOf("latin", "latino", "reggaeton")
        else -> emptyList()
    }
}
