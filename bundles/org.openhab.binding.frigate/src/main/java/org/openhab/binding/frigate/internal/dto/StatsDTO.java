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

import java.util.List;

/**
 * The {@link StatsDTO} entity from the Frigate API
 *
 * @author Scott Hanson - Initial contribution
 */
public class StatsDTO {
    public List<CameraDTO> cameras;
    public String href;
    public String serialNumber;
    public String deviceFamily;
    public String devicePlatform;
    public String deviceType;
    public String name;
    public String createdDate;
    public String accountId;
    public DeviceStateDTO state;
    public String parentDevice;
    public String parentDeviceId;
}
