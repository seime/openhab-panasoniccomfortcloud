package no.seime.openhab.binding.panasoniccomfortcloud.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.core.storage.Storage;
import org.openhab.core.test.storage.VolatileStorage;

import com.google.gson.reflect.TypeToken;

import no.seime.openhab.binding.panasoniccomfortcloud.internal.dto.GetGroupsRequest;
import no.seime.openhab.binding.panasoniccomfortcloud.internal.dto.GetGroupsResponse;

@ExtendWith(MockitoExtension.class)
public class APIClientTest {

    private Storage storage = new VolatileStorage();

    // Disabled - must have credentials to run
    // @Test
    public void test() throws PanasonicComfortCloudException {
        ApiBridge apiBridge = new ApiBridge(storage);

        String username = "EMAIL USERNAME";
        String password = "PASSWORD";

        apiBridge.init(username, password, null);
        apiBridge.sendRequest(new GetGroupsRequest(), new TypeToken<GetGroupsResponse>() {
        }.getType());
    }

    @Test
    public void testSha256() throws NoSuchAlgorithmException {
        String hash = ApiBridge.generateHash("XZlJsY7dnp32w4KCC39xehdfsENsR265TjzHGQoePVP");
        assertEquals("TeFR13C1atlTUPWD1G9NdFCwvNC0Z0yOb7oVI8yjzvk", hash);
    }

    @Test
    public void testGenerateRandom() {
        String random = ApiBridge.generateRandomString(20);
        assertEquals(20, random.length());
    }

    @Test
    public void testParseItunes() throws IOException {
        InputStream is = getClass().getResourceAsStream("/itunes_lookup.json");
        String json = new String(is.readAllBytes());

        ApiBridge apiBridge = new ApiBridge(storage);
        String appVersion = apiBridge.parseItunesAppVersion(json);
        assertEquals("2.1.0", appVersion);
    }

    @Test
    public void testGetQueryParameter() {
        String location = "panasonic-iot-cfc://authglb.digital.panasonic.com/android/com.panasonic.ACCsmart/callback?code=abc%2B123&state=xyz#frag";
        assertEquals("abc+123", ApiBridge.getQueryParameter(location, "code"));
        assertEquals("xyz", ApiBridge.getQueryParameter(location, "state"));
        assertNull(ApiBridge.getQueryParameter(location, "missing"));
        assertEquals("s1", ApiBridge.getQueryParameter("/u/login?state=s1", "state"));
        assertNull(ApiBridge.getQueryParameter("/u/login", "state"));
        assertNull(ApiBridge.getQueryParameter(null, "state"));
    }

    @Test
    public void testResolveAuthUrl() {
        assertEquals("https://authglb.digital.panasonic.com/u/login?state=1",
                ApiBridge.resolveAuthUrl("/u/login?state=1"));
        assertEquals("https://authglb.digital.panasonic.com/u/login", ApiBridge.resolveAuthUrl("u/login"));
        assertEquals("https://other.example/x", ApiBridge.resolveAuthUrl("https://other.example/x"));
    }

    @Test
    public void testBuildUrl() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("scope", "openid offline_access");
        params.put("audience", "https://x/api/v1/");
        params.put("auth0Client", "ab==");
        assertEquals(
                "https://h/authorize?scope=openid%20offline_access&audience=https%3A%2F%2Fx%2Fapi%2Fv1%2F&auth0Client=ab%3D%3D",
                ApiBridge.buildUrl("https://h/authorize", params));
    }

    @Test
    public void testGetAppVersionIntegration() {
        ApiBridge apiBridge = new ApiBridge(storage);
        String appVersion = apiBridge.getAppVersion();
        assertNotNull(appVersion);
        // Version should look like x.y.z
        assertTrue(appVersion.matches("\\d+\\.\\d+\\.\\d+"));
    }
}
