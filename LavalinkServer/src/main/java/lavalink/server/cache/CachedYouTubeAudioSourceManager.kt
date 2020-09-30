package lavalink.server.cache

import com.sedmelluq.discord.lavaplayer.source.youtube.*
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import lavalink.server.extensions.LoggerDelegate
import java.lang.Exception


class CachedYouTubeAudioSourceManager(allowSearch: Boolean,
                                      val cacheService: CacheService,
                                      val cacheConfig: CacheConfig,
                                      val youTubeService: YouTubeService)
    : YoutubeAudioSourceManager(
        allowSearch,
        DefaultYoutubeTrackDetailsLoader(),
        YouTubeApiSearchProvider(youTubeService, cacheConfig),
        YoutubeChannelProvider(),
        YoutubeSignatureCipherManager(),
        YouTubeApiPlaylistLoader(youTubeService, cacheService, cacheConfig),
        DefaultYoutubeLinkRouter(),
        YoutubeMixProvider()
) {

    companion object {
        val log by LoggerDelegate()
    }

    override fun loadTrackWithVideoId(videoId: String, mustExist: Boolean): AudioItem {
        val track = this.cacheService.getTrackById(videoId, this)
        if (track != null) {
            return track
        }
        if (cacheConfig.isYouTubeApiEnabled) {
            try {
                val audioItem = this.youTubeService.getTrackById(videoId, this)
                if (audioItem != null) {
                    return audioItem
                }
            } catch (e: Exception) {
                log.warn("Could not load track from youTube API", e)
            }
        }
        return super.loadTrackWithVideoId(videoId, mustExist)
    }
}