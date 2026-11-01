package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.domain.Payment;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Payments")
@RestController
@RequestMapping("/payments")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  // 201 for declines too: the payment was processed and stored, and the decline is its outcome.
  @Operation(summary = "Process a card payment",
      description = "Sends the payment to the acquiring bank and stores the outcome. An invalid "
          + "request is rejected before it reaches the bank.")
  @ApiResponse(responseCode = "201", description = "Processed: status is Authorized or Declined")
  @ApiResponse(responseCode = "400", description = "Rejected: the request failed validation",
      content = @Content(mediaType = "application/problem+json",
          schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(responseCode = "502", description = "The bank could not process the payment",
      content = @Content(mediaType = "application/problem+json",
          schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(responseCode = "504", description = "The bank timed out, so the outcome is unknown",
      content = @Content(mediaType = "application/problem+json",
          schema = @Schema(implementation = ProblemDetail.class)))
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

  @Operation(summary = "Retrieve a processed payment",
      description = "Returns only the last four digits of the card number.")
  @ApiResponse(responseCode = "200", description = "The payment")
  @ApiResponse(responseCode = "400", description = "The id is not a valid UUID",
      content = @Content(mediaType = "application/problem+json",
          schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(responseCode = "404", description = "No payment has this id",
      content = @Content(mediaType = "application/problem+json",
          schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/{id}")
  public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID id) {
    return ResponseEntity.ok(PaymentResponse.from(paymentGatewayService.getPaymentById(id)));
  }
}
