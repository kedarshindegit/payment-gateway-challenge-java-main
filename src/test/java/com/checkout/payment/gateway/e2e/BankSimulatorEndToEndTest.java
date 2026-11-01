package com.checkout.payment.gateway.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

// Runs against the same Mountebank simulator and imposter file as docker-compose, so it checks
// the real request and response format rather than our own stubs of it.
@Tag("e2e")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BankSimulatorEndToEndTest {

  private static final int IMPOSTER_PORT = 8080;

  @Container
  static GenericContainer<?> bankSimulator = new GenericContainer<>("bbyars/mountebank:2.8.1")
      .withCopyFileToContainer(MountableFile.forHostPath("imposters/bank_simulator.ejs"),
          "/imposters/bank_simulator.ejs")
      .withCommand("--configfile", "/imposters/bank_simulator.ejs", "--allowInjection")
      .withExposedPorts(IMPOSTER_PORT);

  @DynamicPropertySource
  static void pointAtSimulator(DynamicPropertyRegistry registry) {
    registry.add("bank.base-url", () -> "http://" + bankSimulator.getHost() + ":"
        + bankSimulator.getMappedPort(IMPOSTER_PORT));
  }

  @Autowired
  private TestRestTemplate http;

  @Test
  void shouldAuthorizeCardEndingInAnOddDigitAndRetrieveIt() {
    ResponseEntity<JsonNode> created = pay("2222405343248877");

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().get("status").asText()).isEqualTo("Authorized");

    JsonNode fetched = http.getForObject(created.getHeaders().getLocation(), JsonNode.class);
    assertThat(fetched.get("status").asText()).isEqualTo("Authorized");
    assertThat(fetched.get("card_number_last_four").asText()).isEqualTo("8877");
  }

  @Test
  void shouldDeclineCardEndingInAnEvenDigit() {
    ResponseEntity<JsonNode> created = pay("2222405343248878");

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().get("status").asText()).isEqualTo("Declined");
  }

  @Test
  void shouldReturnBadGatewayWhenSimulatorIsUnavailableForCardEndingInZero() {
    ResponseEntity<JsonNode> response = pay("2222405343248870");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/bank-unavailable");
  }

  private ResponseEntity<JsonNode> pay(String cardNumber) {
    return http.postForEntity("/payments", Map.of(
        "card_number", cardNumber,
        "expiry_month", 4,
        "expiry_year", 2030,
        "currency", "GBP",
        "amount", 1050,
        "cvv", "123"), JsonNode.class);
  }
}
