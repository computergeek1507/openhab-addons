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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.zigbee2mqtt.internal.Zigbee2MqttChannelDef;
import org.openhab.binding.zigbee2mqtt.internal.config.Zigbee2MqttDeviceConfig;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelKind;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * Handles a single Zigbee device managed by Zigbee2MQTT.
 *
 * <p>
 * Channels are built from Z2M feature metadata stored in the {@code "channelDefs"} thing
 * property (a JSON array of {@link Zigbee2MqttChannelDef} objects). Well-known channels
 * (temperature, brightness, etc.) reference registered channel types from channel-types.xml;
 * unknown channels are created inline with their accepted item type.
 *
 * <p>
 * For manually-added things (no {@code channelDefs} property), channels are inferred from
 * the first received MQTT payload.
 *
 * @author openHAB Contributors - Initial contribution
 */
@NonNullByDefault
public class Zigbee2MqttDeviceHandler extends BaseThingHandler implements MqttMessageSubscriber {

    public static final String CONFIG_Z2M_KEY = "z2mKey";

    private static final java.util.Map<String, ChannelTypeUID> CHANNEL_TYPE_MAP = java.util.Map.ofEntries(
            java.util.Map.entry(CHANNEL_STATE, new ChannelTypeUID(BINDING_ID, CHANNEL_STATE)),
            java.util.Map.entry(CHANNEL_BRIGHTNESS, new ChannelTypeUID(BINDING_ID, CHANNEL_BRIGHTNESS)),
            java.util.Map.entry(CHANNEL_COLOR_TEMPERATURE, new ChannelTypeUID(BINDING_ID, CHANNEL_COLOR_TEMPERATURE)),
            java.util.Map.entry(CHANNEL_COLOR, new ChannelTypeUID(BINDING_ID, CHANNEL_COLOR)),
            java.util.Map.entry(CHANNEL_TEMPERATURE, new ChannelTypeUID(BINDING_ID, CHANNEL_TEMPERATURE)),
            java.util.Map.entry(CHANNEL_HUMIDITY, new ChannelTypeUID(BINDING_ID, CHANNEL_HUMIDITY)),
            java.util.Map.entry(CHANNEL_PRESSURE, new ChannelTypeUID(BINDING_ID, CHANNEL_PRESSURE)),
            java.util.Map.entry(CHANNEL_LOCAL_TEMPERATURE, new ChannelTypeUID(BINDING_ID, CHANNEL_LOCAL_TEMPERATURE)),
            java.util.Map.entry(CHANNEL_OCCUPIED_HEATING_SETPOINT,
                    new ChannelTypeUID(BINDING_ID, CHANNEL_OCCUPIED_HEATING_SETPOINT)),
            java.util.Map.entry(CHANNEL_OCCUPIED_COOLING_SETPOINT,
                    new ChannelTypeUID(BINDING_ID, CHANNEL_OCCUPIED_COOLING_SETPOINT)),
            java.util.Map.entry(CHANNEL_CURRENT_HEATING_SETPOINT,
                    new ChannelTypeUID(BINDING_ID, CHANNEL_CURRENT_HEATING_SETPOINT)),
            java.util.Map.entry(CHANNEL_SYSTEM_MODE, new ChannelTypeUID(BINDING_ID, CHANNEL_SYSTEM_MODE)),
            java.util.Map.entry(CHANNEL_RUNNING_STATE, new ChannelTypeUID(BINDING_ID, CHANNEL_RUNNING_STATE)),
            java.util.Map.entry(CHANNEL_PRESET, new ChannelTypeUID(BINDING_ID, CHANNEL_PRESET)),
            java.util.Map.entry(CHANNEL_PI_HEATING_DEMAND, new ChannelTypeUID(BINDING_ID, CHANNEL_PI_HEATING_DEMAND)),
            java.util.Map.entry(CHANNEL_CONTACT, new ChannelTypeUID(BINDING_ID, CHANNEL_CONTACT)),
            java.util.Map.entry(CHANNEL_OCCUPANCY, new ChannelTypeUID(BINDING_ID, CHANNEL_OCCUPANCY)),
            java.util.Map.entry(CHANNEL_WATER_LEAK, new ChannelTypeUID(BINDING_ID, CHANNEL_WATER_LEAK)),
            java.util.Map.entry(CHANNEL_SMOKE, new ChannelTypeUID(BINDING_ID, CHANNEL_SMOKE)),
            java.util.Map.entry(CHANNEL_ILLUMINANCE, new ChannelTypeUID(BINDING_ID, CHANNEL_ILLUMINANCE)),
            java.util.Map.entry(CHANNEL_POWER, new ChannelTypeUID(BINDING_ID, CHANNEL_POWER)),
            java.util.Map.entry(CHANNEL_ENERGY, new ChannelTypeUID(BINDING_ID, CHANNEL_ENERGY)),
            java.util.Map.entry(CHANNEL_VOLTAGE, new ChannelTypeUID(BINDING_ID, CHANNEL_VOLTAGE)),
            java.util.Map.entry(CHANNEL_CURRENT, new ChannelTypeUID(BINDING_ID, CHANNEL_CURRENT)),
            java.util.Map.entry(CHANNEL_BATTERY, new ChannelTypeUID(BINDING_ID, CHANNEL_BATTERY)),
            java.util.Map.entry(CHANNEL_BATTERY_LOW, new ChannelTypeUID(BINDING_ID, CHANNEL_BATTERY_LOW)),
            java.util.Map.entry(CHANNEL_PM25, new ChannelTypeUID(BINDING_ID, CHANNEL_PM25)),
            java.util.Map.entry(CHANNEL_VOC_INDEX, new ChannelTypeUID(BINDING_ID, CHANNEL_VOC_INDEX)),
            java.util.Map.entry(CHANNEL_LINKQUALITY, new ChannelTypeUID(BINDING_ID, CHANNEL_LINKQUALITY)),
            java.util.Map.entry(CHANNEL_ACTION, new ChannelTypeUID(BINDING_ID, CHANNEL_ACTION)));

