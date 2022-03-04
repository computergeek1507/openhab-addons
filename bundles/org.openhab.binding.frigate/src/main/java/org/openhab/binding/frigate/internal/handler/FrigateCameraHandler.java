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
package org.openhab.binding.frigate.internal.handler;

import static org.openhab.binding.frigate.internal.FrigateBindingConstants.*;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.frigate.internal.FrigateCameraConfiguration;
import org.openhab.binding.frigate.internal.dto.EventDTO;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The {@link FrigateCameraHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Scott Hanson - Initial contribution
 */
@NonNullByDefault
public class FrigateCameraHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(FrigateCameraHandler.class);

    private @Nullable FrigateCameraConfiguration config;
    private @Nullable Future<?> normalPollFuture;
    //private static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
    private String cameraName = "";
    private int refreshInterval = 60;

    public FrigateCameraHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        if (command instanceof RefreshType) {
            updateImage(true);
            return;
        }
        if (CHANNEL_STATE.equals(channelUID.getId())) {
            if (command instanceof RefreshType) {
                // TODO: handle data refresh
            }

            // TODO: handle command

            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information:
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(FrigateCameraConfiguration.class);
        cameraName = config.name;
        refreshInterval = config.imagerefresh;
        logger.debug("Thing Name: {}", cameraName);
        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            boolean thingReachable = updateImage(true); 
            if (thingReachable) {
                updateStatus(ThingStatus.ONLINE);
                restartPolls();
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }
        });
    }

    @Override
    public void dispose() {
        stopPolls();
    }

    protected @Nullable FrigateServerHandler getGatewayHandler() {
        Bridge gateway = getBridge();
        return gateway == null ? null : (FrigateServerHandler) gateway.getHandler();
    }

    private boolean updateImage(boolean updateAll) {
        FrigateServerHandler localHandler = getGatewayHandler();
        if (localHandler != null) {
            logger.debug("Camera {}: Updating image channel", cameraName);
            String imgurl = String.format("/api/%s/latest.jpg", cameraName);
            RawType image = localHandler.getImage(imgurl);
            if (image != null) {
                updateState(CHANNEL_IMAGE, image);
                if(updateAll) {
                    updateState(CHANNEL_IMAGE_URL, new StringType(localHandler.buildBaseUrl(imgurl)));
                    String streamurl = String.format("/api/%s", cameraName);
                    updateState(CHANNEL_VIDEO_URL, new StringType(localHandler.buildBaseUrl(streamurl)));
                }
                return true;
            }
        }
        updateState(CHANNEL_IMAGE, UnDefType.UNDEF);
        if(updateAll) {
            updateState(CHANNEL_IMAGE_URL, UnDefType.UNDEF);
            updateState(CHANNEL_VIDEO_URL, UnDefType.UNDEF);
        }
        return false;
    }
    private void pollImage()
    {
        updateImage(false);
    }

    private synchronized void restartPolls() {
        stopPolls();
        normalPollFuture = scheduler.scheduleWithFixedDelay(this::pollImage, refreshInterval, refreshInterval,
                    TimeUnit.SECONDS);        
    }

    private synchronized void stopPolls() {
        stopFuture(normalPollFuture);
        normalPollFuture = null;
    }

    private void stopFuture(@Nullable Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    public String GetCameraName() {
        return cameraName;
    }

    public void SetLastObject(byte[] data) {
        RawType image = new RawType(data, "image/jpeg");
        updateState(CHANNEL_LASTOBJECT,  image);
    }

    public void UpdateEvent (EventDTO event){
        updateState(CHANNEL_EVENT_ID,  new StringType(event.id));
        updateState( CHANNEL_EVENT_TYPE, new StringType(event.label));
        updateState( CHANNEL_EVENT_SCORE , new DecimalType(event.top_score));
        //updateState( CHANNEL_EVENT_START ,new DateTimeType(ZonedDateTime.ofInstant(event.getEnd().toInstant(), timeZoneProvider.getTimeZone())));
        //updateState( CHANNEL_EVENT_END , new DateTimeType(event.end_time));
    }
}
