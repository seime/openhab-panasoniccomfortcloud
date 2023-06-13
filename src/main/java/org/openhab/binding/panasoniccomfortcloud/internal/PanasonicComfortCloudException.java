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
package org.openhab.binding.panasoniccomfortcloud.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link PanasonicComfortCloudException} class wraps exceptions raised when communicating with the API
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public abstract class PanasonicComfortCloudException extends Exception {

    private static final long serialVersionUID = 1L;

    protected PanasonicComfortCloudException(String message) {
        super(message);
    }

    protected PanasonicComfortCloudException(String message, Throwable cause) {
        super(message, cause);
    }
}
