package com.checkout.payment.gateway.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.domain.Payment;
import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PaymentsRepositoryTest {

  private final PaymentsRepository repository = new PaymentsRepository();

  @Test
  void shouldReturnSavedPaymentById() {
    Payment payment = aPayment();

    repository.save(payment);

    assertThat(repository.findById(payment.id())).contains(payment);
  }

  @Test
  void shouldReturnEmptyWhenPaymentIsUnknown() {
    assertThat(repository.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void shouldKeepEveryPaymentWhenSavedConcurrently() throws InterruptedException {
    int threads = 16;
    int paymentsPerThread = 2_000;
    List<Payment> payments = IntStream.range(0, threads * paymentsPerThread)
        .mapToObj(i -> aPayment())
        .toList();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      for (int t = 0; t < threads; t++) {
        List<Payment> batch = payments.subList(t * paymentsPerThread, (t + 1) * paymentsPerThread);
        executor.submit(() -> {
          start.await();
          batch.forEach(repository::save);
          return null;
        });
      }
      start.countDown();
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(payments).allSatisfy(
        payment -> assertThat(repository.findById(payment.id())).contains(payment));
  }

  private static Payment aPayment() {
    return new Payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED, "8877", 4, 2027, "GBP", 1050,
        UUID.randomUUID().toString());
  }
}
