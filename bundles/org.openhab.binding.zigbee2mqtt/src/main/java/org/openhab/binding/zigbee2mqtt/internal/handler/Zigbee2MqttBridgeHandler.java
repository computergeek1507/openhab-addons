/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.zigbee2mqtt.internal.handler;

import static org.openhab.binding.zigbee2mqtt.internal.Zigbee2MqttBindingConstants.*;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.zigbee2mqtt.internal.config.Zigbee2MqttBridgeConfig;
import org.openhab.binding.zigbee2mqtt.internal.discovery.Zigbee2MqttDiscoveryService;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttConnectionObserver;
import org.openhab.core.io.transport.mqtt.MqttConnectionState;
import org.openhab.core.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Bridge handler managing the MQTT broker connection and Zigbee2MQTT bridge monitoring.
 *
 * <p>
 * This handler:
 * <ul>
 * <li>Creates and owns the {@link MqttBrokerConnection} used by all child device handlers.</li>
 * <li>Monitors the Zigbee2MQTT bridge health via {@code <baseTopic>/bridge/state}.</li>
 * <li>Publishes device lists to the discovery service via {@code <baseTopic>/bridge/devices}.</li>
 * <li>Provides permit-join and log-level control channels.</li>
 * <li>Tracks device availability via {@code <baseTopic>/+/availability}.</li>
 * </ul>
 *
 * @author openHAB Contributors - Initial contribution
 */
