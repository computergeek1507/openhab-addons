/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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

package org.openhab.binding.frigate.internal.handler;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.openhab.binding.frigate.internal.FrigateBindingConstants.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.frigate.internal.FrigateServerConfiguration;
import org.openhab.binding.frigate.internal.dto.EventsDTO;
import org.openhab.binding.frigate.internal.dto.ServiceDTO;
import org.openhab.binding.frigate.internal.events.IEventsHandler;
import org.openhab.core.io.net.http.HttpUtil;
import org.openhab.core.io.transport.mqtt.MqttConnectionState;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link FrigateServerHandler} is responsible for communicating with the Frigate Server
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
public class FrigateServerHandler extends BaseBridgeHandler {
    private @Nullable FrigateServerConfiguration config;
    private HttpClient httpClient;
    private final Logger logger = LoggerFactory.getLogger(FrigateServerHandler.class);
    private static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
    private String host = "localhost";
    private int portNumber = 5000;
    private String mqttHost = "";
    private int mqttPort = 1883;

    private static final int API_TIMEOUT_MSEC = 10000;

    protected IEventsHandler connection = new IEventsHandler() {
        @Override
        public void receive(final String topic, final byte[] data) {
            FrigateServerHandler.this.receive(topic, data);
        }

        @Override
        public void connectionStateChanged(MqttConnectionState state, @Nullable Throwable error) {
            super.connectionStateChanged(state, error);
            if (state == MqttConnectionState.CONNECTED) {
                logger.debug("MQTT Connected");
            } else {
                logger.debug("MQTT Error: {}", (error != null) ? error.getMessage() : "Unknown reason");
            }
        }
    };

    public FrigateServerHandler(Bridge bridge, HttpClient httpClient) {
        super(bridge);
        this.httpClient = httpClient;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            getStats();
            return;
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(FrigateServerConfiguration.class);
        updateStatus(ThingStatus.UNKNOWN);
        if (config != null) {
            host = config.ipaddress;
            portNumber = config.port;
            mqttHost = config.mqttipaddress;
            mqttPort = config.mqttport;
        }
        // scheduler.execute(() -> {
        boolean worked = getStats();
        if (worked) {
            updateStatus(ThingStatus.ONLINE);
            if (!mqttHost.isEmpty()) {
                connect(mqttHost, mqttPort);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE);
        }
        // });
    }

    private boolean getStats() {
        String response = executeGet(buildBaseUrl("/api/stats"));
        if (response != null) {
            updateStatus(ThingStatus.ONLINE);
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            Set<String> keys = jsonObject.keySet();
            for (String key : keys) {
                if ("detection_fps".equals(key)) {
                    // Float fps = jsonObject.get(key).getAsFloat();
                    // updateState(CHANNEL_DETECTION_FPS, new DecimalType(fps));
                } else if ("detectors".equals(key)) {

                } else if ("service".equals(key)) {
                    ServiceDTO ser = GSON.fromJson(jsonObject.get(key), ServiceDTO.class);
                    if (ser != null) {
                        updateState(CHANNEL_VERSION, new StringType(ser.version));
                    }
                } else {

                }
            }
            return true;
        }
        return false;
    }

    public @Nullable String executeGet(String url) {
        try {
            long startTime = System.currentTimeMillis();
            String response = HttpUtil.executeUrl("GET", url, API_TIMEOUT_MSEC);
            logger.trace("Bridge: Http GET of '{}' returned '{}' in {} ms", url, response,
                    System.currentTimeMillis() - startTime);
            return response;
        } catch (IOException e) {
            logger.debug("Bridge: IOException on GET request, url='{}': {}", url, e.getMessage());
        }
        return null;
    }

    private @Nullable String executePost(String url, String content) {
        return executePost(url, content, "application/x-www-form-urlencoded");
    }

