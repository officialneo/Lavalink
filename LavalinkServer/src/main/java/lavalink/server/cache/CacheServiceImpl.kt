package lavalink.server.cache

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.notfab.caching.client.CacheClient
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
        val response = get(id)
        if (response == null || response.failure || response.track == null) {
            return null
        }
        return convertTrack(sourceManager, response.track.toAudioTrack(sourceManager))
    }

    private fun convertTrack(sourceManager: AudioSourceManager, track: AudioTrack): AudioTrack {
        if (track is YoutubeAudioTrack) {
            track.info.metadata["artworkUrl"] = "https://img.youtube.com/vi/${track.info.identifier}/0.jpg"
            return CachedYouTubeAudioTrack(track, sourceManager as YoutubeAudioSourceManager)
        }
        return track
    }

    override fun get(id: String) = if (client != null) client!![id] else null

    override fun addToIndex(playlist: AudioPlaylist) {
        if (client != null) {
            playlist.tracks.forEach { addToIndex(it) }
        }
    }

    override fun addToIndex(track: AudioTrack) {
        if (client != null && track !is CachedYouTubeAudioTrack) {
            client!!.addToIndex(track)
        }
    }
}