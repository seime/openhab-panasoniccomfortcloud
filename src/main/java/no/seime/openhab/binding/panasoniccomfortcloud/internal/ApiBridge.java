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

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.openhab.core.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import no.seime.openhab.binding.panasoniccomfortcloud.internal.dto.AbstractRequest;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.dto.GetAccClientIdDTO;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.dto.LoginRequestDTO;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * The {@link ApiBridge} is responsible for API login and communication
 *
 * @author Arne Seime - Initial contribution
 */
public class ApiBridge {
    public static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json; charset=utf-8";
    public static final MediaType JSON = MediaType.parse(APPLICATION_JSON_CHARSET_UTF_8);

    private static final String APP_CLIENT_ID = "Xmy6xIYIitMxngjB2rHvlm6HSDNnaMJx";
    private static final String AUTH_0_CLIENT = "eyJuYW1lIjoiQXV0aDAuQW5kcm9pZCIsImVudiI6eyJhbmRyb2lkIjoiMzAifSwidmVyc2lvbiI6IjIuOS4zIn0=";
    private static final String REDIRECT_URI = "panasonic-iot-cfc://authglb.digital.panasonic.com/android/com.panasonic.ACCsmart/callback";
    private static final String BASE_PATH_AUTH = "https://authglb.digital.panasonic.com";
    private static final String BASE_PATH_ACC = "https://accsmart.panasonic.com";
    private static final String APPBRAIN_URL = "https://www.appbrain.com/app/panasonic-comfort-cloud/com.panasonic.ACCsmart";
    private static final String DEFAULT_APP_VERSION = "1.21.0";

    private static final String ACCESS_TOKEN_KEY = "accessToken";
    private static final String REFRESH_TOKEN_KEY = "refreshToken";
    private static final String CLIENT_ID_KEY = "clientId";
    private static final String TOKEN_EXPIRY_KEY = "tokenExpiry";
    private static final String SCOPE_KEY = "scope";

    private static final int ERROR_CODE_UPDATE_VERSION = 4106;

    private final Logger logger = LoggerFactory.getLogger(ApiBridge.class);

    private String clientId;
    private String username;
    private String password;
    private String appVersion;
    private Gson gson;
    private OkHttpClient client;
    private Storage<String> storage;

    public ApiBridge(Storage<String> storage) {
        this.storage = storage;
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> logger.debug(message));
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        CookieJar cookieJar = new CookieJar() {

            private Map<String, List<Cookie>> cookies = new HashMap<>();

            @Override
            public void saveFromResponse(HttpUrl httpUrl, List<Cookie> list) {
                List<Cookie> cookies = this.cookies.computeIfAbsent(httpUrl.host(), s -> new ArrayList<>());
                cookies.addAll(list);
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl httpUrl) {
                List<Cookie> cookies = this.cookies.get(httpUrl.host());
                return cookies != null ? cookies : new ArrayList<>();
            }
        };

