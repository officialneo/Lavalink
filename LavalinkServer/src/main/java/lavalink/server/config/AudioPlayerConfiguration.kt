package lavalink.server.config

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.soundcloud.*
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.yamusic.YandexHttpContextFilter
import com.sedmelluq.discord.lavaplayer.source.yamusic.YandexMusicAudioSourceManager
import com.sedmelluq.lava.extensions.youtuberotator.YoutubeIpRotatorSetup
import com.sedmelluq.lava.extensions.youtuberotator.planner.AbstractRoutePlanner
import com.sedmelluq.lava.extensions.youtuberotator.planner.BalancingIpRoutePlanner
import com.sedmelluq.lava.extensions.youtuberotator.planner.NanoIpRoutePlanner
import com.sedmelluq.lava.extensions.youtuberotator.planner.RotatingIpRoutePlanner
import com.sedmelluq.lava.extensions.youtuberotator.planner.RotatingNanoIpRoutePlanner
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv4Block
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv6Block
import lavalink.server.cache.CacheConfig
import lavalink.server.cache.CacheService
import lavalink.server.cache.CachedYouTubeAudioSourceManager
import lavalink.server.cache.YouTubeService
import lavalink.server.extensions.yandex.YandexIpRotatorSetup
import lavalink.server.util.RotatingIpv4RoutePlanner
import org.apache.http.HttpHost
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.net.InetAddress
import java.util.function.Predicate

/**
 * Created by napster on 05.03.18.
 */
@Configuration
class AudioPlayerConfiguration {

    private val log = LoggerFactory.getLogger(AudioPlayerConfiguration::class.java)

    companion object {
        const val YANDEX_ROUTE_PLANNER = "yandex"
    }

    @Bean
    fun audioPlayerManagerSupplier(sources: AudioSourcesConfig,
                                   serverConfig: ServerConfig,
                                   cacheConfig: CacheConfig,
                                   cacheService: CacheService,
                                   youTubeService: YouTubeService,
                                   routePlanner: AbstractRoutePlanner?,
                                   @Qualifier(YANDEX_ROUTE_PLANNER) yandexRoutePlanner: AbstractRoutePlanner?): AudioPlayerManager {
        val audioPlayerManager = DefaultAudioPlayerManager()

        if (serverConfig.isGcWarnings) {
            audioPlayerManager.enableGcMonitoring()
        }

        if (sources.isYoutube) {
            val youtube = CachedYouTubeAudioSourceManager(serverConfig.isYoutubeSearchEnabled,
                    cacheService,
                    cacheConfig,
                    youTubeService)
            if (routePlanner != null) {
                var retryLimit = serverConfig.ratelimit?.retryLimit ?: -1
                if (retryLimit == 0) {
                    retryLimit = Int.MAX_VALUE
                }
                val builder = YoutubeIpRotatorSetup(routePlanner).forSource(youtube)
                if (retryLimit > 0) {
                    builder.withRetryLimit(retryLimit)
                }
                builder.setup()
            }
            val playlistLoadLimit = serverConfig.youtubePlaylistLoadLimit
            if (playlistLoadLimit != null) youtube.setPlaylistPageCount(playlistLoadLimit)
            audioPlayerManager.registerSourceManager(youtube)
        }
        if (sources.isSoundcloud) {
            val dataReader = DefaultSoundCloudDataReader();
            val htmlDataLoader = DefaultSoundCloudHtmlDataLoader();
            val formatHandler = DefaultSoundCloudFormatHandler();

            audioPlayerManager.registerSourceManager(SoundCloudAudioSourceManager(
                    serverConfig.isSoundcloudSearchEnabled,
                    dataReader,
                    htmlDataLoader,
                    formatHandler,
                    DefaultSoundCloudPlaylistLoader(htmlDataLoader, dataReader, formatHandler)
            ));
        }
        if (sources.isBandcamp) audioPlayerManager.registerSourceManager(BandcampAudioSourceManager())
        if (sources.isTwitch) audioPlayerManager.registerSourceManager(TwitchStreamAudioSourceManager())
        if (sources.isVimeo) audioPlayerManager.registerSourceManager(VimeoAudioSourceManager())
        if (sources.isMixer) audioPlayerManager.registerSourceManager(BeamAudioSourceManager())

        val yandex = sources.yandex
        if (yandex.isEnabled) {
            val yandexSourceManager = YandexMusicAudioSourceManager()
            if (yandex.proxyHost.isNotBlank()) {
                yandexSourceManager.configureBuilder { builder -> builder.setProxy(HttpHost(yandex.proxyHost, yandex.proxyPort)) }
            }
            if (yandex.token.isNotBlank()) {
                YandexHttpContextFilter.setOAuthToken(yandex.token)
            }
            yandexRoutePlanner?.let { it ->
                var retryLimit = sources.yandex.ratelimit?.retryLimit ?: -1
                if (retryLimit == 0) {
                    retryLimit = Int.MAX_VALUE
                }
                val builder = YandexIpRotatorSetup(it).forSource(yandexSourceManager)
                if (retryLimit > 0) {
                    builder.withRetryLimit(retryLimit)
                }
                builder.setup()
            }
            audioPlayerManager.registerSourceManager(yandexSourceManager)
        }
        if (sources.isHttp) audioPlayerManager.registerSourceManager(HttpAudioSourceManager())
        if (sources.isLocal) audioPlayerManager.registerSourceManager(LocalAudioSourceManager())

        audioPlayerManager.configuration.isFilterHotSwapEnabled = true

        return audioPlayerManager
    }

