# Echo — AI Agent Workflow Guide

Bu doküman projedeki AI-assisted geliştirme sürecini tanımlar.

## Temel Prensipler

### 1. Plan First
Trivial olmayan her değişiklik için önce plan yaz.
- Kodu etkileyecek tüm dosyaları tanımla
- Mevcut fonksiyonları yeniden kullan — gereksiz kod yazma
- Kullanıcıya onaylatmadan implemente etme

### 2. Subagent Strategy
Bağımsız görevleri paralel agent'larla yürüt:
- Explore agent: codebase keşfi
- Plan agent: mimari tasarım
- Bash agent: komut çalıştırma
- Birden fazla Write aynı anda — bağımsızlarsa bekletme

### 3. Self-Improvement Loop
Hata düzeltildikten sonra `lessons.md`'yi güncelle.
Aynı hata iki kez yapılmamalı.

### 4. Verify Before Done
Hiçbir task "complete" olarak işaretlenmeden önce:
- Kod derleniyor mu? (mvn compile)
- Test geçiyor mu?
- Endpoint çalışıyor mu? (curl test)

### 5. Demand Elegance
Trivial olmayan her değişiklik için: "Daha zarif bir yolu var mı?"
- 50 satır yerine 10 satırla halledilebilir mi?
- Mevcut Spring mekanizmaları kullanılıyor mu?

### 6. Autonomous Bug Fixing
Hata gördüğünde sormadan düzelt.
Sadece mimari değişiklikler için kullanıcıya sor.

### 7. Task Management (TodoWrite)
- Her faz başında todo listesi güncelle
- Tek seferde sadece bir task `in_progress`
- Task bitmeden bir sonrakine geçme

## Core Principles

| Prensip | Açıklama |
|---------|----------|
| **Simplicity First** | En basit çözüm en iyisidir |
| **No Laziness** | Kısa yol alma, doğru yap |
| **Minimal Impact** | Değiştirilmesi gerekmeyen koda dokunma |

## Security Rules (Frontend)
- User ID asla client'tan alınmaz — JWT'den çıkarılır
- Token'lar Keychain'de saklanır, UserDefaults'ta değil
- Tüm validasyon server-side yapılır
- Frontend sadece UI — business logic yoktur

## Java Rules (Backend)
- Constructor injection — `@Autowired` field injection yasak
- DTO'lar `record`
- Hardcoded değer yasak — `@ConfigurationProperties`
- `@Transactional` sadece service layer
- `ddl-auto: validate` — Flyway yönetir
