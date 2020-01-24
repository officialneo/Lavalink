package lavalink.server.cache

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.SearchResult
import com.google.api.services.youtube.model.Video
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.lang.IllegalStateException
import java.time.Duration
import javax.annotation.PostConstruct
import com.google.api.services.youtube.model.Playlist
import java.util.ArrayList
import org.bouncycastle.asn1.x500.style.RFC4519Style.title
import com.google.api.services.youtube.model.PlaylistItem
import java.util.stream.Collectors
import java.io.IOException










@Service
class YouTubeServiceImpl : YouTubeService {

    @Volatile
    private var keyCursor = 0

    @Autowired
    private lateinit var cacheConfig: CacheConfig

    private lateinit var youTube: YouTube

    val apiKey: String?
        @Synchronized get() {
            if (cacheConfig.youTubeApiKeys.isNullOrEmpty()) {
                return null
            }
            if (keyCursor >= cacheConfig.youTubeApiKeys!!.size) {
                keyCursor = 0
            }
            return cacheConfig.youTubeApiKeys!![keyCursor++]
        }

    @PostConstruct
    fun init() {
        youTube = YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance()) { _ -> }
                .setApplicationName(YouTubeServiceImpl::class.java.simpleName)
                .build()
    }

    override fun search(query: String, maxResults: Long): List<SearchResult> {
        return youTube.search().list("snippet")
                .setKey(apiKey ?: throw IllegalStateException("No API key Found!"))
                .setQ(query)
                .setType("video")
                .setFields("items(id/kind,id/videoId,snippet/title)")
                .setMaxResults(maxResults)
                .execute().items;
    }

    override fun searchTrackInfo(query: String): AudioTrackInfo? {
        val results = search(query, 1)
        if (results.isNullOrEmpty()) {
            return null
        }
        return getTrackInfoById(results[0].id.videoId)
    }

    override fun getPlaylistName(id: String): String? {
        val result = youTube.playlists()
                .list("snippet")
                .setId(id)
                .setKey(apiKey ?: throw IllegalStateException("No API key Found!"))
                .execute()
                .items
        return if (result.isNotEmpty()) result[0].snippet.title else null
    }

    override fun getPlaylistPageById(id: String, cursor: String?, withName: Boolean): YouTubePlaylistInfo? {
        val name = (if (withName) getPlaylistName(id) else "") ?: return null
        val response = youTube.playlistItems()
                .list("snippet,contentDetails")
                .setPageToken(cursor)
                .setPlaylistId(id)
                .setMaxResults(20L)
                .setKey(apiKey ?: throw IllegalStateException("No API key Found!"))
                .execute()
        val items = response.items
        if (items.isEmpty()) {
            return YouTubePlaylistInfo(id, name, null, listOf())
        }
        val tracks = items.map { playListItemToTrackInfo(it)!! }
        return YouTubePlaylistInfo(id, name, response.nextPageToken, tracks)
    }

    override fun getVideoById(videoId: String): Video? {
        val items = buildVideoList(videoId).setMaxResults(1L).execute().items
        return if (!items.isNullOrEmpty()) items[0] else null
    }

    override fun getTrackById(videoId: String, sourceManager: YoutubeAudioSourceManager): AudioItem? {
        val info = getTrackInfoById(videoId) ?: return null
        return YoutubeAudioTrack(info, sourceManager)
    }

    override fun getTrackInfoById(videoId: String): AudioTrackInfo? {
        val video = getVideoById(videoId) ?: return null
        return videoToTrackInfo(video)
    }

    fun videoToTrackInfo(video: Video): AudioTrackInfo {
        val snippet = video.snippet
        val details = video.contentDetails

        return AudioTrackInfo(
                snippet.title,
                snippet.channelTitle,
                Duration.parse(details.duration).toMillis(),
                video.id,
                false,
                "https://www.youtube.com/watch?v=" + video.id,
                mapOf("artworkUrl" to "https://img.youtube.com/vi/${video.id}/0.jpg")
        )
    }

    fun playListItemToTrackInfo(playlistItem: PlaylistItem): AudioTrackInfo? {
        val videoId = playlistItem.contentDetails.videoId
        return getTrackInfoById(videoId)
    }

    private fun buildVideoList(videoIds: String): YouTube.Videos.List {
        return youTube.videos().list("id,snippet,contentDetails")
                .setId(videoIds)
                .setKey(apiKey ?: throw IllegalStateException("No API key Found!"))
                .setFields("items(id/*,snippet/title,snippet/channelTitle,contentDetails/duration)")
    }
}