package com.checkout.payment.gateway.configuration;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// The brief caps the allow-list at three currencies, so a fourth fails at startup rather than
// being discovered by a merchant.
@Validated
@ConfigurationProperties(prefix = "payments")
public record PaymentProperties(@NotEmpty @Size(max = 3) List<String> supportedCurrencies) {
}
