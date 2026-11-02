# Spring Boot Backend Skill

## Projekt-Vorgabe
- **Framework**: Spring Boot 3.4 + Java 21
- **Build**: Gradle (Groovy DSL)
- **Security**: Spring Security FilterChain
- **Validation**: Jakarta Bean Validation (@Valid)
- **HTTP Client**: RestClient (Spring 6)
- **Logging**: SLF4J + Logback (JSON Structured)
- **Testing**: JUnit 5 + Mockito + MockMvc
- **Port**: 3000 (konfigurierbar via PORT env var)

---

## Architektur

```
Browser → Angular App → Spring Boot (:3000) → SoftNet API
```

### Request-Lifecycle

```
Incoming HTTP Request
    ↓
RequestIdFilter          → X-Request-Id Header generieren/durchreichen
    ↓
LoggingFilter            → Request/Response loggen (JSON structured)
    ↓
RateLimitFilter          → 100 Requests / 15 Min pro IP (Bucket4j)
    ↓
OriginFilter             → CORS_ALLOWED_ORIGINS pruefen
    ↓
SessionTokenFilter       → x-session-token validieren (HMAC-SHA256)
    ↓
Jakarta Validation       → @Valid auf Request DTOs
    ↓
Controller → Service → SoftnetApiClient
    ↓
ResponseWrapperAdvice    → { data: T, meta: { timestamp, requestId } }
    ↓
GlobalExceptionHandler   → Einheitliches Error-Format
    ↓
HTTP Response
```

---

## Patterns

### Controller
```java
@RestController
@RequestMapping("/api/v1/branches")
public class BranchesController {
    private final BranchesService branchesService;
    
    public BranchesController(BranchesService branchesService) {
        this.branchesService = branchesService;
    }
    
    @GetMapping
    public List<BranchResponseDto> getBranches() {
        return branchesService.getBranches();
    }
}
```

### Service
```java
@Service
public class BranchesService {
    private final SoftnetApiClient softnetApi;
    
    // Constructor injection — NO @Autowired
    public BranchesService(SoftnetApiClient softnetApi) {
        this.softnetApi = softnetApi;
    }
}
```

### DTOs (Records)
```java
public record BranchResponseDto(String id, String name, String description, List<String> brands) {}

public record CreateCarRequest(
    @NotBlank @Pattern(regexp = "^[A-ZAEOEUE]{1,3}[\\s\\-]?[A-Z]{1,2}[\\s\\-]?\\d{1,4}[A-Z]?$") String licensePlate,
    @Min(0) @Max(999999) int mileage,
    @NotBlank @Size(min = 17, max = 17) String vin,
    String brandId
) {}
```

### Configuration Properties
```java
@ConfigurationProperties(prefix = "softnet")
public record SoftnetProperties(
    String baseUrl, String authUrl, String fingerprintPath,
    int timeout, String groupId, String branchId
) {}
```

### Error Handling
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(...) { ... }
    
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(...) { ... }
}
```

### Testing
```java
@ExtendWith(MockitoExtension.class)
class BranchesServiceTest {
    @Mock private SoftnetApiClient softnetApi;
    
    @Test
    void getBranches_filtersToGroupBranches() {
        when(softnetApi.getGroups()).thenReturn(List.of(...));
        // assert...
    }
}
```

---

## SoftNet-Payloads: Unbekanntes weglassen (PFLICHT)

**Regel:** Ist eine **optionale** Angabe unbekannt (Fahrgestellnummer/VIN, Erstzulassung, …), wird das Feld im Payload an SoftNet **weggelassen** — der JSON-Key wird gar nicht gesetzt. KEIN Platzhalter (`"UNKNOWN"`, `"N/A"`, `"-"`), KEIN leerer String, KEIN erfundener Default (z. B. Erstzulassung `01/2020`).

**Ausnahme Pflichtfeld — `model` nie weglassen:** SoftNet verlangt `model` an manchen Standorten. Am 02.11.2026 lehnten Volkswagen Essen-Kray, Volkswagen Zentrum Wuppertal und Volkswagen Düsseldorf-Benrath jede Buchung ohne `model` ab („Mandatory field model not set“), das führte zum Rollback (REQ-045 5.3). Die Buchungsstrecke fragt nur Autohaus und Marke ab, nie das Modell. Deshalb gilt (Nutzerentscheid 02.11.2026): Modell des Kunden, sonst ein echtes Modell aus dem Snap, sonst `AppointmentsService.MODEL_UNKNOWN` (`"Unknown"`) — nie leer, nie weggelassen (`AppointmentsService.bookingModel`).

**Warum:** SoftNet übernimmt die Werte in den Fahrzeugstamm. Platzhalter verschmutzen dort die Daten, die SoftNet-Mitarbeiter beschweren sich (Nutzer-Vorgabe 30.10.2026, REQ-045).

**Eingehende Platzhalter ebenfalls entfernen:** Kommt ein Platzhalter von außen herein (Frontend, Snap-Objekt, Fremdsystem), wird er nicht durchgereicht, sondern verworfen. Der Vergleich läuft getrimmt und ohne Groß-/Kleinschreibung. Das gilt auch für Werte, die aus einem bestehenden Objekt kopiert werden (`existingCar.deepCopy()`): geerbte Platzhalter entfernen.

```java
// ✅ GOOD — optional field: set the key only for a real value, remove an inherited placeholder
SoftnetPayload.putIfKnown(car, "VIN", normalizeVin(dto.vehicle().vin()));
SoftnetPayload.putIfKnown(car, "firstRegYear", dto.vehicle().firstRegYear());

