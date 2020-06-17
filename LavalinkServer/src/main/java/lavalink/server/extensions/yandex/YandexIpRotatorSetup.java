package lavalink.server.extensions.yandex;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.yamusic.YandexHttpContextFilter;
import com.sedmelluq.discord.lavaplayer.source.yamusic.YandexMusicAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.lava.extensions.youtuberotator.planner.AbstractRoutePlanner;

import java.util.ArrayList;
import java.util.List;

public class YandexIpRotatorSetup {
  private static final int DEFAULT_RETRY_LIMIT = 4;
  private static final HttpContextFilter DEFAULT_DELEGATE = new YandexHttpContextFilter();
  private static final YandexIpRotatorRetryHandler RETRY_HANDLER = new YandexIpRotatorRetryHandler();

  private final AbstractRoutePlanner routePlanner;
  private final List<ExtendedHttpConfigurable> mainConfiguration;
  private int retryLimit = DEFAULT_RETRY_LIMIT;
  private HttpContextFilter mainDelegate = DEFAULT_DELEGATE;

  public YandexIpRotatorSetup(AbstractRoutePlanner routePlanner) {
    this.routePlanner = routePlanner;
    mainConfiguration = new ArrayList<>();
  }

  public YandexIpRotatorSetup forConfiguration(ExtendedHttpConfigurable configurable) {
    mainConfiguration.add(configurable);
    return this;
  }

  public YandexIpRotatorSetup forSource(YandexMusicAudioSourceManager sourceManager) {
    forConfiguration(sourceManager.getSearchHttpConfiguration());
    forConfiguration(sourceManager.getPlaylistLHttpConfiguration());
    forConfiguration(sourceManager.getTrackLHttpConfiguration());
    forConfiguration(sourceManager.getDirectUrlLHttpConfiguration());
    return this;
  }

  public YandexIpRotatorSetup forManager(AudioPlayerManager playerManager) {
    YandexMusicAudioSourceManager sourceManager = playerManager.source(YandexMusicAudioSourceManager.class);
    if (sourceManager != null) {
      forSource(sourceManager);
    }
    return this;
  }

  public YandexIpRotatorSetup withRetryLimit(int retryLimit) {
    this.retryLimit = retryLimit;
    return this;
  }

  public YandexIpRotatorSetup withMainDelegateFilter(HttpContextFilter filter) {
    this.mainDelegate = filter;
    return this;
  }

  public void setup() {
    apply(mainConfiguration, new YandexIpRotatorFilter(mainDelegate, routePlanner, retryLimit));
  }

  protected void apply(List<ExtendedHttpConfigurable> configurables, YandexIpRotatorFilter filter) {
    for (ExtendedHttpConfigurable configurable : configurables) {
      configurable.configureBuilder(it -> {
        it.setRoutePlanner(routePlanner);
        // No retry for some exceptions we know are hopeless for retry.
        it.setRetryHandler(RETRY_HANDLER);
        // Regularly cleans up per-route connection pool which gets huge due to many routes caused by
        // each request having an unique route.
        it.evictExpiredConnections();
      });

      configurable.setHttpContextFilter(filter);
    }
  }
}
