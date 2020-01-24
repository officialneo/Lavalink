package lavalink.server.cache

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import net.notfab.caching.shared.CacheResponse

interface CacheService {

    fun getTrackById(id: String, sourceManager: AudioSourceManager): AudioTrack?

    fun getTrackInfoById(id: String): AudioTrackInfo?

    fun get(id: String): CacheResponse?

    fun addToIndex(playlist: AudioPlaylist)

    fun addToIndex(track: AudioTrack)
}