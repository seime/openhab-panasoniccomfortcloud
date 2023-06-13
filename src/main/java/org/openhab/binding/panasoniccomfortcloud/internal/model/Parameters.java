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
package org.openhab.binding.panasoniccomfortcloud.internal.model;

import org.openhab.binding.panasoniccomfortcloud.internal.dto.ParametersDTO;

/**
 * @author Arne Seime - Initial contribution
 */
public class Parameters {

    private Parameters shadowParameters = null;
    private AirSwingUpDown swingUpDown;
    private AirSwingSideways airSwingSideways;
    private OperationMode mode;
    private EcoMode ecoMode;
    private AirSwingAutoMode airSwingAutoMode;
    private FanSpeed fanSpeed;
    private NanoeMode nanoeMode;
    private NanoeMode actualNanoeMode;
    private Double targetTemperature;

    private Integer ecoFunctionData;
    private Integer lastSettingMode;
    private Integer ecoNavi;
    private Integer iAuto;
    private Integer airQuality;
    private Integer insideTemperature;
    private Integer outsideTemperature;
    private boolean masterSwitch;
    private Integer airDirection;

    /**
     * Parse from wire format
     */
    public Parameters(ParametersDTO dto, Device device) {
        swingUpDown = AirSwingUpDown.parseValue(dto.airSwingUD);
        airSwingSideways = AirSwingSideways.parseValue(dto.airSwingLR);
        mode = OperationMode.parseValue(dto.operationMode);
        ecoMode = EcoMode.parseValue(dto.ecoMode);
        airSwingAutoMode = AirSwingAutoMode.parseValue(dto.fanAutoMode);
        fanSpeed = FanSpeed.parseValue(dto.fanSpeed);
        nanoeMode = NanoeMode.parseValue(dto.nanoe);
        actualNanoeMode = NanoeMode.parseValue(dto.actualNanoe);
        masterSwitch = dto.operate != null && dto.operate != 0;

        if (dto.temperatureSet > 0 && dto.temperatureSet < 120) {
            targetTemperature = dto.temperatureSet;
        }
        if (dto.insideTemperature > -50 && dto.insideTemperature < 120) {
            insideTemperature = dto.insideTemperature;
        }
        if (dto.outTemperature > -50 && dto.outTemperature < 120) {
            outsideTemperature = dto.outTemperature;
        }

        this.airQuality = dto.airQuality;
        this.ecoNavi = dto.ecoNavi;
        this.iAuto = dto.iAuto;
        this.airDirection = dto.airDirection;
        this.lastSettingMode = dto.lastSettingMode;
        this.ecoFunctionData = dto.ecoFunctionData;
    }

    public Parameters(OperationMode mode, boolean masterSwitch) {
        this.setMode(mode);
        this.setMasterSwitch(masterSwitch);
    }

    public Parameters(OperationMode mode, boolean masterSwitch, Parameters currentParameters) {
        this.setMode(mode);
        this.setMasterSwitch(masterSwitch);
        this.shadowParameters = currentParameters;
    }

    /**
     * Convert to wire format
     */
    public ParametersDTO toParametersDTO(Device device) {
        ParametersDTO dto = new ParametersDTO();

        if (!device.getFeatureSet().getSupportedSwingUpDownModes().isEmpty() && swingUpDown != null) {
            dto.airSwingUD = swingUpDown.value;
        }

        if (!device.getFeatureSet().getSupportedSwingSidewayModes().isEmpty() && airSwingSideways != null) {
            dto.airSwingLR = airSwingSideways.value;
        }

        dto.operationMode = mode.value; // Always set

        dto.ecoMode = ecoMode != null ? ecoMode.value : null;
        dto.fanAutoMode = airSwingAutoMode != null ? airSwingAutoMode.value : null;
        dto.fanSpeed = fanSpeed != null ? fanSpeed.value : null;

        dto.nanoe = nanoeMode != null ? nanoeMode.value : null;
        dto.actualNanoe = actualNanoeMode != null ? actualNanoeMode.value : null;

        dto.operate = masterSwitch ? 1 : 0;
        dto.temperatureSet = targetTemperature;

        if (device.getFeatureSet().isEcoNavi()) {
            dto.ecoNavi = ecoNavi;
        }

        if (device.getFeatureSet().isiAutoX()) {
            dto.iAuto = iAuto;
        }

        dto.airDirection = airDirection;

        return dto;
    }

