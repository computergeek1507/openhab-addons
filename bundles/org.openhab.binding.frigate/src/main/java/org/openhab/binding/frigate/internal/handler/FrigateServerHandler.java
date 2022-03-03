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

import static org.openhab.binding.frigate.internal.FrigateBindingConstants.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.frigate.internal.FrigateConfiguration;
import org.openhab.binding.frigate.internal.dto.ServiceDTO;
import org.openhab.core.io.net.http.HttpUtil;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.DecimalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

/**
 * The {@link FrigateServerHandler} is responsible for communicating with the Frigate Server
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
public class FrigateServerHandler extends BaseBridgeHandler {
    private @Nullable FrigateConfiguration config;
    private HttpClient httpClient;
    private final Logger logger = LoggerFactory.getLogger(FrigateServerHandler.class);
    private static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
    private String host = "localhost";
    private int portNumber = 5000;

    private static final int API_TIMEOUT_MSEC = 10000;

    public FrigateServerHandler(Bridge bridge, HttpClient httpClient) {
        super(bridge);
        this.httpClient = httpClient;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        config = getConfigAs(FrigateConfiguration.class);
        updateStatus(ThingStatus.UNKNOWN);

        host = config.ipaddress;
        portNumber = config.port;
        
        // int returnCode = Integer.parseInt(rootNode.get("ReturnCode").getAsString());
        // StatsDTO stats = GSON.fromJson(response, StatsDTO.class);
        // Example for background initialization:
        scheduler.execute(() -> {
            //boolean thingReachable = true; // <background task with long running initialization here>
            // when done do:
            String response = executeGet(buildBaseUrl("/api/stats"));
            if(response != null) {
                updateStatus(ThingStatus.ONLINE);
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                Set<String> keys  =  jsonObject.keySet();
                for (String key : keys){
                    System.out.println("Key:: !!! >>> "+key);
                    Object value = jsonObject.get(key);
                    System.out.println("Value Type "+value.getClass().getName());
                    if(key.equals( "detection_fps")){
                        //Float fps = jsonObject.get(key).getAsFloat();
                        //updateState(CHANNEL_DETECTION_FPS, new DecimalType(fps));
                    }
                    else if(key.equals( "detectors")){

                    }
                    else if(key.equals( "service")){
                        ServiceDTO ser = GSON.fromJson(jsonObject.get(key), ServiceDTO.class);
                        updateState(CHANNEL_VERSION, new StringType(ser.version));
                    }
                    else{

                    }
                }
            }            
            else{
                updateStatus(ThingStatus.OFFLINE);
            }           
        });
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

    private String buildBaseUrl(String path) {
        StringBuilder sb = new StringBuilder();
        sb.append("http://");
        sb.append(host);
        sb.append(":").append(portNumber);
        sb.append(path);
        return sb.toString();
    }

    
}
