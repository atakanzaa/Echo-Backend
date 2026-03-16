# Lessons Learned — Backend

Sık yapılan hataları ve çözümlerini buraya ekliyoruz.
Her ekleme şu formatı takip etsin: **Ne oldu → Neden oldu → Nasıl düzeltildi → Kural**.

---

## 1. `@CreationTimestamp` Sonrası `createdAt` Null Geliyor

**Tarih:** 2026-03-02
**Dosyalar:** `CommunityPost.java`, `CommunityPostResponse.java`, `CommunityService.java`

### Ne Oldu?

Post oluşturulurken 500 Internal Server Error aldık:

```
NullPointerException: Cannot invoke "OffsetDateTime.toString()"
because "CommunityPost.getCreatedAt()" is null
  at CommunityPostResponse.from(CommunityPostResponse.java:40)
```

### Neden Oldu?

`CommunityPost.java`'da şu annotation vardı:

```java
@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private OffsetDateTime createdAt;
```

`@CreationTimestamp` (Hibernate), timestamp'i veritabanına **INSERT SQL çalıştığı anda** oluşturur.
Ancak Spring Data JPA'nın `repository.save()` metodu veritabanından tekrar okuma (refresh) yapmaz —
`save()` çağrısından önce belleğe aldığı **aynı Java nesnesini** geri döndürür.

Sonuç: `save()` döndükten sonra Java nesnesi üzerinde `createdAt` hâlâ `null`.
`CommunityPostResponse.from()` içinde `post.getCreatedAt().toString()` dediğimizde NPE patlıyor.

```
save() çağrısı
    → Hibernate SQL'e INSERT çalıştırıyor (DB'de createdAt doldu)
    → Ama Java nesnesinde createdAt hâlâ null
    → from() metodunda NPE
```

### Nasıl Düzeltildi?

**Asıl fix — `@PrePersist` eklendi (`CommunityPost.java`):**

```java
@PrePersist
protected void onCreate() {
    if (createdAt == null) createdAt = OffsetDateTime.now();
    if (updatedAt == null) updatedAt = OffsetDateTime.now();
}
```

`@PrePersist` JPA standardıdır. Entity persist edilmeden **önce** çalışır ve değeri doğrudan
Java nesnesine yazar. Yani `save()` döndüğünde `createdAt` artık null değil.

**Savunma katmanı — null-safe guard eklendi (`CommunityPostResponse.java`):**

```java
// Eski (tehlikeli):
post.getCreatedAt().toString()

// Yeni (güvenli):
post.getCreatedAt() != null
    ? post.getCreatedAt().toString()
    : OffsetDateTime.now().toString()
```

### Altın Kural

> **`@CreationTimestamp` / `@UpdateTimestamp` kullanıyorsan, yanına mutlaka `@PrePersist` / `@PreUpdate` ekle.**
> Bu annotationlar DB seviyesinde çalışır; `save()` sonrasında Java nesnesinde değer yok.
> `@PrePersist` ise Java nesnesine yazar — DB'ye gitmeden önce, refresh gerekmeden.

### Alternatif Yöntemler (Neden Seçmedik?)

| Yöntem | Sorun |
|--------|-------|
| `saveAndFlush()` + `entityManager.refresh()` | Gereksiz ekstra DB sorgusu |
| `@EnableJpaAuditing` + `@CreatedDate` | Spring Auditing config gerektirir, proje buna hazır değil |
| Null-check'i sadece response'a eklemek | Root cause'u çözmüyor; DB'de de null kalıyor |

---

## 2. DTO'da `.toString()` Çağrısı Hiçbir Zaman Null-Check'siz Yapılmaz

**Kural:** Bir entity alanını `String`'e çevirirken, o alanın veritabanından null dönüp dönmeyeceğini
her zaman sorgula. `nullable = false` olsa bile Java nesnesi o anı göremiyor olabilir (yukarıdaki derse bak).

```java
// KÖTÜ — Her zaman NPE riski taşır:
entity.getSomeDate().toString()

// İYİ — Null-safe:
entity.getSomeDate() != null ? entity.getSomeDate().toString() : ""
```

> **Not:** Aynı hata `PostComment.java` + `PostCommentResponse.java`'da da yaşandı (2026-03-02).
> `@CreationTimestamp` kullanan **her entity**'de `@PrePersist` olduğundan emin ol.

---

## 3. Backend 204 No Content → iOS Tarafında Decode Hatası

**Tarih:** 2026-03-02
**Dosya (iOS):** `APIClient.swift`

### Ne Oldu?

Like/unlike butonuna basılıyor, UI anlık olarak güncelleniyordu ama hemen eski haline dönüyordu.

### Neden Oldu?

Backend like/unlike endpoint'leri başarı durumunda **204 No Content** döndürür — body yok.

`APIClient.handleResponse()` içinde:
```swift
return try APIClient.decoder.decode(T.self, from: data)  // data = boş Data()
```

Boş `Data()` geçerli JSON değildir. `JSONDecoder` hata fırlatır →
`CommunityViewModel.toggleLike` bunu `catch`'e alır → rollback yapar → UI geri döner.

### Nasıl Düzeltildi?

```swift
// Eski:
return try APIClient.decoder.decode(T.self, from: data)

// Yeni:
let body = data.isEmpty ? Data("{}".utf8) : data
return try APIClient.decoder.decode(T.self, from: body)
```

Boş body geldiğinde `{}` kullanılır; `APIVoidResponse {}` başarıyla decode edilir.

### Altın Kural

> **Backend'den void/boş dönebilecek her endpoint için `APIClient` boş body'yi handle etmeli.**
> `204 No Content` standart bir HTTP status'tur; client bunu decode hatası olarak yorumlamamalıdır.
> iOS tarafında `APIVoidResponse` kullanan tüm çağrılar bu tek değişiklikten otomatik yararlanır.

---

## 4. Async Task Race Condition — Tab/Sayfa Geçişlerinde Eski Sonuç Yazar

**Tarih:** 2026-03-02
**Dosya (iOS):** `CommunityViewModel.swift`

### Ne Oldu?

Following tabına geçilip hemen Global'e dönüldüğünde postlar kayboluyor.

### Neden Oldu?

```
1. loadFeed(tab: "following") → Task A başlar
2. loadFeed(tab: "global")   → selectedTab = "global", posts = [], Task B başlar
3. Task B tamamlanır → posts = [global posts] ✓
4. Task A tamamlanır → posts = [] (kimse takip edilmiyor) ✗ — global postlar silindi!
```

Task A, tamamlandığında `selectedTab` artık "global"'dir ama sonucunu yine de yazar.

### Nasıl Düzeltildi?

```swift
Task {
    do {
        let paged = try await ...
        guard self.selectedTab == activeTab else { return }  // ← stale guard
        posts = paged.content
        ...
    }
}
```

Task sonuç yazmadan önce `selectedTab`'ın hâlâ aynı olduğunu kontrol eder.
Eğer tab değişmişse sonuç sessizce atılır.

### Altın Kural

> **`Task {}` içinde ağ sonucu yazılmadan önce state'in hâlâ geçerli olduğunu kontrol et.**
> Özellikle liste/tab/sayfa geçişlerinde: `guard self.currentTab == requestedTab else { return }`
> Bu pattern, herhangi bir "stale async result" sorununu çözer.
> Daha güçlü alternatif: önceki Task'ı `.cancel()` ile iptal etmek.
