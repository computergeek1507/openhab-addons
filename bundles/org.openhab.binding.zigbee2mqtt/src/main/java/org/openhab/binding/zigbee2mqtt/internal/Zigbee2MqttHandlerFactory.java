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

import static org.openhab.binding.zigbee2mqtt.internal.Zigbee2MqttBindingConstants.*;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.zigbee2mqtt.internal.handler.Zigbee2MqttBridgeHandler;
import org.openhab.binding.zigbee2mqtt.internal.handler.Zigbee2MqttDeviceHandler;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;

/**
 * Factory creating Zigbee2MQTT bridge and device handlers.
 *
 * @author openHAB Contributors - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.zigbee2mqtt", service = ThingHandlerFactory.class)
public class Zigbee2MqttHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES;

    static {
        SUPPORTED_THING_TYPES = new java.util.HashSet<>(DEVICE_THING_TYPES);
        SUPPORTED_THING_TYPES.add(THING_TYPE_BRIDGE);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID uid = thing.getThingTypeUID();
        if (THING_TYPE_BRIDGE.equals(uid)) {
            return new Zigbee2MqttBridgeHandler((Bridge) thing);
        }
        if (DEVICE_THING_TYPES.contains(uid)) {
            return new Zigbee2MqttDeviceHandler(thing);
        }
        return null;
    }
}
