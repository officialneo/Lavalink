/*
 * This file is part of JuniperBot.
 *
 * JuniperBot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * JuniperBot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with JuniperBot. If not, see <http://www.gnu.org/licenses/>.
 */
package lavalink.server

import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.config.client.ConfigClientProperties
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.get
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class SSLConfigServiceBootstrapConfiguration(
        @Autowired private val properties: ConfigClientProperties,
        @Autowired private val context: ApplicationContext
) {

    @Bean
    fun configServicePropertySourceLocator(): ConfigServicePropertySourceLocator {
        val configServicePropertySourceLocator = ConfigServicePropertySourceLocator(properties)

        val keyStoreEnv = context.environment["SERVER_SSL_KEY_STORE"]
        val keyStorePasswordEnv = context.environment["SERVER_SSL_KEY_STORE_PASSWORD"]
        val keyPasswordEnv = context.environment["SERVER_SSL_KEY_PASSWORD"]

        val restTemplate: RestTemplate
        if (!keyStoreEnv.isNullOrBlank() && !keyStorePasswordEnv.isNullOrBlank() && !keyPasswordEnv.isNullOrBlank() ) {
            val resource = context.getResource(keyStoreEnv)
            val storePassword = keyStorePasswordEnv.toCharArray()
            val keyPassword = keyPasswordEnv.toCharArray()

            val sslContext = SSLContexts.custom()
                    .loadKeyMaterial(resource.file, storePassword, keyPassword)
                    .loadTrustMaterial(resource.file, storePassword, TrustSelfSignedStrategy()).build()
            val httpClient = HttpClients.custom()
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier { _, _ -> true }
                    .build()
            val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)
            restTemplate = RestTemplate(requestFactory)
        } else {
            restTemplate = RestTemplate()
        }
        configServicePropertySourceLocator.setRestTemplate(restTemplate)
        return configServicePropertySourceLocator
    }
}