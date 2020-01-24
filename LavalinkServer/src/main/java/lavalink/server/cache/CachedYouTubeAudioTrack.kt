package lavalink.server.cache

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

class CachedYouTubeAudioTrack(track: AudioTrack, sourceManager: YoutubeAudioSourceManager)
    : YoutubeAudioTrack(track.info, sourceManager)