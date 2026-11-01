package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.checkout.payment.gateway.bank.AcquiringBank;
import com.checkout.payment.gateway.bank.BankAuthorization;
import com.checkout.payment.gateway.bank.BankUnavailableException;
import com.checkout.payment.gateway.domain.Payment;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentGatewayServiceTest {

  private static final PaymentRequest REQUEST =
      new PaymentRequest("2222405343248877", 4, 2027, "GBP", 1050L, "123");

  private final PaymentsRepository repository = new PaymentsRepository();
  private final AcquiringBank bank = mock(AcquiringBank.class);
  private final PaymentGatewayService service = new PaymentGatewayService(repository, bank);

  @Test
  void shouldStoreAuthorizedPaymentWithOnlyTheLastFourCardDigits() {
    given(bank.authorize(REQUEST)).willReturn(new BankAuthorization(true, "auth-123"));

    Payment payment = service.processPayment(REQUEST);

    assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(payment.cardNumberLastFour()).isEqualTo("8877");
    assertThat(payment.authorizationCode()).isEqualTo("auth-123");
    assertThat(payment)
        .extracting(Payment::expiryMonth, Payment::expiryYear, Payment::currency, Payment::amount)
        .containsExactly(4, 2027, "GBP", 1050L);
    assertThat(repository.findById(payment.id())).contains(payment);
  }

  @Test
  void shouldStoreDeclinedPaymentWithoutAnAuthorizationCode() {
    given(bank.authorize(REQUEST)).willReturn(new BankAuthorization(false, ""));

    Payment payment = service.processPayment(REQUEST);

    assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(payment.authorizationCode()).isNull();
    assertThat(repository.findById(payment.id())).contains(payment);
  }

  @Test
  void shouldGiveEachPaymentItsOwnId() {
    given(bank.authorize(REQUEST)).willReturn(new BankAuthorization(true, "auth-123"));

    assertThat(service.processPayment(REQUEST).id())
        .isNotEqualTo(service.processPayment(REQUEST).id());
  }

  @Test
  void shouldLetBankFailuresReachTheCaller() {
    BankUnavailableException failure = new BankUnavailableException("bank down");
    given(bank.authorize(REQUEST)).willThrow(failure);

    assertThatThrownBy(() -> service.processPayment(REQUEST)).isSameAs(failure);
  }

  @Test
  void shouldThrowNotFoundForUnknownPayment() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> service.getPaymentById(id))
        .isInstanceOf(PaymentNotFoundException.class)
        .hasMessage("Payment " + id + " was not found");
  }
}
