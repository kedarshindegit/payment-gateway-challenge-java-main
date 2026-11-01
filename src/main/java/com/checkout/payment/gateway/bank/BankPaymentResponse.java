package com.checkout.payment.gateway.bank;

import com.fasterxml.jackson.annotation.JsonProperty;

record BankPaymentResponse(
    @JsonProperty("authorized") Boolean authorized,
    @JsonProperty("authorization_code") String authorizationCode) {
}
