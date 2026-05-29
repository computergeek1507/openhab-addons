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
package org.openhab.binding.zigbee2mqtt.internal;

import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * Constants for the Zigbee2MQTT binding.
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
public class Zigbee2MqttBindingConstants {

    public static final String BINDING_ID = "zigbee2mqtt";

    // Thing types — bridge
    public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");

    // Thing types — classified device types (inferred from Z2M features)
    public static final ThingTypeUID THING_TYPE_LIGHT = new ThingTypeUID(BINDING_ID, "light");
    public static final ThingTypeUID THING_TYPE_PLUG = new ThingTypeUID(BINDING_ID, "plug");
    public static final ThingTypeUID THING_TYPE_SWITCH = new ThingTypeUID(BINDING_ID, "switch");
    public static final ThingTypeUID THING_TYPE_CLIMATE_SENSOR = new ThingTypeUID(BINDING_ID, "climate-sensor");
    public static final ThingTypeUID THING_TYPE_MOTION_SENSOR = new ThingTypeUID(BINDING_ID, "motion-sensor");
    public static final ThingTypeUID THING_TYPE_CONTACT_SENSOR = new ThingTypeUID(BINDING_ID, "contact-sensor");
    public static final ThingTypeUID THING_TYPE_WATER_SENSOR = new ThingTypeUID(BINDING_ID, "water-sensor");
    public static final ThingTypeUID THING_TYPE_AIR_SENSOR = new ThingTypeUID(BINDING_ID, "air-sensor");
    public static final ThingTypeUID THING_TYPE_THERMOSTAT = new ThingTypeUID(BINDING_ID, "thermostat");
    public static final ThingTypeUID THING_TYPE_DEVICE = new ThingTypeUID(BINDING_ID, "device");

    /** All device thing types — used in the handler factory. */
    public static final Set<ThingTypeUID> DEVICE_THING_TYPES = Set.of(THING_TYPE_LIGHT, THING_TYPE_PLUG,
            THING_TYPE_SWITCH, THING_TYPE_CLIMATE_SENSOR, THING_TYPE_MOTION_SENSOR, THING_TYPE_CONTACT_SENSOR,
            THING_TYPE_WATER_SENSOR, THING_TYPE_AIR_SENSOR, THING_TYPE_THERMOSTAT, THING_TYPE_DEVICE);

    // Bridge channels
    public static final String CHANNEL_PERMIT_JOIN = "permit-join";
    public static final String CHANNEL_LOG_LEVEL = "log-level";

    // Channels — control
    public static final String CHANNEL_STATE = "state";
    public static final String CHANNEL_BRIGHTNESS = "brightness";
    public static final String CHANNEL_COLOR_TEMPERATURE = "color-temperature";
    public static final String CHANNEL_COLOR = "color";

    // Channels — climate sensors
    public static final String CHANNEL_TEMPERATURE = "temperature";
    public static final String CHANNEL_HUMIDITY = "humidity";
    public static final String CHANNEL_PRESSURE = "pressure";

    // Channels — climate control (thermostats / TRVs)
    public static final String CHANNEL_LOCAL_TEMPERATURE = "local-temperature";
    public static final String CHANNEL_OCCUPIED_HEATING_SETPOINT = "occupied-heating-setpoint";
    public static final String CHANNEL_OCCUPIED_COOLING_SETPOINT = "occupied-cooling-setpoint";
    public static final String CHANNEL_CURRENT_HEATING_SETPOINT = "current-heating-setpoint";
    public static final String CHANNEL_SYSTEM_MODE = "system-mode";
    public static final String CHANNEL_RUNNING_STATE = "running-state";
    public static final String CHANNEL_PRESET = "preset";
    public static final String CHANNEL_PI_HEATING_DEMAND = "pi-heating-demand";

    // Channels — binary sensors
    public static final String CHANNEL_CONTACT = "contact";
    public static final String CHANNEL_OCCUPANCY = "occupancy";
    public static final String CHANNEL_MOTION = "motion";
    public static final String CHANNEL_WATER_LEAK = "water-leak";
    public static final String CHANNEL_SMOKE = "smoke";
    public static final String CHANNEL_ILLUMINANCE = "illuminance";
    public static final String CHANNEL_TAMPER = "tamper";

