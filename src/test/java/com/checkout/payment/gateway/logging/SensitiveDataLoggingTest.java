package com.checkout.payment.gateway.logging;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Debug logging is switched on for our code and Spring's web layer, so this also catches card
// data that would only leak through a toString() at a verbose log level.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "logging.level.com.checkout=DEBUG",
    "logging.level.org.springframework.web=DEBUG"})
@ExtendWith(OutputCaptureExtension.class)
class SensitiveDataLoggingTest {

  private static final String CARD_NUMBER = "2222405343248877";
  private static final String CVV = "987";

  @RegisterExtension
  static WireMockExtension bank = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  @DynamicPropertySource
  static void pointAtStubBank(DynamicPropertyRegistry registry) {
    registry.add("bank.base-url", bank::baseUrl);
  }

  @LocalServerPort
  private int port;

  // A plain JDK client sends the request, because a Spring client would log the card number
  // itself at debug level and make the test fail for the wrong reason.
  @Test
  void shouldTagLogsWithTheRequestIdAndNeverLogCardData(CapturedOutput output) throws Exception {
    bank.stubFor(post("/payments").willReturn(okJson(
        "{\"authorized\": true, \"authorization_code\": \"auth-1\"}")));
    String body = """
        {"card_number": "%s", "expiry_month": 4, "expiry_year": 2030, "currency": "GBP",
         "amount": 1050, "cvv": "%s"}
        """.formatted(CARD_NUMBER, CVV);
    URI payments = URI.create("http://localhost:" + port + "/payments");
    HttpRequest request = HttpRequest.newBuilder(payments)
        .header("Content-Type", "application/json")
        .header(RequestIdFilter.HEADER, "log-test-1")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> response = HttpClient.newHttpClient()
        .send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(output).contains("[log-test-1]").contains("processed with status AUTHORIZED");
    assertThat(output).doesNotContain(CARD_NUMBER).doesNotContain("cvv=" + CVV);
  }
}
