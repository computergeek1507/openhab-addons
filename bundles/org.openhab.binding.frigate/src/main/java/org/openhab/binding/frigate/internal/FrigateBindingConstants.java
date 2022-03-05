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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link frigateBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
public class FrigateBindingConstants {

    private static final String BINDING_ID = "frigate";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_SERVER = new ThingTypeUID(BINDING_ID, "server");
    public static final ThingTypeUID THING_TYPE_CAMERA = new ThingTypeUID(BINDING_ID, "camera");

    public static final String CONFIG_CAMERA = "name";
    public static final String CHANNEL_IMAGE = "image";
    public static final String CHANNEL_IMAGE_URL = "imageUrl";
    public static final String CHANNEL_VIDEO_URL = "videoUrl";
    public static final String CHANNEL_LASTOBJECT = "lastObject";

    public static final String CHANNEL_EVENT_STATE = "eventState";
    public static final String CHANNEL_EVENT_ID = "eventId";
    public static final String CHANNEL_EVENT_TYPE = "eventType";
    public static final String CHANNEL_EVENT_SCORE = "eventScore";
    public static final String CHANNEL_EVENT_START = "eventStart";
    public static final String CHANNEL_EVENT_END = "eventEnd";
    public static final String CHANNEL_EVENT_CLIP_URL = "eventClipUrl";
    public static final String CHANNEL_EVENT_SNAPSHOT_URL = "eventSnapshotUrl";
    public static final String CHANNEL_EVENT_HAS_CLIP = "eventHasClip";
    public static final String CHANNEL_EVENT_HAS_SNAPSHOT = "eventHasSnapshot";

    // List of all Channel ids
    public static final String CHANNEL_VERSION = "frigateversion";
    public static final String CHANNEL_DETECTION_FPS = "detectionfps";

    public static final String CHANNEL_STATE = "state";
}
