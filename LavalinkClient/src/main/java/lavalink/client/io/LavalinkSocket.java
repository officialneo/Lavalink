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

package lavalink.client.io;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import lavalink.client.LavalinkUtil;
import lavalink.client.player.LavalinkPlayer;
import lavalink.client.player.event.PlayerEvent;
import lavalink.client.player.event.TrackEndEvent;
import lavalink.client.player.event.TrackExceptionEvent;
import lavalink.client.player.event.TrackStuckEvent;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.channels.NotYetConnectedException;
import java.util.Map;
import java.util.Objects;

public class LavalinkSocket extends ReusableWebSocket {

    private static final Logger log = LoggerFactory.getLogger(LavalinkSocket.class);

    private static final int TIMEOUT_MS = 5000;
    @Nonnull
    private final String name;
    @Nonnull
    private final Lavalink lavalink;
    @Nullable
    private RemoteStats stats;
    long lastReconnectAttempt = 0;
    private int reconnectsAttempted = 0;
    @Nonnull
    private final URI remoteUri;
    private boolean available = false;

    LavalinkSocket(@Nonnull String name, @Nonnull Lavalink lavalink, @Nonnull URI serverUri, Draft protocolDraft, Map<String, String> headers) {
        super(serverUri, protocolDraft, headers, TIMEOUT_MS);
        this.name = name;
        this.lavalink = lavalink;
        this.remoteUri = serverUri;
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        log.info("Received handshake from server");
        available = true;
        lavalink.loadBalancer.onNodeConnect(this);
        reconnectsAttempted = 0;
    }

    @Override
    public void onMessage(String message) {
        JSONObject json = new JSONObject(message);

        if (!Objects.equals(json.getString("op"), "playerUpdate")) {
            log.debug(message);
        }

        switch (json.getString("op")) {
            case "playerUpdate":
                lavalink.getLink(json.getString("guildId"))
                        .getPlayer()
                        .provideState(json.getJSONObject("state"));
                break;
            case "stats":
                stats = new RemoteStats(json);
                break;
            case "event":
                try {
                    handleEvent(json);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            default:
                log.warn("Unexpected operation: " + json.getString("op"));
                break;
        }
    }

    /**
     * Implementation details:
     * The only events extending {@link lavalink.client.player.event.PlayerEvent} produced by the remote server are these:
     * 1. TrackEndEvent
     * 2. TrackExceptionEvent
     * 3. TrackStuckEvent
     * <p>
     * The remaining are caused by the client
     */
    private void handleEvent(JSONObject json) throws IOException {
        LavalinkPlayer player = lavalink.getLink(json.getString("guildId")).getPlayer();
        PlayerEvent event = null;

        switch (json.getString("type")) {
            case "TrackEndEvent":
                event = new TrackEndEvent(player,
                        LavalinkUtil.toAudioTrack(json.getString("track")),
                        AudioTrackEndReason.valueOf(json.getString("reason"))
                );
                break;
            case "TrackExceptionEvent":
                event = new TrackExceptionEvent(player,
                        LavalinkUtil.toAudioTrack(json.getString("track")),
                        new RemoteTrackException(json.getString("error"))
                );
                break;
            case "TrackStuckEvent":
                event = new TrackStuckEvent(player,
                        LavalinkUtil.toAudioTrack(json.getString("track")),
                        json.getLong("thresholdMs")
                );
                break;
            default:
                log.warn("Unexpected event type: " + json.getString("type"));
                break;
        }

        if (event != null) player.emitEvent(event);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        available = false;
        reason = reason == null ? "<no reason given>" : reason;
        if (code == 1000) {
            log.info("Connection to " + getRemoteUri() + " closed gracefully with reason: " + reason + " :: Remote=" + remote);
        } else {
            log.warn("Connection to " + getRemoteUri() + " closed unexpectedly with reason " + code + ": " + reason + " :: Remote=" + remote);
        }

        lavalink.loadBalancer.onNodeDisconnect(this);
    }

    @Override
    public void onError(Exception ex) {
        if (ex instanceof ConnectException) {
            log.warn("Failed to connect to " + getRemoteUri() + ", retrying in " + getReconnectInterval()/1000 + " seconds.");
            return;
        }

        log.error("Caught exception in websocket", ex);
    }

    @Override
    public void send(String text) throws NotYetConnectedException {
        // Note: If we lose connection we will reconnect and initialize properly
        if (isOpen()) {
            super.send(text);
        } else if (isConnecting()) {
            log.warn("Attempting to send messages to " + getRemoteUri() + " WHILE connecting. Ignoring.");
        }
    }

    @Nonnull
    @SuppressWarnings("unused")
    public URI getRemoteUri() {
        return remoteUri;
    }

    void attemptReconnect() {
        lastReconnectAttempt = System.currentTimeMillis();
        reconnectsAttempted++;
        connect();
    }

    long getReconnectInterval() {
        return reconnectsAttempted * 2000 - 2000;
    }

    @Nullable
    public RemoteStats getStats() {
        return stats;
    }

    public boolean isAvailable() {
        return available && isOpen() && !isClosing();
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "LavalinkSocket{" +
                "name=" + name +
                ",remoteUri=" + remoteUri +
                '}';
    }
}
