package com.checkout.payment.gateway.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final AtomicReference<String> idSeenByHandler = new AtomicReference<>();
  private final MockFilterChain chain =
      new MockFilterChain(new HttpServlet() {
        @Override
        protected void service(HttpServletRequest req, HttpServletResponse res) {
          idSeenByHandler.set(MDC.get(RequestIdFilter.MDC_KEY));
        }
      });

  @Test
  void shouldReuseTheCallersRequestId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER, "merchant-req-42");

    filter.doFilter(request, response, chain);

    assertThat(idSeenByHandler.get()).isEqualTo("merchant-req-42");
    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("merchant-req-42");
  }

  @Test
  void shouldGenerateARequestIdWhenNoneIsSent() throws Exception {
    filter.doFilter(new MockHttpServletRequest(), response, chain);

    String assignedId = response.getHeader(RequestIdFilter.HEADER);
    assertThat(UUID.fromString(assignedId)).isNotNull();
    assertThat(idSeenByHandler.get()).isEqualTo(assignedId);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "",
      "id with spaces",
      "id\nFAKE LOG LINE",
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
  void shouldReplaceUnsafeRequestIds(String unsafe) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER, unsafe);

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotEqualTo(unsafe);
    assertThat(UUID.fromString(response.getHeader(RequestIdFilter.HEADER))).isNotNull();
  }

  @Test
  void shouldNotLeakTheRequestIdIntoTheNextRequestOnTheSameThread() throws Exception {
    filter.doFilter(new MockHttpServletRequest(), response, chain);

    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }
}
