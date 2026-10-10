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

package io.github.uditkarode.able.adapters

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.github.uditkarode.able.R
import io.github.uditkarode.able.model.discovery.ArtistSuggestion
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import io.github.uditkarode.able.utils.LastFmClient
import io.github.uditkarode.able.utils.ViewMode
import java.text.NumberFormat
import java.util.Locale
import java.util.HashSet
import java.util.concurrent.Executors

/**
 * Renders Last.fm discovery suggestions (tracks and artists) reusing the
 * `rv_ytm_result` row layout or the grid card. Items is a heterogeneous
 * [List] of [TrackSuggestion] / [ArtistSuggestion].
 *
 * Artwork is resolved lazily per row: Last.fm frequently answers
 * `track.search` / chart methods with a generic placeholder, so rows without
 * a real cover show a spinner and resolve the true artwork in the background
 * (`LastFmClient.resolveTrackImage`, persistent-cache + rate-limit aware).
 */
class DiscoverResultAdapter(
    private val items: List<Any>,
    private val onTrackPressed: (TrackSuggestion) -> Unit,
    private val onArtistPressed: (ArtistSuggestion) -> Unit
) : RecyclerView.Adapter<DiscoverResultAdapter.RVVH>() {

    private val numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

    /** Small worker pool for lazy artwork resolution. */
    private val resolver = Executors.newFixedThreadPool(2)

    /** Keys of suggestions currently being resolved (avoids duplicate work). */
    private val inFlight = HashSet<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RVVH =
        RVVH(
            LayoutInflater.from(parent.context).inflate(
                if (ViewMode.isGrid(parent.context)) R.layout.rv_grid_result else R.layout.rv_ytm_result,
                parent, false
            )
        )

    override fun getItemCount() = items.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RVVH, position: Int) {
        val context = holder.itemView.context
        val current = items[position]
        holder.itemView.tag = position

        holder.songName.typeface =
            Typeface.createFromAsset(context.assets, "fonts/interbold.otf")
        holder.songUploader.typeface =
            Typeface.createFromAsset(context.assets, "fonts/inter.otf")

        when (current) {
            is ArtistSuggestion -> {
                holder.songName.text = current.name
                val listeners = current.listeners.toLongOrNull()
                val listenersTxt = if (listeners != null)
                    " • " + numberFormat.format(listeners)
                else ""
                holder.songUploader.text = "Artist$listenersTxt"
                holder.itemView.setOnClickListener { onArtistPressed(current) }
                bindArt(holder, current, position) { LastFmClient.resolveArtistImage(current.name) }
            }

            is TrackSuggestion -> {
                holder.songName.text = current.name
                holder.songUploader.text = current.artist
                holder.itemView.setOnClickListener { onTrackPressed(current) }
                bindArt(holder, current, position) {
                    LastFmClient.resolveTrackImage(current.artist, current.name)
                }
            }

            else -> {
                holder.itemView.setOnClickListener(null)
                holder.artProgress.visibility = View.GONE
                Glide.with(context).load(R.drawable.def_albart).into(holder.songAlbumArt)
            }
        }
    }

    private fun bindArt(holder: RVVH, item: Any, position: Int, resolve: () -> String) {
        val artUrl = when (item) {
            is TrackSuggestion -> item.image
            is ArtistSuggestion -> item.image
            else -> ""
        }
        val context = holder.itemView.context
        if (artUrl.isNotBlank() && !LastFmClient.isPlaceholderImage(artUrl)) {
            holder.artProgress.visibility = View.GONE
            Glide.with(context)
                .load(artUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(holder.songAlbumArt)
            return
        }
        // Needs resolution: placeholder now, spinner on, background lookup.
        holder.songAlbumArt.setImageResource(R.drawable.def_albart)
        val key = when (item) {
            is TrackSuggestion -> "t:${item.artist}|${item.name}"
            is ArtistSuggestion -> "a:${item.name}"
            else -> return.also { holder.artProgress.visibility = View.GONE }
        }
        holder.artProgress.visibility = View.VISIBLE
        if (!inFlight.add(key)) return // already resolving elsewhere
        resolver.execute {
            val url = try { resolve() } catch (e: Exception) { "" }
            if (url.isNotBlank()) {
                (items.getOrNull(position) as? TrackSuggestion)?.let { if (it.image.isBlank() || LastFmClient.isPlaceholderImage(it.image)) it.image = url }
                (items.getOrNull(position) as? ArtistSuggestion)?.let { if (it.image.isBlank() || LastFmClient.isPlaceholderImage(it.image)) it.image = url }
            }
            inFlight.remove(key)
            holder.itemView.post {
                if (holder.itemView.tag == position && holder.itemView.parent != null) {
                    holder.artProgress.visibility = View.GONE
                    if (url.isNotBlank())
                        Glide.with(context)
                            .load(url)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(holder.songAlbumArt)
                }
            }
        }
    }

    class RVVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val songName = itemView.findViewById<TextView>(R.id.vid_song)!!
        val songUploader = itemView.findViewById<TextView>(R.id.vid_uploader)!!
        val songAlbumArt = itemView.findViewById<ImageView>(R.id.vid_albart)!!
        val artProgress = itemView.findViewById<ProgressBar>(R.id.vid_art_progress)!!
    }
}
