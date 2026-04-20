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

## Rules

| DO | DON'T |
|----|-------|
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
