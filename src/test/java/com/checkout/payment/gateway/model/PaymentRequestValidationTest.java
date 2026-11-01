package com.checkout.payment.gateway.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.configuration.PaymentProperties;
import com.checkout.payment.gateway.validation.ExpiryDateValidator;
import com.checkout.payment.gateway.validation.SupportedCurrencyValidator;
import jakarta.validation.Configuration;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentRequestValidationTest {

  private static final Clock MID_NOVEMBER_2026 =
      Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC);
  private static final PaymentProperties PROPERTIES =
      new PaymentProperties(List.of("GBP", "USD", "EUR"));

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    Configuration<?> configuration = Validation.byDefaultProvider().configure();
    ConstraintValidatorFactory defaults = configuration.getDefaultConstraintValidatorFactory();
    validatorFactory = configuration
        .constraintValidatorFactory(new ConstraintValidatorFactory() {
          @Override
          @SuppressWarnings("unchecked")
          public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            if (key == ExpiryDateValidator.class) {
              return (T) new ExpiryDateValidator(MID_NOVEMBER_2026);
            }
            if (key == SupportedCurrencyValidator.class) {
              return (T) new SupportedCurrencyValidator(PROPERTIES);
            }
            return defaults.getInstance(key);
          }

          @Override
          public void releaseInstance(ConstraintValidator<?, ?> instance) {
            defaults.releaseInstance(instance);
          }
        })
        .buildValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  void shouldAcceptValidRequest() {
    assertThat(invalidFields(aRequest())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"12345678901234", "1234567890123456789"})
  void shouldAcceptCardNumberOf14To19Digits(String cardNumber) {
    assertThat(invalidFields(withCardNumber(cardNumber))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234567890123", "12345678901234567890", "2222abcd43248877",
      "2222 4053 4324 8877", ""})
  void shouldRejectCardNumberThatIsNot14To19Digits(String cardNumber) {
    assertThat(invalidFields(withCardNumber(cardNumber))).containsExactly("cardNumber");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 12})
  void shouldAcceptExpiryMonthFrom1To12(int month) {
    assertThat(invalidFields(withExpiryMonth(month))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 13})
  void shouldRejectExpiryMonthOutside1To12(int month) {
    assertThat(invalidFields(withExpiryMonth(month))).containsExactly("expiryMonth");
  }

  @ParameterizedTest
  @ValueSource(strings = {"123", "1234", "012"})
  void shouldAcceptCvvOf3Or4Digits(String cvv) {
    assertThat(invalidFields(withCvv(cvv))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"12", "12345", "12a"})
  void shouldRejectCvvThatIsNot3Or4Digits(String cvv) {
    assertThat(invalidFields(withCvv(cvv))).containsExactly("cvv");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void shouldRejectAmountThatIsNotPositive(long amount) {
    assertThat(invalidFields(withAmount(amount))).containsExactly("amount");
  }

  @Test
  void shouldAcceptSmallestPositiveAmount() {
    assertThat(invalidFields(withAmount(1))).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"11, 2026", "12, 2026", "1, 2027"})
  void shouldAcceptCardExpiringThisMonthOrLater(int month, int year) {
    assertThat(invalidFields(withExpiry(month, year))).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"10, 2026", "12, 2025"})
  void shouldRejectCardThatExpiredBeforeThisMonth(int month, int year) {
    assertThat(invalidFields(withExpiry(month, year))).containsExactly("expiryYear");
  }

  @Test
  void shouldAcceptExpiryYear9999() {
    assertThat(invalidFields(withExpiry(12, 9999))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {10000, 1_000_000_000, Integer.MAX_VALUE})
  void shouldRejectExpiryYearAbove9999(int year) {
    assertThat(invalidFields(withExpiry(12, year))).containsExactly("expiryYear");
  }

  @ParameterizedTest
  @ValueSource(strings = {"GBP", "USD", "EUR"})
  void shouldAcceptSupportedCurrency(String currency) {
    assertThat(invalidFields(withCurrency(currency))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"JPY", "gbp", "GBPX", ""})
  void shouldRejectUnsupportedCurrency(String currency) {
    assertThat(invalidFields(withCurrency(currency))).containsExactly("currency");
  }

  @Test
  void shouldReportEveryMissingField() {
    PaymentRequest empty = new PaymentRequest(null, null, null, null, null, null);

    assertThat(invalidFields(empty)).containsExactlyInAnyOrder(
        "cardNumber", "expiryMonth", "expiryYear", "currency", "amount", "cvv");
  }

  @Test
  void shouldNotExposeCardNumberOrCvvInToString() {
    PaymentRequest request = aRequest();

    assertThat(request.toString())
        .doesNotContain(request.cardNumber())
        .doesNotContain(request.cvv())
        .contains("****8877");
  }

  private static Set<String> invalidFields(PaymentRequest request) {
    return validator.validate(request).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }

  private static PaymentRequest aRequest() {
    return new PaymentRequest("2222405343248877", 4, 2030, "GBP", 1050L, "123");
  }

  private static PaymentRequest withCardNumber(String cardNumber) {
    return new PaymentRequest(cardNumber, 4, 2030, "GBP", 1050L, "123");
  }

  private static PaymentRequest withExpiryMonth(int expiryMonth) {
    return new PaymentRequest("2222405343248877", expiryMonth, 2030, "GBP", 1050L, "123");
  }

  private static PaymentRequest withCvv(String cvv) {
    return new PaymentRequest("2222405343248877", 4, 2030, "GBP", 1050L, cvv);
  }

  private static PaymentRequest withExpiry(int expiryMonth, int expiryYear) {
    return new PaymentRequest("2222405343248877", expiryMonth, expiryYear, "GBP", 1050L, "123");
  }

  private static PaymentRequest withCurrency(String currency) {
    return new PaymentRequest("2222405343248877", 4, 2030, currency, 1050L, "123");
  }

  private static PaymentRequest withAmount(long amount) {
    return new PaymentRequest("2222405343248877", 4, 2030, "GBP", amount, "123");
  }
}
