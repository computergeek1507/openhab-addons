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
package org.openhab.binding.frigate.internal.events;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttConnectionObserver;
import org.openhab.core.io.transport.mqtt.MqttConnectionState;
import org.openhab.core.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.core.io.transport.mqtt.reconnect.PeriodicReconnectStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link IEventsHandler} is responsible for handling Frigate MQTT connection.
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
public abstract class IEventsHandler implements MqttConnectionObserver, MqttMessageSubscriber {
    private final Logger logger = LoggerFactory.getLogger(IEventsHandler.class);

    private static final int RECONNECT_DELAY = 10000; // In milliseconds
    private @Nullable Future<?> reconnect;
    private @Nullable MqttBrokerConnection connection;

    public IEventsHandler() {
    }

    public synchronized void connect(final String ip, final int port, final String userid) {

        // BLID is used as both client ID and username. The name of BLID also came from Roomba980-python
        MqttBrokerConnection connection = new MqttBrokerConnection(ip, port, false, userid);

        // MQTT connection reconnects itself, so we don't have to reconnect, when it breaks
        connection.setReconnectStrategy(new PeriodicReconnectStrategy(RECONNECT_DELAY, RECONNECT_DELAY));

        connection.start().exceptionally(exception -> {
            connectionStateChanged(MqttConnectionState.DISCONNECTED, exception);
            return false;
        }).thenAccept(successful -> {
            MqttConnectionState state = successful ? MqttConnectionState.CONNECTED : MqttConnectionState.DISCONNECTED;
            connectionStateChanged(state, successful ? null : new TimeoutException("Timeout"));
        });

        this.connection = connection;
    }

    public synchronized void disconnect() {
        Future<?> reconnect = this.reconnect;
        if (reconnect != null) {
            reconnect.cancel(false);
            this.reconnect = null;
        }

        MqttBrokerConnection connection = this.connection;
        if (connection != null) {
            connection.unsubscribe("frigate/#", this);
            CompletableFuture<Boolean> future = connection.stop();
            try {
                future.get(10, TimeUnit.SECONDS);
                if (logger.isTraceEnabled()) {
                    logger.trace("MQTT disconnect successful");
                }
            } catch (InterruptedException | ExecutionException | TimeoutException exception) {
                logger.warn("MQTT disconnect failed: {}", exception.getMessage());
            }
            this.connection = null;
        }
    }

    @Override
    public void connectionStateChanged(MqttConnectionState state, @Nullable Throwable error) {
        if (state == MqttConnectionState.CONNECTED) {
            MqttBrokerConnection connection = this.connection;

            // This would be very strange, but Eclipse forces us to do the check
            if (connection != null) {
                reconnect = null;

                connection.subscribe("frigate/#", this).exceptionally(exception -> {
                    logger.warn("MQTT subscription failed: {}", exception.getMessage());
                    return false;
                }).thenAccept(successful -> {
                    if (successful && logger.isTraceEnabled()) {
                        logger.trace("MQTT subscription successful");
                    } else {
                        logger.warn("MQTT subscription failed: Timeout");
                    }
                });
            } else {
                logger.warn("Established connection without broker pointer");
            }
        } else {
            String message = (error != null) ? error.getMessage() : "Unknown reason";
            logger.warn("MQTT connection failed: {}", message);
        }
    }

    @Override
    public void processMessage(String topic, byte[] payload) {
        receive(topic, payload);
    }

    public abstract void receive(final String topic, final byte[] payload);

    public void send(final String topic, final String payload) {
        MqttBrokerConnection connection = this.connection;
        if (connection != null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Sending {}: {}", topic, payload);
            }
            connection.publish(topic, payload.getBytes(UTF_8), connection.getQos(), false);
        }
    }
}
