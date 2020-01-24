package lavalink.server.cache

import com.sedmelluq.discord.lavaplayer.source.youtube.DefaultYoutubePlaylistLoader
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist
import lavalink.server.extensions.LoggerDelegate
import java.util.function.Function


class YouTubeApiPlaylistLoader(val youTubeService: YouTubeService,
                               val cacheConfig: CacheConfig) : DefaultYoutubePlaylistLoader() {

    companion object {
        val log by LoggerDelegate()
    }

    private var playlistPageCount: Int = 6

    override fun load(httpInterface: HttpInterface?,
                      playlistId: String,
                      selectedVideoId: String?,
                      trackFactory: Function<AudioTrackInfo, AudioTrack>): AudioPlaylist {
        if (cacheConfig.isYouTubeApiEnabled) {
            try {
                val firstPage = youTubeService.getPlaylistPageById(playlistId, null, true)
                        ?: throw FriendlyException("This playlist does not exist", COMMON, null)
                return buildPlaylist(firstPage, playlistId, selectedVideoId, trackFactory)
            } catch (e: FriendlyException) {
                throw e // pass it next
            } catch (e: Exception) {
                log.warn("Can't search YouTube API", e)
            }
        }
        return super.load(httpInterface, playlistId, selectedVideoId, trackFactory)
    }

    private fun buildPlaylist(firstPage: YouTubePlaylistInfo,
                              playlistId: String,
                              selectedVideoId: String?,
                              trackFactory: Function<AudioTrackInfo, AudioTrack>): AudioPlaylist {
        val tracks = mutableListOf<AudioTrack>()
        firstPage.tracks
                .map { trackFactory.apply(it) }
                .forEach { tracks.add(it) }

        var cursor = firstPage.cursor
        var loadCount = 0
        val pageCount = playlistPageCount

        while (cursor != null && ++loadCount < pageCount) {
            cursor = fetchNextPage(cursor, playlistId, trackFactory, tracks)
        }

        return BasicAudioPlaylist(
                firstPage.title,
                tracks,
                getSelectedTrack(selectedVideoId, tracks),
                false
        )
    }

    private fun getSelectedTrack(selectedVideoId: String?, tracks: List<AudioTrack>): AudioTrack? {
        if (selectedVideoId == null) {
            return null
        }
        for (track in tracks) {
            if (selectedVideoId == track.identifier) {
                return track
            }
        }
        return null
    }

    private fun fetchNextPage(cursor: String,
                              playlistId: String,
                              trackFactory: Function<AudioTrackInfo, AudioTrack>,
                              tracks: MutableList<AudioTrack>): String? {
        val page = youTubeService.getPlaylistPageById(playlistId, cursor, false) ?: return null
        page.tracks.map { trackFactory.apply(it) }.forEach { tracks.add(it) }
        return page.cursor
    }

    override fun setPlaylistPageCount(playlistPageCount: Int) {
        this.playlistPageCount = playlistPageCount
    }
}