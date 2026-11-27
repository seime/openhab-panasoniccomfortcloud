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
package no.seime.openhab.binding.panasoniccomfortcloud.internal.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import no.seime.openhab.binding.panasoniccomfortcloud.internal.BindingConstants;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.model.Device;

/**
 * @author Arne Seime - Initial contribution
 */
public class PanasonicComfortCloudDiscoveryServiceTest {

    @Test
    public void testIllegalCharactersInThingUID() {
        assertEquals("A-B", PanasonicComfortCloudDiscoveryService.createCleanDeviceId("A+B"));
        assertEquals("A-B", PanasonicComfortCloudDiscoveryService.createCleanDeviceId("A/B"));
        assertEquals("A-B", PanasonicComfortCloudDiscoveryService.createCleanDeviceId("A#B"));
        assertEquals("A_B", PanasonicComfortCloudDiscoveryService.createCleanDeviceId("A_B"));
        assertEquals("A_B", PanasonicComfortCloudDiscoveryService.createCleanDeviceId("A_B"));
        assertEquals("A-B", PanasonicComfortCloudDiscoveryService.createCleanDeviceId("A-B"));
        assertEquals("a-b", PanasonicComfortCloudDiscoveryService.createCleanDeviceId("a-b"));
        assertEquals("1-2", PanasonicComfortCloudDiscoveryService.createCleanDeviceId("1-2"));
    }

    @Test
    public void testThingTypeMapping() {
        assertEquals(BindingConstants.THING_TYPE_AIRCONDITION,
                PanasonicComfortCloudDiscoveryService.getThingType(device("1")));
        assertEquals(BindingConstants.THING_TYPE_AIRCONDITION,
                PanasonicComfortCloudDiscoveryService.getThingType(device("3")));
        assertEquals(BindingConstants.THING_TYPE_AIRCONDITION,
                PanasonicComfortCloudDiscoveryService.getThingType(device("9")));
        assertNull(PanasonicComfortCloudDiscoveryService.getThingType(device("2")));
    }

    private static Device device(String type) {
        Device device = new Device(null);
        device.setType(type);
        return device;
    }
}
