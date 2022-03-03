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
package org.openhab.binding.frigate.internal.dto;

/**
 * The {@link EventDTO} entity from the Frigate API
 *
 * @author Scott Hanson - Initial contribution
 */
public class EventDTO {
    public String id;
    public String camera;
    public float frame_time;
    public float snapshot_time;
    public String label;
    public float top_score;
    public boolean false_positive;
    public float start_time;
    public float end_time";
    public float score;
    //"box": [424, 500, 536, 712],
    public float area;
    //"region": [264, 450, 667, 853],
    public String current_zones;
    public String entered_zones;
    public boolean has_snapshot;
    public boolean has_clip;
    public boolean stationary;
    public int motionless_count;
    public int position_changes;
}