    public AirSwingUpDown getSwingUpDown() {
        return swingUpDown;
    }

    public AirSwingSideways getSwingSideways() {
        return airSwingSideways;
    }

    public OperationMode getMode() {
        return mode;
    }

    public EcoMode getEcoMode() {
        return ecoMode;
    }

    public AirSwingAutoMode getFanAutoMode() {
        return airSwingAutoMode;
    }

    public FanSpeed getFanSpeed() {
        return fanSpeed;
    }

    public NanoeMode getNanoeMode() {
        return nanoeMode;
    }

    public NanoeMode getActualNanoeMode() {
        return actualNanoeMode;
    }

    public Double getTargetTemperature() {
        return targetTemperature;
    }

    public Integer getEcoFunctionData() {
        return ecoFunctionData;
    }

    public Integer getLastSettingMode() {
        return lastSettingMode;
    }

    public Integer getEcoNavi() {
        return ecoNavi;
    }

    public Integer getiAuto() {
        return iAuto;
    }

    public Integer getAirQuality() {
        return airQuality;
    }

    public Integer getInsideTemperature() {
        return insideTemperature;
    }

    public Integer getOutsideTemperature() {
        return outsideTemperature;
    }

    public boolean isMasterSwitch() {
        return masterSwitch;
    }

    public Integer getAirDirection() {
        return airDirection;
    }

    public void setSwingUpDown(AirSwingUpDown airSwingUpDown) {
        this.swingUpDown = airSwingUpDown;
        if (shadowParameters != null) {
            this.shadowParameters.setSwingUpDown(this.swingUpDown);
        }
    }

    public void setSwingSideways(AirSwingSideways airSwingSideways) {
        this.airSwingSideways = airSwingSideways;
        if (shadowParameters != null) {
            this.shadowParameters.setSwingSideways(airSwingSideways);
        }
    }

    public void setMode(OperationMode mode) {
        this.mode = mode;
        if (shadowParameters != null) {
            this.shadowParameters.setMode(mode);
        }
    }

    public void setEcoMode(EcoMode ecoMode) {
        this.ecoMode = ecoMode;
        if (shadowParameters != null) {
            this.shadowParameters.setEcoMode(ecoMode);
        }
    }

    public void setFanAutoMode(AirSwingAutoMode airSwingAutoMode) {
        this.airSwingAutoMode = airSwingAutoMode;
        if (shadowParameters != null) {
            this.shadowParameters.setFanAutoMode(airSwingAutoMode);
        }
    }

    public void setFanSpeed(FanSpeed fanSpeed) {
        this.fanSpeed = fanSpeed;
        if (shadowParameters != null) {
            this.shadowParameters.setFanSpeed(fanSpeed);
        }
    }

    public void setNanoeMode(NanoeMode nanoeMode) {
        this.nanoeMode = nanoeMode;
        if (shadowParameters != null) {
            this.shadowParameters.setNanoeMode(nanoeMode);
        }
    }

    public void setTargetTemperature(Double targetTemperature) {
        this.targetTemperature = targetTemperature;
        if (shadowParameters != null) {
            this.shadowParameters.setTargetTemperature(targetTemperature);
        }
    }

    public void setMasterSwitch(boolean masterSwitch) {
        this.masterSwitch = masterSwitch;
        if (shadowParameters != null) {
            this.shadowParameters.setMasterSwitch(masterSwitch);
        }
    }
}
