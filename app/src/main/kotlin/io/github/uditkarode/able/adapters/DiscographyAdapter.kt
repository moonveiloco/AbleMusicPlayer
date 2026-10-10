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

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.github.uditkarode.able.R
import io.github.uditkarode.able.model.discovery.AlbumSuggestion
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import io.github.uditkarode.able.utils.LastFmClient

/**
 * Renders an artist's discography as a flat list with album header rows
 * (art + title + year) followed by the album's tracks, and a final
 * "Singles" section with the leftover top tracks. Always a vertical list —
 * section headers would be confusing in a grid.
 */
class DiscographyAdapter(
    private val items: List<Any>,
    private val singles: Set<String>,
    private val onTrackPressed: (TrackSuggestion) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ALBUM = 0
        private const val TYPE_TRACK = 1
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position] is AlbumSuggestion) TYPE_ALBUM else TYPE_TRACK

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ALBUM)
            AlbumVH(inflater.inflate(R.layout.rv_album_header, parent, false))
        else
            TrackVH(inflater.inflate(R.layout.rv_ytm_result, parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holderRaw: RecyclerView.ViewHolder, position: Int) {
        val context = holderRaw.itemView.context
        holderRaw.itemView.tag = position
        when (val item = items[position]) {
            is AlbumSuggestion -> {
                val holder = holderRaw as AlbumVH
                holder.name.text = item.name
                holder.name.typeface =
                    Typeface.createFromAsset(context.assets, "fonts/interbold.otf")
                holder.year.text = buildString {
                    if (item.year != 0) append(item.year) else append(item.releaseDate)
                    append(" • ")
                    append(item.tracks.size)
                }
                holder.itemView.setOnClickListener(null)
                if (item.image.isNotBlank() && !LastFmClient.isPlaceholderImage(item.image)) {
                    holder.artProgress.visibility = View.GONE
                    Glide.with(context)
                        .load(item.image)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(holder.art)
                } else {
                    holder.artProgress.visibility = View.GONE
                    holder.art.setImageResource(R.drawable.def_albart)
                }
            }

            is TrackSuggestion -> {
                val holder = holderRaw as TrackVH
                holder.name.text = item.name
                holder.name.typeface =
                    Typeface.createFromAsset(context.assets, "fonts/interbold.otf")
                holder.uploader.typeface =
                    Typeface.createFromAsset(context.assets, "fonts/inter.otf")
                holder.uploader.text =
                    if (item.name.lowercase() in singles) context.getString(R.string.disc_singles)
                    else item.artist
                holder.itemView.setOnClickListener { onTrackPressed(item) }
                if (item.image.isNotBlank() && !LastFmClient.isPlaceholderImage(item.image)) {
                    holder.artProgress.visibility = View.GONE
                    Glide.with(context)
                        .load(item.image)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(holder.songAlbumArt)
                } else {
                    holder.artProgress.visibility = View.GONE
                    holder.songAlbumArt.setImageResource(R.drawable.def_albart)
                }
            }
        }
    }

    class AlbumVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.album_name)
        val year: TextView = itemView.findViewById(R.id.album_year)
        val art: ImageView = itemView.findViewById(R.id.album_art)
        val artProgress: android.widget.ProgressBar =
            itemView.findViewById(R.id.album_art_progress)
    }

    class TrackVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.vid_song)
        val uploader: TextView = itemView.findViewById(R.id.vid_uploader)
        val songAlbumArt: ImageView = itemView.findViewById(R.id.vid_albart)
        val artProgress: android.widget.ProgressBar =
            itemView.findViewById(R.id.vid_art_progress)
    }
}
