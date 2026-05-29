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
package org.openhab.binding.zigbee2mqtt.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Configuration for a single Zigbee device managed by Zigbee2MQTT.
 *
 * @author openHAB Contributors - Initial contribution
 */
@NonNullByDefault
public class Zigbee2MqttDeviceConfig {
    /** The device's friendly_name as configured in Zigbee2MQTT. Used as the MQTT topic suffix. */
    public String deviceName = "";
}
