package com.checkout.payment.gateway.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value must be one of the ISO 4217 codes configured in {@code payments.supported-currencies}.
 * Matching is case-sensitive: merchant input is rejected rather than silently normalised.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SupportedCurrencyValidator.class)
public @interface SupportedCurrency {

  String message() default "is not a supported currency";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
