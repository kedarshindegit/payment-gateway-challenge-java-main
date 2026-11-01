package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.domain.Payment;
import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    PaymentStatus status,
    String cardNumberLastFour,
    int expiryMonth,
    int expiryYear,
    String currency,
    long amount) {

  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.id(),
        payment.status(),
        payment.cardNumberLastFour(),
        payment.expiryMonth(),
        payment.expiryYear(),
        payment.currency(),
        payment.amount());
  }
}
