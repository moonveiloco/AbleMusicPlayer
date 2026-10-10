/*
    Copyright 2025 AbleMusicPlayer contributors

    This file is part of AbleMusicPlayer.

    AbleMusicPlayer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 of the License.

    AbleMusicPlayer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with AbleMusicPlayer.  If not, see <https://www.gnu.org/licenses/>.
*/

package io.github.uditkarode.able.utils

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.uditkarode.able.model.discovery.ArtistSuggestion
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Minimal Last.fm API v2.0 client (https://www.last.fm/api). Read-only methods,
 * authenticated with the application API key only (no OAuth).
 *
 * All network calls are **synchronous** — invoke from a background dispatcher
 * (callers in this codebase already run on `Dispatchers.IO`). A small TTL cache
 * keeps us well under the public rate limit of 5 requests/second.
 *
 * Last.fm wraps the same payload under different keys depending on the method
 * (`tracks`, `toptracks`, `similartracks`, `results.trackmatches`, `artists`,
 * `topartists`, `tags`) and reports the artist as a string or an object, so the
 * responses are parsed generically instead of via fixed DTOs.
 */
object LastFmClient {
    private const val TAG = "LastFmClient"
    private const val BASE = "https://ws.audioscrobbler.com/2.0/"
    /** Cache entry lifetime in milliseconds. */
    private const val CACHE_TTL_MS = 30L * 60 * 1000

    /** Last.fm artwork hashes for the generic gray-star placeholder. */
    private val PLACEHOLDER_HASHES = setOf(
        "2a96cbd8b46e442fc41c2b86b821562f", // album/track placeholder
        "c6f59c1e5e7248a8c33aba0087ea49d0"  // artist placeholder
    )

    /** Max artwork lookups done per list to stay under the 5 req/s limit. */
    private const val ART_FALLBACK_BUDGET = 15

    /** Delay between artwork fallback calls (5 req/s → 200+ ms). */
    private const val ART_FALLBACK_DELAY_MS = 210L

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Simple in-memory cache: key -> (payload, timestamp). */
    private val cache = HashMap<String, Pair<String, Long>>()

    private val hasKey: Boolean get() = ApiKeyProvider.hasLastFmKey

    /** chart.getTopTracks — most-played tracks globally. */
    fun topTracks(): List<TrackSuggestion> = callTracks("chart.gettoptracks")

    /** geo.getTopTracks — most-played tracks in [country] (English country name, e.g. "Italy"). */
    fun geoTopTracks(country: String): List<TrackSuggestion> =
        callTracks("geo.gettoptracks", "country" to country)

    /** tag.getTopTracks — most-played tracks for a genre tag, e.g. "rock". */
    fun tagTopTracks(tag: String): List<TrackSuggestion> =
        callTracks("tag.gettoptracks", "tag" to tag)

    /** track.getSimilar — tracks similar to the given artist/title pair. */
    fun similarTracks(artist: String, track: String): List<TrackSuggestion> =
        callTracks("track.getsimilar", "artist" to artist, "track" to track)

    /** Last.fm `track.search` — metadata search used by the Discover search mode. */
    fun searchTracks(query: String): List<TrackSuggestion> =
        callTracks("track.search", "track" to query)

    /** artist.getTopTracks — drill-down used when pressing an artist suggestion. */
    fun artistTopTracks(artist: String): List<TrackSuggestion> =
        callTracks("artist.gettoptracks", "artist" to artist)

    /** chart.getTopArtists — most-played artists globally. */
    fun topArtists(): List<ArtistSuggestion> = callArtists("chart.gettopartists")

    /** geo.getTopArtists — most-played artists in [country] (English country name). */
    fun geoTopArtists(country: String): List<ArtistSuggestion> =
        callArtists("geo.gettopartists", "country" to country)

    /** chart.getTopTags — popular genre/mood tags. */
    fun topTags(): List<String> {
        val root = cachedJson("chart.gettoptags", emptyList()) ?: return emptyList()
        return findArray(root, "tag")
            .mapNotNull { it.asObjectOrNull()?.stringField("name") }
            .filter { it.isNotBlank() }
    }

    /** track.getInfo — richer metadata incl. the real album art. */
    private fun trackInfoImage(artist: String, track: String): String {
        val root = infoObject("track.getinfo", listOf("artist" to artist, "track" to track))
            ?: return ""
        val fromAlbum = (root.get("album") as? JsonObject)?.bestImage().orEmpty()
        val best = if (fromAlbum.isNotBlank() && !isPlaceholderImage(fromAlbum))
            fromAlbum else root.bestImage()
        return if (isPlaceholderImage(best)) "" else best
    }

    /** artist.getInfo — real artist artwork. */
    private fun artistInfoImage(artist: String): String {
        val root = infoObject("artist.getinfo", listOf("artist" to artist)) ?: return ""
        val best = root.bestImage()
        return if (isPlaceholderImage(best)) "" else best
    }

    /** Fetches a getInfo response and returns the wrapped track/artist object. */
    private fun infoObject(method: String, params: List<Pair<String, String>>): JsonObject? {
        val json = cachedJson(method, params) ?: return null
        val root = try { JsonParser.parseString(json) } catch (_: Exception) { return null }
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        return (obj.get("track") as? JsonObject) ?: (obj.get("artist") as? JsonObject)
    }

    private fun isPlaceholderImage(url: String): Boolean =
        PLACEHOLDER_HASHES.any { url.contains(it) }

    // region internals

    /** Live validation of a candidate API key (used by the in-app import prompt). */
    fun validateKey(candidate: String): Boolean {
        if (candidate.isBlank()) return false
        val url = "$BASE?method=chart.gettoptags&format=json&api_key=$candidate"
        return try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string() ?: return false
                !body.contains("\"error\"")
            }
        } catch (e: Exception) {
            Log.e(TAG, "API key validation failed", e)
            false
        }
    }

    private fun callTracks(method: String, vararg params: Pair<String, String>): List<TrackSuggestion> {
        val root = cachedJson(method, params.toList()) ?: return emptyList()
        return findArray(root, "track")
            .mapNotNull { it.asObjectOrNull() }
            .filter { it.stringField("name")?.isNotBlank() == true }
            .map { entry ->
                TrackSuggestion(
                    name = entry.stringField("name") ?: "",
                    artist = entry.artistName(),
                    image = entry.bestImage(),
                    listeners = entry.stringField("listeners")
                        ?: entry.stringField("listener") ?: "",
                    mbid = entry.stringField("mbid") ?: ""
                )
            }
            .filter { it.artist.isNotBlank() }
            .enrichTrackArt()
    }

    private fun callArtists(method: String, vararg params: Pair<String, String>): List<ArtistSuggestion> {
        val root = cachedJson(method, params.toList()) ?: return emptyList()
        return findArray(root, "artist")
            .mapNotNull { it.asObjectOrNull() }
            .filter { it.stringField("name")?.isNotBlank() == true }
            .map {
                ArtistSuggestion(
                    name = it.stringField("name") ?: "",
                    image = it.bestImage(),
                    listeners = it.stringField("listeners") ?: "",
                    mbid = it.stringField("mbid") ?: ""
                )
            }
            .enrichArtistArt()
    }

    /**
     * Last.fm often answers `track.search` / `chart.gettoptracks` with the
     * generic gray-star placeholder instead of real artwork. Re-fetch the
     * missing/placeholder images one by one via `track.getInfo`, which has
     * the proper album art, respecting the 5 req/s rate limit.
     */
    private fun List<TrackSuggestion>.enrichTrackArt(): List<TrackSuggestion> {
        var budget = ART_FALLBACK_BUDGET
        for (suggestion in this) {
            if (budget == 0) break
            if (suggestion.image.isNotBlank() && !isPlaceholderImage(suggestion.image)) continue
            if (suggestion.artist.isBlank() || suggestion.name.isBlank()) continue
            budget--
            try { Thread.sleep(ART_FALLBACK_DELAY_MS) } catch (_: InterruptedException) {}
            val real = trackInfoImage(suggestion.artist, suggestion.name)
            if (real.isNotBlank()) suggestion.image = real
        }
        return this
    }

    /** Same fallback for artists, via `artist.getInfo`. */
    private fun List<ArtistSuggestion>.enrichArtistArt(): List<ArtistSuggestion> {
        var budget = ART_FALLBACK_BUDGET
        for (suggestion in this) {
            if (budget == 0) break
            if (suggestion.image.isNotBlank() && !isPlaceholderImage(suggestion.image)) continue
            if (suggestion.name.isBlank()) continue
            budget--
            try { Thread.sleep(ART_FALLBACK_DELAY_MS) } catch (_: InterruptedException) {}
            val real = artistInfoImage(suggestion.name)
            if (real.isNotBlank()) suggestion.image = real
        }
        return this
    }

    /**
     * Locates the payload array for [memberName] anywhere within the response
     * object tree. Covers `tracks.track` (chart/geo), `toptracks.track`
     * (artist/tag), `similartracks.track`, `results.trackmatches.track`
     * (track.search), `artists.artist`, `topartists.artist` and `tags.tag`.
     */
    private fun findArray(json: String, memberName: String): List<JsonElement> {
        val root = try { JsonParser.parseString(json) } catch (_: Exception) { return emptyList() }
        if (!root.isJsonObject) return emptyList()
        return findArray(root.asJsonObject, memberName)
    }

    private fun findArray(obj: JsonObject, memberName: String): List<JsonElement> {
        val candidate = obj.get(memberName)
        if (candidate is JsonArray) return candidate.toList()
        for ((_, value) in obj.entrySet()) {
            val child = value.asObjectOrNull() ?: continue
            val found = findArray(child, memberName)
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /** Artist may be a string (track.search) or an object (`artist.name`). */
    private fun JsonObject.artistName(): String {
        val artist = get("artist") ?: return ""
        return when {
            artist.isJsonObject -> artist.asJsonObject.stringField("name") ?: ""
            artist.isJsonPrimitive -> artist.asString
            else -> ""
        }
    }

    /** Picks the last non-blank image (Last.fm sizes go small → extralarge). */
    private fun JsonObject.bestImage(): String {
        val images = get("image") as? JsonArray ?: return ""
        return images.mapNotNull { it.asObjectOrNull()?.stringField("#text") }
            .lastOrNull { it.isNotBlank() } ?: ""
    }

    private fun JsonObject.stringField(name: String): String? {
        val value = get(name) ?: return null
        return when {
            value.isJsonNull -> null
            value.isJsonPrimitive -> value.asString
            else -> null
        }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null

    /** Builds the request URL and caches the raw JSON body per (method, params). */
    private fun cachedJson(method: String, params: List<Pair<String, String>>): String? {
        if (!hasKey) {
            Log.w(TAG, "LASTFM_API_KEY is not set — discovery unavailable")
            return null
        }
        val cacheKey = method + params.joinToString("|") { "${it.first}=${it.second}" }

        synchronized(cache) {
            val hit = cache[cacheKey]
            if (hit != null && System.currentTimeMillis() - hit.second < CACHE_TTL_MS)
                return hit.first
        }

        val url = StringBuilder(BASE)
            .append("?method=").append(method)
            .append("&format=json")
            .append("&api_key=").append(ApiKeyProvider.lastFmApiKey)
        params.forEach { (k, v) ->
            url.append("&").append(k).append("=").append(java.net.URLEncoder.encode(v, "UTF-8"))
        }

        val body = try {
            http.newCall(Request.Builder().url(url.toString()).build()).execute().use { resp ->
                resp.body?.string()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Last.fm request failed: $method", e)
            null
        } ?: return null

        if (body.contains("\"error\"")) {
            val err = try { JsonParser.parseString(body).asJsonObject } catch (_: Exception) { null }
            Log.w(TAG, "Last.fm error on $method: ${err?.get("error")} ${err?.get("message")}")
            return null
        }

        synchronized(cache) {
            if (cache.size > 64) cache.clear()
            cache[cacheKey] = body to System.currentTimeMillis()
        }
        return body
    }

    // endregion
}