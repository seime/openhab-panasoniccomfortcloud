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

import java.net.URLEncoder;
import java.nio.charset.Charset;

/**
 * All classes in the .dto are data transfer classes used by the GSON mapper. This class reflects a
 * part of a request/response data structure.
 *
 * @author Arne Seime - Initial contribution.
 */

public class GetDeviceRequest extends AbstractRequest {
    public final String deviceId;

    public GetDeviceRequest(final String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public String getRequestUrl() {
        return String.format("/deviceStatus/%s",
                URLEncoder.encode(deviceId.replace("/", "f"), Charset.defaultCharset()));
    }
}
