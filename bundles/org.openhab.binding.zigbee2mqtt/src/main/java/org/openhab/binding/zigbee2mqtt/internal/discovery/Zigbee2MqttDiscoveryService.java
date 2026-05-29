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
package org.openhab.binding.zigbee2mqtt.internal.discovery;

import static org.openhab.binding.zigbee2mqtt.internal.Zigbee2MqttBindingConstants.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.zigbee2mqtt.internal.Zigbee2MqttChannelDef;
import org.openhab.binding.zigbee2mqtt.internal.handler.Zigbee2MqttBridgeHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Discovers Zigbee devices from the Zigbee2MQTT {@code bridge/devices} MQTT topic.
 *
 * @author openHAB Contributors - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = Zigbee2MqttDiscoveryService.class)
@NonNullByDefault
public class Zigbee2MqttDiscoveryService extends AbstractThingHandlerDiscoveryService<Zigbee2MqttBridgeHandler> {

    private static final int DISCOVERY_TIMEOUT_SECONDS = 30;

    private final Logger logger = LoggerFactory.getLogger(Zigbee2MqttDiscoveryService.class);
    private final Gson gson = new Gson();

    public Zigbee2MqttDiscoveryService() {
        super(Zigbee2MqttBridgeHandler.class, DEVICE_THING_TYPES, DISCOVERY_TIMEOUT_SECONDS, true);
    }

    @Override
    public void initialize() {
        thingHandler.setDiscoveryService(this);
        super.initialize();
    }

    @Override
    protected void startScan() {
        thingHandler.requestDeviceList();
    }

    public void onDevicesReceived(String devicesJson) {
        try {
            JsonArray devices = JsonParser.parseString(devicesJson).getAsJsonArray();
            ThingUID bridgeUID = thingHandler.getThing().getUID();

            for (JsonElement el : devices) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject device = el.getAsJsonObject();
                String friendlyName = getStringOrNull(device, "friendly_name");
                String ieeeAddress = getStringOrNull(device, "ieee_address");

                if (friendlyName == null || ieeeAddress == null) {
                    continue;
                }

                String type = getStringOrNull(device, "type");
                if ("Coordinator".equalsIgnoreCase(type)) {
                    continue;
                }

                String thingId = ieeeAddress.replaceAll("[^a-zA-Z0-9_]", "");
                String label = friendlyName;
                String vendor = "";
                String model = "";
                String description = "";
                List<Zigbee2MqttChannelDef> defs = new ArrayList<>();
                Set<String> seenIds = new LinkedHashSet<>();

                JsonElement defEl = device.get("definition");
                if (defEl != null && defEl.isJsonObject()) {
                    JsonObject def = defEl.getAsJsonObject();
                    String v = getStringOrNull(def, "vendor");
                    String m = getStringOrNull(def, "model");
                    String d = getStringOrNull(def, "description");
                    if (v != null) {
                        vendor = v;
                    }
                    if (m != null) {
                        model = m;
                    }
                    if (d != null) {
                        description = d;
                    }
                    if (!vendor.isBlank() && !model.isBlank()) {
                        label = friendlyName + " (" + vendor + " " + model + ")";
                    }

                    JsonElement featuresEl = getJsonArray(def, "exposes");
                    if (featuresEl == null) {
                        featuresEl = getJsonArray(def, "features");
                    }
                    if (featuresEl == null) {
                        logger.debug("No exposes/features array in definition for {} ({})", friendlyName, ieeeAddress);
                    }
                    extractChannelDefs(featuresEl, defs, seenIds, null);
                } else {
                    logger.debug("No definition object for {} ({})", friendlyName, ieeeAddress);
                }

                // Fallback: some Z2M versions put exposes at the device level
                if (defs.isEmpty()) {
                    JsonElement featuresEl = getJsonArray(device, "exposes");
                    if (featuresEl == null) {
                        featuresEl = getJsonArray(device, "features");
                    }
                    if (featuresEl != null) {
                        logger.debug("Using device-level exposes for {} ({})", friendlyName, ieeeAddress);
                    }
                    extractChannelDefs(featuresEl, defs, seenIds, null);
                }

                Set<String> channelIds = new LinkedHashSet<>();
                for (Zigbee2MqttChannelDef def : defs) {
                    channelIds.add(def.channelId);
                }

                ThingTypeUID thingTypeUID = classifyDevice(channelIds);
                ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, thingId);

                DiscoveryResultBuilder builder = DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID)
                        .withLabel(label).withProperty(PROPERTY_IEEE_ADDRESS, ieeeAddress)
                        .withProperty(PROPERTY_VENDOR, vendor).withProperty(PROPERTY_MODEL, model)
                        .withProperty("description", description).withProperty(CONFIG_DEVICE_NAME, friendlyName)
                        .withRepresentationProperty(CONFIG_DEVICE_NAME);

