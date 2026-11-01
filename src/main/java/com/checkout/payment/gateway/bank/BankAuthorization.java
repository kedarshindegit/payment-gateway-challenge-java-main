package com.checkout.payment.gateway.bank;

public record BankAuthorization(boolean authorized, String authorizationCode) {
}
