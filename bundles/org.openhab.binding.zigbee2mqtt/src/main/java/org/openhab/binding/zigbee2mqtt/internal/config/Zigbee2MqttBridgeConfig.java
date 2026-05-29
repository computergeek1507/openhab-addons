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
 * Configuration for the Zigbee2MQTT bridge (MQTT broker + Z2M connection).
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
public class Zigbee2MqttBridgeConfig {
    public String host = "";
    public int port = 1883;
    public String username = "";
    public String password = "";
    public String baseTopic = "zigbee2mqtt";
    public boolean secure = false;
    public int keepAlive = 60;
}
