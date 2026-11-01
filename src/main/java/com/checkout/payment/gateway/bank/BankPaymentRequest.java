package com.checkout.payment.gateway.bank;

import com.checkout.payment.gateway.model.PaymentRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

// Field names are pinned to the bank's contract so they cannot drift with our own API's
// JSON naming settings.
record BankPaymentRequest(
    @JsonProperty("card_number") String cardNumber,
    @JsonProperty("expiry_date") String expiryDate,
    @JsonProperty("currency") String currency,
    @JsonProperty("amount") long amount,
    @JsonProperty("cvv") String cvv) {

  static BankPaymentRequest from(PaymentRequest request) {
    return new BankPaymentRequest(
        request.cardNumber(),
        String.format("%02d/%d", request.expiryMonth(), request.expiryYear()),
        request.currency(),
        request.amount(),
        request.cvv());
  }

  @Override
  public String toString() {
    return "BankPaymentRequest[cardNumber=****"
        + cardNumber.substring(cardNumber.length() - 4)
        + ", expiryDate=" + expiryDate
        + ", currency=" + currency
        + ", amount=" + amount
        + ", cvv=***]";
  }
}
