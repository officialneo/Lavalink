package lavalink.server.metrics

import io.prometheus.client.Collector
import io.prometheus.client.GaugeMetricFamily
import org.springframework.context.ApplicationContext
import java.util.*

class NodeInfoExports(private val context: ApplicationContext) : Collector() {

    override fun collect(): List<MetricFamilySamples> {
        val samples = ArrayList<MetricFamilySamples>()

        val jvmInfo = GaugeMetricFamily(
                "lavalink_instance_info",
                "Lavalink Instance Info",
                listOf("node_name"))
        jvmInfo.addMetric(
                listOf(context.environment.getProperty("lavalink.instanceName", "unknown")),
                1.0)
        samples.add(jvmInfo)

        return samples
    }
}
