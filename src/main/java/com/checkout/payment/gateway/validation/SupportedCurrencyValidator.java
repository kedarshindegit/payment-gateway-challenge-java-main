package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.configuration.PaymentProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class SupportedCurrencyValidator implements ConstraintValidator<SupportedCurrency, String> {

  private final List<String> supportedCurrencies;

  public SupportedCurrencyValidator(PaymentProperties properties) {
    this.supportedCurrencies = properties.supportedCurrencies();
  }

  @Override
  public boolean isValid(String currency, ConstraintValidatorContext context) {
    if (currency == null || supportedCurrencies.contains(currency)) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(
            "must be one of " + String.join(", ", supportedCurrencies))
        .addConstraintViolation();
    return false;
  }
}
