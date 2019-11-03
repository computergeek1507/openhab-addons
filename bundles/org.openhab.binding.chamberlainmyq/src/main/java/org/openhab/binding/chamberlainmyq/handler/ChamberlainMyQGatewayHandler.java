/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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
package org.openhab.binding.chamberlainmyq.handler;

import static org.openhab.binding.chamberlainmyq.ChamberlainMyQBindingConstants.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.chamberlainmyq.config.ChamberlainMyQGatewayConfig;
import org.openhab.binding.chamberlainmyq.internal.ChamberlainMyQResponseCode;
import org.openhab.binding.chamberlainmyq.internal.HttpUtil;
import org.openhab.binding.chamberlainmyq.internal.InvalidLoginException;
import org.openhab.binding.chamberlainmyq.internal.discovery.ChamberlainMyQDeviceDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

//import org.eclipse.smarthome.io.net.http.HttpUtil;

/**
 * The {@link ChamberlainMyQGatewayHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Scott Hanson - Initial contribution
 */
public class ChamberlainMyQGatewayHandler extends BaseBridgeHandler {

    private Logger logger = LoggerFactory.getLogger(ChamberlainMyQGatewayHandler.class);

    private String securityToken;
    private String accountId;
    ScheduledFuture<?> mainPollRefreshJob;
    private int refreshInterval;
    private int quickPoll;

    private ScheduledExecutorService pollService = Executors.newSingleThreadScheduledExecutor();

    /**
     * The regular polling task
     */
    private ScheduledFuture<?> pollFuture;

    /**
     * This task will reset the poll interval back to normal after a rapid poll
     * cycle
     */
    private ScheduledFuture<?> pollResetFuture;

    /**
     * Cap the time we poll rapidly to not overwhelm the servers with api
     * requests.
     */
    private static int MAX_RAPID_REFRESH = 30 * 1000;
    // private Properties header;

    public ChamberlainMyQGatewayHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.error("The gateway doesn't support any command!");
    }

    @Override
    public void initialize() {
        this.config = getThing().getConfiguration().as(ChamberlainMyQGatewayConfig.class);
        if (validConfiguration()) {
            ChamberlainMyQDeviceDiscoveryService discovery = new ChamberlainMyQDeviceDiscoveryService(this);

            this.bundleContext.registerService(DiscoveryService.class, discovery, null);

            refreshInterval = config.pollPeriod;
            quickPoll = config.quickPollPeriod;
            startAutomaticRefresh();
            updateStatus(ThingStatus.ONLINE);
        }
    }

    @Override
    public void dispose() {
        if (mainPollRefreshJob != null) {
            mainPollRefreshJob.cancel(true);
        }
        if (pollFuture != null) {
            pollFuture.cancel(true);
        }
        if (pollResetFuture != null) {
            pollResetFuture.cancel(true);
        }
        super.dispose();
    }

    private boolean validConfiguration() {
        if (this.config == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Gateway configuration missing");
            return false;
        } else if (StringUtils.isEmpty(this.config.username)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "username not specified");
            return false;
        } else if (StringUtils.isEmpty(this.config.password)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "password not specified");
            return false;
        } else if (!login()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "failed to login to Chamberlain MyQ Service");
            return false;
        }
        return true;
    }

    public ChamberlainMyQGatewayConfig getGatewayConfig() {
        return this.config;
    }

    private ChamberlainMyQGatewayConfig config;

    // REST API variables
    /**
     * Returns the currently cached security token, this will make a call to
     * login if the token does not exist.
     *
     * @return The cached security token
     * @throws IOException
     * @throws InvalidLoginException
     */
    private String getSecurityToken() throws IOException, InvalidLoginException {
        if (securityToken == null) {
            login();
        }
        return securityToken;
    }
    
    /**
     * Returns the currently cached Account ID, this will make a call to
     * findAccount if the Account ID is not known.
     *
     * @return The cached Account ID
     * @throws IOException
     * @throws InvalidLoginException
     */
    private String getAccountID() throws IOException, InvalidLoginException {

        if (accountId == null) {
            findAccount();
        }
        return accountId;
    }
    
    /**
     * Get the Account ID for the current user
     */
    private boolean findAccount() throws InvalidLoginException, IOException {
        if (securityToken == null) {
            login();
        }
        logger.trace("attempting to get acount");
        String url = String.format("%s/api/v5/My/?expand=account", WEBSITE);

        String message = "{\"expand\":\"account\"}";
        //header.put("SecurityToken", getSecurityToken());
        JsonObject data = request("GET", url, null, null, true, false, enc(getSecurityToken()), false );
        
        if (data.isJsonNull()) {
            logger.error("getting myq accountId failed");
            return false;
        }

        if (!data.get("UserId").isJsonNull()) {
            accountId = data.get("UserId").getAsString();
            logger.debug("myq UserId: {}", accountId);
            return true;
        }
        logger.error("getting myq accountId failed");
        return false;
    }

    private boolean login() {
        logger.debug("attempting to login");

        String url = String.format("%s/api/v5/Login", WEBSITE);

        String message = String.format("{\"Username\":\"%s\",\"Password\":\"%s\"}", this.config.username,
                this.config.password);
        // Result result = http.post(url, message);
        // logger.debug(result.getBody());
        JsonObject data = request("POST", url, message, "application/json", true, true, "", false);

        if (data.isJsonNull()) {
            logger.error("getting myq securityToken failed");
            return false;
        }

        if (!data.get("SecurityToken").isJsonNull()) {
            securityToken = data.get("SecurityToken").getAsString();
            logger.debug("myq securityToken: {}", securityToken);
            return true;
        }
        logger.error("getting myq securityToken failed");
        return false;
    }

    /**
     * Retrieves MyQ device data from myq website, throws if connection
     * fails or user login fails
     *
     */
    public JsonObject getMyqData() throws InvalidLoginException, IOException {
        logger.debug("Retrieving door data");
        String tempToken = getSecurityToken();
        String url = String.format("%s/api/v5/Accounts/%s/Devices", WEBSITE, getAccountID());

        JsonObject data = request("GET", url, null, null, true, false, enc(tempToken), false);

        return data;
    }

    /**
     * Send Command to open/close garage door opener with MyQ API Returns false
     * if return code from API is not correct or connection fails
     *
     * @param deviceSerial MyQ deviceSerial of Garage Door Opener.
     * @param command     command value "open", "close", "turnon" , or "turnoff"
     * @param state    Desired state to put the door in, 1 = open, 0 = closed
     *                     Desired state to put the lamp in, 1 = on, 0 = off
     */
    public void executeMyQCommand(String deviceSerial, String command, boolean rapidPoll)
            throws InvalidLoginException, IOException {
        String tempToken = getSecurityToken();
        String message = String.format( "{\"action_type\":\"%s\"}", command);
        String url = String.format("%s/api/v5/Accounts/%s/Devices/%s/actions", WEBSITE,
                getAccountID(), deviceSerial);

        request("PUT", url, message, "application/json", true, false, enc(tempToken),true);

        if (rapidPoll) {
            beginRapidPoll();
        } else {
            doFuturePoll(quickPoll * 1000);
        }

    }

    public interface RequestCallback {
        public void parseRequestResult(JsonObject resultJson);

        public void onError(String error);
    }

    protected class Request implements Runnable {
        private RequestCallback callback;

        public Request(RequestCallback callback) {
            this.callback = callback;
        }

        protected String checkForFailure(JsonObject jsonResult) {
            if (jsonResult.get("data").isJsonNull()) {
                return jsonResult.get("errors").getAsString();
            }
            return null;
        }

        @Override
        public void run() {
            try {
                JsonObject resultJson = getMyqData();
                callback.parseRequestResult(resultJson);
            } catch (Exception e) {
                logger.error("An exception occurred while executing a request to the Gateway: '{}'", e.getMessage());
            }
        }
    }

    public void sendRequestToServer(RequestCallback callback) throws IOException {
        Request request = new Request(callback);
        request.run();
    }

    private void startAutomaticRefresh() {
        mainPollRefreshJob = scheduler.scheduleAtFixedRate(() -> {
            try {
                refreshDeviceState();
            } catch (Exception e) {
                logger.debug("Exception occurred during refresh: {}", e.getMessage(), e);
            }
        }, 0, refreshInterval, TimeUnit.SECONDS);
    }

    private synchronized void refreshDeviceState() {
        try {
            JsonObject resultJson = getMyqData();
            Bridge bridge = getThing();

            List<Thing> things = bridge.getThings();
            if (!resultJson.get("items").isJsonNull()) {
                JsonElement deviceData = resultJson.get("items");
                Iterator<JsonElement> deviceDataIter = deviceData.getAsJsonArray().iterator();
                while (deviceDataIter.hasNext()) {
                    JsonElement element = deviceDataIter.next();
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    if (element.getAsJsonObject().get(MYQ_SERIAL) != null) {
                        String findDeviceSerial = element.getAsJsonObject().get(MYQ_SERIAL).toString();
                        for (Thing thing : things) {
                            if (thing.getUID().getId().compareTo(findDeviceSerial) == 0) {
                                ChamberlainMyQHandler test = (ChamberlainMyQHandler) thing.getHandler();
                                if (test != null) {
                                    test.updateState(element.getAsJsonObject());
                                }
                            }
                        }
                    }

                }
            }
        } catch (Exception e) {
            logger.error("An exception occurred while executing a request to the Gateway: '{}'", e.getMessage());
        }
    }

    // UTF-8 URL encode
    private String enc(String str) {
        try {
            return URLEncoder.encode(str, "utf-8");
        } catch (UnsupportedEncodingException e) {
            // throw new EndOfTheWorldException()
            throw new UnsupportedOperationException("UTF-8 not supported");
        }
    }

    /**
     * Make a request to the server, optionally retry the call if there is a
     * login issue. Will throw a InvalidLoginExcpetion if the account is
     * invalid, locked or soon to be locked.
     *
     * @param method      The Http Method Type (GET,PUT)
     * @param url         The request URL
     * @param payload     Payload string for put operations
     * @param payloadType Payload content type for put operations
     * @param retry       Retry the attempt if our session key is not valid
     * @return The JsonNode representing the response data
     * @throws IOException
     * @throws InvalidLoginException
     */
    private synchronized JsonObject request(String method, String url, String payload, String payloadType,
            boolean retry, boolean login, String securityToken, boolean command) {
        Properties header;
        header = new Properties();
        header.put("Accept", "application/json");
        //header.put("Connection", "keep-alive");
        header.put("Content-Type", "application/json");
        header.put("User-Agent", USERAGENT);
        logger.debug("User-Agent: {}", USERAGENT);
        header.put("BrandId", BRANDID);
        logger.debug("BrandId: {}", BRANDID);
        //header.put("ApiVersion", APIVERSION);
        //logger.debug("ApiVersion: {}", APIVERSION);
        //header.put("Culture", CULTURE);
        //logger.debug("Culture: {}", CULTURE);
        if (!login) {
            header.put("SecurityToken", securityToken);
            logger.debug("SecurityToken: {}", securityToken);
        }
        header.put("MyQApplicationId", APP_ID);
        logger.debug("MyQApplicationId: {}", APP_ID);
        logger.debug("Requesting method {}", method);
        logger.debug("Requesting URL {}", url);
        logger.debug("Requesting payload {}", payload);
        logger.debug("Requesting payloadType {}", payloadType);

        String dataString;
        try {
            dataString = HttpUtil.executeUrl(method, url, header,
                    payload == null ? null : IOUtils.toInputStream(payload), payloadType, (this.config.timeout * 1000));

            if(command && dataString == null)
                return null;

            logger.debug("Received MyQ JSON: {}", dataString);

            if (dataString == null) {
                logger.error("Null response from MyQ server");
                throw new IOException("Null response from MyQ server");
            }
        } catch (Exception e) {
            logger.error("Requesting URL Failed", e);
            return new JsonObject();
        }
        try {
            JsonParser parser = new JsonParser();
            JsonObject rootNode = parser.parse(dataString).getAsJsonObject();

            int returnCode = Integer.parseInt(rootNode.get("code").getAsString());
            logger.debug("myq ReturnCode: {}", returnCode);

            ChamberlainMyQResponseCode rc = ChamberlainMyQResponseCode.fromCode(returnCode);

            switch (rc) {
                case OK: {
                    return rootNode;
                }
                case ACCOUNT_INVALID:
                case ACCOUNT_NOT_FOUND:
                case ACCOUNT_LOCKED:
                case ACCOUNT_LOCKED_PENDING:
                    // these are bad, we do not want to continue to log in and
                    // lock an account
                    // throw new InvalidLoginException(rc.getDesc());
                    logger.error("Your MyQ Acount is invalid: {}", rc.getDesc());
                    return new JsonObject();
                case LOGIN_ERROR:
                    // Our session key has expired, request a new one
                    if (retry) {
                        login();
                        return request(method, url, payload, payloadType, false, login, securityToken, command);
                    }
                    // fall through to default
                default:
                    logger.error("Request Failed: {}", rc.getDesc());
                    return new JsonObject();
            }

        } catch (Exception e) {
            logger.error("Could not parse response", e);
            return new JsonObject();
        }
    }

    /**
     * Schedule our polling task
     *
     * @param millis
     */
    private void schedulePoll(long millis) {
        logger.trace("rapidRefreshFuture scheduling for {} millis", millis);
        // start polling at the RAPID_REFRESH_SECS interval
        pollFuture = pollService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    refreshDeviceState();
                } catch (Exception e) {
                    logger.trace("Exception occurred during refresh: {}", e.getMessage(), e);
                }
            }
        }, 0, millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Schedule the task to reset out poll rate in a future time
     */
    private void scheduleFuturePollReset() {
        // stop rapid polling after MAX_RAPID_REFRESH_SECS
        pollResetFuture = pollService.schedule(new Runnable() {
            @Override
            public void run() {
                logger.trace("rapidRefreshFutureEnd stopping");
                if (pollFuture != null && !pollFuture.isCancelled()) {
                    pollFuture.cancel(false);
                }
            }
        }, MAX_RAPID_REFRESH, TimeUnit.MILLISECONDS);
    }

    /**
     * Start rapid polling
     *
     */
    private void beginRapidPoll() {
        if (pollResetFuture != null) {
            pollResetFuture.cancel(true);
            pollResetFuture = null;
        }

        if (pollResetFuture == null || pollResetFuture.isCancelled()) {
            if (pollFuture == null || pollFuture.isCancelled()) {
                schedulePoll(quickPoll * 1000);
            }
            scheduleFuturePollReset();
        }
    }

    /**
     * schedule a Poll in the near future
     */
    private void doFuturePoll(long millis) {
        pollFuture = pollService.schedule(new Runnable() {
            @Override
            public void run() {
                logger.trace("do schedule poll");
                try {
                    refreshDeviceState();
                } catch (Exception e) {
                    logger.debug("Exception occurred during refresh: {}", e.getMessage(), e);
                }
            }
        }, millis, TimeUnit.MILLISECONDS);
    }
}