    public @Nullable String executePost(String url, String content, String contentType) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            long startTime = System.currentTimeMillis();
            String response = HttpUtil.executeUrl("POST", url, inputStream, contentType, API_TIMEOUT_MSEC);
            logger.trace("Bridge: Http POST content '{}' to '{}' returned: {} in {} ms", content, url, response,
                    System.currentTimeMillis() - startTime);
            return response;
        } catch (IOException e) {
            logger.debug("Bridge: IOException on POST request, url='{}': {}", url, e.getMessage());
        }
        return null;
    }

    public @Nullable RawType getImage(String url) {
        int timeout = API_TIMEOUT_MSEC;
        Request request = httpClient.newRequest(buildBaseUrl(url));
        request.method(HttpMethod.GET);
        request.timeout(timeout, TimeUnit.MILLISECONDS);

        String errorMsg;
        try {
            ContentResponse response = request.send();
            if (response.getStatus() == HttpStatus.OK_200) {
                RawType image = new RawType(response.getContent(), response.getHeaders().get(HttpHeader.CONTENT_TYPE));
                return image;
            } else {
                errorMsg = String.format("HTTP GET failed: %d, %s", response.getStatus(), response.getReason());
            }
        } catch (TimeoutException e) {
            errorMsg = String.format("TimeoutException: Call to Frigate Server timed out after {} msec", timeout);
        } catch (ExecutionException e) {
            errorMsg = String.format("ExecutionException: %s", e.getMessage());
        } catch (InterruptedException e) {
            errorMsg = String.format("InterruptedException: %s", e.getMessage());
            Thread.currentThread().interrupt();
        }
        logger.debug("{}", errorMsg);
        return null;
    }

    public String buildBaseUrl(String path) {
        StringBuilder sb = new StringBuilder();
        sb.append("http://");
        sb.append(host);
        sb.append(":").append(portNumber);
        sb.append(path);
        return sb.toString();
    }

    private void connect(String mqttIP, int port) {
        logger.debug("Connecting MQTT to {}", mqttIP);
        String userID = "openHabFrigate_" + randomString(5);
        connection.connect(mqttIP, port, userID);
    }

    private static String randomString(int length) {
        int low = 97; // a-z
        int high = 122; // A-Z
        StringBuilder sb = new StringBuilder(length);
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append((char) (low + (int) (random.nextFloat() * (high - low + 1))));
        }
        return sb.toString();
    }

    public void receive(final String topic, final byte[] message) {
        try {
            logger.trace("Got topic {}", topic);
            // String json = new String(message, UTF_8);

            if (topic.startsWith("frigate/") && topic.endsWith("/snapshot")) {
                Bridge bridge = getThing();
                List<Thing> things = bridge.getThings();
                for (Thing thing : things) {
                    FrigateCameraHandler cam = (FrigateCameraHandler) thing.getHandler();
                    if (cam != null) {
                        String detect_topic = String.format("frigate/%s/", cam.GetCameraName());
                        // frigate/<camera_name>/<type>/snapshot
                        if (topic.contains(detect_topic)) {
                            logger.trace("updating snapshot for {}", cam.GetCameraName());
                            cam.SetLastObject(message);
                        }
                    }
                }
            }
            if (topic.startsWith("frigate/events")) {
                String json = new String(message, UTF_8);
                EventsDTO event = GSON.fromJson(json, EventsDTO.class);
                if (event != null) {
                    Bridge bridge = getThing();
                    List<Thing> things = bridge.getThings();
                    for (Thing thing : things) {
                        FrigateCameraHandler cam = (FrigateCameraHandler) thing.getHandler();
                        if (cam != null) {
                            if (event.after.camera.equals(cam.GetCameraName())) {
                                logger.trace("updating event for {}", cam.GetCameraName());
                                cam.UpdateEvent(event);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Error {}", ex.toString());
            logger.error("Bad MQTT Topic {} : JSON {}", topic, new String(message, UTF_8));
        }
    }
}
