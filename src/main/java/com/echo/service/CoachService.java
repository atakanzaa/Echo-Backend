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
import com.echo.dto.response.PagedResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.CoachMessageRepository;
import com.echo.repository.CoachSessionRepository;
import com.echo.repository.GoalRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        CoachSession session = CoachSession.builder().user(user).build();

        // journal-linked session
        String journalContext = null;
        if (journalEntryId != null) {
            JournalEntry journalEntry = journalEntryRepo.findById(journalEntryId).orElse(null);
            if (journalEntry != null) {
                session.setJournalEntry(journalEntry);
                journalContext = buildJournalContext(journalEntry);
                String dateStr = journalEntry.getEntryDate()
                        .format(DateTimeFormatter.ofPattern("d MMMM", new Locale("tr")));
                session.setTitle(dateStr + " Journal Discussion");
            }
        }

        if (session.getTitle() == null) {
            session.setTitle("New Conversation");
        }

        CoachSession saved = sessionRepo.saveAndFlush(session);

        // generate personalized welcome message
        String welcomeContent = generateWelcomeMessage(userId, user.getDisplayName(), journalContext);
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

    private String generateWelcomeMessage(UUID userId, String displayName, String journalContext) {
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
                    ctx.moodContext(), ctx.topics(), ctx.goals(), name
            ));
            return response.content();
        } catch (Exception e) {
            log.warn("Welcome message generation failed, using fallback: {}", e.getMessage());
            String name = (displayName != null && !displayName.isBlank()) ? displayName : "Hello";
            return name + ", how are you feeling today? I'm here to talk.";
        }
    }

    @Transactional(readOnly = true)
    public List<CoachMessageResponse> getMessages(UUID sessionId, UUID userId) {
        sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream().map(CoachMessageResponse::from).toList();
    }

    @Transactional
    public List<CoachMessageResponse> sendMessage(UUID sessionId, UUID userId, SendCoachMessageRequest request) {
        CoachSession session = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        User user = userRepo.findById(userId).orElseThrow();

        // load history BEFORE saving user message to prevent duplicate in AI context
        List<CoachMessage> allMessages = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        int size = allMessages.size();

        // take LAST N messages, not first N
        List<CoachMessage> recentHistory = size > MAX_HISTORY
                ? allMessages.subList(size - MAX_HISTORY, size)
                : allMessages;

        List<AICoachRequest.ChatMessage> chatHistory = recentHistory.stream()
                .map(m -> new AICoachRequest.ChatMessage(m.getRole().name().toLowerCase(), m.getContent()))
                .toList();

        // single DB call for all context (was 3 separate queries)
        UserContext ctx = buildUserContext(userId);

        // save user message
        CoachMessage userMsg = CoachMessage.builder()
                .session(session).user(user).role(MessageRole.USER).content(request.content()).build();
        messageRepo.saveAndFlush(userMsg);

        // AI call
        var aiResponse = router.coach().chat(new AICoachRequest(
                request.content(), chatHistory,
                userMemoryService.getUserProfile(userId),
                ctx.moodContext(), ctx.topics(), ctx.goals(),
                user.getDisplayName()
        ));

        // save AI response
        CoachMessage assistantMsg = CoachMessage.builder()
                .session(session).user(user).role(MessageRole.ASSISTANT).content(aiResponse.content()).build();
        messageRepo.saveAndFlush(assistantMsg);

        // trigger profile synthesis every 5 exchanges (10 messages)
        int newTotal = size + 2;
        if (newTotal >= 10 && newTotal % 10 == 0) {
            synthesisService.synthesizeAsync(userId);
        }

        return List.of(CoachMessageResponse.from(userMsg), CoachMessageResponse.from(assistantMsg));
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

        List<String> goals = goalRepo.findByUserIdAndStatusOrderByDetectedAtDesc(userId, "PENDING")
                .stream()
                .limit(MAX_GOALS)
                .map(g -> g.getTimeframe() != null && !g.getTimeframe().isBlank()
                        ? g.getTitle() + " (" + g.getTimeframe() + ")"
                        : g.getTitle())
                .toList();

        return new UserContext(moodContext, topics, goals);
    }

    private record UserContext(String moodContext, List<String> topics, List<String> goals) {}
}
