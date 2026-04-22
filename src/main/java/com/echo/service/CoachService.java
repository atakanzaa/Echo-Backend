package com.echo.service;

import com.echo.ai.AICoachRequest;
import com.echo.ai.AIProviderRouter;
import com.echo.domain.coach.CoachMessage;
import com.echo.domain.coach.CoachSession;
import com.echo.domain.coach.MessageRole;
import com.echo.domain.goal.GoalStatus;
import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.journal.JournalEntry;
import com.echo.domain.subscription.FeatureKey;
import com.echo.domain.user.User;
import com.echo.dto.request.SendCoachMessageRequest;
import com.echo.dto.response.CoachMessageResponse;
import com.echo.dto.response.CoachSessionResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.exception.QuotaExceededException;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.CoachMessageRepository;
import com.echo.repository.CoachSessionRepository;
import com.echo.repository.GoalRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoachService {

    private final CoachSessionRepository sessionRepo;
    private final CoachMessageRepository messageRepo;
    private final UserRepository userRepo;
    private final AnalysisResultRepository analysisRepo;
    private final GoalRepository goalRepo;
    private final JournalEntryRepository journalEntryRepo;
    private final AIProviderRouter router;
    private final UserMemoryService userMemoryService;
    private final AISynthesisService synthesisService;
    private final EntitlementService entitlementService;
    private final GoalIntegrationService goalIntegrationService;
    private final PlatformTransactionManager transactionManager;

    private static final int MAX_HISTORY = 10;
    private static final int CONTEXT_DAYS = 7;
    private static final int MAX_TOPICS = 5;
    private static final int MAX_GOALS = 5;

    @Transactional(readOnly = true)
    public PagedResponse<CoachSessionResponse> getSessions(UUID userId, Pageable pageable) {
        return PagedResponse.from(
                sessionRepo.findByUserIdOrderByUpdatedAtDesc(userId, pageable),
                CoachSessionResponse::from
        );
    }

    @Transactional
    public CoachSessionResponse createSession(UUID userId, UUID journalEntryId) {
        if (!entitlementService.consumeQuota(userId, FeatureKey.COACH_SESSIONS)) {
            throw new QuotaExceededException(
                    "COACH_SESSION_LIMIT",
                    "Monthly coach session limit reached. Upgrade to Premium for unlimited sessions."
            );
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        CoachSession session = CoachSession.builder().user(user).build();

        String journalContext = null;
        if (journalEntryId != null) {
            JournalEntry journalEntry = journalEntryRepo.findByIdAndUserId(journalEntryId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found"));
            session.setJournalEntry(journalEntry);
            journalContext = buildJournalContext(journalEntry);
            String dateStr = journalEntry.getEntryDate()
                    .format(DateTimeFormatter.ofPattern("d MMMM", new Locale(user.getPreferredLanguage())));
            session.setTitle(dateStr + " Journal Discussion");
        }

        if (session.getTitle() == null) {
            session.setTitle("New Conversation");
        }

        CoachSession saved = sessionRepo.saveAndFlush(session);

        // generate personalized welcome message
        String welcomeContent = generateWelcomeMessage(userId, user.getDisplayName(), journalContext, user.getPreferredLanguage());
        CoachMessage welcome = CoachMessage.builder()
                .session(saved).user(user)
                .role(MessageRole.ASSISTANT)
                .content(welcomeContent)
                .build();
        messageRepo.save(welcome);

        return CoachSessionResponse.from(saved);
    }

    private String buildJournalContext(JournalEntry entry) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("[JOURNAL ENTRY - ").append(entry.getEntryDate()).append("]\n");
        if (entry.getTranscript() != null) {
            String transcript = entry.getTranscript();
            if (transcript.length() > 500) transcript = transcript.substring(0, 500) + "...";
            ctx.append("Transcript: ").append(transcript).append("\n");
        }
        var analysisOpt = analysisRepo.findByJournalEntryId(entry.getId());
        if (analysisOpt.isPresent()) {
            AnalysisResult analysis = analysisOpt.get();
            if (analysis.getSummary() != null) ctx.append("AI Summary: ").append(analysis.getSummary()).append("\n");
            if (analysis.getMoodLabel() != null) ctx.append("Mood: ").append(analysis.getMoodLabel()).append("\n");
            if (analysis.getKeyEmotions() != null) ctx.append("Emotions: ").append(String.join(", ", analysis.getKeyEmotions())).append("\n");
        }
        return ctx.toString();
    }

    private String generateWelcomeMessage(UUID userId, String displayName, String journalContext, String language) {
        try {
            // single DB call for all context instead of 3 separate queries
            UserContext ctx = buildUserContext(userId);
            String name = (displayName != null && !displayName.isBlank()) ? displayName : "User";

            String prompt;
            if (journalContext != null) {
                prompt = "[USER NAME]: " + name + "\n" +
                        journalContext + "\n" +
                        "[NEW SESSION - JOURNAL DISCUSSION] The user wants to discuss this journal entry. " +
                        "Greet by name, briefly reference the entry content, and ask a deepening question. " +
                        "2-3 sentences. Do not repeat the entire transcript.";
            } else {
                prompt = "[USER NAME]: " + name + "\n" +
                        "[NEW SESSION] Greet the user by name with a warm, short (2 sentences) message. " +
                        "Ask how they feel today or what they'd like to share. " +
                        "Use context but don't repeat it directly.";
            }

            var response = router.coach().chat(new AICoachRequest(
                    prompt, List.of(),
                    userMemoryService.getUserProfile(userId),
                    ctx.moodContext(), ctx.topics(), ctx.goals(), name, language,
                    buildNarrative(ctx, language)
            ));
            return response.content();
        } catch (Exception e) {
            log.warn("Welcome message generation failed, using fallback: {}", e.getMessage());
            String name = (displayName != null && !displayName.isBlank()) ? displayName : "Hello";
            return name + ", how are you feeling today? I'm here to talk.";
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<CoachMessageResponse> getMessages(UUID sessionId, UUID userId, Pageable pageable) {
        sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        return PagedResponse.from(
                messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId, pageable),
                CoachMessageResponse::from
        );
    }

    public List<CoachMessageResponse> sendMessage(UUID sessionId, UUID userId, SendCoachMessageRequest request) {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);

        // Phase 1: validate + load context in a short transaction — DB connection released before AI call
        SendMessageContext ctx = readTx.execute(status -> {
            CoachSession session = sessionRepo.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
            if (!session.isActive()) {
                throw new IllegalArgumentException("Coach session is inactive");
            }
            User user = userRepo.findById(userId).orElseThrow();

            if (!entitlementService.hasSessionQuota(userId, sessionId, FeatureKey.COACH_MESSAGES_PER_SESSION)) {
                throw new QuotaExceededException(
                        "COACH_SESSION_MESSAGE_LIMIT",
                        "Message limit reached for this coach session. Upgrade to Premium for longer conversations."
                );
            }
            if (!entitlementService.consumeQuota(userId, FeatureKey.COACH_MESSAGES_TOTAL)) {
                throw new QuotaExceededException(
                        "COACH_MONTHLY_LIMIT",
                        "Monthly coach message limit reached. Upgrade to Premium for higher monthly limits."
                );
            }

            List<CoachMessage> recent = new ArrayList<>(
                    messageRepo.findBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, MAX_HISTORY))
            );
            Collections.reverse(recent);
            long userMessageCount = messageRepo.countBySessionIdAndRole(sessionId, MessageRole.USER);

            List<AICoachRequest.ChatMessage> chatHistory = recent.stream()
                    .map(m -> new AICoachRequest.ChatMessage(m.getRole().name().toLowerCase(), m.getContent()))
                    .toList();

            return new SendMessageContext(chatHistory, buildUserContext(userId),
                    user.getDisplayName(), user.getPreferredLanguage(), userMessageCount);
        });

        // Phase 2: AI call — no DB connection held
        var aiResponse = callCoachWithQuotaRefund(sessionId, userId, request, ctx);

        // Phase 3: persist in a new write transaction
        TransactionTemplate writeTx = new TransactionTemplate(transactionManager);
        try {
            return writeTx.execute(status -> {
                CoachSession session = sessionRepo.findByIdAndUserIdForUpdate(sessionId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
                if (!session.isActive()) {
                    throw new IllegalArgumentException("Coach session is inactive");
                }
                if (!entitlementService.hasSessionQuota(userId, sessionId, FeatureKey.COACH_MESSAGES_PER_SESSION)) {
                    throw new QuotaExceededException(
                            "COACH_SESSION_MESSAGE_LIMIT",
                            "Message limit reached for this coach session. Upgrade to Premium for longer conversations."
                    );
                }
                User user = userRepo.findById(userId).orElseThrow();

                CoachMessage userMsg = CoachMessage.builder()
                        .session(session).user(user).role(MessageRole.USER).content(request.content()).build();
                messageRepo.saveAndFlush(userMsg);

                CoachMessage assistantMsg = CoachMessage.builder()
                        .session(session).user(user).role(MessageRole.ASSISTANT).content(aiResponse.content()).build();
                messageRepo.saveAndFlush(assistantMsg);

                try {
                    goalIntegrationService.processCoachUtterance(userId, sessionId, userMsg.getId(), request.content());
                } catch (Exception e) {
                    log.warn("Goal completion detection skipped for coach message: sessionId={}, userId={}, error={}",
                            sessionId, userId, e.getMessage());
                }

                long newUserMessageCount = ctx.userMessageCount() + 1;
                int interval = entitlementService.getLimit(userId, FeatureKey.SYNTHESIS_INTERVAL);
                if (interval > 0 && newUserMessageCount >= interval && newUserMessageCount % interval == 0) {
                    synthesisService.synthesizeAsync(userId);
                }

                return List.of(CoachMessageResponse.from(userMsg), CoachMessageResponse.from(assistantMsg));
            });
        } catch (RuntimeException ex) {
            entitlementService.refundQuota(userId, FeatureKey.COACH_MESSAGES_TOTAL);
            throw ex;
        }
    }

    private record SendMessageContext(
            List<AICoachRequest.ChatMessage> chatHistory,
            UserContext userContext,
            String displayName,
            String language,
            long userMessageCount
    ) {}

    private com.echo.ai.AICoachResponse callCoachWithQuotaRefund(UUID sessionId,
                                                                  UUID userId,
                                                                  SendCoachMessageRequest request,
                                                                  SendMessageContext ctx) {
        try {
            return router.coach().chat(new AICoachRequest(
                    request.content(), ctx.chatHistory(),
                    userMemoryService.getUserProfile(userId),
                    ctx.userContext().moodContext(), ctx.userContext().topics(), ctx.userContext().goals(),
                    ctx.displayName(), ctx.language(),
                    buildNarrative(ctx.userContext(), ctx.language())
            ));
        } catch (RuntimeException ex) {
            entitlementService.refundQuota(userId, FeatureKey.COACH_MESSAGES_TOTAL);
            log.warn("Coach AI call failed: sessionId={} userId={} error={}", sessionId, userId, ex.getMessage());
            throw ex;
        }
    }

    // soft-close session and trigger async profile update
    @Transactional
    public void endSession(UUID sessionId, UUID userId) {
        CoachSession session = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        session.setActive(false);
        sessionRepo.save(session);
        synthesisService.synthesizeAsync(userId);
    }

    // bulk delete messages instead of load-all-then-delete-one-by-one
    @Transactional
    public void deleteSession(UUID sessionId, UUID userId) {
        CoachSession session = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        messageRepo.deleteBySessionId(sessionId);
        sessionRepo.delete(session);
    }

    // single query for mood context, topics, and goals (was 3 separate queries)
    private UserContext buildUserContext(UUID userId) {
        List<AnalysisResult> results = analysisRepo
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                        userId, LocalDate.now().minusDays(CONTEXT_DAYS), LocalDate.now());

        String moodContext = null;
        List<String> topics = List.of();

        if (!results.isEmpty()) {
            BigDecimal avg = results.stream()
                    .map(AnalysisResult::getMoodScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(results.size()), 3, RoundingMode.HALF_UP);

            String trend = results.size() >= 2
                    ? (results.get(0).getMoodScore().compareTo(results.get(results.size() - 1).getMoodScore()) > 0
                    ? "improving" : "declining")
                    : "stable";

            moodContext = String.format("Last %d days: %d entries. Average mood: %.2f/1.00, trend: %s.",
                    CONTEXT_DAYS, results.size(), avg, trend);

            topics = results.stream()
                    .filter(r -> r.getTopics() != null)
                    .flatMap(r -> r.getTopics().stream())
                    .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(MAX_TOPICS)
                    .map(Map.Entry::getKey)
                    .toList();
        }

        List<String> goals = goalRepo.findByUserIdAndStatusInOrderByDetectedAtDesc(
                        userId,
                        GoalStatus.openStatuses()
                )
                .stream()
                .limit(MAX_GOALS)
                .map(g -> g.getTimeframe() != null && !g.getTimeframe().isBlank()
                        ? g.getTitle() + " (" + g.getTimeframe() + ")"
                        : g.getTitle())
                .toList();

        return new UserContext(moodContext, topics, goals);
    }

    private record UserContext(String moodContext, List<String> topics, List<String> goals) {}

    // Builds a natural-language paragraph about the user — so the coach sounds like someone who
    // already knows the person, instead of listing facts under separate headers.
    private String buildNarrative(UserContext ctx, String language) {
        boolean tr = language == null || !"en".equalsIgnoreCase(language);
        StringBuilder sb = new StringBuilder();

        String moodLine = describeMood(ctx.moodContext(), tr);
        if (moodLine != null) {
            sb.append(moodLine).append(" ");
        }

        if (ctx.topics() != null && !ctx.topics().isEmpty()) {
            sb.append(tr ? "Sık bahsettiği konular: " : "Recent topics: ")
              .append(String.join(", ", ctx.topics()))
              .append(". ");
        }
        if (ctx.goals() != null && !ctx.goals().isEmpty()) {
            sb.append(tr ? "Üstünde çalıştığı şeyler: " : "Working on: ")
              .append(String.join(", ", ctx.goals()))
              .append(".");
        }
        return sb.toString().trim();
    }

    // Turns the numeric moodContext string ("Last 7 days: 3 entries. Average mood: 0.42/1.00,
    // trend: improving.") into a soft human sentence.
    private String describeMood(String moodContext, boolean tr) {
        if (moodContext == null) return null;
        java.util.regex.Matcher avgM = java.util.regex.Pattern
                .compile("Average mood: ([0-9.]+)").matcher(moodContext);
        java.util.regex.Matcher trendM = java.util.regex.Pattern
                .compile("trend: (\\w+)").matcher(moodContext);
        if (!avgM.find()) return null;

        double avg;
        try { avg = Double.parseDouble(avgM.group(1)); } catch (NumberFormatException e) { return null; }
        String trend = trendM.find() ? trendM.group(1) : "stable";

        String moodWord;
        if (tr) {
            moodWord = avg >= 0.75 ? "moralı iyi" : avg >= 0.55 ? "orta seviyede" : "biraz düşük";
        } else {
            moodWord = avg >= 0.75 ? "doing well" : avg >= 0.55 ? "mixed" : "a bit low";
        }

        String trendClause;
        if (tr) {
            trendClause = switch (trend) {
                case "improving" -> ", son günlerde toparlanıyor gibi";
                case "declining" -> ", ama son günlerde düşüşte";
                default -> "";
            };
            return "Son bir hafta " + moodWord + trendClause + ".";
        } else {
            trendClause = switch (trend) {
                case "improving" -> ", picking up lately";
                case "declining" -> ", trending down recently";
                default -> "";
            };
            return "Past week has been " + moodWord + trendClause + ".";
        }
    }
}
