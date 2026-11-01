# Payment Gateway

A payment gateway API that lets a merchant process a card payment through an acquiring bank and retrieve the result later. It is built on the Java skeleton for the Checkout.com payment gateway challenge, using Java 17, Spring Boot 3.5 and Gradle, with the provided Mountebank simulator standing in for the bank.

## Running it

You need JDK 17 and Docker.

Start the gateway and the bank simulator together:

```bash
docker compose up --build
```

The gateway listens on `http://localhost:8090` and the simulator on `http://localhost:8080`.

To run the gateway from the IDE or Gradle instead, start only the simulator and then the application:

```bash
docker compose up -d bank_simulator
./gradlew bootRun
```

Swagger UI is at http://localhost:8090/swagger-ui/index.html and the OpenAPI document at http://localhost:8090/v3/api-docs.

## Using the API

Process a payment:

```bash
curl -i http://localhost:8090/payments \
  -H 'Content-Type: application/json' \
  -d '{
        "card_number": "2222405343248877",
        "expiry_month": 4,
        "expiry_year": 2030,
        "currency": "GBP",
        "amount": 1050,
        "cvv": "123"
      }'
```

```http
HTTP/1.1 201
Location: http://localhost:8090/payments/9b200423-5110-4744-9f15-ef222c069d76
X-Request-Id: 1f0c0d1e-...

{"id":"9b200423-5110-4744-9f15-ef222c069d76","status":"Authorized","card_number_last_four":"8877",
 "expiry_month":4,"expiry_year":2030,"currency":"GBP","amount":1050}
```

Retrieve it with `GET /payments/{id}`, which returns the same body.

The simulator decides the outcome from the last digit of the card number: odd is authorised, even is declined, and 0 makes it return 503.

| Request | Outcome | Response |
|---|---|---|
| `POST /payments` | Authorised or declined by the bank | `201` with the payment, `status` is `Authorized` or `Declined` |
| `POST /payments` | Invalid request (validation, malformed JSON, fractional amount) | `400` problem, `payment_status: Rejected`, field errors |
| `POST /payments` | Bank returned an error, or could not be reached | `502` problem `bank-unavailable` |
| `POST /payments` | Bank did not answer within the read timeout | `504` problem `bank-timeout` |
| `GET /payments/{id}` | Found | `200` with the payment |
| `GET /payments/{id}` | Not a UUID | `400` problem `invalid-parameter` |
| `GET /payments/{id}` | Unknown id | `404` problem `payment-not-found` |

Every error is an RFC 9457 Problem Details body (`application/problem+json`). A rejected payment looks like this:

```json
{
  "type": "/problems/payment-rejected",
  "title": "Payment rejected",
  "status": 400,
  "detail": "The payment request failed validation",
  "instance": "/payments",
  "payment_status": "Rejected",
  "errors": [
    {"field": "card_number", "message": "must be 14 to 19 digits"},
    {"field": "cvv", "message": "must be 3 or 4 digits"}
  ]
}
```

## Validation rules

| Field | Rule |
|---|---|
| `card_number` | Required, 14 to 19 digits |
| `expiry_month` | Required, 1 to 12 |
| `expiry_year` | Required. Month and year together must not be in the past |
| `currency` | Required, one of the configured codes (`GBP`, `USD`, `EUR`) |
| `amount` | Required, a positive whole number in the currency's minor unit |
| `cvv` | Required, 3 or 4 digits |

All errors are reported at once, so a merchant can fix a request in one go.

## Design decisions and assumptions

**Status codes.** A declined payment still returns `201`: the gateway processed and stored it, and the decline is its outcome, not an error. A rejected payment returns `400` because nothing was created and the bank was never called. RFC 9457 reserves `status` for the HTTP status code, so the `Rejected` outcome goes in `payment_status`.

**Declined payments are stored** so the merchant can look up any processed payment. Rejected payments are not stored, as they never became payments.

**No retries to the bank.** An authorisation is not idempotent at the bank, so retrying after a timeout could charge the card twice. I would only add retries together with an idempotency key the bank understands.

**502 and 504 mean different things to the merchant.** A 502 (bank error, connection refused or reset, unusable response) means the payment was not authorised. A 504 means the request reached the bank but no answer came back in time, so the outcome is unknown and the merchant should not assume either way. Neither case is stored. A connect timeout counts as a 502, because the request never reached the bank.

**Timeouts are explicit**: 2s to connect and 5s to read, both configurable under `bank.*`. Without them a slow bank would hold the merchant's request and a server thread open indefinitely.

**Card data never leaves the gateway in a response or a log.** Responses carry only the last four digits. `PaymentRequest` and the bank request override `toString()` to mask the card number and CVV, and a test runs a payment with debug logging switched on and asserts neither appears in the output. The CVV and full card number are not stored at all.

**Snake case JSON** throughout, matching the bank's API. I configured springdoc to use the application's `ObjectMapper`, because by default it documented camelCase field names that the API does not accept.

