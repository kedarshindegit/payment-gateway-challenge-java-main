package com.checkout.payment.gateway.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

  @Bean
  public OpenAPI paymentGatewayApi() {
    return new OpenAPI().info(new Info()
        .title("Payment Gateway API")
        .version("v1")
        .description("Lets a merchant process a card payment through the acquiring bank and "
            + "retrieve it later."));
  }

  @Bean
  public ModelResolver modelResolver(ObjectMapper objectMapper) {
    return new ModelResolver(objectMapper);
  }
}
