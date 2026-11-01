package com.checkout.payment.gateway.exception;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import com.checkout.payment.gateway.bank.BankTimeoutException;
import com.checkout.payment.gateway.bank.BankUnavailableException;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Extends Spring's handler so framework errors (unknown route, wrong method, bad JSON) also
// come back as Problem Details instead of falling through to the catch-all 500 below.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final PropertyNamingStrategies.SnakeCaseStrategy SNAKE_CASE =
      new PropertyNamingStrategies.SnakeCaseStrategy();

  public record InvalidField(String field, String message) {
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  public ProblemDetail handlePaymentNotFound(PaymentNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "payment-not-found", "Payment not found",
        ex.getMessage());
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ProblemDetail handleBankUnavailable(BankUnavailableException ex) {
    LOG.warn("Payment not processed: {}", ex.getMessage());
    return problem(HttpStatus.BAD_GATEWAY, "bank-unavailable", "Acquiring bank unavailable",
        "The acquiring bank could not process the payment. It was not authorised.");
  }

  // Unlike a 502, the bank may have authorised the payment before the timeout, so the detail
  // tells the merchant not to assume either outcome.
  @ExceptionHandler(BankTimeoutException.class)
  public ProblemDetail handleBankTimeout(BankTimeoutException ex) {
    LOG.warn("Payment outcome unknown: {}", ex.getMessage());
    return problem(HttpStatus.GATEWAY_TIMEOUT, "bank-timeout", "Acquiring bank timed out",
        "The acquiring bank did not respond in time, so the payment outcome is unknown.");
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    LOG.error("Unexpected error while handling request", ex);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
        "An unexpected error occurred");
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String requiredType = ex.getRequiredType() == null
        ? "a different type"
        : "a valid " + ex.getRequiredType().getSimpleName();
    ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "invalid-parameter",
        "Invalid request parameter",
        "'" + ex.getPropertyName() + "' must be " + requiredType);
    return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    // Field names are reported as the merchant sent them (card_number), not as Java names.
    List<InvalidField> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> new InvalidField(SNAKE_CASE.translate(error.getField()),
            error.getDefaultMessage()))
        .sorted(comparing(InvalidField::field).thenComparing(InvalidField::message))
        .toList();
    return rejected(ex, "The payment request failed validation", errors, headers, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    // A value of the wrong type (such as 10.5 for an amount in minor units) is reported against
    // its field. Malformed JSON has no field to point at.
    List<InvalidField> errors = ex.getCause() instanceof JsonMappingException mapping
        && !mapping.getPath().isEmpty()
        ? List.of(new InvalidField(jsonPath(mapping), "has an invalid value"))
        : List.of();
    return rejected(ex, "The request body could not be read", errors, headers, request);
  }

  // RFC 9457 reserves "status" for the HTTP status code, so the payment outcome goes in
  // payment_status.
  private ResponseEntity<Object> rejected(Exception ex, String detail, List<InvalidField> errors,
      HttpHeaders headers, WebRequest request) {
    ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "payment-rejected", "Payment rejected",
        detail);
    body.setProperty("payment_status", PaymentStatus.REJECTED.getName());
    body.setProperty("errors", errors);
    return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
  }

  private static String jsonPath(JsonMappingException ex) {
    return ex.getPath().stream()
        .map(JsonMappingException.Reference::getFieldName)
        .filter(Objects::nonNull)
        .collect(joining("."));
  }

  private static ProblemDetail problem(HttpStatus status, String type, String title,
      String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("/problems/" + type));
    problem.setTitle(title);
    return problem;
  }
}