    @Bean
    @Primary
    fun routePlanner(serverConfig: ServerConfig): AbstractRoutePlanner? {
        return getRoutePlanner(serverConfig.ratelimit, "primary")
    }

    @Bean
    @Qualifier(YANDEX_ROUTE_PLANNER)
    fun yandexRoutePlanner(audioSourcesConfig: AudioSourcesConfig): AbstractRoutePlanner? {
        return getRoutePlanner(audioSourcesConfig.yandex.ratelimit, "yandex")
    }

    private fun getRoutePlanner(rateLimitConfig: RateLimitConfig?, name: String): AbstractRoutePlanner? {
        if (rateLimitConfig == null) {
            log.debug("No $name rate limit config block found, skipping setup of route planner")
            return null
        }
        val ipBlockList = rateLimitConfig.ipBlocks
        if (ipBlockList.isEmpty()) {
            log.info("List of ip blocks is empty, skipping setup of $name route planner")
            return null
        }

        val blacklisted = rateLimitConfig.excludedIps.map { InetAddress.getByName(it) }
        val filter = Predicate<InetAddress> {
            !blacklisted.contains(it)
        }
        val ipBlocks = ipBlockList.map {
            when {
                Ipv4Block.isIpv4CidrBlock(it) -> Ipv4Block(it)
                Ipv6Block.isIpv6CidrBlock(it) -> Ipv6Block(it)
                else -> throw RuntimeException("Invalid IP Block '$it' for $name, make sure to provide a valid CIDR notation")
            }
        }

        return when (rateLimitConfig.strategy.toLowerCase().trim()) {
            "rotateonban" -> RotatingIpRoutePlanner(ipBlocks, filter, rateLimitConfig.searchTriggersFail)
            "rotateonbanipv4_32" -> RotatingIpv4RoutePlanner(ipBlocks, filter, rateLimitConfig.searchTriggersFail)
            "loadbalance" -> BalancingIpRoutePlanner(ipBlocks, filter, rateLimitConfig.searchTriggersFail)
            "nanoswitch" -> NanoIpRoutePlanner(ipBlocks, rateLimitConfig.searchTriggersFail)
            "rotatingnanoswitch" -> RotatingNanoIpRoutePlanner(ipBlocks, filter, rateLimitConfig.searchTriggersFail)
            else -> throw RuntimeException("Unknown strategy!")
        }
    }
}
