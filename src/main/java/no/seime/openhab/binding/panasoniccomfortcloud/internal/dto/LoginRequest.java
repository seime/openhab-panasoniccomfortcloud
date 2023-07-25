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

/**
 * All classes in the .dto are data transfer classes used by the GSON mapper. This class reflects a
 * part of a request/response data structure.
 *
 * @author Arne Seime - Initial contribution.
 */

public class LoginRequest extends AbstractRequest {

    public int language = 0;
    public String loginId;
    public String password;

    public LoginRequest(String loginId, String password) {
        this.loginId = loginId;
        this.password = password;
    }

    @Override
    public String getMethod() {
        return "POST";
    }

    @Override
    public String getRequestUrl() {
        return "/auth/login";
    }
}
