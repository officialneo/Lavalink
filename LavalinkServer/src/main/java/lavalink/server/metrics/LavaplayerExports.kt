package lavalink.server.metrics

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import io.prometheus.client.Collector
import io.prometheus.client.GaugeMetricFamily
import java.util.*
import java.util.concurrent.ThreadPoolExecutor

class LavaplayerExports(private val playerManager: AudioPlayerManager) : Collector() {

    override fun collect(): List<MetricFamilySamples> {
        val samples = ArrayList<MetricFamilySamples>()
        addThreadPoolSamples(samples)
        return samples
    }

    private fun addThreadPoolSamples(sampleFamilies: MutableList<MetricFamilySamples>) {
        val activeCount = GaugeMetricFamily(
                "lavaplayer_thread_pool_active_count",
                "Approximate number of threads that are actively in a given thread pool.",
                listOf("pool"))
        sampleFamilies.add(activeCount)
        val maximumPoolSize = GaugeMetricFamily(
                "lavaplayer_thread_pool_max_size",
                "Maximum allowed number of threads in a given thread pool.",
                listOf("pool"))
        sampleFamilies.add(maximumPoolSize)
        val poolSize = GaugeMetricFamily(
                "lavaplayer_thread_pool_current_size",
                "Current number of threads in a given thread pool.",
                listOf("pool"))
        sampleFamilies.add(poolSize)

        val addPool = { name: String, executor: ThreadPoolExecutor ->
            poolSize.addMetric(listOf(name), executor.poolSize.toDouble())
            activeCount.addMetric(listOf(name), executor.activeCount.toDouble())
            maximumPoolSize.addMetric(listOf(name), executor.maximumPoolSize.toDouble())
        }

        addPool("track_info", playerManager.trackInfoExecutor)
        addPool("track_playback", playerManager.trackPlaybackExecutor)
    }
}
