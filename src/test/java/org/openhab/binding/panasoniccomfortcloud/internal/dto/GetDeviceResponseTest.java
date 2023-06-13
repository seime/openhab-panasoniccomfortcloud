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
package org.openhab.binding.panasoniccomfortcloud.internal.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;
import org.openhab.binding.panasoniccomfortcloud.internal.BindingConstants;
import org.openhab.binding.panasoniccomfortcloud.internal.model.Device;
import org.openhab.binding.panasoniccomfortcloud.internal.model.Group;
import org.openhab.binding.panasoniccomfortcloud.internal.model.Parameters;

import com.google.gson.reflect.TypeToken;

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
}
