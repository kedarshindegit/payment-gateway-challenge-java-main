package com.checkout.payment.gateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PaymentPropertiesTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(PropertiesConfiguration.class);

  @Test
  void shouldBindSupportedCurrencies() {
    contextRunner
        .withPropertyValues("payments.supported-currencies=GBP,USD,EUR")
        .run(context -> assertThat(context.getBean(PaymentProperties.class).supportedCurrencies())
            .containsExactly("GBP", "USD", "EUR"));
  }

  @Test
  void shouldFailToStartWithMoreThanThreeCurrencies() {
    contextRunner
        .withPropertyValues("payments.supported-currencies=GBP,USD,EUR,JPY")
        .run(context -> assertThat(context).getFailure()
            .rootCause()
            .hasMessageContaining("supportedCurrencies")
            .hasMessageContaining("size must be between 0 and 3"));
  }

  @Configuration
  @EnableConfigurationProperties(PaymentProperties.class)
  static class PropertiesConfiguration {
  }
}
