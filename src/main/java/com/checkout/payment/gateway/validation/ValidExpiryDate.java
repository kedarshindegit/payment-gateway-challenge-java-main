package com.checkout.payment.gateway.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The expiry month and year together must not be in the past. A card stays valid until the
 * last day of its expiry month, so the current month is accepted.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExpiryDateValidator.class)
public @interface ValidExpiryDate {

  String message() default "card has expired";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
