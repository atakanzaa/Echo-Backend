package com.echo.service;

import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Haftalık kullanıcı profil güncelleme scheduler.
 * Her Pazar gece 02:00'de son 7 günde aktif olan kullanıcılar için
 * synthesis çalıştırır → UserMemoryService üzerinden profil güncellenir.
 * Maliyet: 300 aktif kullanıcı × 1 çağrı/hafta ≈ $0.36/ay
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryUpdateScheduler {

    private final UserRepository     userRepo;
    private final AISynthesisService synthesisService;

    @Scheduled(cron = "0 0 2 * * SUN")
    public void updateWeeklyProfiles() {
        LocalDate since = LocalDate.now().minusDays(7);
        var activeUsers = userRepo.findUsersWithRecentEntries(since);
        log.info("Haftalık profil güncellemesi başladı: {} aktif kullanıcı", activeUsers.size());

        int success = 0;
        int failed  = 0;
        for (var user : activeUsers) {
            try {
                synthesisService.synthesize(user.getId(), 7);
                success++;
            } catch (Exception e) {
                log.warn("Profil güncellenemedi userId={}: {}", user.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Haftalık profil güncellemesi tamamlandı: {} başarılı, {} başarısız", success, failed);
    }
}
