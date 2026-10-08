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

package io.github.uditkarode.able.fragments

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.list.listItems
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.DiscoverResultAdapter
import io.github.uditkarode.able.databinding.DiscoverBinding
import io.github.uditkarode.able.model.discovery.ArtistSuggestion
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.LastFmClient
import io.github.uditkarode.able.utils.TrackResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Fifth tab. Song discovery powered by the Last.fm public API: global charts,
 * country charts, genre tags and "similar to what's playing" suggestions.
 */
class Discover : Fragment(), CoroutineScope {
    private lateinit var itemPressed: Search.SongCallback
    private var _binding: DiscoverBinding? = null
    private val binding get() = _binding!!

    override val coroutineContext = Dispatchers.Main + SupervisorJob()

    companion object {
        private const val TAG = "Discover"

        /** Last.fm geo methods require the English country name. */
        fun currentCountry(): String =
            Locale.getDefault().getDisplayCountry(Locale.ENGLISH).ifBlank { "Italy" }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
        _binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            itemPressed = context as Activity as Search.SongCallback
        } catch (e: ClassCastException) {
            throw ClassCastException(
                activity.toString()
                        + " must implement SongCallback"
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addChip(R.string.disc_top_tracks) { loadTracks { LastFmClient.topTracks() } }
        addChip(R.string.disc_top_artists) { loadArtists { LastFmClient.topArtists() } }
        addChip(R.string.disc_local_tracks) { loadTracks { LastFmClient.geoTopTracks(currentCountry()) } }
        addChip(R.string.disc_local_artists) { loadArtists { LastFmClient.geoTopArtists(currentCountry()) } }
        addChip(R.string.disc_genres) { pickGenre() }
        addChip(R.string.disc_similar) { loadSimilar() }

        // Preload the first source so the tab isn't empty.
        binding.discoverChips.getChildAt(0).performClick()
    }

    // region source loading

    private fun loadTracks(fetch: () -> List<TrackSuggestion>) {
        setLoading(true)
        launch(Dispatchers.IO) {
            val suggestions = try { fetch() } catch (e: Exception) {
                Log.e(TAG, "Discovery fetch failed", e); emptyList()
            }
            launch(Dispatchers.Main) { showSuggestions(suggestions) }
        }
    }

    private fun loadArtists(fetch: () -> List<ArtistSuggestion>) {
        setLoading(true)
        launch(Dispatchers.IO) {
            val suggestions = try { fetch() } catch (e: Exception) {
                Log.e(TAG, "Discovery fetch failed", e); emptyList()
            }
            launch(Dispatchers.Main) { showSuggestions(suggestions) }
        }
    }

    private fun pickGenre() {
        setLoading(true)
        launch(Dispatchers.IO) {
            val tags = try { LastFmClient.topTags() } catch (e: Exception) {
                Log.e(TAG, "Tag fetch failed", e); emptyList()
            }
            launch(Dispatchers.Main) {
                setLoading(false)
                if (tags.isEmpty()) {
                    showEmpty(getString(R.string.disc_no_genres))
                    return@launch
                }
                MaterialDialog(requireContext(), BottomSheet()).show {
                    title(R.string.disc_genres)
                    listItems(items = tags) { _, index, _ ->
                        loadTracks { LastFmClient.tagTopTracks(tags[index]) }
                    }
                }
            }
        }
    }

    private fun loadSimilar() {
        val current = MusicService.playQueue.getOrNull(MusicService.currentIndex)
        if (current == null) {
            showEmpty(getString(R.string.disc_play_first))
            return
        }
        if (current.artist.isBlank()) {
            showEmpty(getString(R.string.disc_unknown_artist))
            return
        }
        loadTracks { LastFmClient.similarTracks(current.artist, current.name) }
    }

    // endregion

    // region rendering

    private fun showSuggestions(items: List<Any>) {
        setLoading(false)
        val rv = binding.discoverRv
        if (items.isEmpty()) {
            showEmpty(getString(R.string.disc_no_suggestions))
            return
        }
        binding.discoverEmpty.visibility = View.GONE
        rv.adapter = DiscoverResultAdapter(
            items,
            onTrackPressed = ::onTrackPressed,
            onArtistPressed = ::onArtistPressed
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.alpha = 0f
        rv.visibility = View.VISIBLE
        rv.animate().alpha(1f).duration = 200
    }

    private fun showEmpty(message: String) {
        setLoading(false)
        binding.discoverRv.visibility = View.GONE
        binding.discoverEmpty.text = message
        binding.discoverEmpty.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        val lv = binding.discoverLoading
        if (loading) {
            lv.progress = 0.3080229f
            lv.visibility = View.VISIBLE
            lv.playAnimation()
            binding.discoverEmpty.visibility = View.GONE
        } else {
            lv.visibility = View.GONE
            lv.pauseAnimation()
        }
    }

    // endregion

    // region item handling

    private fun onArtistPressed(artist: ArtistSuggestion) {
        setLoading(true)
        launch(Dispatchers.IO) {
            val tracks = try { LastFmClient.artistTopTracks(artist.name) } catch (e: Exception) {
                Log.e(TAG, "Artist drill-down failed", e); emptyList()
            }
            launch(Dispatchers.Main) {
                if (tracks.isEmpty())
                    showEmpty(getString(R.string.disc_no_artist_tracks, artist.name))
                else
                    showSuggestions(tracks)
            }
        }
    }

    /**
     * Lazy resolution: the Last.fm suggestion has no playable URL yet, so we first
     * look the track up on YouTube Music and only then hand it to MainActivity.
     */
    private fun onTrackPressed(track: TrackSuggestion) {
        if (!io.github.uditkarode.able.utils.Shared.isInternetConnected(requireContext())) {
            Toast.makeText(requireContext(), "No Internet Connection", Toast.LENGTH_LONG).show()
            return
        }
        setLoading(true)
        launch(Dispatchers.IO) {
            val song = try { TrackResolver.resolve(track) } catch (e: Exception) {
                Log.e(TAG, "Resolution failed for ${track.name}", e); null
            }
            launch(Dispatchers.Main) {
                setLoading(false)
                if (song == null)
                    Toast.makeText(requireContext(), R.string.disc_yt_fail, Toast.LENGTH_SHORT).show()
                else
                    itemPressed.sendItem(song)
            }
        }
    }

    // endregion

    // region chips

    private fun addChip(labelRes: Int, onClick: () -> Unit) {
        val chip = TextView(requireContext()).apply {
            text = getString(labelRes)
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            typeface = Typeface.createFromAsset(requireContext().assets, "fonts/intersemibold.otf")
            background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_rectangle)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener {
                highlightChip(this)
                onClick()
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = dp(8)
        binding.discoverChips.addView(chip, params)
    }

    private fun highlightChip(selected: TextView) {
        for (i in 0 until binding.discoverChips.childCount) {
            val child = binding.discoverChips.getChildAt(i) as TextView
            child.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (child === selected) R.color.colorAccent else android.R.color.white
                )
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // endregion
}
