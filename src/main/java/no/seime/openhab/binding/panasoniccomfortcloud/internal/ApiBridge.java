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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

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

/**
 * The {@link ApiBridge} is responsible for API login and communication
 *
 * @author Arne Seime - Initial contribution
 */
public class ApiBridge {
    public static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json; charset=utf-8";
    private static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String OKHTTP_USER_AGENT = "okhttp/4.10.0";
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Mobile Safari/537.36";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final String APP_CLIENT_ID = "Xmy6xIYIitMxngjB2rHvlm6HSDNnaMJx";
    private static final String AUTH_0_CLIENT = "eyJuYW1lIjoiQXV0aDAuQW5kcm9pZCIsImVudiI6eyJhbmRyb2lkIjoiMzAifSwidmVyc2lvbiI6IjIuOS4zIn0=";
    private static final String REDIRECT_URI = "panasonic-iot-cfc://authglb.digital.panasonic.com/android/com.panasonic.ACCsmart/callback";
    private static final String BASE_PATH_AUTH = "https://authglb.digital.panasonic.com";
    private static final String BASE_PATH_ACC = "https://accsmart.panasonic.com";
    private static final String ITUNES_APP_VERSION_URL = "https://itunes.apple.com/lookup?id=1348640525";
    // Last version known to be accepted by the API (5.1.1 was an iOS-only release that the API rejected)
    private static final String DEFAULT_APP_VERSION = "5.1.0";

    private static final String APP_VERSION_KEY = "appVersion";
    private static final String ACCESS_TOKEN_KEY = "accessToken";
    private static final String REFRESH_TOKEN_KEY = "refreshToken";
    private static final String CLIENT_ID_KEY = "clientId";
    private static final String TOKEN_EXPIRY_KEY = "tokenExpiry";
    private static final String SCOPE_KEY = "scope";

    private static final int ERROR_CODE_UPDATE_VERSION = 4106;

    private final Logger logger = LoggerFactory.getLogger(ApiBridge.class);
    private MessageDigest digest = null;

    private String clientId;
    private String username;
    private String password;
    private volatile String appVersion;
    // Versions the API has rejected with 4106 during the lifetime of this bridge - never switch back to one of these
    private final Set<String> rejectedAppVersions = ConcurrentHashMap.newKeySet();
    private Gson gson;
    private HttpClient client;
    private Storage<String> storage;
    private DateTimeFormatter dateTimeFormatter;

