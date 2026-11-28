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
package no.seime.openhab.binding.panasoniccomfortcloud.internal.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.reflect.TypeToken;

import no.seime.openhab.binding.panasoniccomfortcloud.internal.BindingConstants;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.model.AirSwingUpDown;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.model.Device;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.model.EcoMode;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.model.Group;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.model.OperationMode;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.model.Parameters;

/**
 * @author Arne Seime - Initial contribution
 */
public class GetDeviceResponseTest extends AbstractSerializationDeserializationTest {

    @Test
    public void testDeserialize() throws IOException {
        final Type type = new TypeToken<DeviceDTO>() {
        }.getType();

        final DeviceDTO rsp = wireHelper.deSerializeFromClasspathResource("/get_device_response_on.json", type);
        Device device = new Device(new Group());
        device.mergeFromDeviceDetails(rsp);

        assertEquals(20, device.getCurrentParameters().getInsideTemperature());

        Parameters currentParameters = device.getCurrentParameters();
        assertTrue(currentParameters.isMasterSwitch());
        Parameters sendRequestParameters = device.createSendRequestParameters();
        assertTrue(sendRequestParameters.isMasterSwitch());

        sendRequestParameters.setTargetTemperature(22d);
        ParametersDTO parametersDTO = sendRequestParameters.toParametersDTO(device);
        assertEquals(1, parametersDTO.operate);

        // API reports "fanMode": false, but fan-only mode is still supported
        assertTrue(device.getFeatureSet().getSupportedOperationModes().contains(OperationMode.FAN));
    }

    @Test
    public void testFanModeSupportedDespiteFlagFalse() throws IOException {
        final Type type = new TypeToken<DeviceDTO>() {
        }.getType();

        final DeviceDTO rsp = wireHelper.deSerializeFromClasspathResource("/get_device_response_issue5.json", type);
        Device device = new Device(new Group());
        device.mergeFromDeviceDetails(rsp);

        assertEquals(OperationMode.FAN, device.getCurrentParameters().getMode());
        assertEquals(Set.of(OperationMode.AUTO, OperationMode.DRY, OperationMode.COOL, OperationMode.HEAT,
                OperationMode.FAN), device.getFeatureSet().getSupportedOperationModes());

        Parameters sendRequestParameters = device.createSendRequestParameters();
        sendRequestParameters.setMode(OperationMode.FAN);
        assertEquals(4, sendRequestParameters.toParametersDTO(device).operationMode);
    }

    @Test
    public void testDeserializeWifiDongleInvalidInsideTemperature() throws IOException {
        final Type type = new TypeToken<DeviceDTO>() {
        }.getType();

        final DeviceDTO rsp = wireHelper.deSerializeFromClasspathResource("/get_device_response_wifi_dongle_off.json",
                type);
        Device device = new Device(new Group());
        device.setType(BindingConstants.DEVICE_TYPE_WIFI_DONGLE);
        device.mergeFromDeviceDetails(rsp);

        assertEquals(null, device.getCurrentParameters().getInsideTemperature());
        assertEquals(null, device.getCurrentParameters().getTargetTemperature());
        assertEquals(null, device.getCurrentParameters().getOutsideTemperature());
    }

    @Test
    public void testParseJensError() throws IOException {
        final Type type = new TypeToken<DeviceDTO>() {
        }.getType();

        final DeviceDTO rsp = wireHelper.deSerializeFromClasspathResource("/get_device_response_jens.json", type);
        Device device = new Device(new Group());
        device.setType(BindingConstants.DEVICE_TYPE_WIFI_DONGLE);
        device.mergeFromDeviceDetails(rsp);
    }

    @Test
    public void testWiredControllerIgnoresAdvertisedEcoAndSwingModes() throws IOException {
        final Type type = new TypeToken<DeviceDTO>() {
        }.getType();

        // Issue #7: CZ-RTC6WBLW2 reports quietMode/powerfulMode/airSwingLR true but supports none of them
        final DeviceDTO rsp = wireHelper.deSerializeFromClasspathResource("/get_device_response_wired_controller.json",
                type);
        Device device = new Device(new Group());
        device.setType(BindingConstants.DEVICE_TYPE_WIRED_CONTROLLER);
        device.mergeFromDeviceDetails(rsp);

        assertEquals(Set.of(EcoMode.AUTO), device.getFeatureSet().getSupportedEcoModes());
        assertTrue(device.getFeatureSet().getSupportedSwingUpDownModes().isEmpty());
        assertTrue(device.getFeatureSet().getSupportedSwingSidewayModes().isEmpty());
        assertEquals(Set.of(OperationMode.AUTO, OperationMode.DRY, OperationMode.COOL, OperationMode.HEAT,
                OperationMode.FAN), device.getFeatureSet().getSupportedOperationModes());
        assertTrue(device.getFeatureSet().isNanoeStandAlone());
    }

    @Test
    public void testSameFlagsHonouredForBuiltinWifi() throws IOException {
        final Type type = new TypeToken<DeviceDTO>() {
        }.getType();

        final DeviceDTO rsp = wireHelper.deSerializeFromClasspathResource("/get_device_response_wired_controller.json",
                type);
        Device device = new Device(new Group());
        device.setType(BindingConstants.DEVICE_TYPE_WIFI_BUILTIN);
        device.mergeFromDeviceDetails(rsp);

        assertEquals(Set.of(EcoMode.AUTO, EcoMode.POWERFUL, EcoMode.QUIET),
                device.getFeatureSet().getSupportedEcoModes());
        assertEquals(Set.of(AirSwingUpDown.values()), device.getFeatureSet().getSupportedSwingUpDownModes());
    }
}
