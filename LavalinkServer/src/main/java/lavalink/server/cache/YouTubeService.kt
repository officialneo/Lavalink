package lavalink.server.cache

import com.google.api.services.youtube.model.SearchResult
import com.google.api.services.youtube.model.Video
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo

interface YouTubeService {

    fun getVideoById(videoId: String): Video?

    fun getTrackById(videoId: String, sourceManager: YoutubeAudioSourceManager): AudioItem?

    fun getTrackInfoById(videoId: String): AudioTrackInfo?

    fun search(query: String, maxResults: Long): List<SearchResult>

    fun searchTrackInfo(query: String): AudioTrackInfo?

    fun getPlaylistPageById(id: String, cursor: String?, withName: Boolean): YouTubePlaylistInfo?

    fun getPlaylistName(id: String): String?
}