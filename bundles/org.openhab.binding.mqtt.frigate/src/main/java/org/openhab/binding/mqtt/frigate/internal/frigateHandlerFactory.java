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
package org.openhab.binding.mqtt.frigate.internal;

import static org.openhab.binding.mqtt.frigate.internal.mqtt.frigateBindingConstants.*;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link mqtt.frigateHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
@Component( service = ThingHandlerFactory.class)
public class frigateHandlerFactory extends BaseThingHandlerFactory {
    private final ThingRegistry thingRegistry;

    @Activate
    public frigateHandlerFactory(final @Reference ThingRegistry thingRegistry) {
        this.thingRegistry = thingRegistry;
    }
    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_SAMPLE.equals(thingTypeUID)) {
            return new mqtt.frigateHandler(thing);
        }

        return null;
    }
}
