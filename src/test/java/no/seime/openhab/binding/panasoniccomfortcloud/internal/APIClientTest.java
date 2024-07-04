package no.seime.openhab.binding.panasoniccomfortcloud.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.NoSuchAlgorithmException;

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

        String username = "FILL IN USERNAME TO TEST";
        String password = "FILL IN PASSWORD TO TEST";

        apiBridge.init(username, password, "1.20.0");
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
    public void testGenerateRandomHexString() {
        int length = "B7d80fb2bc3faE769b89Bf4EC9C4729eCfe690C1CbBa7c42a40A062dc2f4f3671daAaFF1Cf6777cdC23dfcfFfa6DabdDec825c8b0BfB2EFDe04FCE17Bb5e086e"
                .length();
        String random = ApiBridge.generateRandomStringHex(length);
        assertEquals(length, random.length());
    }
}