        client = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).addInterceptor(logging).cookieJar(cookieJar).build();

        gson = new GsonBuilder().setLenient().setPrettyPrinting().create();
    }

    private static @NonNull Map<String, String> parseCookies(Response redirectResponse) {
        Map<String, String> cookies = new HashMap<>();

        for (String header : redirectResponse.headers("Set-Cookie")) {
            Cookie cookie = Cookie.parse(HttpUrl.parse("https://www.example.com/"), header);
            cookies.put(cookie.name(), cookie.value());

        }
        return cookies;
    }

    public static String generateRandomStringHex(int bitLength) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < bitLength; i++) {
            b.append(Integer.toHexString((int) (Math.random() * 16)));
        }

        return b.toString();
    }

    public static String generateHash(String codeVerifier) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().encodeToString(encodedhash).replace("=", "");
    }

    public static String generateRandomString(int length) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < length; i++) {
            b.append((char) (Math.random() * 26 + 'a'));
        }

        return b.toString();
    }

    public void init(String username, String password, String configuredAppVersion) {
        this.username = username;
        this.password = password;

        if (configuredAppVersion == null) {
            logger.debug("No configured appVersion in thing configuration, trying to fetch from AppBrain website");
            appVersion = getAppVersion();
            if (appVersion == null) {
                logger.info(
                        "Could not fetch appVersion dynamically, and no value is provided on the bridge thing. Defaulting to  {}",
                        DEFAULT_APP_VERSION);
                appVersion = DEFAULT_APP_VERSION;
            } else {
                logger.debug("Fetched appVersion from AppBrain: {}", appVersion);
            }
        } else {
            appVersion = configuredAppVersion;
        }
    }

    private Request buildRequest(Token token, final AbstractRequest req) {

        Request.Builder request = new Request.Builder().url(BASE_PATH_ACC + req.getRequestUrl());

        if (req.getMethod().equals("POST")) {
            final String reqJson = gson.toJson(req);
            request = request.post(RequestBody.create(JSON, reqJson));
        }

        request.addHeader("Accept-Encoding", "gzip, deflate").addHeader("Accept", "*/*")
                .addHeader("User-Agent", "G-RAC").addHeader("Content-Type", "application/json;charset=utf-8")
                .addHeader("x-app-name", "Comfort Cloud")
                .addHeader("x-app-timestamp",
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
                                .format(Instant.now()))
                .addHeader("x-app-type", "1").addHeader("x-app-version", appVersion)
                .addHeader("x-cfc-api-key", generateRandomStringHex(128))
                .addHeader("x-user-authorization-v2", "Bearer " + token.getAccessToken())
                .addHeader("x-client-id", token.getClientId()).build();

        return request.build();
    }

    public <T> T sendRequest(final AbstractRequest req, final Type responseType) throws PanasonicComfortCloudException {

        Token token = getStoredToken();
        if (token == null || token.isExpired()) {
            token = getNewToken();
        }

        if (token.shouldRefresh()) {
            try {
                token = refreshToken(token);
                storeToken(token);
            } catch (CommunicationException | IOException e) {
                clearToken();
                token = getNewToken();
            }
        }

        return sendRequestInternal(buildRequest(token, req), req, responseType);
    }

    private @NonNull Token getNewToken() throws CommunicationException {
        try {
            Token token = doV2AuthorizationFlow();
            storeToken(token);
            return token;
        } catch (Exception e) {
            clearToken();
            throw new CommunicationException("Error obtaining access token - check credentials and appVersion", e);
        }
    }

    private String getQueryStringParameterFromHeaderEntryUrl(Response response, String headerEntry,
            String queryStringParameter) {
        String location = response.header(headerEntry);
        HttpUrl redirectUrl = HttpUrl.parse("https://www.example.com/" + location);
        return redirectUrl.queryParameter(queryStringParameter);
    }

    private Token doV2AuthorizationFlow() throws IOException, NoSuchAlgorithmException, CommunicationException {

        // TODO future: Most of this code might be replaced by the Auth0 Java SDK

        // --------------------------------------------------------------------
        // AUTHORIZE
        // --------------------------------------------------------------------
        String state = generateRandomString(20);
        String codeVerifier = generateRandomString(43);
        String codeChallenge = generateHash(codeVerifier);

        HttpUrl url = new HttpUrl.Builder().scheme("https").host("authglb.digital.panasonic.com")
                .addPathSegments("/authorize")
                .addQueryParameter("scope", "openid offline_access comfortcloud.control a2w.control")
                .addQueryParameter("audience", "https://digital.panasonic.com/" + APP_CLIENT_ID + "/api/v1/")
                .addQueryParameter("protocol", "oauth2").addQueryParameter("response_type", "code")
                .addQueryParameter("code_challenge", codeChallenge).addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("auth0Client", AUTH_0_CLIENT).addQueryParameter("client_id", APP_CLIENT_ID)
                .addQueryParameter("redirect_uri", REDIRECT_URI).addQueryParameter("state", state).build();

        Request authorizeRequest = new Request.Builder().get().url(url).addHeader("user-agent", "okhttp/4.10.0")
                .build();

        Response authorizeResponse = client.newCall(authorizeRequest).execute();

        if (authorizeResponse.code() != 302) {
            throw new CommunicationException("Authorize request failed with code " + authorizeResponse.code()
                    + ". Check credentials and appVersion");
        }

        // -------------------------------------------------------------------
        // FOLLOW REDIRECT
        // -------------------------------------------------------------------

        String location = authorizeResponse.header("Location");
        state = getQueryStringParameterFromHeaderEntryUrl(authorizeResponse, "Location", "state");

        if (!location.startsWith(REDIRECT_URI)) {
            HttpUrl redirectUrl = HttpUrl.parse(BASE_PATH_AUTH + "/" + location);

            Response redirectResponse = client.newCall(new Request.Builder().get().url(redirectUrl).build()).execute();
            Map<String, String> cookies = parseCookies(redirectResponse);
            String csrf = cookies.get("_csrf");

            if (csrf == null) {
                throw new CommunicationException("Expected cookie _csrf not found");
            }

            if (redirectResponse.code() != 200) {
                throw new CommunicationException("Follow Redirect request failed with code " + redirectResponse.code()
                        + ". Check credentials and appVersion");
            }

            // -------------------------------------------------------------------
            // LOGIN
            // -------------------------------------------------------------------
            LoginRequestDTO loginJson = new LoginRequestDTO();
            loginJson.client_id = APP_CLIENT_ID;
            loginJson.redirect_uri = REDIRECT_URI;
            loginJson.tenant = "pdpauthglb-a1";
            loginJson.response_type = "code";
            loginJson.scope = "openid offline_access comfortcloud.control a2w.control";
            loginJson.audience = "https://digital.panasonic.com/" + APP_CLIENT_ID + "/api/v1/";
            loginJson._csrf = csrf;
            loginJson.state = state;
            loginJson._intstate = "deprecated";
            loginJson.username = username;
            loginJson.password = password;
            loginJson.lang = "en";
            loginJson.connection = "PanasonicID-Authentication";

            Request.Builder loginRequest = new Request.Builder().url(BASE_PATH_AUTH + "/usernamepassword/login")
                    .addHeader("user-agent", "okhttp/4.10.0").addHeader("Auth0-Client", AUTH_0_CLIENT)
                    .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(loginJson)));
            Response loginResponse = client.newCall(loginRequest.build()).execute();

            if (loginResponse.code() != 200) {
                throw new CommunicationException("Login request failed with code " + loginResponse.code()
                        + ". Check credentials and appVersion");
            }

            // -------------------------------------------------------------------
            // CALLBACK
            // -------------------------------------------------------------------

            Document loginForm = Jsoup.parse(loginResponse.body().string());
            Elements elements = loginForm.selectXpath("//input[@type='hidden']");

            FormBody.Builder requestBody = new FormBody.Builder();
            for (int i = 0; i < elements.size(); i++) {
                requestBody.add(elements.get(i).attr("name"), elements.get(i).attr("value"));
            }

            Request submitFormRequest = new Request.Builder().url(BASE_PATH_AUTH + "/login/callback").addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Mobile Safari/537.36")
                    .addHeader("Content-Type", "application/x-www-form-urlencoded").post(requestBody.build()).build();
            Response submitFormResponse = client.newCall(submitFormRequest).execute();

            if (submitFormResponse.code() != 302) {
                throw new CommunicationException("Callback request failed with code " + submitFormResponse.code()
                        + ". Check credentials and appVersion");
            }

            // ------------------------------------------------------------------
            // FOLLOW REDIRECT
            // ------------------------------------------------------------------

            String newLocation = submitFormResponse.header("Location");
            Request followRedirectRequest = new Request.Builder().url(BASE_PATH_AUTH + "/" + newLocation).build();
            authorizeResponse = client.newCall(followRedirectRequest).execute();

            if (authorizeResponse.code() != 302) {
                throw new CommunicationException("Follow Redirect request failed with code " + authorizeResponse.code()
                        + ". Check credentials and appVersion");
            }
        }

        // ------------------------------------------------------------------
        // GET TOKEN
        // ------------------------------------------------------------------

        String code = getQueryStringParameterFromHeaderEntryUrl(authorizeResponse, "Location", "code");
        Instant now = Instant.now();

        Request getTokenRequest = new Request.Builder().url(BASE_PATH_AUTH + "/oauth/token")
                .addHeader("User-Agent", "okhttp/4.10.0").addHeader("Auth0-Client", AUTH_0_CLIENT)
                .post(new FormBody.Builder().add("scope", "openid").add("client_id", APP_CLIENT_ID)
                        .add("grant_type", "authorization_code").add("code", code).add("redirect_uri", REDIRECT_URI)
                        .add("code_verifier", codeVerifier).build())
                .build();
        Response getTokenResponse = client.newCall(getTokenRequest).execute();

        if (getTokenResponse.code() != 200) {
            throw new CommunicationException("Get token request failed with code " + authorizeResponse.code()
                    + ". Check credentials and appVersion");
        }

        String tokenResponse = getTokenResponse.body().string();
        JsonObject tokenResponseObject = gson.fromJson(tokenResponse, JsonObject.class);
        String accessToken = tokenResponseObject.get("access_token").getAsString();

        // ------------------------------------------------------------------
        // RETRIEVE ACC_CLIENT_ID
        // ------------------------------------------------------------------

        RequestBody body = RequestBody.create(MediaType.parse("application/json;charset=utf-8"),
                gson.toJson(new GetAccClientIdDTO()));
        Request getAccClientIdRequest = new Request.Builder().post(body).url(BASE_PATH_ACC + "/auth/v2/login")
                .addHeader("Accept-Encoding", "gzip, deflate").addHeader("Accept", "*/*")
                .addHeader("User-Agent", "G-RAC").addHeader("Content-Type", "application/json;charset=utf-8")
                .addHeader("x-app-name", "Comfort Cloud")
                .addHeader("x-app-timestamp",
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(now))
                .addHeader("x-app-type", "1").addHeader("x-app-version", appVersion)
                .addHeader("x-cfc-api-key", generateRandomStringHex(128))
                .addHeader("x-user-authorization-v2", "Bearer " + accessToken).build();

        Response getAccClientResponse = client.newCall(getAccClientIdRequest).execute();

        if (getAccClientResponse.code() != 200) {

            final JsonObject o = JsonParser.parseString(getAccClientResponse.body().string()).getAsJsonObject();
            int errorCode = o.has("code") ? o.get("code").getAsInt() : -1;
            String errorMessage = o.has("message") ? o.get("message").getAsString() : "<not provided>";

            if (errorCode == ERROR_CODE_UPDATE_VERSION) {
                throw new CommunicationException(String.format(
                        "New app version published - check the version number of your mobile app and enter the value as account config parameter (currently using %s)",
                        appVersion));
            } else {
                throw new CommunicationException("Get clientId request failed with code " + getAccClientResponse.code()
                        + " and message " + errorMessage + ". Check credentials and appVersion");
            }
        }

        String bodyString = getAccClientResponse.body().string();

        JsonObject jsonObject = gson.fromJson(bodyString, JsonObject.class);
        clientId = jsonObject.get("clientId").getAsString();

        String refreshToken = tokenResponseObject.get("refresh_token").getAsString();
        String expiresInSec = tokenResponseObject.get("expires_in").getAsString();
        String scope = tokenResponseObject.get("scope").getAsString();
        long unixTimestampTokenReceived = now.getEpochSecond();

        Token token = new Token(accessToken, refreshToken, clientId,
                unixTimestampTokenReceived + Long.parseLong(expiresInSec), scope);
        return token;
    }

    private Token refreshToken(Token currentToken) throws IOException, CommunicationException {
        Request getTokenRequest = new Request.Builder().url(BASE_PATH_AUTH + "/oauth/token")
                .addHeader("User-Agent", "okhttp/4.10.0").addHeader("Auth0-Client", AUTH_0_CLIENT)
                .post(new FormBody.Builder().add("scope", currentToken.getScope()).add("client_id", APP_CLIENT_ID)
                        .add("refresh_token", currentToken.getRefreshToken()).add("grant_type", "refresh_token")
                        .build())
                .build();
        Response getTokenResponse = client.newCall(getTokenRequest).execute();

        if (getTokenResponse.code() != 200) {
            throw new CommunicationException("Refresh token request failed with code " + getTokenResponse.code()
                    + ". Check credentials and appVersion");
        }

        String tokenResponse = getTokenResponse.body().string();
        JsonObject tokenResponseObject = gson.fromJson(tokenResponse, JsonObject.class);

        Token refreshedToken = new Token(tokenResponseObject.get("access_token").getAsString(),
                tokenResponseObject.get("refresh_token").getAsString(), currentToken.getClientId(),
                Instant.now().getEpochSecond() + Long.parseLong(tokenResponseObject.get("expires_in").getAsString()),
                tokenResponseObject.get("scope").getAsString());

        return refreshedToken;
    }

    private String getAppVersion() {
        try {

            HttpRequest request = HttpRequest.newBuilder().uri(new URI(APPBRAIN_URL)).headers("User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Mobile Safari/537.36")
                    .GET().build();

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String body = response.body();
            return parseAppBrainAppVersion(body);

        } catch (Exception e) {
            logger.warn("Exception getting appVersion", e);
        }
        return null;
    }

    public String parseAppBrainAppVersion(String body) {
        Document doc = Jsoup.parse(body);
        Elements elements = doc.selectXpath("//meta[@itemprop='softwareVersion']");

        if (elements.size() == 1) {
            return elements.get(0).attr("content");
        }

        return null;
    }

    public <T> T sendRequestInternal(final Request request, final AbstractRequest req, final Type responseType)
            throws PanasonicComfortCloudException {

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 200) {
                final JsonObject o = JsonParser.parseString(response.body().string()).getAsJsonObject();
                if (o.has("message")) {
                    throw new CommunicationException(req, o.get("message").getAsString());
                } else {
                    T responseJson = gson.fromJson(o, responseType);
                    if (responseJson != null) {
                        return responseJson;
                    } else {
                        throw new CommunicationException(
                                "Unable to unmarshal response from API: " + response.body().string());
                    }
                }

            } else if (response.code() != 200) {
                final JsonObject o = JsonParser.parseString(response.body().string()).getAsJsonObject();
                int errorCode = o.has("code") ? o.get("code").getAsInt() : -1;
                String errorMessage = o.has("message") ? o.get("message").getAsString() : "<not provided>";

                if (errorCode == ERROR_CODE_UPDATE_VERSION) {
                    throw new CommunicationException(String.format(
                            "New app version published - check the version number of your mobile app and enter the value as account config parameter (currently using %s)",
                            appVersion));
                } else {
                    throw new CommunicationException(
                            String.format("Request failed: code %d, message %s", errorCode, errorMessage));
                }
            } else {
                throw new CommunicationException("Error sending request to server. Server responded with "
                        + response.code() + " and payload " + response.body().string());
            }
        } catch (Exception e) {
            throw new CommunicationException("General error communicating with service: " + e);
        }
    }

    @Nullable
    private Token getStoredToken() {
        String accessToken = storage.get(ACCESS_TOKEN_KEY);
        String refreshToken = storage.get(REFRESH_TOKEN_KEY);
        String clientId = storage.get(CLIENT_ID_KEY);
        String tokenExpiryString = storage.get(TOKEN_EXPIRY_KEY);
        if (tokenExpiryString == null) {
            tokenExpiryString = Instant.now().minus(1, ChronoUnit.MINUTES).getEpochSecond() + ""; // Expired, but should
                                                                                                  // not happen
        }
        long tokenExpiry = Long.parseLong(tokenExpiryString);
        String scope = storage.get(SCOPE_KEY);

        if (accessToken == null || refreshToken == null || clientId == null || scope == null) {
            return null;
        }

        return new Token(accessToken, refreshToken, clientId, tokenExpiry, scope);
    }

    private void storeToken(Token token) {
        storage.put(ACCESS_TOKEN_KEY, token.getAccessToken());
        storage.put(REFRESH_TOKEN_KEY, token.getRefreshToken());
        storage.put(CLIENT_ID_KEY, token.getClientId());
        storage.put(TOKEN_EXPIRY_KEY, String.valueOf(token.getTokenExpiry()));
        storage.put(SCOPE_KEY, token.getScope());
    }

    private void clearToken() {
        storage.remove(ACCESS_TOKEN_KEY);
        storage.remove(REFRESH_TOKEN_KEY);
        storage.remove(CLIENT_ID_KEY);
        storage.remove(TOKEN_EXPIRY_KEY);
        storage.remove(SCOPE_KEY);
    }

    private static class Token {
        String accessToken;
        String refreshToken;
        String clientId;
        long tokenExpiry;
        String scope;

        public Token(String accessToken, String refreshToken, String clientId, long tokenExpiry, String scope) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.clientId = clientId;
            this.tokenExpiry = tokenExpiry;
            this.scope = scope;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public String getClientId() {
            return clientId;
        }

        public long getTokenExpiry() {
            return tokenExpiry;
        }

        public String getScope() {
            return scope;
        }

        public boolean shouldRefresh() {
            return (tokenExpiry - 60 * 60) < Instant.now().getEpochSecond(); // Refresh 1 hour before expiry
        }

        public boolean isExpired() {
            return tokenExpiry < Instant.now().getEpochSecond();
        }
    }
}
