package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.PaymentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Clock;
import java.time.YearMonth;

public class ExpiryDateValidator implements ConstraintValidator<ValidExpiryDate, PaymentRequest> {

  private final Clock clock;

  public ExpiryDateValidator(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean isValid(PaymentRequest request, ConstraintValidatorContext context) {
    Integer month = request.expiryMonth();
    Integer year = request.expiryYear();
    // Missing or out-of-range values are already reported by the field constraints.
    if (month == null || year == null || month < 1 || month > 12) {
      return true;
    }
    if (!YearMonth.of(year, month).isBefore(YearMonth.now(clock))) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
        .addPropertyNode("expiryYear")
        .addConstraintViolation();
    return false;
  }
}
