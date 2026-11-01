package com.checkout.payment.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Spring Boot switches metrics export off in tests unless asked, and the scrape test needs it.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "bank.read-timeout=500ms")
@AutoConfigureObservability(tracing = false)
class PaymentFlowIntegrationTest {

  @RegisterExtension
  static WireMockExtension bank = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  @DynamicPropertySource
  static void pointAtStubBank(DynamicPropertyRegistry registry) {
    registry.add("bank.base-url", bank::baseUrl);
  }

  @Autowired
  private TestRestTemplate http;

  @Test
  void shouldAuthorizeStoreAndRetrievePaymentWithoutTheFullCardNumber() {
    bank.stubFor(post("/payments").willReturn(okJson(
        "{\"authorized\": true, \"authorization_code\": \"auth-1\"}")));

    ResponseEntity<JsonNode> created = http.postForEntity("/payments",
        payment("2222405343248877"), JsonNode.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().get("status").asText()).isEqualTo("Authorized");
    bank.verify(postRequestedFor(urlEqualTo("/payments")).withRequestBody(equalToJson("""
        {"card_number": "2222405343248877", "expiry_date": "04/2030", "currency": "GBP",
         "amount": 1050, "cvv": "123"}
        """)));

    ResponseEntity<String> fetched = http.getForEntity(created.getHeaders().getLocation(),
        String.class);

    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(fetched.getBody())
        .contains("\"id\":\"" + created.getBody().get("id").asText() + "\"")
        .contains("\"status\":\"Authorized\"")
        .contains("\"card_number_last_four\":\"8877\"")
        .doesNotContain("2222405343248877")
        .doesNotContain("cvv")
        .doesNotContain("authorization_code");
  }

  @Test
  void shouldStoreDeclinedPaymentSoItCanBeRetrieved() {
    bank.stubFor(post("/payments").willReturn(okJson(
        "{\"authorized\": false, \"authorization_code\": \"\"}")));

    ResponseEntity<JsonNode> created = http.postForEntity("/payments",
        payment("2222405343248878"), JsonNode.class);
    ResponseEntity<JsonNode> fetched = http.getForEntity(created.getHeaders().getLocation(),
        JsonNode.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(fetched.getBody().get("status").asText()).isEqualTo("Declined");
  }

  @Test
  void shouldRejectInvalidPaymentWithoutCallingTheBank() {
    Map<String, Object> invalid = payment("2222405343248877");
    invalid.put("cvv", "1");

    ResponseEntity<JsonNode> response = http.postForEntity("/payments", invalid, JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("payment_status").asText()).isEqualTo("Rejected");
    assertThat(response.getBody().at("/errors/0/field").asText()).isEqualTo("cvv");
    bank.verify(0, postRequestedFor(urlEqualTo("/payments")));
  }

  @Test
  void shouldReturnBadGatewayWhenBankIsUnavailable() {
    bank.stubFor(post("/payments").willReturn(aResponse().withStatus(503)));

    ResponseEntity<JsonNode> response = http.postForEntity("/payments",
        payment("2222405343248870"), JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/bank-unavailable");
  }

  @Test
  void shouldReturnGatewayTimeoutWhenBankIsTooSlow() {
    bank.stubFor(post("/payments").willReturn(okJson(
        "{\"authorized\": true, \"authorization_code\": \"late\"}").withFixedDelay(2_000)));

    ResponseEntity<JsonNode> response = http.postForEntity("/payments",
        payment("2222405343248877"), JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/bank-timeout");
  }

  @Test
  void shouldExposePaymentMetricsForPrometheus() {
    bank.stubFor(post("/payments").willReturn(okJson(
        "{\"authorized\": false, \"authorization_code\": \"\"}")));
    http.postForEntity("/payments", payment("2222405343248878"), JsonNode.class);

    ResponseEntity<String> scrape = http.getForEntity("/actuator/prometheus", String.class);

    assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(scrape.getBody())
        .contains("bank_authorizations_seconds_count{outcome=\"declined\"")
        .contains("http_server_requests_seconds_bucket");
  }

  @Test
  void shouldReportLiveAndReady() {
    assertThat(http.getForEntity("/actuator/health/liveness", String.class).getBody())
        .contains("\"status\":\"UP\"");
    assertThat(http.getForEntity("/actuator/health/readiness", String.class).getBody())
        .contains("\"status\":\"UP\"");
  }

  @Test
  void shouldReturnNotFoundForUnknownPayment() {
    ResponseEntity<JsonNode> response = http.getForEntity("/payments/" + UUID.randomUUID(),
        JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private static Map<String, Object> payment(String cardNumber) {
    return new HashMap<>(Map.of(
        "card_number", cardNumber,
        "expiry_month", 4,
        "expiry_year", 2030,
        "currency", "GBP",
        "amount", 1050,
        "cvv", "123"));
  }
}
