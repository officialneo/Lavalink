package lavalink.server.cache

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSearchProvider
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import lavalink.server.extensions.LoggerDelegate
import java.util.function.Function

class YouTubeApiSearchProvider(val youTubeService: YouTubeService,
                               val cacheConfig: CacheConfig) : YoutubeSearchProvider() {

    companion object {
        val log by LoggerDelegate()
    }

    override fun loadSearchResult(query: String, trackFactory: Function<AudioTrackInfo, AudioTrack>): AudioItem? {
        if (cacheConfig.isYouTubeApiEnabled) {
            try {
                val track = youTubeService.searchTrackInfo(query) ?: return AudioReference.NO_TRACK
                return trackFactory.apply(track)
            } catch (e: Exception) {
                log.warn("Can't search YouTube API", e)
            }
        }
        return super.loadSearchResult(query, trackFactory)
    }
}