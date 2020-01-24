package lavalink.server.cache

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo

class CachedYouTubeAudioTrack(info: AudioTrackInfo, sourceManager: YoutubeAudioSourceManager)
    : YoutubeAudioTrack(info, sourceManager)