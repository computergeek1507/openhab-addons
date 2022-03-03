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
package org.openhab.binding.frigate.internal;

import static org.openhab.binding.frigate.internal.FrigateBindingConstants.*;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.frigate.internal.handler.FrigateCameraHandler;
import org.openhab.binding.frigate.internal.handler.FrigateServerHandler;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link frigateHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.frigate", service = ThingHandlerFactory.class)
public class FrigateHandlerFactory extends BaseThingHandlerFactory {
    private final HttpClient httpClient;
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_CAMERA, THING_TYPE_SERVER);

    @Activate
    public FrigateHandlerFactory(final @Reference HttpClientFactory httpClientFactory) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_SERVER.equals(thingTypeUID)) {
            return new FrigateServerHandler((Bridge) thing, httpClient);
        }
        if (THING_TYPE_CAMERA.equals(thingTypeUID)) {
            return new FrigateCameraHandler(thing);
        }

        return null;
    }
}
