// Live smoke test against the real API; costs nothing. Run: mvn test -Psmoke
// It reads the public catalog and calls getBalance with a fake key (expects AuthenticationException).
// Set SURSOLVER_API_KEY to also read your real balance; SURSOLVER_BASE_URL overrides the origin.
package com.sursolver;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("smoke")
class SmokeTest {
    @Test
    void liveCatalogAndFakeKey() throws Exception {
        String baseUrl = System.getenv().getOrDefault("SURSOLVER_BASE_URL", SurSolverClient.DEFAULT_BASE_URL);
        SurSolverClient client = SurSolverClient.builder(ConformanceTest.TEST_API_KEY).baseUrl(baseUrl).build();

        TaskTypeCatalog catalog = client.listTaskTypes();
        System.out.println("listTaskTypes: " + catalog.taskTypes().size() + " types, " + catalog.errorCodes().size() + " error codes");
        List<ServiceInfo> services = client.listServices();
        System.out.println("listServices: " + services.size() + " active services");
        AuthenticationException error = assertThrows(AuthenticationException.class, client::getBalance);
        System.out.println("getBalance (fake key): " + error.getMessage());

        String apiKey = System.getenv("SURSOLVER_API_KEY");
        if (apiKey != null && !apiKey.isEmpty()) {
            double balance = SurSolverClient.builder(apiKey).baseUrl(baseUrl).build().getBalance();
            System.out.println("getBalance (your key): " + balance + " USD");
        }
    }
}
