package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.bank.AcquiringBank;
import com.checkout.payment.gateway.bank.BankAuthorization;
import com.checkout.payment.gateway.domain.Payment;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final AcquiringBank acquiringBank;

  public PaymentGatewayService(PaymentsRepository paymentsRepository, AcquiringBank acquiringBank) {
    this.paymentsRepository = paymentsRepository;
    this.acquiringBank = acquiringBank;
  }

  public Payment processPayment(PaymentRequest request) {
    BankAuthorization authorization = acquiringBank.authorize(request);
    PaymentStatus status = authorization.authorized()
        ? PaymentStatus.AUTHORIZED
        : PaymentStatus.DECLINED;

    // Declined payments are stored too, so the merchant can look up the outcome later.
    Payment payment = new Payment(
        UUID.randomUUID(),
        status,
        lastFour(request.cardNumber()),
        request.expiryMonth(),
        request.expiryYear(),
        request.currency(),
        request.amount(),
        authorization.authorized() ? authorization.authorizationCode() : null);
    paymentsRepository.save(payment);

    LOG.info("Payment {} processed with status {}", payment.id(), status);
    return payment;
  }

  public Payment getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    return paymentsRepository.findById(id)
        .orElseThrow(() -> new PaymentNotFoundException(id));
  }

  private static String lastFour(String cardNumber) {
    return cardNumber.substring(cardNumber.length() - 4);
  }
}
