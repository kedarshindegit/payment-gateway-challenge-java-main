package com.checkout.payment.gateway.domain;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;

public record Payment(
    UUID id,
    PaymentStatus status,
    String cardNumberLastFour,
    int expiryMonth,
    int expiryYear,
    String currency,
    long amount,
    String authorizationCode) {
}
