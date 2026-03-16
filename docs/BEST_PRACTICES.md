# Echo Backend — Java Best Practices

## 1. Dependency Injection
**Constructor injection kullan, asla `@Autowired` field injection.**
```java
// ✅ Doğru
@Service
@RequiredArgsConstructor
public class JournalService {
    private final JournalEntryRepository repository;
}

// ❌ Yanlış
@Service
public class JournalService {
    @Autowired
    private JournalEntryRepository repository;
}
```

## 2. DTO'lar için Record Kullan
```java
// ✅ Doğru — immutable, otomatik equals/hashCode/toString
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min=8) String password
) {}

// ❌ Yanlış
public class RegisterRequest {
    private String email;
    // getters/setters...
}
```

## 3. Controller'da Sıfır Business Logic
```java
// ✅ Doğru — controller sadece HTTP, service business logic bilir
@PostMapping("/register")
public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
    return ResponseEntity.status(CREATED).body(authService.register(req));
}
```

## 4. @Transactional Sadece Service Layer'da
```java
// ✅ Doğru
@Service
public class AuthService {
    @Transactional
    public AuthResponse register(RegisterRequest request) { ... }
}

// ❌ Yanlış — controller'da @Transactional
@RestController
public class AuthController {
    @Transactional  // ← asla
    @PostMapping("/register")
    public ResponseEntity<?> register(...) { ... }
}
```

## 5. Hardcoded Değer Yasak
```java
// ✅ Doğru
this.accessTokenExpiryMs = props.getJwt().getAccessTokenExpirySeconds() * 1000L;

// ❌ Yanlış
private static final long ACCESS_TOKEN_EXPIRY = 900000L;
```

## 6. Optional Kullan, Null Dönme
```java
// ✅ Doğru
public Optional<User> findByEmail(String email) { ... }

// Service'de:
userRepository.findByEmail(email)
    .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));
```

## 7. Entity'lerde @Builder + @NoArgsConstructor
```java
@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    // ...
}
```

## 8. Flyway Yönetir, Hibernate Değil
```yaml
# application.yml
jpa:
  hibernate:
    ddl-auto: validate  # asla create-drop veya update
```

## 9. Partial Index ile Performans
```sql
-- Sadece işlenmekte olan kayıtları hızlı bul
CREATE INDEX idx_journal_entries_active
    ON journal_entries(status)
    WHERE status NOT IN ('complete', 'failed');
```

## 10. Test Stratejisi
- **Service testleri:** `@ExtendWith(MockitoExtension.class)` + mock repository
- **Controller testleri:** `@WebMvcTest` + `MockMvc`
- **Entegrasyon testleri:** `@SpringBootTest` + Testcontainers (PostgreSQL)

## 11. Security Kuralı
**Client'tan gelen userId'e asla güvenme. JWT'den al.**
```java
// ✅ Doğru — JWT'den gelen güvenilir userId
@GetMapping("/entries")
public ResponseEntity<?> getEntries(@AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(journalService.getEntries(principal.getId()));
}

// ❌ Yanlış — request body'den userId alırsak manipüle edilebilir
@GetMapping("/entries")
public ResponseEntity<?> getEntries(@RequestParam UUID userId) { ... }
```

## 12. Async Pipeline
```java
@Async("journalProcessingExecutor")
@Transactional
public CompletableFuture<Void> processEntryAsync(UUID entryId) {
    // Her adımda status güncellenir
    // Hata durumunda status=failed, error_message set edilir
}
```
