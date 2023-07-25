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
package no.seime.openhab.binding.panasoniccomfortcloud.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.Configuration;

/**
 * The {@link AccountConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public class AccountConfiguration extends Configuration {

    @Nullable
    public String username;
    @Nullable
    public String password;

    @Nullable
    public String appVersion;

    public int refreshInterval = 120;

    @Override
    public String toString() {
        return "AccountConfiguration{" + "appVersion='" + appVersion + '\'' + ", password='<REDACTED>'"
                + ", refreshInterval=" + refreshInterval + ", username='" + username + '\'' + '}';
    }
}
