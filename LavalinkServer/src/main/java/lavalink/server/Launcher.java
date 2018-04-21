/*
 * Copyright (c) 2017 Frederik Ar. Mikkelsen & NoobLance
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package lavalink.server;

import ch.qos.logback.classic.LoggerContext;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.logback.SentryAppender;
import lavalink.server.config.ServerConfig;
import lavalink.server.io.SocketServer;
import lavalink.server.util.SimpleLogToSLF4JAdapter;
import net.dv8tion.jda.utils.SimpleLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.util.Properties;

@SpringBootApplication
@ComponentScan
public class Launcher {

    private static final Logger log = LoggerFactory.getLogger(Launcher.class);

    public final static long startTime = System.currentTimeMillis();

    public static void main(String[] args) {
        SpringApplication sa = new SpringApplication(Launcher.class);
        sa.setWebApplicationType(WebApplicationType.SERVLET);
        sa.run(args);
    }

    public Launcher(ServerConfig serverConfig, SocketServer socketServer) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered");
            try {
                socketServer.stop(30);
            } catch (InterruptedException e) {
                log.warn("Interrupted while stopping socket server", e);
            }
        }, "shutdown hook"));

        SimpleLog.LEVEL = SimpleLog.Level.OFF;
        SimpleLog.addListener(new SimpleLogToSLF4JAdapter());
        initSentry(serverConfig);
    }

    private void initSentry(ServerConfig serverConfig) {
        String sentryDsn = serverConfig.getSentryDsn();
        if (sentryDsn == null || sentryDsn.isEmpty()) {
            log.info("No sentry dsn found, turning off sentry.");
            turnOffSentry();
            return;
        }
        SentryClient sentryClient = Sentry.init(sentryDsn);
        log.info("Set up sentry.");

        // set the git commit hash this was build on as the release
        Properties gitProps = new Properties();
        try {
            gitProps.load(Launcher.class.getClassLoader().getResourceAsStream("git.properties"));
        } catch (NullPointerException | IOException e) {
            log.error("Failed to load git repo information", e);
        }

        String commitHash = gitProps.getProperty("git.commit.id");
        if (commitHash != null && !commitHash.isEmpty()) {
            log.info("Setting sentry release to commit hash {}", commitHash);
            sentryClient.setRelease(commitHash);
        } else {
            log.warn("No git commit hash found to set up sentry release");
        }
    }

    private void turnOffSentry() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        SentryAppender sentryAppender = (SentryAppender) lc.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("SENTRY");
        Sentry.close();
        sentryAppender.stop();
    }
}
