package lavalink.server.metrics

import com.sedmelluq.lava.extensions.youtuberotator.planner.AbstractRoutePlanner
import io.prometheus.client.Collector
import io.prometheus.client.CounterMetricFamily
import java.util.*

class RoutePlannerExports(private val planner: AbstractRoutePlanner) : Collector() {

    override fun collect(): List<MetricFamilySamples> {
        val samples = ArrayList<MetricFamilySamples>()
        samples.add(CounterMetricFamily("lavalink_routeplanner_total_addresses_count",
                "Total lavalink route planner addresses",
                planner.ipBlock.size.toDouble()))
        samples.add(CounterMetricFamily("lavalink_routeplanner_failed_addresses_count",
                "Failed lavalink route planner addresses",
                planner.failingAddresses.size.toDouble()))
        return samples
    }
}
