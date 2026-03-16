package com.echo.service;

import com.echo.ai.AICoachRequest;
import com.echo.ai.AIProviderRouter;
import com.echo.domain.coach.CoachMessage;
import com.echo.domain.coach.CoachSession;
import com.echo.domain.coach.MessageRole;
import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.journal.JournalEntry;
import com.echo.domain.user.User;
import com.echo.dto.request.SendCoachMessageRequest;
import com.echo.dto.response.CoachMessageResponse;
import com.echo.dto.response.CoachSessionResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.CoachMessageRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.CoachSessionRepository;
import com.echo.repository.GoalRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoachService {

    private final CoachSessionRepository    coachSessionRepository;
    private final CoachMessageRepository    coachMessageRepository;
    private final UserRepository            userRepository;
    private final AnalysisResultRepository  analysisResultRepository;
    private final GoalRepository            goalRepository;
    private final JournalEntryRepository    journalEntryRepository;
    private final AIProviderRouter          router;
    private final UserMemoryService         userMemoryService;
    private final AISynthesisService        synthesisService;

    private static final int MAX_HISTORY = 10;
    private static final int CONTEXT_DAYS = 7;
    private static final int MAX_TOPICS   = 5;
    private static final int MAX_GOALS    = 5;

    @Transactional(readOnly = true)
    public List<CoachSessionResponse> getSessions(UUID userId) {
        return coachSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream().map(CoachSessionResponse::from).toList();
    }

    @Transactional
    public CoachSessionResponse createSession(UUID userId, UUID journalEntryId) {
        User user = userRepository.findById(userId).orElseThrow();
        CoachSession session = CoachSession.builder().user(user).build();

        // Journal konteksti — kullanıcı bir journal hakkında konuşmak istiyorsa
        JournalEntry journalEntry = null;
        String journalContext = null;
        if (journalEntryId != null) {
            journalEntry = journalEntryRepository.findById(journalEntryId).orElse(null);
            if (journalEntry != null) {
                session.setJournalEntry(journalEntry);
                journalContext = buildJournalContext(journalEntry);
            }
        }

        // saveAndFlush: INSERT'i hemen çalıştırır → @CreationTimestamp null kalmaz
        CoachSession saved = coachSessionRepository.saveAndFlush(session);

        // Dinamik karşılama mesajı — journal konteksti varsa onu da ekle
        String welcomeContent = generateWelcomeMessage(userId, user.getDisplayName(), journalContext);
        CoachMessage welcome = CoachMessage.builder()
                .session(saved).user(user)
                .role(MessageRole.ASSISTANT)
                .content(welcomeContent)
                .build();
        coachMessageRepository.save(welcome);

        return CoachSessionResponse.from(saved);
    }

    /** Journal entry'den coach konteksti oluşturur (transkript + analiz özeti) */
    private String buildJournalContext(JournalEntry entry) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("[JOURNAL GİRİŞİ - ").append(entry.getEntryDate()).append("]\n");
        if (entry.getTranscript() != null) {
            // Çok uzun transkriptleri kısalt
            String transcript = entry.getTranscript();
            if (transcript.length() > 500) transcript = transcript.substring(0, 500) + "...";
            ctx.append("Transkript: ").append(transcript).append("\n");
        }
        // Analiz sonuçlarını ekle
        var analysisOpt = analysisResultRepository.findByJournalEntryId(entry.getId());
        if (analysisOpt.isPresent()) {
            AnalysisResult analysis = analysisOpt.get();
            if (analysis.getSummary() != null) ctx.append("AI Özet: ").append(analysis.getSummary()).append("\n");
            if (analysis.getMoodLabel() != null) ctx.append("Ruh hali: ").append(analysis.getMoodLabel()).append("\n");
            if (analysis.getKeyEmotions() != null) ctx.append("Duygular: ").append(String.join(", ", analysis.getKeyEmotions())).append("\n");
        }
        return ctx.toString();
    }

    /**
     * AI'nın ürettiği kişiselleştirilmiş karşılama mesajı.
     * Son 7 günün ruh hali, konuları ve aktif hedefleri bağlam olarak verilir.
     * AI hata verirse sabit bir fallback mesajı döner — session oluşturma asla başarısız olmaz.
     */
    private String generateWelcomeMessage(UUID userId, String displayName, String journalContext) {
        try {
            String mood          = buildMoodContext(userId);
            List<String> topics  = buildRecentTopics(userId);
            List<String> goals   = buildActiveGoals(userId);
            String name          = (displayName != null && !displayName.isBlank()) ? displayName : "Kullanıcı";

            String prompt;
            if (journalContext != null) {
                // Journal hakkında konuşma — AI'ya journal kontekstini ver
                prompt = "[KULLANICI ADI]: " + name + "\n" +
                        journalContext + "\n" +
                        "[YENİ OTURUM - JOURNAL TARTIŞMASI] Kullanıcı bu günlük girişi hakkında konuşmak istiyor. " +
                        "Adıyla karşıla, günlük girişinin içeriğine kısaca değin ve derinleştirici bir soru sor. " +
                        "2-3 cümle. Doğrudan tüm transkripti tekrarlama.";
            } else {
                prompt = "[KULLANICI ADI]: " + name + "\n" +
                        "[YENİ OTURUM] Kullanıcıyı adıyla, sıcak ve kısa (2 cümle) bir mesajla karşıla. " +
                        "Bugün nasıl hissettiklerini veya ne paylaşmak istediklerini sor. " +
                        "Bağlamı kullan ama doğrudan tekrarlama.";
            }

            var response = router.coach().chat(new AICoachRequest(
                    prompt,
                    List.of(),   // yeni oturum — geçmiş yok
                    userMemoryService.getUserProfile(userId),
                    mood,
                    topics,
                    goals
            ));
            return response.content();
        } catch (Exception e) {
            log.warn("Karşılama mesajı üretilemedi, varsayılan kullanılıyor: {}", e.getMessage());
            String name = (displayName != null && !displayName.isBlank()) ? displayName : "Merhaba";
            return name + ", bugün nasıl hissediyorsun? Seninle konuşmaya hazırım. 🌟";
        }
    }

    @Transactional(readOnly = true)
    public List<CoachMessageResponse> getMessages(UUID sessionId, UUID userId) {
        coachSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Oturum bulunamadı"));
        return coachMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream().map(CoachMessageResponse::from).toList();
    }

    @Transactional
    public List<CoachMessageResponse> sendMessage(UUID sessionId, UUID userId, SendCoachMessageRequest request) {
        CoachSession session = coachSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Oturum bulunamadı"));
        User user = userRepository.findById(userId).orElseThrow();

        // BUG DÜZELTMESİ #1: Kullanıcı mesajını kaydetmeden ÖNCE geçmişi yükle
        // Böylece AI'ya gönderilen history'de bu mesaj bulunmaz (çift gönderim engellenir)
        List<CoachMessage> allMessages = coachMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        int size = allMessages.size();

        // BUG DÜZELTMESİ #2: İLK 10 değil SON 10 mesajı al
        List<CoachMessage> recentHistory = size > MAX_HISTORY
                ? allMessages.subList(size - MAX_HISTORY, size)
                : allMessages;

        List<AICoachRequest.ChatMessage> chatHistory = recentHistory.stream()
                .map(m -> new AICoachRequest.ChatMessage(m.getRole().name().toLowerCase(), m.getContent()))
                .collect(Collectors.toList());

        // Son 7 günün analiz verileriyle + aktif hedeflerle kullanıcı bağlamı oluştur
        String moodContext      = buildMoodContext(userId);
        List<String> topics     = buildRecentTopics(userId);
        List<String> activeGoals = buildActiveGoals(userId);

        // Kullanıcı mesajını kaydet
        // saveAndFlush: INSERT'i hemen çalıştırır → @CreationTimestamp null kalmaz
        CoachMessage userMsg = CoachMessage.builder()
                .session(session).user(user).role(MessageRole.USER).content(request.content()).build();
        coachMessageRepository.saveAndFlush(userMsg);

        // AI çağrısı — userMessage ayrıca gönderilir, history'de YOK (çift gönderim yok)
        var aiResponse = router.coach().chat(new AICoachRequest(
                request.content(),
                chatHistory,
                userMemoryService.getUserProfile(userId),
                moodContext,
                topics,
                activeGoals
        ));

        // AI yanıtını kaydet
        // saveAndFlush: INSERT'i hemen çalıştırır → @CreationTimestamp null kalmaz
        CoachMessage assistantMsg = CoachMessage.builder()
                .session(session).user(user).role(MessageRole.ASSISTANT).content(aiResponse.content()).build();
        coachMessageRepository.saveAndFlush(assistantMsg);

        // Her 5. mesaj alışverişinde (10 mesaj) profil belleğini async güncelle
        int newTotal = size + 2;
        if (newTotal >= 10 && newTotal % 10 == 0) {
            synthesisService.synthesizeAsync(userId);
        }

        return List.of(CoachMessageResponse.from(userMsg), CoachMessageResponse.from(assistantMsg));
    }

    /**
     * Oturumu soft-close eder ve kullanıcı profilini async günceller.
     * iOS, kullanıcı coach ekranından ayrıldığında bu endpoint'i çağırmalıdır.
     */
    @Transactional
    public void endSession(UUID sessionId, UUID userId) {
        CoachSession session = coachSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Oturum bulunamadı"));
        session.setActive(false);
        coachSessionRepository.save(session);
        synthesisService.synthesizeAsync(userId);  // non-blocking — profil güncellenir
    }

    @Transactional
    public void deleteSession(UUID sessionId, UUID userId) {
        CoachSession session = coachSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Oturum bulunamadı"));
        coachMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .forEach(coachMessageRepository::delete);
        coachSessionRepository.delete(session);
    }

    // ── Bağlam yardımcıları ───────────────────────────────────────────────────

    private String buildMoodContext(UUID userId) {
        List<AnalysisResult> results = analysisResultRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                        userId,
                        LocalDate.now().minusDays(CONTEXT_DAYS),
                        LocalDate.now()
                );

        if (results.isEmpty()) return null;

        BigDecimal avg = results.stream()
                .map(AnalysisResult::getMoodScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(results.size()), 3, RoundingMode.HALF_UP);

        String trend = results.size() >= 2
                ? (results.get(0).getMoodScore().compareTo(results.get(results.size() - 1).getMoodScore()) > 0
                    ? "yükseliş" : "düşüş")
                : "stabil";

        return String.format("Son %d günde %d giriş. Ortalama ruh hali: %.2f/1.00, trend: %s.",
                CONTEXT_DAYS, results.size(), avg, trend);
    }

    private List<String> buildRecentTopics(UUID userId) {
        List<AnalysisResult> results = analysisResultRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                        userId,
                        LocalDate.now().minusDays(CONTEXT_DAYS),
                        LocalDate.now()
                );

        return results.stream()
                .filter(r -> r.getTopics() != null)
                .flatMap(r -> r.getTopics().stream())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_TOPICS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** PENDING hedefler — coach'a bağlam olarak verilir, max 5 adet. */
    private List<String> buildActiveGoals(UUID userId) {
        return goalRepository.findByUserIdAndStatusOrderByDetectedAtDesc(userId, "PENDING")
                .stream()
                .limit(MAX_GOALS)
                .map(g -> g.getTimeframe() != null && !g.getTimeframe().isBlank()
                        ? g.getTitle() + " (" + g.getTimeframe() + ")"
                        : g.getTitle())
                .collect(Collectors.toList());
    }
}
