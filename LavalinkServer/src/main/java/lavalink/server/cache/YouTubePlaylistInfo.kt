package lavalink.server.cache

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo

data class YouTubePlaylistInfo(
        val id: String,
        val title: String,
        val cursor: String?,
        val tracks: List<AudioTrackInfo>)