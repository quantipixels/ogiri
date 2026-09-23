// SPDX-License-Identifier: Apache-2.0
package example.ogiri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** The login CSRF exemption follows Spring MVC when its servlet has a path prefix. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${OGIRI_TEST_JDBC_URL}",
        "spring.datasource.username=${OGIRI_TEST_JDBC_USER}",
        "spring.datasource.password=${OGIRI_TEST_JDBC_PASSWORD}",
        "demo.password=test-password",
        "spring.mvc.servlet.path=/api"
})
class ServletPathConsumerTest {
    @Value("${local.server.port}") private int port;

    @Test void prefixedSignInIsReachableButSimplePostsRemainCsrfProtected() throws Exception {
        var uri = URI.create("http://localhost:" + port + "/api/auth/sign-in");
        var payload = HttpRequest.BodyPublishers.ofString(
                "{\"username\":\"demo\",\"password\":\"test-password\",\"client\":\"browser\"}");
        var allowed = HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .header("X-Requested-With", "Ogiri").POST(payload).build();
        var simple = HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        var client = HttpClient.newHttpClient();
        assertEquals(403, client.send(simple, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(201, client.send(allowed, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