                if (!defs.isEmpty()) {
                    builder.withProperty("channelDefs", gson.toJson(defs));
                }

                DiscoveryResult result = builder.build();
                thingDiscovered(result);
                logger.debug("Discovered {} ({}) as {} with {} channel(s)", friendlyName, ieeeAddress,
                        thingTypeUID.getId(), defs.size());
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            logger.warn("Could not parse bridge/devices payload: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Feature extraction
    // -------------------------------------------------------------------------

    /**
     * Recursively walks the Z2M feature tree and builds channel definitions.
     *
     * <p>
     * Z2M uses two kinds of feature containers:
     * <ul>
     * <li><b>Specific types</b> ({@code light}, {@code switch}, {@code climate}, {@code fan},
     * {@code lock}, {@code cover}) — device-capability wrappers with a {@code features} sub-array.
     * These may have an {@code endpoint} field for multi-endpoint devices.</li>
     * <li><b>Generic composites</b> ({@code composite}) — named composites like {@code color_xy},
     * {@code color_hs} that may map to a single openHAB channel.</li>
     * </ul>
     *
     * @param featuresEl the JSON array of features to walk
     * @param defs accumulator for channel definitions
     * @param seenIds channel IDs already added (to avoid duplicates)
     * @param endpoint endpoint suffix for multi-endpoint devices, or {@code null}
     */
    private void extractChannelDefs(@Nullable JsonElement featuresEl, List<Zigbee2MqttChannelDef> defs,
            Set<String> seenIds, @Nullable String endpoint) {
        if (featuresEl == null || !featuresEl.isJsonArray()) {
            return;
        }
        for (JsonElement feat : featuresEl.getAsJsonArray()) {
            if (!feat.isJsonObject()) {
                continue;
            }
            JsonObject feature = feat.getAsJsonObject();
            String name = getStringOrNull(feature, "name");
            String property = getStringOrNull(feature, "property");
            String featureType = getStringOrNull(feature, "type");
            String unit = getStringOrNull(feature, "unit");
            String featureLabel = getStringOrNull(feature, "label");
            int access = feature.has("access") && feature.get("access").isJsonPrimitive()
                    ? feature.get("access").getAsInt()
                    : 1;
            boolean readOnly = (access & 2) == 0;

            // Determine the endpoint for multi-endpoint container features
            String ep = getStringOrNull(feature, "endpoint");
            if (ep == null) {
                ep = endpoint;
            }

            // Check for sub-features (container types: light, switch, climate, fan, lock, cover, composite)
            JsonElement subFeatures = feature.get("features");
            if (subFeatures != null && subFeatures.isJsonArray()) {
                // Known composites that map to a single channel
                if (name != null && ("color_xy".equals(name) || "color_hs".equals(name) || "color".equals(name))) {
                    String cId = ep != null ? "color-" + ep : "color";
                    String z2mKey = property != null ? property : name;
                    addIfNew(cId, z2mKey, "Color", featureLabel != null ? featureLabel : "Color", false, false, defs,
                            seenIds);
                    continue;
                }
                // Skip cover type (position/tilt not yet supported)
                if ("cover".equals(featureType)) {
                    continue;
                }
                // Recurse into sub-features
                extractChannelDefs(subFeatures, defs, seenIds, ep);
                continue;
            }

            // Skip list features and features without a name
            if (name == null || "list".equals(featureType)) {
                continue;
            }

            // The property field is what appears in the MQTT payload
            String z2mKey = property != null ? property : name;

            // Detect trigger channels (action/click from buttons and remotes)
            boolean isTrigger = ("action".equals(name) || "click".equals(name)) && "enum".equals(featureType);

            String channelId = toChannelId(name);
            // For multi-endpoint devices, append endpoint to make channel IDs unique
            if (ep != null) {
                channelId = channelId + "-" + ep;
            }

            String itemType = isTrigger ? "" : inferItemType(featureType, unit, name);
            String lbl = featureLabel != null && !featureLabel.isBlank() ? featureLabel : toLabel(name);
            if (ep != null) {
                lbl = lbl + " (" + ep + ")";
            }

            addIfNew(channelId, z2mKey, itemType, lbl, readOnly, isTrigger, defs, seenIds);
        }
    }

    private void addIfNew(String channelId, String z2mKey, String itemType, String label, boolean readOnly,
            boolean trigger, List<Zigbee2MqttChannelDef> defs, Set<String> seenIds) {
        if (!seenIds.contains(channelId)) {
            seenIds.add(channelId);
            defs.add(new Zigbee2MqttChannelDef(channelId, z2mKey, itemType, label, readOnly, trigger));
        }
    }

    // -------------------------------------------------------------------------
    // Inference helpers
    // -------------------------------------------------------------------------

    private String inferItemType(@Nullable String featureType, @Nullable String unit, String name) {
        if ("brightness".equals(name)) {
            return "Dimmer";
        }
        if ("color_temp".equals(name) || "color_temp_startup".equals(name)) {
            return "Dimmer";
        }
        if ("contact".equals(name)) {
            return "Contact";
        }

        if ("binary".equals(featureType)) {
            return "Switch";
        }
        if ("enum".equals(featureType) || "text".equals(featureType)) {
            return "String";
        }
        if ("numeric".equals(featureType)) {
            return itemTypeFromUnit(unit);
        }
        return "String";
    }

    private String itemTypeFromUnit(@Nullable String unit) {
        if (unit == null || unit.isBlank()) {
            return "Number:Dimensionless";
        }
        return switch (unit.trim()) {
            case "°C", "celsius" -> "Number:Temperature";
            case "°F", "fahrenheit" -> "Number:Temperature";
            case "W" -> "Number:Power";
            case "kWh" -> "Number:Energy";
            case "V", "mV" -> "Number:ElectricPotential";
            case "A", "mA" -> "Number:ElectricCurrent";
            case "hPa", "Pa", "mbar" -> "Number:Pressure";
            case "lux", "lx" -> "Number:Illuminance";
            case "s", "sec" -> "Number:Time";
            case "min" -> "Number:Time";
            case "%", "ppm" -> "Number:Dimensionless";
            case "µg/m³" -> "Number:Density";
            case "mired" -> "Number:Dimensionless";
            case "lqi" -> "Number:Dimensionless";
            default -> "Number:Dimensionless";
        };
    }

    private String toChannelId(String name) {
        String mapped = Z2M_KEY_TO_CHANNEL_ID.get(name);
        return mapped != null ? mapped : name.toLowerCase().replace("_", "-");
    }

    private String toLabel(String name) {
        String[] words = name.split("[_\\s]+");
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

    // -------------------------------------------------------------------------
    // Device classification
    // -------------------------------------------------------------------------

    private ThingTypeUID classifyDevice(Set<String> channelIds) {
        if (hasAny(channelIds, CHANNEL_COLOR, CHANNEL_COLOR_TEMPERATURE, CHANNEL_BRIGHTNESS)) {
            return THING_TYPE_LIGHT;
        }
        if (channelIds.contains(CHANNEL_STATE)
                && (channelIds.contains(CHANNEL_POWER) || channelIds.contains(CHANNEL_ENERGY))) {
            return THING_TYPE_PLUG;
        }
        if (channelIds.contains(CHANNEL_STATE) && !hasAny(channelIds, CHANNEL_TEMPERATURE, CHANNEL_HUMIDITY,
                CHANNEL_CONTACT, CHANNEL_OCCUPANCY, CHANNEL_MOTION)) {
            return THING_TYPE_SWITCH;
        }
        if (hasAny(channelIds, CHANNEL_SYSTEM_MODE, CHANNEL_OCCUPIED_HEATING_SETPOINT, CHANNEL_CURRENT_HEATING_SETPOINT,
                CHANNEL_OCCUPIED_COOLING_SETPOINT, CHANNEL_RUNNING_STATE)) {
            return THING_TYPE_THERMOSTAT;
        }
        if (hasAny(channelIds, CHANNEL_PM25, CHANNEL_VOC_INDEX)) {
            return THING_TYPE_AIR_SENSOR;
        }
        if (channelIds.contains(CHANNEL_WATER_LEAK)) {
            return THING_TYPE_WATER_SENSOR;
        }
        if (channelIds.contains(CHANNEL_CONTACT)) {
            return THING_TYPE_CONTACT_SENSOR;
        }
        if (hasAny(channelIds, CHANNEL_OCCUPANCY, CHANNEL_MOTION)) {
            return THING_TYPE_MOTION_SENSOR;
        }
        if (hasAny(channelIds, CHANNEL_TEMPERATURE, CHANNEL_HUMIDITY)) {
            return THING_TYPE_CLIMATE_SENSOR;
        }
        return THING_TYPE_DEVICE;
    }

    /**
     * Classification-aware check: also matches multi-endpoint channel IDs that start with the
     * base channel ID (e.g. "brightness-white" matches a check for "brightness").
     */
    private boolean hasAny(Set<String> channelIds, String... candidates) {
        for (String candidate : candidates) {
            for (String id : channelIds) {
                if (id.equals(candidate) || id.startsWith(candidate + "-")) {
                    return true;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private @Nullable String getStringOrNull(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && el.isJsonPrimitive()) ? el.getAsString() : null;
    }

    private @Nullable JsonElement getJsonArray(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && el.isJsonArray()) ? el : null;
    }
}
