package com.checkout.payment.gateway.bank;

import com.checkout.payment.gateway.configuration.BankProperties;
import com.checkout.payment.gateway.model.PaymentRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankSimulatorClient implements AcquiringBank {

  private final RestTemplate restTemplate;
  private final MeterRegistry meterRegistry;

  public BankSimulatorClient(RestTemplateBuilder restTemplateBuilder, BankProperties properties,
      MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    // The JDK client is chosen explicitly because the timeout handling below depends on the
    // exception types it throws. It is pinned to HTTP/1.1 because by default it tries to upgrade
    // plain-HTTP connections to HTTP/2, which servers handle inconsistently.
    this.restTemplate = restTemplateBuilder
        .requestFactoryBuilder(ClientHttpRequestFactoryBuilder.jdk()
            .withHttpClientCustomizer(client -> client.version(HttpClient.Version.HTTP_1_1)))
        .rootUri(properties.baseUrl().toString())
        .connectTimeout(properties.connectTimeout())
        .readTimeout(properties.readTimeout())
        .build();
  }

  // One timer tagged by outcome gives both the bank's latency and how often it declines or fails.
  @Override
  public BankAuthorization authorize(PaymentRequest request) {
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "unavailable";
    try {
      BankAuthorization authorization = callBank(request);
      outcome = authorization.authorized() ? "authorized" : "declined";
      return authorization;
    } catch (BankTimeoutException ex) {
      outcome = "timeout";
      throw ex;
    } finally {
      sample.stop(meterRegistry.timer("bank.authorizations", "outcome", outcome));
    }
  }

  private BankAuthorization callBank(PaymentRequest request) {
    BankPaymentResponse response = send(BankPaymentRequest.from(request));
    if (response == null || response.authorized() == null) {
      throw new BankUnavailableException("Acquiring bank returned no authorisation result");
    }
    return new BankAuthorization(response.authorized(), response.authorizationCode());
  }

  private BankPaymentResponse send(BankPaymentRequest request) {
    try {
      return restTemplate.postForObject("/payments", request, BankPaymentResponse.class);
    } catch (ResourceAccessException ex) {
      throw classifyIoFailure(ex);
    } catch (RestClientResponseException ex) {
      throw new BankUnavailableException(
          "Acquiring bank responded with HTTP " + ex.getStatusCode().value(), ex);
    } catch (RestClientException ex) {
      throw new BankUnavailableException("Acquiring bank returned an unreadable response", ex);
    }
  }

  // A connect timeout means the request never reached the bank, so the payment was definitely
  // not authorised. A read timeout means it did reach the bank and the outcome is unknown.
  private static RuntimeException classifyIoFailure(ResourceAccessException ex) {
    Throwable cause = ex.getCause();
    if (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException)) {
      return new BankTimeoutException("Acquiring bank did not respond in time", ex);
    }
    return new BankUnavailableException("Could not reach the acquiring bank", ex);
  }
}
