package lavalink.server.metrics;

import ch.qos.logback.classic.LoggerContext;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.lava.extensions.youtuberotator.planner.AbstractRoutePlanner;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.logback.InstrumentedAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.management.NotificationEmitter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Optional;

/**
 * Created by napster on 08.05.18.
 */
@Component
@ConditionalOnProperty("metrics.prometheus.enabled")
public class PrometheusMetrics {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMetrics.class);

    public PrometheusMetrics(ApplicationContext context,
                             AudioPlayerManager playerManager,
                             Optional<AbstractRoutePlanner> routePlanner) {

        InstrumentedAppender prometheusAppender = new InstrumentedAppender();
        //log metrics
        final LoggerContext factory = (LoggerContext) LoggerFactory.getILoggerFactory();
        final ch.qos.logback.classic.Logger root = factory.getLogger(Logger.ROOT_LOGGER_NAME);
        prometheusAppender.setContext(root.getLoggerContext());
        prometheusAppender.start();
        root.addAppender(prometheusAppender);

        //jvm (hotspot) metrics
        DefaultExports.initialize();

        // lavaplayer-related metrics
        new LavaplayerExports(playerManager).register();

        // node-related metrics
        new NodeInfoExports(context).register();

        // Route Planner exports
        routePlanner.ifPresent(e -> new RoutePlannerExports(e).register());

        //gc pause buckets
        final GcNotificationListener gcNotificationListener = new GcNotificationListener();
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter) {
                ((NotificationEmitter) gcBean).addNotificationListener(gcNotificationListener, null, gcBean);
            }
        }

        log.info("Prometheus metrics set up");
    }
}
