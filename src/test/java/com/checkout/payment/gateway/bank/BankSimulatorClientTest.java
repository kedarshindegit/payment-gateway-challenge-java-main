package com.checkout.payment.gateway.bank;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkout.payment.gateway.configuration.BankProperties;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

class BankSimulatorClientTest {

  private static final Duration READ_TIMEOUT = Duration.ofMillis(500);

  @RegisterExtension
  static WireMockExtension bank = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private BankSimulatorClient client;

  @BeforeEach
  void createClient() {
    BankProperties properties = new BankProperties(
        URI.create(bank.baseUrl()), Duration.ofSeconds(1), READ_TIMEOUT);
    client = new BankSimulatorClient(new RestTemplateBuilder(), properties, meterRegistry);
  }

  @Test
  void shouldSendPaymentInTheBankFormat() {
    bank.stubFor(post("/payments").willReturn(okJson(authorized())));

    client.authorize(new PaymentRequest("2222405343248877", 4, 2027, "GBP", 1050L, "012"));

    bank.verify(postRequestedFor(urlEqualTo("/payments")).withRequestBody(equalToJson("""
        {
          "card_number": "2222405343248877",
          "expiry_date": "04/2027",
          "currency": "GBP",
          "amount": 1050,
          "cvv": "012"
        }
        """)));
  }

  @Test
  void shouldReturnAuthorizationWhenBankAuthorizes() {
    bank.stubFor(post("/payments").willReturn(okJson(authorized())));

    assertThat(client.authorize(aRequest()))
        .isEqualTo(new BankAuthorization(true, "0bb07405-6d44-4b50-a14f-7ae0beff13ad"));
    assertThat(callsRecordedWithOutcome("authorized")).isEqualTo(1);
  }

  @Test
  void shouldReturnDeclineWhenBankDoesNotAuthorize() {
    bank.stubFor(post("/payments")
        .willReturn(okJson("{\"authorized\": false, \"authorization_code\": \"\"}")));

    assertThat(client.authorize(aRequest()).authorized()).isFalse();
    assertThat(callsRecordedWithOutcome("declined")).isEqualTo(1);
  }

  @Test
  void shouldReportBankUnavailableWhenBankReturnsServiceUnavailable() {
    bank.stubFor(post("/payments").willReturn(aResponse().withStatus(503)));

    assertThatThrownBy(() -> client.authorize(aRequest()))
        .isInstanceOf(BankUnavailableException.class)
        .hasMessageContaining("503");
    assertThat(callsRecordedWithOutcome("unavailable")).isEqualTo(1);
  }

  @Test
  void shouldReportBankUnavailableWhenBankRejectsOurRequest() {
    bank.stubFor(post("/payments").willReturn(aResponse().withStatus(400)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"error_message\": \"Not all required properties were sent\"}")));

    assertThatThrownBy(() -> client.authorize(aRequest()))
        .isInstanceOf(BankUnavailableException.class)
        .hasMessageContaining("400");
  }

  @Test
  void shouldReportTimeoutWhenBankAnswersAfterTheReadTimeout() {
    bank.stubFor(post("/payments").willReturn(okJson(authorized())
        .withFixedDelay((int) READ_TIMEOUT.multipliedBy(4).toMillis())));

    assertThatThrownBy(() -> client.authorize(aRequest()))
        .isInstanceOf(BankTimeoutException.class);
    assertThat(callsRecordedWithOutcome("timeout")).isEqualTo(1);
  }

  @Test
  void shouldReportBankUnavailableWhenConnectionIsReset() {
    bank.stubFor(post("/payments")
        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThatThrownBy(() -> client.authorize(aRequest()))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void shouldReportBankUnavailableWhenBankIsNotListening() {
    BankProperties nowhere = new BankProperties(
        URI.create("http://localhost:1"), Duration.ofSeconds(1), READ_TIMEOUT);
    BankSimulatorClient unreachable =
        new BankSimulatorClient(new RestTemplateBuilder(), nowhere, meterRegistry);

    assertThatThrownBy(() -> unreachable.authorize(aRequest()))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void shouldReportBankUnavailableWhenBankReturnsNoResult() {
    bank.stubFor(post("/payments").willReturn(okJson("{}")));

    assertThatThrownBy(() -> client.authorize(aRequest()))
        .isInstanceOf(BankUnavailableException.class);
  }

  private long callsRecordedWithOutcome(String outcome) {
    return meterRegistry.get("bank.authorizations").tag("outcome", outcome).timer().count();
  }

  private static PaymentRequest aRequest() {
    return new PaymentRequest("2222405343248877", 4, 2027, "GBP", 1050L, "123");
  }

  private static String authorized() {
    return """
        {"authorized": true, "authorization_code": "0bb07405-6d44-4b50-a14f-7ae0beff13ad"}
        """;
  }
}