    private final Logger logger = LoggerFactory.getLogger(Zigbee2MqttDeviceHandler.class);
    private final Gson gson = new Gson();

    private @Nullable Zigbee2MqttDeviceConfig config;
    private @Nullable String subscribedTopic;
    private final AtomicBoolean channelsBuilt = new AtomicBoolean(false);

    public Zigbee2MqttDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        channelsBuilt.set(false);
        Zigbee2MqttDeviceConfig cfg = getConfigAs(Zigbee2MqttDeviceConfig.class);
        if (cfg.deviceName.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error-missing-device-name");
            return;
        }
        config = cfg;

        Bridge bridge = getBridge();
        if (bridge == null || bridge.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }

        updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "@text/unknown.waiting-for-device");
        // Defer channel rebuild + subscribe to avoid updateThing() during initialize()
        scheduler.execute(() -> {
            rebuildChannelsFromProperty();
            subscribe();
        });
    }

    @Override
    public void dispose() {
        unsubscribe();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            if (ThingStatusDetail.BRIDGE_OFFLINE.equals(getThing().getStatusInfo().getStatusDetail())) {
                updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "@text/unknown.waiting-for-device");
                subscribe();
            }
        } else {
            unsubscribe();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    public void onBridgeConnected() {
        if (getThing().getStatus() != ThingStatus.OFFLINE
                || ThingStatusDetail.BRIDGE_OFFLINE.equals(getThing().getStatusInfo().getStatusDetail())) {
            updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "@text/unknown.waiting-for-device");
            subscribe();
        }
    }

    public void onAvailabilityChanged(String deviceName, boolean online) {
        Zigbee2MqttDeviceConfig cfg = config;
        if (cfg == null || !cfg.deviceName.equals(deviceName)) {
            return;
        }
        if (online) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/offline.device-unavailable");
        }
    }

    // -------------------------------------------------------------------------
    // MqttMessageSubscriber
    // -------------------------------------------------------------------------

    @Override
    public void processMessage(String topic, byte[] payload) {
        String json = new String(payload, StandardCharsets.UTF_8);
        logger.trace("Message on {}: {}", topic, json);
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!channelsBuilt.get()) {
                buildChannelsFromPayload(obj);
            }
            updateChannelsFromPayload(obj);
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            logger.debug("Could not parse message on {}: {}", topic, json);
        }
    }

    // -------------------------------------------------------------------------
    // Command handling
    // -------------------------------------------------------------------------

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            requestState();
            return;
        }
        Channel channel = getThing().getChannel(channelUID);
        if (channel == null) {
            return;
        }
        if (channel.getKind() == ChannelKind.TRIGGER) {
            return;
        }
        String z2mKey = (String) channel.getConfiguration().get(CONFIG_Z2M_KEY);
        String itemType = channel.getAcceptedItemType();
        if (z2mKey == null || itemType == null) {
            return;
        }

        JsonObject jsonPayload = new JsonObject();
        buildCommandPayload(jsonPayload, z2mKey, channelUID.getId(), itemType, command);
        if (jsonPayload.size() > 0) {
            publish(getSetTopic(), jsonPayload.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Dynamic channel management
    // -------------------------------------------------------------------------

    private void rebuildChannelsFromProperty() {
        String defsJson = getThing().getProperties().get("channelDefs");
        if (defsJson != null && !defsJson.isBlank()) {
            try {
                List<Zigbee2MqttChannelDef> defs = gson.fromJson(defsJson,
                        new TypeToken<List<Zigbee2MqttChannelDef>>() {
                        }.getType());
                if (defs != null && !defs.isEmpty()) {
                    applyChannelDefs(defs);
                    return;
                }
            } catch (JsonSyntaxException e) {
                logger.debug("Could not parse channelDefs property: {}", e.getMessage());
            }
        }
        String channelsProp = getThing().getProperties().get("channels");
        if (channelsProp != null && !channelsProp.isBlank()) {
            List<Zigbee2MqttChannelDef> defs = new ArrayList<>();
            for (String id : channelsProp.split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isBlank()) {
                    String z2mKey = trimmed.replace("-", "_");
                    defs.add(new Zigbee2MqttChannelDef(trimmed, z2mKey, inferLegacyItemType(trimmed), toLabel(trimmed),
                            false, false));
                }
            }
            if (!defs.isEmpty()) {
                applyChannelDefs(defs);
            }
            return;
        }
        List<Channel> existing = getThing().getChannels();
        if (!existing.isEmpty()) {
            List<Zigbee2MqttChannelDef> defs = new ArrayList<>();
            for (Channel ch : existing) {
                String channelId = ch.getUID().getId();
                String z2mKey = (String) ch.getConfiguration().get(CONFIG_Z2M_KEY);
                if (z2mKey == null) {
                    z2mKey = channelId.replace("-", "_");
                }
                String itemType = ch.getAcceptedItemType();
                if (itemType == null || itemType.isBlank()) {
                    itemType = inferLegacyItemType(channelId);
                }
                String label = ch.getLabel();
                boolean isTrigger = ch.getKind() == ChannelKind.TRIGGER;
                defs.add(new Zigbee2MqttChannelDef(channelId, z2mKey, itemType,
                        label != null && !label.isBlank() ? label : toLabel(channelId), false, isTrigger));
            }
            if (!defs.isEmpty()) {
                applyChannelDefs(defs);
            }
        }
    }

    private void buildChannelsFromPayload(JsonObject json) {
        List<Zigbee2MqttChannelDef> defs = new ArrayList<>();
        for (java.util.Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String z2mKey = entry.getKey();
            String channelId = toChannelId(z2mKey);
            JsonElement value = entry.getValue();
            boolean isTrigger = "action".equals(z2mKey) || "click".equals(z2mKey);
            String itemType = isTrigger ? "" : inferItemTypeFromJson(z2mKey, value);
            defs.add(new Zigbee2MqttChannelDef(channelId, z2mKey, itemType, toLabel(z2mKey), true, isTrigger));
        }
        if (!defs.isEmpty()) {
            applyChannelDefs(defs);
        }
    }

    private void applyChannelDefs(List<Zigbee2MqttChannelDef> defs) {
        ThingHandlerCallback cb = getCallback();
        if (cb == null) {
            logger.warn("Cannot build channels for '{}': handler callback is null", getThing().getUID());
            return;
        }
        ThingBuilder builder = editThing().withoutChannels(getThing().getChannels());
        for (Zigbee2MqttChannelDef def : defs) {
            ChannelUID channelUID = new ChannelUID(getThing().getUID(), def.channelId);
            Configuration channelConfig = new Configuration();
            channelConfig.put(CONFIG_Z2M_KEY, def.z2mKey);

            ChannelTypeUID channelTypeUID = lookupChannelType(def.channelId);
            ChannelBuilder channelBuilder;

            if (def.trigger) {
                channelBuilder = ChannelBuilder.create(channelUID).withKind(ChannelKind.TRIGGER);
            } else {
                channelBuilder = ChannelBuilder.create(channelUID, def.itemType);
            }

            if (channelTypeUID != null) {
                channelBuilder.withType(channelTypeUID);
            }

            Channel channel = channelBuilder.withLabel(def.label).withConfiguration(channelConfig).build();
            builder.withChannel(channel);
        }
        updateThing(builder.build());
        channelsBuilt.set(true);
        logger.debug("Built {} channel(s) for '{}'", defs.size(), getThing().getUID());
    }

    // -------------------------------------------------------------------------
    // State updates
    // -------------------------------------------------------------------------

    private void updateChannelsFromPayload(JsonObject json) {
        for (Channel channel : getThing().getChannels()) {
            String channelId = channel.getUID().getId();
            String z2mKey = (String) channel.getConfiguration().get(CONFIG_Z2M_KEY);
            if (z2mKey == null) {
                continue;
            }

            // Trigger channels fire events instead of updating state
            if (channel.getKind() == ChannelKind.TRIGGER) {
                JsonElement value = json.get(z2mKey);
                if (value != null && value.isJsonPrimitive() && !value.getAsString().isEmpty()) {
                    triggerChannel(channelId, value.getAsString());
                }
                continue;
            }

            String itemType = channel.getAcceptedItemType();
            if (itemType == null) {
                continue;
            }

            if (channelId.equals(CHANNEL_COLOR) || channelId.startsWith(CHANNEL_COLOR + "-")) {
                updateColorChannel(channelId, z2mKey, json);
                continue;
            }

            JsonElement value = json.get(z2mKey);
            if (value == null || value.isJsonNull()) {
                continue;
            }

            State state = jsonToState(itemType, channelId, value);
            if (state != null) {
                updateState(channelId, state);
            }
        }
    }

    private @Nullable State jsonToState(String itemType, String channelId, JsonElement value) {
        try {
            if ("Switch".equals(itemType)) {
                if (value.isJsonPrimitive()) {
                    JsonPrimitive p = value.getAsJsonPrimitive();
                    if (p.isBoolean()) {
                        return OnOffType.from(p.getAsBoolean());
                    }
                    String s = p.getAsString();
                    return OnOffType.from("ON".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s));
                }
            }
            if ("Contact".equals(itemType)) {
                boolean closed = value.isJsonPrimitive() && value.getAsBoolean();
                return closed ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
            }
            if ("Dimmer".equals(itemType)) {
                double raw = value.getAsDouble();
                if (isBrightnessChannel(channelId)) {
                    return new PercentType((int) Math.round(raw / 254.0 * 100.0));
                }
                if (isColorTempChannel(channelId)) {
                    int pct = (int) Math.round((raw - 153.0) / (500.0 - 153.0) * 100.0);
                    return new PercentType(Math.max(0, Math.min(100, pct)));
                }
                return new PercentType((int) Math.round(raw));
            }
            if ("Color".equals(itemType)) {
                return null;
            }
            if ("String".equals(itemType)) {
                return new StringType(value.getAsString());
            }
            if (itemType.startsWith("Number")) {
                double v = value.getAsDouble();
                return switch (itemType) {
                    case "Number:Temperature" -> new QuantityType<>(v, SIUnits.CELSIUS);
                    case "Number:Power" -> new QuantityType<>(v, Units.WATT);
                    case "Number:Energy" -> new QuantityType<>(v, Units.KILOWATT_HOUR);
                    case "Number:ElectricPotential" -> {
                        double volts = v > 100 ? v / 1000.0 : v;
                        yield new QuantityType<>(volts, Units.VOLT);
                    }
                    case "Number:ElectricCurrent" -> new QuantityType<>(v, Units.AMPERE);
                    case "Number:Pressure" -> new QuantityType<>(v, Units.MILLIBAR);
                    case "Number:Illuminance" -> new QuantityType<>(v, Units.LUX);
                    case "Number:Density" -> new QuantityType<>(v, Units.MICROGRAM_PER_CUBICMETRE);
                    case "Number:Time" -> new QuantityType<>(v, Units.SECOND);
                    case "Number:Dimensionless" -> new QuantityType<>(v, Units.ONE);
                    case "Number" -> new DecimalType(v);
                    default -> new DecimalType(v);
                };
            }
        } catch (Exception e) {
            logger.trace("Could not convert {} value to {}: {}", channelId, itemType, e.getMessage());
        }
        return null;
    }

    private void updateColorChannel(String channelId, String z2mKey, JsonObject json) {
        JsonElement colorEl = json.get(z2mKey);
        if (colorEl == null || !colorEl.isJsonObject()) {
            return;
        }
        JsonObject color = colorEl.getAsJsonObject();
        Double h = getDouble(color, "hue");
        Double s = getDouble(color, "saturation");
        if (h != null && s != null) {
            Double b = getDouble(json, "brightness");
            int bVal = b != null ? (int) Math.round(b / 254.0 * 100.0) : 100;
            updateState(channelId, new HSBType(new DecimalType(h), new PercentType((int) Math.round(s / 254.0 * 100.0)),
                    new PercentType(bVal)));
        }
    }

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------

    private void buildCommandPayload(JsonObject payload, String z2mKey, String channelId, String itemType,
            Command command) {
        if ("Color".equals(itemType) && command instanceof HSBType hsb) {
            JsonObject color = new JsonObject();
            color.addProperty("hue", hsb.getHue().doubleValue());
            color.addProperty("saturation", (int) Math.round(hsb.getSaturation().doubleValue() / 100.0 * 254.0));
            payload.add("color", color);
            payload.addProperty("brightness", (int) Math.round(hsb.getBrightness().doubleValue() / 100.0 * 254.0));
            return;
        }
        JsonElement jsonValue = commandToJsonValue(channelId, itemType, command);
        if (jsonValue != null) {
            payload.add(z2mKey, jsonValue);
        }
    }

    private @Nullable JsonElement commandToJsonValue(String channelId, String itemType, Command command) {
        if (command instanceof OnOffType onOff) {
            if ("Contact".equals(itemType)) {
                return null;
            }
            return new JsonPrimitive(onOff.name());
        }
        if (command instanceof PercentType pct) {
            if ("Dimmer".equals(itemType)) {
                if (isBrightnessChannel(channelId)) {
                    return new JsonPrimitive((int) Math.round(pct.doubleValue() / 100.0 * 254.0));
                }
                if (isColorTempChannel(channelId)) {
                    int mired = (int) Math.round(153.0 + pct.doubleValue() / 100.0 * (500.0 - 153.0));
                    return new JsonPrimitive(mired);
                }
                return new JsonPrimitive(pct.intValue());
            }
        }
        if (command instanceof QuantityType<?> qty) {
            if ("Number:Temperature".equals(itemType)) {
                QuantityType<?> celsius = qty.toUnit(SIUnits.CELSIUS);
                return new JsonPrimitive(celsius != null ? celsius.doubleValue() : qty.doubleValue());
            }
            return new JsonPrimitive(qty.doubleValue());
        }
        if (command instanceof DecimalType dec) {
            return new JsonPrimitive(dec.doubleValue());
        }
        if (command instanceof StringType str) {
            return new JsonPrimitive(str.toString());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // MQTT helpers
    // -------------------------------------------------------------------------

    private void subscribe() {
        MqttBrokerConnection conn = getConnection();
        Zigbee2MqttDeviceConfig cfg = config;
        if (conn == null || cfg == null) {
            return;
        }
        String topic = getBridgeBaseTopic() + "/" + cfg.deviceName;
        conn.subscribe(topic, this);
        subscribedTopic = topic;
        requestState();
    }

    private void unsubscribe() {
        MqttBrokerConnection conn = getConnection();
        String topic = subscribedTopic;
        if (conn != null && topic != null) {
            conn.unsubscribe(topic, this);
            subscribedTopic = null;
        }
    }

    private void requestState() {
        MqttBrokerConnection conn = getConnection();
        Zigbee2MqttDeviceConfig cfg = config;
        if (conn == null || cfg == null) {
            return;
        }
        conn.publish(getBridgeBaseTopic() + "/" + cfg.deviceName + "/get", "{}".getBytes(StandardCharsets.UTF_8), 1,
                false);
    }

    private void publish(String topic, String payload) {
        MqttBrokerConnection conn = getConnection();
        if (conn == null) {
            logger.debug("Cannot publish: no MQTT connection");
            return;
        }
        conn.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, false);
    }

    private String getSetTopic() {
        Zigbee2MqttDeviceConfig cfg = config;
        return getBridgeBaseTopic() + "/" + (cfg != null ? cfg.deviceName : "") + "/set";
    }

    private String getBridgeBaseTopic() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof Zigbee2MqttBridgeHandler bh) {
            return bh.getBaseTopic();
        }
        return "zigbee2mqtt";
    }

    private @Nullable MqttBrokerConnection getConnection() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof Zigbee2MqttBridgeHandler bh) {
            return bh.getConnection();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Inference helpers
    // -------------------------------------------------------------------------

    private String toChannelId(String z2mKey) {
        String mapped = Z2M_KEY_TO_CHANNEL_ID.get(z2mKey);
        return mapped != null ? mapped : z2mKey.toLowerCase().replace("_", "-");
    }

    private String toLabel(String name) {
        String[] words = name.split("[_\\-\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append(" ");
                }
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private String inferLegacyItemType(String channelId) {
        return switch (channelId) {
            case "state", "occupancy", "motion", "water-leak", "smoke", "tamper", "battery-low" -> "Switch";
            case "contact" -> "Contact";
            case "brightness", "color-temperature" -> "Dimmer";
            case "color" -> "Color";
            case "temperature" -> "Number:Temperature";
            case "pressure" -> "Number:Pressure";
            case "illuminance" -> "Number:Illuminance";
            case "power" -> "Number:Power";
            case "energy" -> "Number:Energy";
            case "voltage" -> "Number:ElectricPotential";
            case "current" -> "Number:ElectricCurrent";
            default -> "Number:Dimensionless";
        };
    }

    private String inferItemTypeFromJson(String z2mKey, JsonElement value) {
        if ("contact".equals(z2mKey)) {
            return "Contact";
        }
        if ("brightness".equals(z2mKey)) {
            return "Dimmer";
        }
        if ("color_temp".equals(z2mKey)) {
            return "Dimmer";
        }
        if ("color".equals(z2mKey) || "color_xy".equals(z2mKey) || "color_hs".equals(z2mKey)) {
            return "Color";
        }
        if (value.isJsonPrimitive()) {
            JsonPrimitive p = value.getAsJsonPrimitive();
            if (p.isBoolean()) {
                return "Switch";
            }
            if (p.isNumber()) {
                return "Number:Dimensionless";
            }
            String s = p.getAsString();
            if ("ON".equalsIgnoreCase(s) || "OFF".equalsIgnoreCase(s)) {
                return "Switch";
            }
        }
        return "String";
    }

    private @Nullable Double getDouble(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) {
            return null;
        }
        try {
            return el.getAsDouble();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBrightnessChannel(String channelId) {
        return CHANNEL_BRIGHTNESS.equals(channelId) || channelId.startsWith(CHANNEL_BRIGHTNESS + "-");
    }

    private boolean isColorTempChannel(String channelId) {
        return CHANNEL_COLOR_TEMPERATURE.equals(channelId) || channelId.startsWith(CHANNEL_COLOR_TEMPERATURE + "-");
    }

    /**
     * Looks up the channel type UID for a channel ID. Handles multi-endpoint channel IDs
     * like "brightness-white" by stripping the endpoint suffix.
     */
    private @Nullable ChannelTypeUID lookupChannelType(String channelId) {
        ChannelTypeUID uid = CHANNEL_TYPE_MAP.get(channelId);
        if (uid != null) {
            return uid;
        }
        int dashIdx = channelId.lastIndexOf('-');
        if (dashIdx > 0) {
            return CHANNEL_TYPE_MAP.get(channelId.substring(0, dashIdx));
        }
        return null;
    }
}