**Fractional amounts are rejected** (`spring.jackson.deserialization.accept-float-as-int=false`). By default Jackson would truncate `10.5` to `10`, silently charging the wrong amount.

**Supported currencies are configuration** (`payments.supported-currencies`), validated at startup to hold at most three codes. Matching is case-sensitive, because ISO 4217 codes are upper case.

**Expiry** is compared by month in UTC: a card is valid until the end of its expiry month. I did not add a Luhn check, as the bank is the authority on whether a card number is real and the brief does not ask for it.

**Storage** is an in-memory `ConcurrentHashMap`, as the brief allows. Payments are lost on restart and are not shared between instances.

**The bank client** is a `RestTemplate` on the JDK HTTP client. I pinned it to HTTP/1.1, because by default it tries to upgrade plain HTTP connections to HTTP/2, which servers handle inconsistently. Compose gives the simulator a `bank-simulator` alias, because the JDK client rejects host names containing `_`, such as the `bank_simulator` service name.

**Spring Boot upgrade.** I moved the skeleton from Spring Boot 3.1.5, which is out of support, to 3.5.16 (and Gradle 8.14.3) for security fixes and built-in structured logging. Java stays at 17.

## Observability

**Request ids.** Every response has an `X-Request-Id` header. If the caller sends one that is short and plain (`[A-Za-z0-9-]{1,64}`) I reuse it, otherwise I generate a UUID, so a caller cannot forge log lines through the header. The id is on every log line written while handling the request.

**Logs.** Locally the logs are plain text with the request id after the level. In Docker (the `docker` profile) they are one JSON object per line in Logstash format, ready for a log platform to index.

**Metrics** are exposed for Prometheus at `/actuator/prometheus`:

- `bank_authorizations_seconds`, a timer tagged with `outcome` (`authorized`, `declined`, `unavailable`, `timeout`). It gives the bank's latency and its decline and failure rates from one metric.
- `http_server_requests_seconds` from Spring, tagged with status code, which also counts rejections as 400s.

Both publish histogram buckets so p95 and p99 latency can be calculated across instances.

**Health.** `/actuator/health/liveness` and `/actuator/health/readiness` are ready for Kubernetes probes. Readiness deliberately does not depend on the bank: if the bank is down, taking every gateway instance out of service would not help, and merchants would get connection errors instead of a clear 502.

**Shutdown** is graceful, with a 10 second grace period so a payment waiting on the bank (up to the 5 second read timeout) can finish.

## Testing

```bash
./gradlew build      # unit, web and integration tests, plus the coverage check
./gradlew e2eTest    # end-to-end tests against the real simulator (needs Docker)
```

| Level | What it covers |
|---|---|
| Unit | Validation rules and their boundaries, the service, the repository under concurrent writes, the request id filter |
| Web (`@WebMvcTest`) | Status codes, headers, JSON shape and Problem Details for each outcome, with the service mocked |
| Bank client (WireMock) | The exact request sent to the bank, and how each bank failure is classified: 503, 400, timeout, connection reset, refused connection, empty body |
| Integration (`@SpringBootTest` and WireMock) | The full flow through the running application, metrics, health, the OpenAPI document and the no-card-data log check |
| End-to-end (Testcontainers) | The gateway against the same Mountebank image and imposter file that compose uses |

Time-dependent rules use an injected `Clock`, so the expiry tests are fixed to a date and will not start failing as time passes.

The coverage report is at `build/reports/jacoco/test/html/index.html`. The build fails if line coverage drops below 90%; this is a floor to catch untested code, not a target.

CI (`.github/workflows/ci.yml`) runs the build, the end-to-end tests and the Docker image build on every push to `main` and on pull requests.

## Project layout

```
src/main/java/com/checkout/payment/gateway
  bank/            acquiring bank interface, simulator client and its DTOs
  configuration/   application, bank, currency and OpenAPI configuration
  controller/      REST endpoints
  domain/          the stored Payment
  exception/       Problem Details mapping for every error
  logging/         request id filter
  model/           API request and response
  repository/      in-memory storage
  service/         payment processing
  validation/      expiry date and currency constraints
```

## What I would do next

- **Idempotency keys.** Accept an `Idempotency-Key` header and return the original result for a repeated key, so a merchant can safely retry after a timeout. This is the most important gap for a real gateway.
- **Durable storage** with a database, so payments survive restarts and can be shared between instances.
- **Merchant authentication**, and scoping retrieval so a merchant can only read its own payments.
- **Resolving unknown outcomes** after a 504, by querying the bank for the payment's status once the bank offers that.
- **A circuit breaker** around the bank, so the gateway fails fast while the bank is down instead of waiting for each request to time out.
- **Passing the request id to the bank**, so one payment can be traced across both systems.
- **Running Actuator on a separate management port**, so metrics and health are not exposed to merchants.
- **Tokenising card data** to reduce the PCI DSS scope of the gateway itself.
