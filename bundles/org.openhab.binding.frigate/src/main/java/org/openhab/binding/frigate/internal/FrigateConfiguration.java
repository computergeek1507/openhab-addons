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

/**
 * The {@link frigateConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
public class FrigateConfiguration {

    /**
     * Sample configuration parameters. Replace with your own.
     */
    public String ipaddress = "";
    public String ipAddress = "";
    public int port = 5000;
    public String mqttipaddress = "";
    public int mqttport = 1883;
}