    // Channels — air quality
    public static final String CHANNEL_PM25 = "pm25";
    public static final String CHANNEL_VOC_INDEX = "voc-index";

    // Channels — power monitoring
    public static final String CHANNEL_POWER = "power";
    public static final String CHANNEL_ENERGY = "energy";
    public static final String CHANNEL_VOLTAGE = "voltage";
    public static final String CHANNEL_CURRENT = "current";

    // Channels — device metadata
    public static final String CHANNEL_BATTERY = "battery";
    public static final String CHANNEL_BATTERY_LOW = "battery-low";
    public static final String CHANNEL_LINKQUALITY = "linkquality";

    // Channels — trigger
    public static final String CHANNEL_ACTION = "action";

    /**
     * Maps Zigbee2MQTT feature/payload key names to openHAB channel IDs.
     * Used by the discovery service (feature names) and device handler (payload keys).
     */
    public static final Map<String, String> Z2M_KEY_TO_CHANNEL_ID = Map.ofEntries(Map.entry("state", CHANNEL_STATE),
            Map.entry("brightness", CHANNEL_BRIGHTNESS), Map.entry("color_temp", CHANNEL_COLOR_TEMPERATURE),
            Map.entry("color_temp_startup", CHANNEL_COLOR_TEMPERATURE), Map.entry("color_xy", CHANNEL_COLOR),
            Map.entry("color_hs", CHANNEL_COLOR), Map.entry("color", CHANNEL_COLOR),
            Map.entry("temperature", CHANNEL_TEMPERATURE), Map.entry("humidity", CHANNEL_HUMIDITY),
            Map.entry("pressure", CHANNEL_PRESSURE), Map.entry("local_temperature", CHANNEL_LOCAL_TEMPERATURE),
            Map.entry("occupied_heating_setpoint", CHANNEL_OCCUPIED_HEATING_SETPOINT),
            Map.entry("occupied_cooling_setpoint", CHANNEL_OCCUPIED_COOLING_SETPOINT),
            Map.entry("current_heating_setpoint", CHANNEL_CURRENT_HEATING_SETPOINT),
            Map.entry("system_mode", CHANNEL_SYSTEM_MODE), Map.entry("running_state", CHANNEL_RUNNING_STATE),
            Map.entry("preset", CHANNEL_PRESET), Map.entry("pi_heating_demand", CHANNEL_PI_HEATING_DEMAND),
            Map.entry("illuminance_lux", CHANNEL_ILLUMINANCE), Map.entry("illuminance", CHANNEL_ILLUMINANCE),
            Map.entry("contact", CHANNEL_CONTACT), Map.entry("occupancy", CHANNEL_OCCUPANCY),
            Map.entry("motion", CHANNEL_MOTION), Map.entry("water_leak", CHANNEL_WATER_LEAK),
            Map.entry("smoke", CHANNEL_SMOKE), Map.entry("tamper", CHANNEL_TAMPER), Map.entry("pm25", CHANNEL_PM25),
            Map.entry("voc_index", CHANNEL_VOC_INDEX), Map.entry("battery_voltage", CHANNEL_VOLTAGE),
            Map.entry("power", CHANNEL_POWER), Map.entry("energy", CHANNEL_ENERGY),
            Map.entry("voltage", CHANNEL_VOLTAGE), Map.entry("current", CHANNEL_CURRENT),
            Map.entry("battery", CHANNEL_BATTERY), Map.entry("battery_low", CHANNEL_BATTERY_LOW),
            Map.entry("linkquality", CHANNEL_LINKQUALITY), Map.entry("action", CHANNEL_ACTION),
            Map.entry("click", CHANNEL_ACTION));

    // Config keys
    public static final String CONFIG_HOST = "host";
    public static final String CONFIG_PORT = "port";
    public static final String CONFIG_USERNAME = "username";
    public static final String CONFIG_PASSWORD = "password";
    public static final String CONFIG_BASE_TOPIC = "baseTopic";
    public static final String CONFIG_DEVICE_NAME = "deviceName";

    // Thing property keys
    public static final String PROPERTY_IEEE_ADDRESS = "ieee_address";
    public static final String PROPERTY_VENDOR = "vendor";
    public static final String PROPERTY_MODEL = "model";
}
