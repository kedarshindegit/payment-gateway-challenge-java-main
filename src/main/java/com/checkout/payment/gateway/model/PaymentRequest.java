package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.validation.SupportedCurrency;
import com.checkout.payment.gateway.validation.ValidExpiryDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@ValidExpiryDate
public record PaymentRequest(
    @NotNull
    @Pattern(regexp = "\\d{14,19}", message = "must be 14 to 19 digits")
    String cardNumber,

    @NotNull
    @Min(1)
    @Max(12)
    Integer expiryMonth,

    @NotNull
    Integer expiryYear,

    @NotNull
    @SupportedCurrency
    String currency,

    @NotNull
    @Positive
    Long amount,

    @NotNull
    @Pattern(regexp = "\\d{3,4}", message = "must be 3 or 4 digits")
    String cvv) {

  // The default record toString would print the full card number and CVV into any log line
  // that includes this request.
  @Override
  public String toString() {
    return "PaymentRequest[cardNumber=" + maskedCardNumber()
        + ", expiryMonth=" + expiryMonth
        + ", expiryYear=" + expiryYear
        + ", currency=" + currency
        + ", amount=" + amount
        + ", cvv=***]";
  }

  private String maskedCardNumber() {
    if (cardNumber == null || cardNumber.length() < 4) {
      return "****";
    }
    return "****" + cardNumber.substring(cardNumber.length() - 4);
  }
}