// ✅ GOOD — mandatory field (model): never omitted, falls back to MODEL_UNKNOWN
car.put("model", bookingModel(dto.vehicle().model(), existingCar.path("model").asText(null)));

// ❌ BAD — placeholder or invented default for an optional field
car.put("VIN", vin == null ? "UNKNOWN" : vin);
car.put("firstRegYear", year != null ? year : "2020");

// ❌ BAD — omitting the mandatory model: SoftNet rejects the booking at some branches
SoftnetPayload.putIfKnown(car, "model", dto.vehicle().model());
```

**Test-Pflicht:** Jeder Payload-Bau bekommt einen Unit-Test, der bei unbekannter Angabe prüft, dass der Key **fehlt** (`assertFalse(car.has("VIN"))`) — nicht, dass ein Platzhalter darin steht.

**Pflichtfelder prüfen:** SoftNet hat Pflichtfelder, und sie sind **je Standort verschieden** (REQ-015: leeres `model` abgelehnt; REQ-045 5.3: fehlendes `model` an drei Standorten abgelehnt, an anderen angenommen). Vor dem Deploy einer Payload-Änderung zuerst **lesend** `GET /api/planning/fields?scope=car&mandatory=true` auswerten (OpenAPI `api.planning.fields.query`, ändert nichts). **Testbuchungen:** Das lokale Backend spricht `api.soft-nrg.com`, laut OpenAPI das **Produktivsystem** (INT: `int-api.soft-nrg.com`) — jede Buchung ist echt. Erlaubt sind höchstens ein bis zwei, immer mit dem Hinweistext „lhind, Testbuchung, bitte stornieren“ (Nutzer-Vorgabe 02.11.2026). Eine Testbuchung belegt nur ihren Standort, also an einem strengen Standort testen (z. B. Volkswagen Essen-Kray). **Nach dem Deploy** 10 Minuten lang die echten Buchungen im Log prüfen (`SOFTNET_ERROR`, `ValidationError`, „Mandatory field“) und bei Ablehnung sofort zurückrollen. Ein neues Pflichtfeld NICHT eigenmächtig mit einem Platzhalter füllen, sondern mit dem Nutzer klären.

---

## Rules

| DO | DON'T |
|----|-------|
| Unbekannte optionale Angaben im SoftNet-Payload weglassen (`SoftnetPayload.putIfKnown`); Pflichtfeld `model` immer senden (`bookingModel`, Rückfall `MODEL_UNKNOWN`) | Platzhalter (`UNKNOWN`, `N/A`), leere Strings oder erfundene Defaults bei optionalen Feldern; Pflichtfelder weglassen |
| Constructor injection | `@Autowired` field injection |
| Java Records for DTOs | Mutable POJOs with getters/setters |
| `@ConfigurationProperties` | `@Value` for grouped config |
| Jakarta `@Valid` | Manual validation in controllers |
| `RestClient` (Spring 6) | `RestTemplate` (deprecated) |
| `@RestControllerAdvice` for errors | Try-catch in every controller |
| `ResponseStatusException` | Custom exception classes (unless needed) |
| Structured JSON logging (SLF4J) | `System.out.println` |
| `OncePerRequestFilter` | Servlet Filter directly |

---

## Commands

```bash
cd server && ./gradlew bootRun          # Start server (:3000)
cd server && ./gradlew test             # Run JUnit tests
cd server && ./gradlew jacocoTestReport # Coverage report
cd server && ./gradlew compileJava      # Compile only
cd server && ./gradlew clean build      # Full rebuild
```

---

## Project Structure

```
server/
├── build.gradle
├── settings.gradle
├── src/main/java/com/autohaus/backend/
│   ├── BackendApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── WebConfig.java
│   │   ├── SoftnetProperties.java
│   │   ├── AppProperties.java
│   │   └── DotenvConfig.java
│   ├── softnet/
│   │   ├── SoftnetApiClient.java
│   │   └── SoftnetAuthService.java
│   ├── session/
│   ├── health/
│   ├── branches/
│   ├── brands/
│   ├── jobtypes/
│   ├── snaps/
│   ├── appointments/
│   ├── customers/
│   ├── cars/
│   ├── mobility/
│   └── common/
│       ├── filter/
│       ├── exception/
│       └── dto/
├── src/main/resources/
│   ├── application.yml
│   └── META-INF/spring.factories
└── src/test/java/
```
