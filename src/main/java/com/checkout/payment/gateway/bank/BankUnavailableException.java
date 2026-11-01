package com.checkout.payment.gateway.bank;

public class BankUnavailableException extends RuntimeException {

  public BankUnavailableException(String message) {
    super(message);
  }

  public BankUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
