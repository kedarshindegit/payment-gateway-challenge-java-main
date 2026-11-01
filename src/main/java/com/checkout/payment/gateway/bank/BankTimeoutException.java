package com.checkout.payment.gateway.bank;

public class BankTimeoutException extends RuntimeException {

  public BankTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
