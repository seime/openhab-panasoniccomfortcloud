/**
 * Copyright (c) 2023 Contributors to the Seime Openhab Addons project
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

package no.seime.openhab.binding.panasoniccomfortcloud.internal.handler;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import no.seime.openhab.binding.panasoniccomfortcloud.internal.ApiBridge;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.ConfigurationException;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.PanasonicComfortCloudException;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.config.AccountConfiguration;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.dto.DeviceDTO;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.dto.GetGroupsRequest;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.dto.GetGroupsResponse;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.dto.GroupDTO;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.model.Device;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.model.Group;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.model.GroupModel;

/**
 * The {@link PanasonicComfortCloudAccountHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public class PanasonicComfortCloudAccountHandler extends BaseBridgeHandler {
    private static final int MIN_TIME_BETWEEEN_MODEL_UPDATES = 30;
    public static final int MAX_RETRIES_BEFORE_GIVING_UP = 10;
    private final Logger logger = LoggerFactory.getLogger(PanasonicComfortCloudAccountHandler.class);
    private Optional<ScheduledFuture<?>> statusFuture = Optional.empty();
    private GroupModel model;
    @NonNullByDefault({})
    AccountConfiguration config;
    private ApiBridge apiBridge;
    private int errorCounter = 0;

    private static final String STORAGE_KEY = "PanasonicComfortCloud-Storage";

    public PanasonicComfortCloudAccountHandler(final Bridge bridge, StorageService storageService) {
        super(bridge);
        apiBridge = new ApiBridge(storageService.getStorage(STORAGE_KEY));
        model = new GroupModel(0);
    }

    @Override
    public void handleCommand(final ChannelUID channelUID, final Command command) {
        // Ignore commands as none are supported
    }

    @Override
    public void initialize() {
        // Stop any pending updates if any
        stopScheduledUpdate();

        updateStatus(ThingStatus.UNKNOWN);
        AccountConfiguration loadedConfig = getConfigAs(AccountConfiguration.class);
        config = loadedConfig;
        apiBridge.init(loadedConfig.username, loadedConfig.password, loadedConfig.appVersion);
        int refreshInterval = config.refreshInterval;
        if (refreshInterval < MIN_TIME_BETWEEEN_MODEL_UPDATES) {
            logger.warn("Refresh interval too short, setting minimum value of {}", MIN_TIME_BETWEEEN_MODEL_UPDATES);
            refreshInterval = MIN_TIME_BETWEEEN_MODEL_UPDATES;
        }

        statusFuture = Optional
                .of(scheduler.scheduleWithFixedDelay(this::doPollInternal, 0, refreshInterval, TimeUnit.SECONDS));
    }

    @Override
    public void dispose() {
        stopScheduledUpdate();
        super.dispose();
    }

    private void doPollInternal() {
        doPoll(true);
    }

    public synchronized void doPoll(boolean triggerDeviceUpdate) {

        try {
            final GetGroupsRequest getGroupsRequest = new GetGroupsRequest();
            final GetGroupsResponse getGroupsResponse = apiBridge.sendRequest(getGroupsRequest,
                    new TypeToken<GetGroupsResponse>() {
                    }.getType());

            // Merge model

            for (GroupDTO groupDto : getGroupsResponse.groupList) {
                Optional<Group> existingGroup = model.getGroups().stream()
                        .filter(e -> groupDto.groupId.equals(e.getId())).findFirst();
                Group group;
                if (existingGroup.isEmpty()) {
                    group = new Group();

                    model.addGroup(group);
                } else {
                    group = existingGroup.get();
                }

                group.mergeFrom(groupDto);

                for (final DeviceDTO deviceDTO : groupDto.devices) {
                    // Some device details come from the getGroups call, others come from each device call

                    Optional<Device> existingDevice = group.getDevices().stream()
                            .filter(e -> deviceDTO.deviceGuid.equals(e.getDeviceId())).findFirst();
                    Device device;
                    if (existingDevice.isEmpty()) {
                        device = new Device(group);
                        group.addDevice(device);
                    } else {
                        device = existingDevice.get();
                    }

                    device.mergeFromGroupList(deviceDTO);

                }
            }
            updateStatus(ThingStatus.ONLINE);
            if (triggerDeviceUpdate) {
                try {
                    getThing().getThings().parallelStream()
                            .filter(e -> e.isEnabled()
                                    && (e.getStatus() == ThingStatus.ONLINE || e.getStatus() == ThingStatus.OFFLINE))
                            .forEach(e -> {
                                try {
                                    ((PanasonicComfortCloudBaseThingHandler) e.getHandler()).loadFromServer();
                                } catch (Exception ex) {
                                    logger.warn("Error updating thing {}", e.getUID(), ex);
                                }
                            });
                } catch (NullPointerException ex) {
                    logger.debug(
                            "Nullpointer during update - happens if a thing is deleted while polling is in progress",
                            ex);
                }
            }
        } catch (final ConfigurationException e) {
            logger.info("Error initializing Panasonic Comfort Cloud data: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Error fetching data: " + e.getMessage());
            model.clear();
            stopScheduledUpdate();
        } catch (final PanasonicComfortCloudException e) {
            logger.info("Error initializing data: {}", e.getMessage());
            model.clear();
            errorCounter++;

            int retryDelay = 60 * (errorCounter * errorCounter);
            // Try init again
            if (errorCounter < MAX_RETRIES_BEFORE_GIVING_UP) {
                logger.info("Will try to re-init in {} seconds", retryDelay);
                statusFuture = Optional.of(scheduler.schedule(this::initialize, retryDelay, TimeUnit.SECONDS));
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Error fetching data: " + e.getMessage() + ", will retry in " + retryDelay + " seconds");
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Error fetching data: " + e.getMessage() + ", not retrying due to too many errors");
            }
        }
    }

    /**
     * Stops this thing's polling future
     */
    private void stopScheduledUpdate() {
        statusFuture.ifPresent(future -> {
            if (!future.isCancelled()) {
                future.cancel(true);
            }
            statusFuture = Optional.empty();
        });
    }

    public GroupModel getModel() {
        return model;
    }

    public ApiBridge getApiBridge() {
        return apiBridge;
    }
}
