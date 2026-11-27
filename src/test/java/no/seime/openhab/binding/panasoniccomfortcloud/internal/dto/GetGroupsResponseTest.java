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

import java.io.IOException;
import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;

import com.google.gson.reflect.TypeToken;

/**
 * @author Arne Seime - Initial contribution
 */
public class GetGroupsResponseTest extends AbstractSerializationDeserializationTest {

    @Test
    public void testDeserialize() throws IOException {
        final Type type = new TypeToken<GetGroupsResponse>() {
        }.getType();

        final GetGroupsResponse rsp = wireHelper.deSerializeFromClasspathResource("/get_groups_response.json", type);

        assertEquals(1, rsp.groupList.length);
        assertEquals(3, rsp.groupList[0].devices.length);
    }

    @Test
    public void testDeserializeWiredController() throws IOException {
        final Type type = new TypeToken<GetGroupsResponse>() {
        }.getType();

        final GetGroupsResponse rsp = wireHelper
                .deSerializeFromClasspathResource("/get_groups_response_wired_controller.json", type);

        assertEquals(1, rsp.groupList.length);
        assertEquals(1, rsp.groupList[0].devices.length);
        DeviceDTO device = rsp.groupList[0].devices[0];
        assertEquals("9", device.deviceType);
        assertEquals("S-100PE4R", device.deviceModuleNumber);
        assertEquals(18.0, device.parameters.temperatureSet);
    }
}
