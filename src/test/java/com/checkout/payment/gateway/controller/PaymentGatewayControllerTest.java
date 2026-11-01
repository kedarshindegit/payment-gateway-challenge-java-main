package com.checkout.payment.gateway.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.domain.Payment;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentGatewayController.class)
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private PaymentGatewayService paymentGatewayService;

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
