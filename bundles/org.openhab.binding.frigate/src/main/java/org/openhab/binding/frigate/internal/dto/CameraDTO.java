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
 * The {@link CameraDTO} entity from the Frigate API
 *
 * @author Scott Hanson - Initial contribution
 */
public class CameraDTO {
    public int fps;
    public int h;
    public int bbox;
    public int timestamp;
    public int zones;
    public int mask;
    public int motion;
    public int regions;
}