@NonNullByDefault
public class Zigbee2MqttBridgeHandler extends BaseBridgeHandler
        implements MqttConnectionObserver, MqttMessageSubscriber {

    private final Logger logger = LoggerFactory.getLogger(Zigbee2MqttBridgeHandler.class);

    private @Nullable MqttBrokerConnection connection;
    private @Nullable Zigbee2MqttBridgeConfig config;
    private @Nullable Zigbee2MqttDiscoveryService discoveryService;
    private @Nullable ScheduledFuture<?> reconnectJob;

    public Zigbee2MqttBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(Zigbee2MqttDiscoveryService.class);
    }

    @Override
    public void initialize() {
        Zigbee2MqttBridgeConfig cfg = getConfigAs(Zigbee2MqttBridgeConfig.class);
        if (cfg.host.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error-missing-host");
            return;
        }
        config = cfg;
        updateStatus(ThingStatus.UNKNOWN);
        connect(cfg);
    }

    @Override
    public void dispose() {
        cancelReconnect();
        MqttBrokerConnection conn = connection;
        if (conn != null) {
            conn.removeConnectionObserver(this);
            Zigbee2MqttBridgeConfig cfg = config;
            if (cfg != null) {
                String base = cfg.baseTopic;
                conn.unsubscribe(base + "/bridge/state", this);
                conn.unsubscribe(base + "/bridge/devices", this);
                conn.unsubscribe(base + "/bridge/info", this);
                conn.unsubscribe(base + "/bridge/logging", this);
                conn.unsubscribe(base + "/bridge/response/#", this);
                conn.unsubscribe(base + "/+/availability", this);
            }
            conn.stop();
            connection = null;
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            requestBridgeInfo();
            return;
        }
        String channelId = channelUID.getId();
        if (CHANNEL_PERMIT_JOIN.equals(channelId) && command instanceof OnOffType onOff) {
            publishBridgeRequest("permit_join", "{\"value\":" + (onOff == OnOffType.ON) + "}");
        } else if (CHANNEL_LOG_LEVEL.equals(channelId) && command instanceof StringType str) {
            publishBridgeRequest("options", "{\"options\":{\"advanced\":{\"log_level\":\"" + str + "\"}}}");
        }
    }

    // -------------------------------------------------------------------------
    // MqttConnectionObserver
    // -------------------------------------------------------------------------

    @Override
    public void connectionStateChanged(MqttConnectionState state, @Nullable Throwable error) {
        logger.debug("MQTT connection state changed: {}", state);
        if (state == MqttConnectionState.CONNECTED) {
            cancelReconnect();
            subscribeToSystemTopics();
            updateStatus(ThingStatus.ONLINE);
            getThing().getThings().forEach(child -> {
                if (child.getHandler() instanceof Zigbee2MqttDeviceHandler dh) {
                    dh.onBridgeConnected();
                }
            });
        } else if (state == MqttConnectionState.DISCONNECTED) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    error != null ? error.getMessage() : "@text/offline.comm-error-disconnected");
            scheduleReconnect();
        }
    }

    // -------------------------------------------------------------------------
    // MqttMessageSubscriber
    // -------------------------------------------------------------------------

    @Override
    public void processMessage(String topic, byte[] payload) {
        Zigbee2MqttBridgeConfig cfg = config;
        if (cfg == null) {
            return;
        }
        String base = cfg.baseTopic;
        String message = new String(payload, StandardCharsets.UTF_8);

        if ((base + "/bridge/state").equals(topic)) {
            onBridgeState(message);
        } else if ((base + "/bridge/devices").equals(topic)) {
            onBridgeDevices(message);
        } else if ((base + "/bridge/info").equals(topic)) {
            onBridgeInfo(message);
        } else if ((base + "/bridge/logging").equals(topic)) {
            onBridgeLogging(message);
        } else if (topic.startsWith(base + "/bridge/response/")) {
            onBridgeResponse(topic, message);
        } else if (topic.endsWith("/availability")) {
            onDeviceAvailability(topic, message);
        }
    }

    // -------------------------------------------------------------------------
    // Public API used by device handlers and discovery
    // -------------------------------------------------------------------------

    public @Nullable MqttBrokerConnection getConnection() {
        return connection;
    }

    public String getBaseTopic() {
        Zigbee2MqttBridgeConfig cfg = config;
        return cfg != null ? cfg.baseTopic : "zigbee2mqtt";
    }

    public void setDiscoveryService(@Nullable Zigbee2MqttDiscoveryService service) {
        discoveryService = service;
    }

    public void requestDeviceList() {
        publishBridgeRequest("devices", "");
    }

    // -------------------------------------------------------------------------
    // Internal — connection management
    // -------------------------------------------------------------------------

    private void connect(Zigbee2MqttBridgeConfig cfg) {
        MqttBrokerConnection conn = new MqttBrokerConnection(cfg.host, cfg.port, cfg.secure,
                "openhab-z2m-" + getThing().getUID().getId().replace(':', '-'));
        if (!cfg.username.isBlank()) {
            conn.setCredentials(cfg.username, cfg.password);
        }
        conn.setKeepAliveInterval(cfg.keepAlive);
        conn.addConnectionObserver(this);
        connection = conn;

        conn.start().whenComplete((success, error) -> {
            if (Boolean.TRUE.equals(success)) {
                logger.debug("Connected to MQTT broker {}:{}", cfg.host, cfg.port);
            } else {
                String msg = error != null ? error.getMessage() : "connection failed";
                logger.warn("Failed to connect to MQTT broker: {}", msg);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, msg);
                scheduleReconnect();
            }
        });
    }

    private void subscribeToSystemTopics() {
        MqttBrokerConnection conn = connection;
        Zigbee2MqttBridgeConfig cfg = config;
        if (conn == null || cfg == null) {
            return;
        }
        String base = cfg.baseTopic;
        conn.subscribe(base + "/bridge/state", this);
        conn.subscribe(base + "/bridge/devices", this);
        conn.subscribe(base + "/bridge/info", this);
        conn.subscribe(base + "/bridge/logging", this);
        conn.subscribe(base + "/bridge/response/#", this);
        conn.subscribe(base + "/+/availability", this);
        requestBridgeInfo();
    }

    private void requestBridgeInfo() {
        publishBridgeRequest("info", "");
    }

    private void publishBridgeRequest(String command, String payload) {
        MqttBrokerConnection conn = connection;
        Zigbee2MqttBridgeConfig cfg = config;
        if (conn == null || cfg == null) {
            return;
        }
        byte[] data = payload.isEmpty() ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
        conn.publish(cfg.baseTopic + "/bridge/request/" + command, data, 1, false);
    }

    // -------------------------------------------------------------------------
    // Internal — message handlers
    // -------------------------------------------------------------------------

    private void onBridgeState(String message) {
        logger.debug("Zigbee2MQTT bridge state: {}", message);
        boolean online = message.contains("online");
        if (online) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "@text/offline.z2m-bridge-offline");
        }
    }

    private void onBridgeDevices(String devicesJson) {
        Zigbee2MqttDiscoveryService service = discoveryService;
        if (service != null) {
            service.onDevicesReceived(devicesJson);
        }
    }

    private void onBridgeInfo(String infoJson) {
        try {
            JsonObject info = JsonParser.parseString(infoJson).getAsJsonObject();

            JsonElement permitJoinEl = info.get("permit_join");
            if (permitJoinEl != null && permitJoinEl.isJsonPrimitive()) {
                updateState(CHANNEL_PERMIT_JOIN, OnOffType.from(permitJoinEl.getAsBoolean()));
            }

            JsonElement configEl = info.get("config");
            if (configEl != null && configEl.isJsonObject()) {
                JsonObject cfgObj = configEl.getAsJsonObject();
                JsonElement advancedEl = cfgObj.get("advanced");
                if (advancedEl != null && advancedEl.isJsonObject()) {
                    JsonElement logLevelEl = advancedEl.getAsJsonObject().get("log_level");
                    if (logLevelEl != null && logLevelEl.isJsonPrimitive()) {
                        updateState(CHANNEL_LOG_LEVEL, new StringType(logLevelEl.getAsString()));
                    }
                }
            }

            JsonElement versionEl = info.get("version");
            if (versionEl != null && versionEl.isJsonPrimitive()) {
                getThing().setProperty("z2mVersion", versionEl.getAsString());
            }
            JsonElement coordEl = info.get("coordinator");
            if (coordEl != null && coordEl.isJsonObject()) {
                JsonElement coordTypeEl = coordEl.getAsJsonObject().get("type");
                if (coordTypeEl != null && coordTypeEl.isJsonPrimitive()) {
                    getThing().setProperty("coordinatorType", coordTypeEl.getAsString());
                }
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            logger.debug("Could not parse bridge/info: {}", e.getMessage());
        }
    }

    private void onBridgeLogging(String logJson) {
        try {
            JsonObject log = JsonParser.parseString(logJson).getAsJsonObject();
            JsonElement levelEl = log.get("level");
            JsonElement messageEl = log.get("message");
            if (levelEl != null && messageEl != null) {
                String level = levelEl.getAsString();
                String msg = messageEl.getAsString();
                if ("error".equals(level)) {
                    logger.warn("Zigbee2MQTT: {}", msg);
                } else {
                    logger.debug("Zigbee2MQTT [{}]: {}", level, msg);
                }
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            logger.trace("Could not parse bridge/logging: {}", e.getMessage());
        }
    }

    private void onBridgeResponse(String topic, String message) {
        try {
            JsonObject response = JsonParser.parseString(message).getAsJsonObject();
            JsonElement statusEl = response.get("status");
            if (statusEl != null && !"ok".equals(statusEl.getAsString())) {
                JsonElement errorEl = response.get("error");
                String error = errorEl != null ? errorEl.getAsString() : "unknown error";
                logger.warn("Zigbee2MQTT bridge request failed on {}: {}", topic, error);
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            logger.trace("Could not parse bridge response on {}: {}", topic, e.getMessage());
        }
    }

    private void onDeviceAvailability(String topic, String message) {
        Zigbee2MqttBridgeConfig cfg = config;
        if (cfg == null) {
            return;
        }
        // topic = baseTopic/deviceName/availability
        String prefix = cfg.baseTopic + "/";
        String suffix = "/availability";
        if (!topic.startsWith(prefix) || !topic.endsWith(suffix)) {
            return;
        }
        String deviceName = topic.substring(prefix.length(), topic.length() - suffix.length());
        if (deviceName.startsWith("bridge")) {
            return;
        }

        boolean online;
        try {
            JsonObject obj = JsonParser.parseString(message).getAsJsonObject();
            JsonElement stateEl = obj.get("state");
            online = stateEl != null && "online".equals(stateEl.getAsString());
        } catch (JsonSyntaxException | IllegalStateException e) {
            online = message.contains("online");
        }

        boolean isOnline = online;
        getThing().getThings().forEach(child -> {
            if (child.getHandler() instanceof Zigbee2MqttDeviceHandler dh) {
                dh.onAvailabilityChanged(deviceName, isOnline);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Internal — reconnection
    // -------------------------------------------------------------------------

    private void scheduleReconnect() {
        cancelReconnect();
        reconnectJob = scheduler.schedule(() -> {
            Zigbee2MqttBridgeConfig cfg = config;
            if (cfg != null) {
                connect(cfg);
            }
        }, 30, TimeUnit.SECONDS);
    }

    private void cancelReconnect() {
        ScheduledFuture<?> job = reconnectJob;
        if (job != null) {
            job.cancel(false);
            reconnectJob = null;
        }
    }
}
