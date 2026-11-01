package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.validation.SupportedCurrency;
import com.checkout.payment.gateway.validation.ValidExpiryDate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@ValidExpiryDate
public record PaymentRequest(
    @NotNull
    @Pattern(regexp = "\\d{14,19}", message = "must be 14 to 19 digits")
    @Schema(example = "2222405343248877")
    String cardNumber,

    @NotNull
    @Min(1)
    @Max(12)
    @Schema(example = "4")
    Integer expiryMonth,

    @NotNull
    @Max(9999)
    @Schema(example = "2030", description = "Month and year together must not be in the past")
    Integer expiryYear,

    @NotNull
    @SupportedCurrency
    @Schema(example = "GBP", description = "One of the configured ISO 4217 codes")
    String currency,

    @NotNull
    @Positive
    @Schema(example = "1050", description = "In the currency's minor unit, so 1050 is 10.50")
    Long amount,

    @NotNull
    @Pattern(regexp = "\\d{3,4}", message = "must be 3 or 4 digits")
    @Schema(example = "123")
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
