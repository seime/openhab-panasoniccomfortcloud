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

/**
 * @author Arne Seime - Initial contribution
 */
public enum AirSwingAutoMode {
    AUTO(0),
    DISABLED(1),
    UP_DOWN(2),
    LEFT_RIGHT(3);

    public final int value;

    AirSwingAutoMode(int value) {
        this.value = value;
    }

    public static AirSwingAutoMode parseValue(int value) {
        for (AirSwingAutoMode e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
