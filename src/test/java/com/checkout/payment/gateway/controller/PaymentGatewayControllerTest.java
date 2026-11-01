package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.bank.BankTimeoutException;
import com.checkout.payment.gateway.bank.BankUnavailableException;
import com.checkout.payment.gateway.configuration.PaymentProperties;
import com.checkout.payment.gateway.domain.Payment;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentGatewayController.class)
@Import(PaymentGatewayControllerTest.ValidationBeans.class)
class PaymentGatewayControllerTest {

  private static final String VALID_REQUEST = """
      {
        "card_number": "2222405343248877",
        "expiry_month": 4,
        "expiry_year": 2027,
        "currency": "GBP",
        "amount": 1050,
        "cvv": "123"
      }
      """;

  // The request validators need these, and a web slice does not load the application's config.
  @TestConfiguration
  static class ValidationBeans {

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    PaymentProperties paymentProperties() {
      return new PaymentProperties(List.of("GBP", "USD", "EUR"));
    }
  }

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private PaymentGatewayService paymentGatewayService;

  @Test
  void shouldReturnCreatedWithLocationWhenPaymentIsAuthorized() throws Exception {
    Payment payment = new Payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED, "8877", 4, 2027,
        "GBP", 1050, "auth-123");
    given(paymentGatewayService.processPayment(new PaymentRequest(
        "2222405343248877", 4, 2027, "GBP", 1050L, "123"))).willReturn(payment);

    mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "http://localhost/payments/" + payment.id()))
        .andExpect(jsonPath("$.id").value(payment.id().toString()))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.card_number_last_four").value("8877"))
        .andExpect(jsonPath("$.expiry_month").value(4))
        .andExpect(jsonPath("$.expiry_year").value(2027))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(1050))
        .andExpect(jsonPath("$.card_number").doesNotExist())
        .andExpect(jsonPath("$.cvv").doesNotExist());
  }

  @Test
  void shouldReturnCreatedWhenPaymentIsDeclined() throws Exception {
    Payment payment = new Payment(UUID.randomUUID(), PaymentStatus.DECLINED, "8878", 4, 2027,
        "GBP", 1050, null);
    given(paymentGatewayService.processPayment(any())).willReturn(payment);

    mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"));
  }

  @Test
  void shouldRejectInvalidRequestWithEveryFieldErrorAndNotProcessIt() throws Exception {
    String invalid = VALID_REQUEST
        .replace("2222405343248877", "1234")
        .replace("\"GBP\"", "\"JPY\"")
        .replace("\"123\"", "\"12\"");

    mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(invalid))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("/problems/payment-rejected"))
        .andExpect(jsonPath("$.title").value("Payment rejected"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.payment_status").value("Rejected"))
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(jsonPath("$.errors[0].field").value("card_number"))
        .andExpect(jsonPath("$.errors[0].message").value("must be 14 to 19 digits"))
        .andExpect(jsonPath("$.errors[1].field").value("currency"))
        .andExpect(jsonPath("$.errors[1].message").value("must be one of GBP, USD, EUR"))
        .andExpect(jsonPath("$.errors[2].field").value("cvv"))
        .andExpect(jsonPath("$.errors[2].message").value("must be 3 or 4 digits"));

    verifyNoInteractions(paymentGatewayService);
  }

  @Test
  void shouldRejectExpiredCardAgainstTheExpiryField() throws Exception {
    String expired = VALID_REQUEST.replace("\"expiry_year\": 2027", "\"expiry_year\": 2025");

    mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(expired))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("expiry_year"))
        .andExpect(jsonPath("$.errors[0].message").value("card has expired"));

    verifyNoInteractions(paymentGatewayService);
  }

  @Test
  void shouldRejectFractionalAmountInsteadOfTruncatingIt() throws Exception {
    String fractional = VALID_REQUEST.replace("1050", "10.50");

    mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(fractional))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.payment_status").value("Rejected"))
        .andExpect(jsonPath("$.errors[0].field").value("amount"))
        .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"));

    verifyNoInteractions(paymentGatewayService);
  }

  @Test
  void shouldRejectMalformedJson() throws Exception {
    mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.payment_status").value("Rejected"))
        .andExpect(jsonPath("$.detail").value("The request body could not be read"))
        .andExpect(jsonPath("$.errors").isEmpty());

    verifyNoInteractions(paymentGatewayService);
  }

  @Test
  void shouldReturnBadGatewayWhenBankIsUnavailable() throws Exception {
    given(paymentGatewayService.processPayment(any()))
        .willThrow(new BankUnavailableException("Acquiring bank responded with HTTP 503"));

    mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isBadGateway())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("/problems/bank-unavailable"))
        .andExpect(jsonPath("$.detail")
            .value("The acquiring bank could not process the payment. It was not authorised."));
  }

  @Test
  void shouldReturnGatewayTimeoutWhenBankDoesNotAnswerInTime() throws Exception {
    given(paymentGatewayService.processPayment(any()))
        .willThrow(new BankTimeoutException("Acquiring bank did not respond in time", null));

    mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isGatewayTimeout())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("/problems/bank-timeout"))
        .andExpect(jsonPath("$.detail").value(
            "The acquiring bank did not respond in time, so the payment outcome is unknown."));
  }

  @Test
  void shouldReturnPaymentInSnakeCaseWhenItExists() throws Exception {
    Payment payment = new Payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED, "0321", 12, 2027,
        "USD", 1050, UUID.randomUUID().toString());
    given(paymentGatewayService.getPaymentById(payment.id())).willReturn(payment);

    mvc.perform(get("/payments/{id}", payment.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(payment.id().toString()))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.card_number_last_four").value("0321"))
        .andExpect(jsonPath("$.expiry_month").value(12))
        .andExpect(jsonPath("$.expiry_year").value(2027))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(1050))
        .andExpect(jsonPath("$.authorization_code").doesNotExist());
  }

  @Test
  void shouldReturnProblemDetailWhenPaymentDoesNotExist() throws Exception {
    UUID id = UUID.randomUUID();
    given(paymentGatewayService.getPaymentById(id)).willThrow(new PaymentNotFoundException(id));

    mvc.perform(get("/payments/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("/problems/payment-not-found"))
        .andExpect(jsonPath("$.title").value("Payment not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Payment " + id + " was not found"))
        .andExpect(jsonPath("$.instance").value("/payments/" + id));
  }

  @Test
  void shouldReturnBadRequestWhenPaymentIdIsNotAUuid() throws Exception {
    mvc.perform(get("/payments/{id}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("/problems/invalid-parameter"))
        .andExpect(jsonPath("$.detail").value("'id' must be a valid UUID"));
  }

  @Test
  void shouldReturnProblemDetailWithoutInternalsWhenSomethingUnexpectedFails() throws Exception {
    UUID id = UUID.randomUUID();
    given(paymentGatewayService.getPaymentById(id))
        .willThrow(new IllegalStateException("connection pool exhausted"));

    mvc.perform(get("/payments/{id}", id))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("/problems/internal-error"))
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
  }

  @Test
  void shouldReturnProblemDetailForUnknownRoutes() throws Exception {
    mvc.perform(get("/unknown"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
