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
package no.seime.openhab.binding.panasoniccomfortcloud.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

import no.seime.openhab.binding.panasoniccomfortcloud.internal.dto.AbstractRequest;

/**
 * The {@link CommunicationException} class wraps exceptions raised when communicating with the API
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public class CommunicationException extends PanasonicComfortCloudException {
    private static final long serialVersionUID = 1L;

    public CommunicationException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public CommunicationException(final String message) {
        super(message);
    }

    public CommunicationException(final AbstractRequest req, final String overallStatus) {
        super("Server responded with error to request " + req.getClass().getSimpleName() + "/" + req.getRequestUrl()
                + ": " + overallStatus);
    }
}
