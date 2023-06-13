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
public enum NanoeMode {
    UNAVAILABLE(0),
    OFF(1),
    ON(2),
    MODE_G(3),
    ALL(4);

    public final int value;

    NanoeMode(int value) {
        this.value = value;
    }

    public static NanoeMode parseValue(int value) {
        for (NanoeMode e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