    public ApiBridge(Storage<String> storage) {
        this.storage = storage;
        dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("GMT"));

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).version(HttpClient.Version.HTTP_1_1)
                .build();

        gson = new GsonBuilder().setLenient().setPrettyPrinting().create();
    }

    private static @NonNull Map<String, String> parseCookies(HttpResponse<?> redirectResponse) {
        Map<String, String> cookies = new HashMap<>();

        for (String header : redirectResponse.headers().allValues("Set-Cookie")) {
            try {
                for (HttpCookie cookie : HttpCookie.parse(header)) {
                    cookies.put(cookie.getName(), cookie.getValue());
                }
            } catch (IllegalArgumentException e) {
                // Ignore malformed cookie
            }
        }
        return cookies;
    }

    private static HttpRequest.BodyPublisher jsonBody(String json) {
        return HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
    }

    static HttpRequest.BodyPublisher formBody(Map<String, String> params) {
        String encoded = params.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        return HttpRequest.BodyPublishers.ofString(encoded, StandardCharsets.UTF_8);
    }

    static String buildUrl(String base, Map<String, String> queryParams) {
        return base + "?"
                + queryParams.entrySet().stream()
                        .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8).replace("+", "%20") + "="
                                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8).replace("+", "%20"))
                        .collect(Collectors.joining("&"));
    }

    /**
     * Extract a query string parameter from an URL or relative location. Works for custom schemes as well.
     */
    static @Nullable String getQueryParameter(@Nullable String location, String name) {
        if (location == null) {
            return null;
        }
        int queryStart = location.indexOf('?');
        if (queryStart < 0) {
            return null;
        }
        String query = location.substring(queryStart + 1);
        int fragmentStart = query.indexOf('#');
        if (fragmentStart >= 0) {
            query = query.substring(0, fragmentStart);
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            if (key.equals(name)) {
                return eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private HttpResponse<String> execute(HttpRequest request) throws IOException {
        logger.debug("--> {} {}", request.method(), request.uri());
        HttpResponse<byte[]> rawResponse;
        try {
            rawResponse = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for response", e);
        }
        String body = decodeBody(rawResponse);
        logger.debug("<-- {} {} {}", rawResponse.statusCode(), request.method(), request.uri());
        logger.trace("Response body: {}", body);
        return new StringHttpResponse(rawResponse, body);
    }

    private static String decodeBody(HttpResponse<byte[]> response) throws IOException {
        byte[] bytes = response.body();
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String encoding = response.headers().firstValue("Content-Encoding").orElse("").trim().toLowerCase(Locale.ROOT);
        InputStream in;
        switch (encoding) {
            case "gzip":
                in = new GZIPInputStream(new ByteArrayInputStream(bytes));
                break;
            case "deflate":
                in = new InflaterInputStream(new ByteArrayInputStream(bytes));
                break;
            default:
                return new String(bytes, StandardCharsets.UTF_8);
        }
        try (InputStream is = in) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private HttpRequest.Builder newRequest(String url) {
        // okhttp used to add its own User-Agent when none was set; keep sending that by default
        return HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).setHeader("User-Agent",
                OKHTTP_USER_AGENT);
    }

    public String generateAPIKey(Instant timestamp, String accessToken) {
        StringBuilder b = new StringBuilder();
        b.append("Comfort Cloud");
        b.append("521325fb2dd486bf4831b47644317fca");
        b.append(timestamp.getEpochSecond() * 1000);
        b.append("Bearer ");
        b.append(accessToken);

        byte[] hash = digest.digest(b.toString().getBytes(StandardCharsets.UTF_8));
        String hex = bytesToHex(hash);
        return hex.substring(0, 9) + "cfc" + hex.substring(9);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
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
            logger.debug("No configured appVersion in thing configuration, trying to fetch from iTunes lookup");
            appVersion = getAppVersion();
            if (appVersion == null) {
                // The last version the API accepted is a better guess than the compiled-in default
                String fallbackAppVersion = newestVersion(storage.get(APP_VERSION_KEY), DEFAULT_APP_VERSION);
                logger.info(
                        "Could not fetch appVersion dynamically, and no value is provided on the bridge thing. Defaulting to {}",
                        fallbackAppVersion);
                appVersion = fallbackAppVersion;
            } else if (rejectedAppVersions.contains(appVersion)) {
                String lastAcceptedAppVersion = storage.get(APP_VERSION_KEY);
                if (lastAcceptedAppVersion != null && !rejectedAppVersions.contains(lastAcceptedAppVersion)) {
                    logger.debug("App Store version {} was rejected by the API before, using last accepted version {}",
                            appVersion, lastAcceptedAppVersion);
                    appVersion = lastAcceptedAppVersion;
                }
            } else {
                logger.debug("Fetched appVersion from iTunes lookup: {}", appVersion);
            }
        } else {
            appVersion = configuredAppVersion;
        }
    }

    private HttpRequest buildRequest(Token token, final AbstractRequest req) {

        HttpRequest.Builder request = newRequest(BASE_PATH_ACC + req.getRequestUrl());

        if (req.getMethod().equals("POST")) {
            final String reqJson = gson.toJson(req);
            logger.trace("Request body: {}", reqJson);
            request.POST(jsonBody(reqJson));
        } else {
            request.GET();
        }

        Instant timestamp = Instant.now();

        String formattedDate = dateTimeFormatter.format(timestamp);
        request.header("Accept-Encoding", "gzip, deflate").header("Accept", "*/*").setHeader("User-Agent", "G-RAC")
                .header("Content-Type", "application/json;charset=utf-8").header("x-app-name", "Comfort Cloud")
                .header("x-app-timestamp", formattedDate).header("x-app-type", "1").header("x-app-version", appVersion)
                .header("x-cfc-api-key", generateAPIKey(timestamp, token.getAccessToken()))
                .header("x-user-authorization-v2", "Bearer " + token.getAccessToken())
                .header("x-client-id", token.getClientId());

        return request.build();
    }

    public <T> T sendRequest(final AbstractRequest req, final Type responseType) throws PanasonicComfortCloudException {
        try {
            return sendRequestOnce(req, responseType);
        } catch (AppVersionOutdatedException e) {
            // Panasonic raises the minimum app version from time to time - look up the current one and try again
            if (!updateAppVersionFromAppStore(e.getRejectedAppVersion())) {
                throw e;
            }
            return sendRequestOnce(req, responseType);
        }
    }

    private <T> T sendRequestOnce(final AbstractRequest req, final Type responseType)
            throws PanasonicComfortCloudException {

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

        T response = sendRequestInternal(buildRequest(token, req), req, responseType);
        rememberAppVersion();
        return response;
    }

    /**
     * Picks another app version after the API rejected one: the version currently published in the App Store if it
     * is newer, otherwise the last version the API accepted.
     *
     * @return true if a request should be retried with the (possibly concurrently) updated app version
     */
    synchronized boolean updateAppVersionFromAppStore(String rejectedAppVersion) {
        if (!rejectedAppVersion.equals(appVersion)) {
            // Another request already switched to a different version
            return true;
        }
        return switchAfterRejection(rejectedAppVersion, getAppVersion());
    }

    synchronized boolean switchAfterRejection(String rejectedAppVersion, @Nullable String publishedAppVersion) {
        rejectedAppVersions.add(rejectedAppVersion);
        if (publishedAppVersion != null && compareVersions(publishedAppVersion, rejectedAppVersion) > 0
                && !rejectedAppVersions.contains(publishedAppVersion)) {
            logger.info("API rejected appVersion {}, switching to {} from the App Store", rejectedAppVersion,
                    publishedAppVersion);
            appVersion = publishedAppVersion;
            return true;
        }
        // The App Store can be ahead of what the API accepts, e.g. an iOS release before the matching Android one
        String lastAcceptedAppVersion = storage.get(APP_VERSION_KEY);
        if (lastAcceptedAppVersion != null && !rejectedAppVersions.contains(lastAcceptedAppVersion)) {
            logger.info("API rejected appVersion {} (App Store has {}), falling back to last accepted version {}",
                    rejectedAppVersion, publishedAppVersion, lastAcceptedAppVersion);
            appVersion = lastAcceptedAppVersion;
            return true;
        }
        logger.warn("API rejected appVersion {}, and neither the App Store ({}) nor the last accepted version ({}) "
                + "offers an alternative", rejectedAppVersion, publishedAppVersion, lastAcceptedAppVersion);
        return false;
    }

    String currentAppVersion() {
        return appVersion;
    }

    /**
     * The version a rejected request was actually sent with. The shared appVersion field may already have been
     * switched by a concurrent request, which would make the rejected version look current and skip the retry.
     */
    String sentAppVersion(HttpRequest request) {
        return request.headers().firstValue("x-app-version").orElse(appVersion);
    }

    private void rememberAppVersion() {
        String currentAppVersion = appVersion;
        rejectedAppVersions.remove(currentAppVersion);
        if (!currentAppVersion.equals(storage.get(APP_VERSION_KEY))) {
            storage.put(APP_VERSION_KEY, currentAppVersion);
        }
    }

    static String newestVersion(@Nullable String a, String b) {
        if (a == null) {
            return b;
        }
        return compareVersions(a, b) >= 0 ? a : b;
    }

    static int compareVersions(String a, String b) {
        String[] partsA = a.trim().split("\\.");
        String[] partsB = b.trim().split("\\.");
        for (int i = 0; i < Math.max(partsA.length, partsB.length); i++) {
            int partA = i < partsA.length ? parseVersionPart(partsA[i]) : 0;
            int partB = i < partsB.length ? parseVersionPart(partsB[i]) : 0;
            if (partA != partB) {
                return Integer.compare(partA, partB);
            }
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private @NonNull Token getNewToken() throws CommunicationException {
        try {
            Token token = doV2AuthorizationFlow();
            storeToken(token);
            return token;
        } catch (AppVersionOutdatedException e) {
            clearToken();
            throw e;
        } catch (Exception e) {
            clearToken();
            throw new CommunicationException("Error obtaining access token - check credentials and appVersion", e);
        }
    }

    private @Nullable String getQueryStringParameterFromHeaderEntryUrl(HttpResponse<?> response, String headerEntry,
            String queryStringParameter) {
        return getQueryParameter(response.headers().firstValue(headerEntry).orElse(null), queryStringParameter);
    }

    private Token doV2AuthorizationFlow() throws IOException, NoSuchAlgorithmException, CommunicationException {

        // TODO future: Most of this code might be replaced by the Auth0 Java SDK

        // --------------------------------------------------------------------
        // AUTHORIZE
        // --------------------------------------------------------------------
        String state = generateRandomString(20);
        String codeVerifier = generateRandomString(43);
        String codeChallenge = generateHash(codeVerifier);

        Map<String, String> authorizeParams = new LinkedHashMap<>();
        authorizeParams.put("scope", "openid offline_access comfortcloud.control a2w.control");
        authorizeParams.put("audience", "https://digital.panasonic.com/" + APP_CLIENT_ID + "/api/v1/");
        authorizeParams.put("protocol", "oauth2");
        authorizeParams.put("response_type", "code");
        authorizeParams.put("code_challenge", codeChallenge);
        authorizeParams.put("code_challenge_method", "S256");
        authorizeParams.put("auth0Client", AUTH_0_CLIENT);
        authorizeParams.put("client_id", APP_CLIENT_ID);
        authorizeParams.put("redirect_uri", REDIRECT_URI);
        authorizeParams.put("state", state);

        HttpRequest authorizeRequest = newRequest(buildUrl(BASE_PATH_AUTH + "/authorize", authorizeParams)).GET()
                .setHeader("User-Agent", OKHTTP_USER_AGENT).build();

        HttpResponse<String> authorizeResponse = execute(authorizeRequest);

        if (authorizeResponse.statusCode() != 302) {
            throw new CommunicationException("Authorize request failed with code " + authorizeResponse.statusCode()
                    + ". Check credentials and appVersion");
        }

        // -------------------------------------------------------------------
        // FOLLOW REDIRECT
        // -------------------------------------------------------------------

        String location = authorizeResponse.headers().firstValue("Location").orElse(null);
        if (location == null) {
            throw new CommunicationException("Authorize response did not contain a Location header");
        }
        state = getQueryStringParameterFromHeaderEntryUrl(authorizeResponse, "Location", "state");

        if (!location.startsWith(REDIRECT_URI)) {
            HttpResponse<String> redirectResponse = execute(newRequest(resolveAuthUrl(location)).GET().build());
            Map<String, String> cookies = parseCookies(redirectResponse);
            String csrf = cookies.get("_csrf");

            if (csrf == null) {
                throw new CommunicationException("Expected cookie _csrf not found");
            }

            if (redirectResponse.statusCode() != 200) {
                throw new CommunicationException("Follow Redirect request failed with code "
                        + redirectResponse.statusCode() + ". Check credentials and appVersion");
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

            HttpRequest loginRequest = newRequest(BASE_PATH_AUTH + "/usernamepassword/login")
                    .setHeader("User-Agent", OKHTTP_USER_AGENT).header("Auth0-Client", AUTH_0_CLIENT)
                    .header("Content-Type", "application/json").POST(jsonBody(gson.toJson(loginJson))).build();
            HttpResponse<String> loginResponse = execute(loginRequest);

            if (loginResponse.statusCode() != 200) {
                throw new CommunicationException("Login request failed with code " + loginResponse.statusCode()
                        + ". Check credentials and appVersion");
            }

            // -------------------------------------------------------------------
            // CALLBACK
            // -------------------------------------------------------------------

            Document loginForm = Jsoup.parse(loginResponse.body());
            Elements elements = loginForm.selectXpath("//input[@type='hidden']");

            Map<String, String> formParams = new LinkedHashMap<>();
            for (int i = 0; i < elements.size(); i++) {
                formParams.put(elements.get(i).attr("name"), elements.get(i).attr("value"));
            }

            HttpRequest submitFormRequest = newRequest(BASE_PATH_AUTH + "/login/callback")
                    .setHeader("User-Agent", BROWSER_USER_AGENT).header("Content-Type", APPLICATION_FORM_URLENCODED)
                    .POST(formBody(formParams)).build();
            HttpResponse<String> submitFormResponse = execute(submitFormRequest);

            if (submitFormResponse.statusCode() != 302) {
                throw new CommunicationException("Callback request failed with code " + submitFormResponse.statusCode()
                        + ". Check credentials and appVersion");
            }

            // ------------------------------------------------------------------
            // FOLLOW REDIRECT
            // ------------------------------------------------------------------

            String newLocation = submitFormResponse.headers().firstValue("Location").orElse(null);
            if (newLocation == null) {
                throw new CommunicationException("Callback response did not contain a Location header");
            }
            authorizeResponse = execute(newRequest(resolveAuthUrl(newLocation)).GET().build());

            if (authorizeResponse.statusCode() != 302) {
                throw new CommunicationException("Follow Redirect request failed with code "
                        + authorizeResponse.statusCode() + ". Check credentials and appVersion");
            }
        }

        // ------------------------------------------------------------------
        // GET TOKEN
        // ------------------------------------------------------------------

        String code = getQueryStringParameterFromHeaderEntryUrl(authorizeResponse, "Location", "code");
        Instant now = Instant.now();

        if (code == null) {
            throw new CommunicationException(
                    "Authorization code not found in redirect. Check credentials and appVersion");
        }

        Map<String, String> tokenParams = new LinkedHashMap<>();
        tokenParams.put("scope", "openid");
        tokenParams.put("client_id", APP_CLIENT_ID);
        tokenParams.put("grant_type", "authorization_code");
        tokenParams.put("code", code);
        tokenParams.put("redirect_uri", REDIRECT_URI);
        tokenParams.put("code_verifier", codeVerifier);

        HttpRequest getTokenRequest = newRequest(BASE_PATH_AUTH + "/oauth/token")
                .setHeader("User-Agent", OKHTTP_USER_AGENT).header("Auth0-Client", AUTH_0_CLIENT)
                .header("Content-Type", APPLICATION_FORM_URLENCODED).POST(formBody(tokenParams)).build();
        HttpResponse<String> getTokenResponse = execute(getTokenRequest);

        if (getTokenResponse.statusCode() != 200) {
            throw new CommunicationException("Get token request failed with code " + getTokenResponse.statusCode()
                    + ". Check credentials and appVersion");
        }

        String tokenResponse = getTokenResponse.body();
        JsonObject tokenResponseObject = gson.fromJson(tokenResponse, JsonObject.class);
        String accessToken = tokenResponseObject.get("access_token").getAsString();

        // ------------------------------------------------------------------
        // RETRIEVE ACC_CLIENT_ID
        // ------------------------------------------------------------------

        String formattedDate = dateTimeFormatter.format(now);
        HttpRequest getAccClientIdRequest = newRequest(BASE_PATH_ACC + "/auth/v2/login")
                .POST(jsonBody(gson.toJson(new GetAccClientIdDTO()))).header("Accept-Encoding", "gzip, deflate")
                .header("Accept", "*/*").setHeader("User-Agent", "G-RAC")
                .header("Content-Type", "application/json;charset=utf-8").header("x-app-name", "Comfort Cloud")
                .header("x-app-timestamp", formattedDate).header("x-app-type", "1").header("x-app-version", appVersion)
                .header("x-cfc-api-key", generateAPIKey(now, accessToken))
                .header("x-user-authorization-v2", "Bearer " + accessToken).build();

        HttpResponse<String> getAccClientResponse = execute(getAccClientIdRequest);

        if (getAccClientResponse.statusCode() != 200) {

            final JsonObject o = JsonParser.parseString(getAccClientResponse.body()).getAsJsonObject();
            int errorCode = o.has("code") ? o.get("code").getAsInt() : -1;
            String errorMessage = o.has("message") ? o.get("message").getAsString() : "<not provided>";

            if (errorCode == ERROR_CODE_UPDATE_VERSION) {
                throw new AppVersionOutdatedException(sentAppVersion(getAccClientIdRequest));
            } else {
                throw new CommunicationException(
                        "Get clientId request failed with code " + getAccClientResponse.statusCode() + " and message "
                                + errorMessage + ". Check credentials and appVersion");
            }
        }

        String bodyString = getAccClientResponse.body();

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
        Map<String, String> refreshParams = new LinkedHashMap<>();
        refreshParams.put("scope", currentToken.getScope());
        refreshParams.put("client_id", APP_CLIENT_ID);
        refreshParams.put("refresh_token", currentToken.getRefreshToken());
        refreshParams.put("grant_type", "refresh_token");

        HttpRequest getTokenRequest = newRequest(BASE_PATH_AUTH + "/oauth/token")
                .setHeader("User-Agent", OKHTTP_USER_AGENT).header("Auth0-Client", AUTH_0_CLIENT)
                .header("Content-Type", APPLICATION_FORM_URLENCODED).POST(formBody(refreshParams)).build();
        HttpResponse<String> getTokenResponse = execute(getTokenRequest);

        if (getTokenResponse.statusCode() != 200) {
            throw new CommunicationException("Refresh token request failed with code " + getTokenResponse.statusCode()
                    + ". Check credentials and appVersion");
        }

        String tokenResponse = getTokenResponse.body();
        JsonObject tokenResponseObject = gson.fromJson(tokenResponse, JsonObject.class);

        Token refreshedToken = new Token(tokenResponseObject.get("access_token").getAsString(),
                tokenResponseObject.get("refresh_token").getAsString(), currentToken.getClientId(),
                Instant.now().getEpochSecond() + Long.parseLong(tokenResponseObject.get("expires_in").getAsString()),
                tokenResponseObject.get("scope").getAsString());

        return refreshedToken;
    }

    String getAppVersion() {
        HttpRequest request = newRequest(ITUNES_APP_VERSION_URL).GET().setHeader("User-Agent", BROWSER_USER_AGENT)
                .build();

        try {
            return parseItunesAppVersion(execute(request).body());
        } catch (Exception e) {
            logger.warn("Exception getting appVersion", e);
        }
        return null;
    }

    public String parseItunesAppVersion(String body) {
        JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
        if (jsonObject.has("results")) {
            var results = jsonObject.getAsJsonArray("results");
            if (results.size() > 0) {
                return results.get(0).getAsJsonObject().get("version").getAsString();
            }
        }

        return null;
    }

    public <T> T sendRequestInternal(final HttpRequest request, final AbstractRequest req, final Type responseType)
            throws PanasonicComfortCloudException {

        try {
            HttpResponse<String> response = execute(request);
            if (response.statusCode() == 200) {
                final JsonObject o = JsonParser.parseString(response.body()).getAsJsonObject();
                if (o.has("message")) {
                    throw new CommunicationException(req, o.get("message").getAsString());
                } else {
                    T responseJson = gson.fromJson(o, responseType);
                    if (responseJson != null) {
                        return responseJson;
                    } else {
                        throw new CommunicationException("Unable to unmarshal response from API: " + response.body());
                    }
                }

            } else {
                final JsonObject o = JsonParser.parseString(response.body()).getAsJsonObject();
                int errorCode = o.has("code") ? o.get("code").getAsInt() : -1;
                String errorMessage = o.has("message") ? o.get("message").getAsString() : "<not provided>";

                if (errorCode == ERROR_CODE_UPDATE_VERSION) {
                    throw new AppVersionOutdatedException(sentAppVersion(request));
                } else {
                    throw new CommunicationException(
                            String.format("Request failed: code %d, message %s", errorCode, errorMessage));
                }
            }
        } catch (AppVersionOutdatedException e) {
            throw e;
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

    /**
     * Resolve a Location header value against the auth base path. Kept compatible with the previous behaviour of
     * prefixing relative locations with the auth base path.
     */
    static String resolveAuthUrl(String location) {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        return BASE_PATH_AUTH + (location.startsWith("/") ? "" : "/") + location;
    }

    /**
     * Response wrapper exposing a decoded (decompressed) String body while delegating everything else.
     */
    private static class StringHttpResponse implements HttpResponse<String> {
        private final HttpResponse<byte[]> delegate;
        private final String body;

        StringHttpResponse(HttpResponse<byte[]> delegate, String body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return delegate.statusCode();
        }

        @Override
        public HttpRequest request() {
            return delegate.request();
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public java.net.http.HttpHeaders headers() {
            return delegate.headers();
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return delegate.sslSession();
        }

        @Override
        public URI uri() {
            return delegate.uri();
        }

        @Override
        public HttpClient.Version version() {
            return delegate.version();
        }
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
