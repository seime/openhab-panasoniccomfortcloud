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
package no.seime.openhab.binding.panasoniccomfortcloud.internal.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;

import no.seime.openhab.binding.panasoniccomfortcloud.internal.dto.DeviceDTO;

/**
 * @author Arne Seime - Initial contribution
 */
public class Device {

    private Group group;
    private String deviceId;
    private String type; // Figure out what this is
    private String name;
    private Integer permission; // Figure out what this is
    private Integer summerhouse; // Figure out what this is
    private String deviceModel;

    private FeatureSet featureSet;

    private Unit<Temperature> temperatureUnit;
    /**
     * TODO modeAvlList - figure out what it is
     * * "modeAvlList": {
     * "autoMode": 1,
     * "fanMode": 1
     * },
     */
    private Boolean coordinableFlg;
    private Boolean pairedFlg;

    private Instant lastUpdated;

    private Parameters currentParameters;

    public Parameters getCurrentParameters() {
        return currentParameters;
    }

    private boolean isInitialized = false;

    public Device(Group group) {
        this.group = group;
    }

    public void mergeFromGroupList(DeviceDTO dto) {
        this.deviceId = dto.deviceGuid;
        this.type = dto.deviceType;
        this.deviceModel = dto.deviceModuleNumber;
        this.name = dto.deviceName;
    }

    public void mergeFromDeviceDetails(DeviceDTO dto) {
        this.featureSet = new FeatureSet(dto);

        this.permission = dto.permission;
        this.summerhouse = dto.summerHouse;
        this.temperatureUnit = dto.temperatureUnit == 0 ? SIUnits.CELSIUS : ImperialUnits.FAHRENHEIT;

        currentParameters = new Parameters(dto.parameters, this);

        this.lastUpdated = Instant.ofEpochMilli(dto.timestamp);
        this.isInitialized = true;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Integer getPermission() {
        return permission;
    }

    public Integer getSummerhouse() {
        return summerhouse;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public FeatureSet getFeatureSet() {
        return featureSet;
    }

    public Unit<Temperature> getTemperatureUnit() {
        return temperatureUnit;
    }

    public Boolean getCoordinableFlg() {
        return coordinableFlg;
    }

    public Boolean getPairedFlg() {
        return pairedFlg;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public Parameters createSendRequestParameters() {
        return new Parameters(currentParameters.getMode(), currentParameters.isMasterSwitch(), currentParameters);
    }

    public Map<String, String> getThingProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("deviceId", deviceId);
        properties.put("name", name);
        properties.put("group", group.getName());
        if (deviceModel != null) {
            properties.put("model", deviceModel);
        } else {
            properties.put("model", "(Unknown)");
        }
        if (featureSet != null) {
            properties.put("nanoe", String.valueOf(featureSet.isNanoe()));
            properties.put("iAutoX", String.valueOf(featureSet.isiAutoX()));
            properties.put("nanoeStandalone", String.valueOf(featureSet.isNanoeStandAlone()));
            properties.put("ecoNavi", String.valueOf(featureSet.isEcoNavi()));
            properties.put("supportedOperationModes", String.valueOf(featureSet.getSupportedOperationModes()));
            properties.put("supportedEcoModes", String.valueOf(featureSet.getSupportedEcoModes()));
            properties.put("supportedAirSwingHorizontal", String.valueOf(featureSet.getSupportedSwingSidewayModes()));
            properties.put("supportedAirSwingVertical", String.valueOf(featureSet.getSupportedSwingUpDownModes()));
        }

        properties.put("supportedFanSpeeds", String.valueOf(Arrays.asList(FanSpeed.values())));
        properties.put("supportedAirSwingAutoModes", String.valueOf(Arrays.asList(AirSwingAutoMode.values())));

        if (summerhouse != null)
            properties.put("summerHouse", String.valueOf(summerhouse));

        return properties;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public Group getGroup() {
        return group;
    }

    public void setType(String deviceType) {
        this.type = deviceType;
    }
}
