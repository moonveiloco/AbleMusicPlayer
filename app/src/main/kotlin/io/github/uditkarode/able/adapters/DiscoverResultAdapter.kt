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
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.github.uditkarode.able.R
import io.github.uditkarode.able.utils.ViewMode
import io.github.uditkarode.able.model.discovery.ArtistSuggestion
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import java.text.NumberFormat
import java.util.Locale

/**
 * Renders Last.fm discovery suggestions (tracks and artists) reusing the
 * `rv_ytm_result` row layout. Items is a heterogeneous [List] of
 * [TrackSuggestion] / [ArtistSuggestion].
 */
class DiscoverResultAdapter(
    private val items: List<Any>,
    private val onTrackPressed: (TrackSuggestion) -> Unit,
    private val onArtistPressed: (ArtistSuggestion) -> Unit
) : RecyclerView.Adapter<DiscoverResultAdapter.RVVH>() {

    private val numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

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

        holder.songName.typeface =
            Typeface.createFromAsset(context.assets, "fonts/interbold.otf")
        holder.songUploader.typeface =
            Typeface.createFromAsset(context.assets, "fonts/inter.otf")

        val artUrl: String
        when (current) {
            is ArtistSuggestion -> {
                holder.songName.text = current.name
                val listeners = current.listeners.toLongOrNull()
                val listenersTxt = if (listeners != null)
                    " • " + numberFormat.format(listeners)
                else ""
                holder.songUploader.text = "Artist$listenersTxt"
                artUrl = current.image
                holder.itemView.setOnClickListener { onArtistPressed(current) }
            }

            is TrackSuggestion -> {
                holder.songName.text = current.name
                holder.songUploader.text = current.artist
                artUrl = current.image
                holder.itemView.setOnClickListener { onTrackPressed(current) }
            }

            else -> {
                holder.itemView.setOnClickListener(null)
                artUrl = ""
            }
        }

        holder.songAlbumArt.run {
            if (artUrl.isNotBlank())
                Glide.with(context).load(artUrl).into(this)
            else
                Glide.with(context).load(R.drawable.def_albart).into(this)
        }
    }

    class RVVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val songName = itemView.findViewById<TextView>(R.id.vid_song)!!
        val songUploader = itemView.findViewById<TextView>(R.id.vid_uploader)!!
        val songAlbumArt = itemView.findViewById<ImageView>(R.id.vid_albart)!!
    }
}
