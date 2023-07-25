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

/**
 * @author Arne Seime - Initial contribution
 */
public enum FanSpeed {
    AUTO(0),
    LOW(1),
    LOW_MIDDLE(2),
    MIDDLE(3),
    HIGH_MIDDLE(4),
    HIGH(5);

    public final int value;

    FanSpeed(int value) {
        this.value = value;
    }

    public static FanSpeed parseValue(int value) {
        for (FanSpeed e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
