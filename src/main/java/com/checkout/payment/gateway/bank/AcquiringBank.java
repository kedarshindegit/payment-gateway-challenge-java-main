package com.checkout.payment.gateway.bank;

import com.checkout.payment.gateway.model.PaymentRequest;

/**
 * Authorises a validated payment with an acquiring bank. Implementations never retry: an
 * authorisation is not idempotent at the bank, so a blind retry could charge the card twice.
 */
public interface AcquiringBank {

  /**
   * @throws BankUnavailableException if the bank could not be reached or gave no usable answer,
   *     so the payment was not authorised
   * @throws BankTimeoutException if the bank did not answer in time, so the outcome is unknown
   */
  BankAuthorization authorize(PaymentRequest request);
}
