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

package org.openhab.binding.mqtt.frigate.internal.handler;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigate.internal.Helper;
import org.openhab.binding.mqtt.handler.AbstractBrokerHandler;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttConnectionObserver;
import org.openhab.core.io.transport.mqtt.MqttConnectionState;
import org.openhab.core.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link frigateHandler} is responsible for handling commands of the globes, which are then
 * sent to one of the bridges to be sent out by MQTT.
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
public class FrigateHubHandler extends BaseThingHandler implements MqttConnectionObserver, MqttMessageSubscriber {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private @Nullable MqttBrokerConnection connection;
    private ThingRegistry thingRegistry;
    private String globeType = "";
    private String bulbMode = "";
    private String remotesGroupID = "";
    private String channelPrefix = "";
    private String fullCommandTopic = "";
    private String fullStatesTopic = "";
    private BigDecimal maxColourTemp = BigDecimal.ZERO;
    private BigDecimal minColourTemp = BigDecimal.ZERO;

    public FrigateHubHandler(Thing thing, ThingRegistry thingRegistry) {
        super(thing);
        this.thingRegistry = thingRegistry;
    }

    void changeChannel(String channel, State state) {
        updateState(new ChannelUID(channelPrefix + channel), state);
    }

    private void processIncomingState(String messageJSON) {
        // Need to handle State and Level at the same time to process level=0 as off//
        BigDecimal tempBulbLevel = BigDecimal.ZERO;
        String bulbState = Helper.resolveJSON(messageJSON, "\"state\":\"", 3);
        String bulbLevel = Helper.resolveJSON(messageJSON, "\"level\":", 3);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            return;
        }
        /*
         * switch (channelUID.getId()) {
         * case CHANNEL_LEVEL:
         * handleLevelColour(command);
         * return;
         * case CHANNEL_BULB_MODE:
         * bulbMode = command.toString();
         * break;
         * case CHANNEL_COLOURTEMP:
         * int scaledCommand = (int) Math.round((370 - (2.17 * Float.valueOf(command.toString()))));
         * sendMQTT("{\"state\":\"ON\",\"level\":" + savedLevel + ",\"color_temp\":" + scaledCommand + "}");
         * break;
         * case CHANNEL_COMMAND:
         * sendMQTT("{\"command\":\"" + command + "\"}");
         * break;
         * case CHANNEL_DISCO_MODE:
         * sendMQTT("{\"mode\":\"" + command + "\"}");
         * break;
         * case CHANNEL_COLOUR:
         * handleLevelColour(command);
         * }
         */
    }

    @Override
    public void initialize() {

        Bridge localBridge = getBridge();
        if (localBridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "Globe must have a valid bridge selected before it can come online.");
            return;
        } else {
            /*
             * globeType = thing.getThingTypeUID().getId();// eg rgb_cct
             * String globeLocation = this.getThing().getUID().getId();// eg 0x014
             * remotesGroupID = globeLocation.substring(globeLocation.length() - 1, globeLocation.length());// eg 4
             * String remotesIDCode = globeLocation.substring(0, globeLocation.length() - 1);// eg 0x01
             * fullCommandTopic = COMMANDS_BASE_TOPIC + remotesIDCode + "/" + globeType + "/" + remotesGroupID;
             * fullStatesTopic = STATES_BASE_TOPIC + remotesIDCode + "/" + globeType + "/" + remotesGroupID;
             * // Need to remove the lowercase x from 0x12AB in case it contains all numbers
             * String caseCheck = globeLocation.substring(2, globeLocation.length() - 1);
             * if (!caseCheck.equals(caseCheck.toUpperCase())) {
             * logger.warn(
             * "The milight globe {}{} is using lowercase for the remote code when the hub needs UPPERCASE",
             * remotesIDCode, remotesGroupID);
             * }
             * channelPrefix = BINDING_ID + ":" + globeType + ":" + localBridge.getUID().getId() + ":" + remotesIDCode
             * + remotesGroupID + ":";
             * connectMQTT();
             */
        }
    }

    private void sendMQTT(String payload) {
        MqttBrokerConnection localConnection = connection;
        if (localConnection != null) {
            localConnection.publish(fullCommandTopic, payload.getBytes(), 1, false);
        }
    }

    @Override
    public void processMessage(String topic, byte[] payload) {
        String state = new String(payload, StandardCharsets.UTF_8);
        logger.trace("Recieved the following new Milight state:{}:{}", topic, state);
        processIncomingState(state);
    }

    @Override
    public void connectionStateChanged(MqttConnectionState state, @Nullable Throwable error) {
        logger.debug("MQTT brokers state changed to:{}", state);
        switch (state) {
            case CONNECTED:
                updateStatus(ThingStatus.ONLINE);
                break;
            case CONNECTING:
            case DISCONNECTED:
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Bridge (broker) is not connected to your MQTT broker.");
        }
    }

    public void connectMQTT() {
        Bridge localBridge = this.getBridge();
        if (localBridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                    "Bridge is missing or offline, you need to setup a working MQTT broker first.");
            return;
        }
        ThingUID thingUID = localBridge.getUID();
        Thing thing = thingRegistry.get(thingUID);
        if (thing == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                    "Bridge is missing or offline, you need to setup a working MQTT broker first.");
            return;
        }
        ThingHandler handler = thing.getHandler();
        if (handler instanceof AbstractBrokerHandler) {
            AbstractBrokerHandler abh = (AbstractBrokerHandler) handler;
            MqttBrokerConnection localConnection = abh.getConnection();
            if (localConnection != null) {
                localConnection.setKeepAliveInterval(20);
                localConnection.setQos(1);
                localConnection.setUnsubscribeOnStop(true);
                localConnection.addConnectionObserver(this);
                localConnection.start();
                localConnection.subscribe(fullStatesTopic + "/#", this);
                connection = localConnection;
                if (localConnection.connectionState().compareTo(MqttConnectionState.CONNECTED) == 0) {
                    updateStatus(ThingStatus.ONLINE);
                }
            }
        }
        return;
    }

    @Override
    public void dispose() {
        MqttBrokerConnection localConnection = connection;
        if (localConnection != null) {
            localConnection.unsubscribe(fullStatesTopic + "/#", this);
        }
    }
}
