package lavalink.server.cache

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoBuilder
import net.notfab.caching.client.CacheClient
import net.notfab.caching.shared.Track
import net.notfab.caching.shared.YoutubeTrack
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import javax.annotation.PostConstruct


@Service
class CacheServiceImpl : CacheService {

    @Autowired
    private lateinit var config: CacheConfig

    private var client: CacheClient? = null

    @PostConstruct
    fun init() {
        if (config.isEnabled
                && !config.endpoint.isNullOrBlank()
                && !config.token.isNullOrBlank()) {
            client = CacheClient(config.endpoint, config.token,
                    Executors.newCachedThreadPool { r -> Thread(r, "Cache-Thread") })
        }
    }

    override fun getTrackById(id: String, sourceManager: AudioSourceManager): AudioTrack? {
        val info = getTrackInfoById(id) ?: return null
        return toTrack(sourceManager, info)
    }

    override fun getTrackInfoById(id: String): AudioTrackInfo? {
        val response = get(id)
        if (response == null || response.failure || response.track == null) {
            return null
        }
        val info = AudioTrackInfoBuilder.empty()
                .setTitle(response.track.title)
                .setAuthor(response.track.author)
                .setLength(response.track.length)
                .setIdentifier(response.track.id)
                .setIsStream(response.track.isStream)
                .setUri(response.track.toURL())
                .build()
        return convertTrackInfo(response.track, info)
    }

    private fun convertTrackInfo(track: Track, trackInfo: AudioTrackInfo): AudioTrackInfo? {
        if (!isValidInfo(trackInfo)) {
            return null
        }
        if (track is YoutubeTrack) {
            trackInfo.metadata["artworkUrl"] = "https://img.youtube.com/vi/${trackInfo.identifier}/0.jpg"
        }
        return trackInfo
    }

    private fun toTrack(sourceManager: AudioSourceManager, trackInfo: AudioTrackInfo): AudioTrack? {
        if (sourceManager is YoutubeAudioSourceManager) {
            return CachedYouTubeAudioTrack(trackInfo, sourceManager)
        }
        return null
    }

    private fun isValidInfo(trackInfo: AudioTrackInfo) = trackInfo.title != null && trackInfo.author != null

    override fun get(id: String) = if (client != null) client!![id] else null

    override fun addToIndex(playlist: AudioPlaylist) {
        if (client != null) {
            playlist.tracks.forEach { addToIndex(it) }
        }
    }

    override fun addToIndex(track: AudioTrack) {
        if (client != null && track !is CachedYouTubeAudioTrack && isValidInfo(track.info)) {
            client!!.addToIndex(track)
        }
    }
}