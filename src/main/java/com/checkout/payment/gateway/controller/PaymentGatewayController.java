package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.domain.Payment;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/payments")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  // 201 for declines too: the payment was processed and stored, and the decline is its outcome.
  @PostMapping
  public ResponseEntity<PaymentResponse> processPayment(
      @Valid @RequestBody PaymentRequest request) {
    Payment payment = paymentGatewayService.processPayment(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(payment.id())
        .toUri();
    return ResponseEntity.created(location).body(PaymentResponse.from(payment));
  }

  @GetMapping("/{id}")
  public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID id) {
    return ResponseEntity.ok(PaymentResponse.from(paymentGatewayService.getPaymentById(id)));
  }
}
