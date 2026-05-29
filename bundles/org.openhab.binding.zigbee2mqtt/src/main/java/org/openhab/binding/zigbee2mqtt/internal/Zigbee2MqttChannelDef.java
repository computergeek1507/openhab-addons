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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Describes a single openHAB channel derived from a Zigbee2MQTT feature definition.
 *
 * <p>
 * Instances are extracted from the Z2M {@code bridge/devices} feature tree during discovery
 * and serialised as a Gson JSON array in the {@code "channelDefs"} thing property.
 *
 * <p>
 * Fields must be non-final (and a no-arg constructor must exist) so that Gson can
 * deserialise instances without relying on {@code sun.misc.Unsafe}.
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
public class Zigbee2MqttChannelDef {

    public String channelId = "";
    public String z2mKey = "";
    public String itemType = "";
    public String label = "";
    public boolean readOnly;
    public boolean trigger;

    public Zigbee2MqttChannelDef() {
    }

    public Zigbee2MqttChannelDef(String channelId, String z2mKey, String itemType, String label, boolean readOnly,
            boolean trigger) {
        this.channelId = channelId;
        this.z2mKey = z2mKey;
        this.itemType = itemType;
        this.label = label;
        this.readOnly = readOnly;
        this.trigger = trigger;
    }
}
