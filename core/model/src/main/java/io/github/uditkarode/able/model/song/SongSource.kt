/*
    Copyright 2026 AbleMusicPlayer contributors

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

package io.github.uditkarode.able.model.song

/**
 * Where a [Song] came from. USER songs are picked by the user (library,
 * queue, playlists, the Player's discovery sheet); DISCOVER songs are the
 * automatic suggestions appended by the end-of-queue radio in MusicService.
 *
 * The distinction lives on the object itself because the play queue mixes
 * both lists: the service derives the radio handoff from it and the queue
 * UI renders the Discover suggestions under their own section label. Songs
 * added manually from the Player's discovery sheet stay USER — only the
 * automatic end-of-queue radio flags DISCOVER.
 */
enum class SongSource { USER, DISCOVER }
