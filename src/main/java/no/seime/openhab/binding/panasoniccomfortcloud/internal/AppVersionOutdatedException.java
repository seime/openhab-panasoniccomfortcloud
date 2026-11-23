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

/**
 * The {@link AppVersionOutdatedException} is raised when the API rejects the x-app-version header (error code 4106)
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public class AppVersionOutdatedException extends CommunicationException {
    private static final long serialVersionUID = 1L;

    private final String rejectedAppVersion;

    public AppVersionOutdatedException(final String rejectedAppVersion) {
        super(String.format(
                "New app version published - check the version number of your mobile app and enter the value as account config parameter (currently using %s)",
                rejectedAppVersion));
        this.rejectedAppVersion = rejectedAppVersion;
    }

    public String getRejectedAppVersion() {
        return rejectedAppVersion;
    }
}
