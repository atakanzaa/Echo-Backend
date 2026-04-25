package com.echo.service;

import com.echo.ai.AIGoal;
import com.echo.ai.AIAnalysisResponse;
import com.echo.ai.AIProviderRouter;
import com.echo.ai.GoalMatchCandidate;
import com.echo.ai.GoalMatchDecision;
import com.echo.ai.GoalMatchVerificationRequest;
import com.echo.domain.goal.Goal;
import com.echo.domain.goal.GoalCreationType;
import com.echo.domain.goal.GoalStatus;
import com.echo.domain.goal.GoalSuggestion;
import com.echo.domain.journal.JournalEntry;
import com.echo.domain.subscription.FeatureKey;
import com.echo.domain.user.User;
import com.echo.dto.response.GoalResponse;
import com.echo.dto.response.GoalSuggestionResponse;
import com.echo.exception.QuotaExceededException;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.GoalRepository;
import com.echo.repository.GoalSuggestionRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.UserRepository;
import com.echo.service.goal.GoalDetectionConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoalIntegrationService {

    public static final String SUGGESTION_TYPE_CREATE_GOAL = "CREATE_GOAL";
    public static final String SUGGESTION_TYPE_COMPLETE_GOAL_CONFIRM = "COMPLETE_GOAL_CONFIRM";

    public static final String SUGGESTION_STATUS_PENDING = "PENDING";
    public static final String SUGGESTION_STATUS_ACCEPTED = "ACCEPTED";
    public static final String SUGGESTION_STATUS_REJECTED = "REJECTED";
    public static final String SUGGESTION_STATUS_EXPIRED = "EXPIRED";

    public static final String SOURCE_TYPE_JOURNAL = "JOURNAL";
    public static final String SOURCE_TYPE_COACH = "COACH";

    private static final List<GoalStatus> OPEN_GOAL_STATUSES = GoalStatus.openStatuses();

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(?:[.,]\\d+)?\\b");
    private static final Set<String> STOP_WORDS = GoalDetectionConstants.STOP_WORDS;
    private static final Set<String> ACTION_KEYWORDS = GoalDetectionConstants.ACTION_KEYWORDS;
    private static final List<String> FUTURE_INTENT_MARKERS = GoalDetectionConstants.FUTURE_INTENT_MARKERS;
    private static final List<String> WISH_ONLY_MARKERS = GoalDetectionConstants.WISH_ONLY_MARKERS;
    private static final List<String> VAGUE_MARKERS = GoalDetectionConstants.VAGUE_MARKERS;
    private static final List<String> COMPLETION_MARKERS = GoalDetectionConstants.COMPLETION_MARKERS;
    private static final List<String> NEGATION_MARKERS = GoalDetectionConstants.NEGATION_MARKERS;

    private static final double DETERMINISTIC_AUTO_THRESHOLD = GoalDetectionConstants.DETERMINISTIC_AUTO_THRESHOLD;
    private static final double SECONDARY_CANDIDATE_MAX = GoalDetectionConstants.SECONDARY_CANDIDATE_MAX;
    private static final double AI_CONFIRM_MIN = GoalDetectionConstants.AI_CONFIRM_MIN;
    private static final double AI_AUTO_MIN = GoalDetectionConstants.AI_AUTO_MIN;
    private static final int MAX_SUGGESTIONS_PER_JOURNAL = GoalDetectionConstants.MAX_SUGGESTIONS_PER_JOURNAL;

    private final GoalRepository goalRepository;
    private final GoalSuggestionRepository goalSuggestionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final UserRepository userRepository;
    private final EntitlementService entitlementService;
    private final GoalService goalService;
    private final AIProviderRouter router;

    @Transactional
    public List<GoalSuggestionResponse> getSuggestions(
            UUID userId,
            String sourceType,
            UUID journalEntryId,
            UUID coachSessionId
    ) {
        expireStaleSuggestions(userId);

        List<GoalSuggestion> suggestions;
        String normalizedSourceType = normalizeSourceType(sourceType);

        if (SOURCE_TYPE_JOURNAL.equals(normalizedSourceType) && journalEntryId != null) {
            suggestions = goalSuggestionRepository
                    .findByUserIdAndStatusAndSourceTypeAndSourceJournalEntryIdOrderByCreatedAtDesc(
                            userId, SUGGESTION_STATUS_PENDING, SOURCE_TYPE_JOURNAL, journalEntryId
                    );
        } else if (SOURCE_TYPE_COACH.equals(normalizedSourceType) && coachSessionId != null) {
            suggestions = goalSuggestionRepository
                    .findByUserIdAndStatusAndSourceTypeAndSourceCoachSessionIdOrderByCreatedAtDesc(
                            userId, SUGGESTION_STATUS_PENDING, SOURCE_TYPE_COACH, coachSessionId
                    );
        } else {
            suggestions = goalSuggestionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, SUGGESTION_STATUS_PENDING);
        }

        return suggestions.stream()
                .filter(suggestion -> !isExpired(suggestion))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public GoalResponse acceptSuggestion(UUID userId, UUID suggestionId) {
        GoalSuggestion suggestion = goalSuggestionRepository.findByIdAndUserId(suggestionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal suggestion not found"));

        ensurePendingSuggestion(suggestion);

        GoalResponse response;
        if (SUGGESTION_TYPE_CREATE_GOAL.equals(suggestion.getSuggestionType())) {
            response = acceptCreateGoalSuggestion(userId, suggestion);
        } else if (SUGGESTION_TYPE_COMPLETE_GOAL_CONFIRM.equals(suggestion.getSuggestionType())) {
            Goal goal = getSuggestionGoal(suggestion, userId);
            response = goalService.completeGoalFromAutomation(
                    userId,
                    goal.getId(),
                    suggestion.getSourceType(),
                    sourceRefId(suggestion)
            );
        } else {
            throw new IllegalStateException("Unsupported suggestion type: " + suggestion.getSuggestionType());
        }

        suggestion.setStatus(SUGGESTION_STATUS_ACCEPTED);
        suggestion.setResolvedAt(OffsetDateTime.now());
        goalSuggestionRepository.save(suggestion);
        return response;
    }

    @Transactional
    public void rejectSuggestion(UUID userId, UUID suggestionId) {
        GoalSuggestion suggestion = goalSuggestionRepository.findByIdAndUserId(suggestionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal suggestion not found"));

        ensurePendingSuggestion(suggestion);
        suggestion.setStatus(SUGGESTION_STATUS_REJECTED);
        suggestion.setResolvedAt(OffsetDateTime.now());
        goalSuggestionRepository.save(suggestion);
    }

    @Transactional
    public void processJournalAnalysis(UUID userId, UUID journalEntryId, AIAnalysisResponse analysis) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        JournalEntry entry = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found"));

        createGoalSuggestionsFromAnalysis(user, journalEntryId, analysis.goals());

        if (StringUtils.hasText(entry.getTranscript())) {
            processCompletionUtterance(
                    user,
                    SOURCE_TYPE_JOURNAL,
                    journalEntryId,
                    journalEntryId,
                    null,
                    entry.getTranscript()
            );
        }
    }

    @Transactional
    public void processCoachUtterance(UUID userId, UUID coachSessionId, UUID coachMessageId, String utterance) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        processCompletionUtterance(user, SOURCE_TYPE_COACH, coachMessageId, null, coachSessionId, utterance);
    }

    private GoalResponse acceptCreateGoalSuggestion(UUID userId, GoalSuggestion suggestion) {
        int limit = entitlementService.getLimit(userId, FeatureKey.ACTIVE_GOALS);
        int activeGoalCount = goalRepository.countByUserIdAndStatusIn(userId, OPEN_GOAL_STATUSES);
        if (limit != -1 && activeGoalCount >= limit) {
            throw new QuotaExceededException(
                    "GOALS_LIMIT",
                    "Active goal limit reached. Upgrade to Premium for more goals."
            );
        }

        List<Goal> openGoals = goalRepository.findByUserIdAndStatusInOrderByDetectedAtDesc(userId, OPEN_GOAL_STATUSES);
        Goal existing = findSimilarGoal(openGoals, suggestion.getTitle());
        if (existing != null) {
            suggestion.setGoal(existing);
            return GoalResponse.from(existing);
        }

        User user = suggestion.getUser();
        Goal goal = Goal.builder()
                .user(user)
                .title(suggestion.getTitle())
                .timeframe(suggestion.getTimeframe())
                .goalType(StringUtils.hasText(suggestion.getGoalType()) ? suggestion.getGoalType() : "general")
                .creationType(GoalCreationType.AI)
                .status(GoalStatus.PENDING)
                .sourceJournalEntryId(suggestion.getSourceJournalEntryId())
                .detectedAt(OffsetDateTime.now())
                .build();

        Goal saved = goalRepository.save(goal);
        suggestion.setGoal(saved);
        return GoalResponse.from(saved);
    }

    private void createGoalSuggestionsFromAnalysis(User user, UUID journalEntryId, List<AIGoal> aiGoals) {
        if (aiGoals == null || aiGoals.isEmpty()) {
            return;
        }

        int limit = entitlementService.getLimit(user.getId(), FeatureKey.ACTIVE_GOALS);
        int activeGoalCount = goalRepository.countByUserIdAndStatusIn(user.getId(), OPEN_GOAL_STATUSES);
        if (limit != -1 && activeGoalCount >= limit) {
            log.debug("Goal suggestion skipped because goal limit is full: userId={}", user.getId());
            return;
        }

        List<Goal> openGoals = goalRepository.findByUserIdAndStatusInOrderByDetectedAtDesc(user.getId(), OPEN_GOAL_STATUSES);
        List<GoalSuggestion> recentRejected = goalSuggestionRepository.findByUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                user.getId(),
                SUGGESTION_STATUS_REJECTED,
                OffsetDateTime.now().minusDays(7)
        );

        List<AIGoal> filtered = aiGoals.stream()
                .filter(this::isMeaningfulGoalCandidate)
                .sorted(Comparator.comparingDouble(this::goalConfidence).reversed())
                .toList();

        int slots = limit == -1 ? MAX_SUGGESTIONS_PER_JOURNAL : Math.max(0, limit - activeGoalCount);
        int remaining = Math.min(MAX_SUGGESTIONS_PER_JOURNAL, slots);
        Set<String> batchNormalizedTitles = new HashSet<>();

        for (AIGoal aiGoal : filtered) {
            if (remaining <= 0) {
                break;
            }

            String normalizedTitle = normalizeText(aiGoal.title());
            if (!batchNormalizedTitles.add(normalizedTitle)) {
                continue;
            }
            if (findSimilarGoal(openGoals, aiGoal.title()) != null) {
                continue;
            }
            if (wasRecentlyRejected(aiGoal.title(), recentRejected)) {
                continue;
            }

            String dedupeKey = buildCreateSuggestionDedupeKey(user.getId(), journalEntryId, aiGoal.title());
            if (goalSuggestionRepository.existsByDedupeKey(dedupeKey)) {
                continue;
            }

            GoalSuggestion suggestion = GoalSuggestion.builder()
                    .user(user)
                    .suggestionType(SUGGESTION_TYPE_CREATE_GOAL)
                    .sourceType(SOURCE_TYPE_JOURNAL)
                    .sourceJournalEntryId(journalEntryId)
                    .title(compactTitle(aiGoal.title()))
                    .timeframe(cleanText(aiGoal.timeframe()))
                    .goalType(cleanGoalType(aiGoal.goalType()))
                    .detectedText(cleanText(aiGoal.sourceQuote()))
                    .reason(cleanText(aiGoal.reason()))
                    .confidence(decimal(goalConfidence(aiGoal)))
                    .dedupeKey(dedupeKey)
                    .status(SUGGESTION_STATUS_PENDING)
                    .expiresAt(OffsetDateTime.now().plusDays(7))
                    .build();
            goalSuggestionRepository.save(suggestion);
            remaining--;
        }
    }

    private void processCompletionUtterance(
            User user,
            String sourceType,
            UUID sourceRefId,
            UUID sourceJournalEntryId,
            UUID sourceCoachSessionId,
            String utterance
    ) {
        if (!StringUtils.hasText(utterance)) {
            return;
        }

        String normalizedUtterance = normalizeText(utterance);
        if (!hasCompletionIntent(normalizedUtterance) || hasNegation(normalizedUtterance)) {
            return;
        }

        List<Goal> openGoals = goalRepository.findByUserIdAndStatusInOrderByDetectedAtDesc(user.getId(), OPEN_GOAL_STATUSES);
        if (openGoals.isEmpty()) {
            return;
        }

        List<ScoredGoalCandidate> scored = openGoals.stream()
                .map(goal -> new ScoredGoalCandidate(goal, scoreGoalMatch(utterance, normalizedUtterance, goal)))
                .filter(candidate -> candidate.score() >= 0.45)
                .sorted(Comparator.comparingDouble(ScoredGoalCandidate::score).reversed())
                .toList();

        if (scored.isEmpty()) {
            return;
        }

        ScoredGoalCandidate top = scored.get(0);
        double secondScore = scored.size() > 1 ? scored.get(1).score() : 0.0;

        if (top.score() >= DETERMINISTIC_AUTO_THRESHOLD && secondScore <= SECONDARY_CANDIDATE_MAX) {
            goalService.completeGoalFromAutomation(user.getId(), top.goal().getId(), sourceType, sourceRefId);
            return;
        }

        if (top.score() < AI_CONFIRM_MIN || secondScore >= top.score() - 0.15) {
            return;
        }
        if (wasRecentCompletionSuggestionRejected(user.getId(), top.goal().getId())) {
            return;
        }

        GoalMatchDecision decision = verifyGoalCompletionWithAi(user, utterance, scored.stream()
                .limit(3)
                .map(candidate -> new GoalMatchCandidate(
                        candidate.goal().getId(),
                        candidate.goal().getTitle(),
                        candidate.goal().getTimeframe(),
                        candidate.goal().getGoalType()
                ))
                .toList());

        if (decision == null || decision.goalId() == null) {
            return;
        }

        Goal selectedGoal = openGoals.stream()
                .filter(goal -> goal.getId().equals(decision.goalId()))
                .findFirst()
                .orElse(null);
        if (selectedGoal == null) {
            return;
        }

        if (decision.shouldAutoComplete() && decision.confidence() >= AI_AUTO_MIN) {
            goalService.completeGoalFromAutomation(user.getId(), selectedGoal.getId(), sourceType, sourceRefId);
            return;
        }

        if (decision.needsConfirmation() && decision.confidence() >= AI_CONFIRM_MIN) {
            createCompletionSuggestion(user, selectedGoal, sourceType, sourceRefId, sourceJournalEntryId, sourceCoachSessionId,
                    utterance, decision.reason(), decision.confidence());
        }
    }

    private void createCompletionSuggestion(
            User user,
            Goal goal,
            String sourceType,
            UUID sourceRefId,
            UUID sourceJournalEntryId,
            UUID sourceCoachSessionId,
            String detectedText,
            String reason,
            double confidence
    ) {
        String dedupeKey = buildCompletionSuggestionDedupeKey(goal.getId(), sourceType, sourceRefId);
        if (goalSuggestionRepository.existsByDedupeKey(dedupeKey)) {
            return;
        }

        GoalSuggestion suggestion = GoalSuggestion.builder()
                .user(user)
                .goal(goal)
                .suggestionType(SUGGESTION_TYPE_COMPLETE_GOAL_CONFIRM)
                .sourceType(sourceType)
                .sourceJournalEntryId(sourceJournalEntryId)
                .sourceCoachSessionId(sourceCoachSessionId)
                .sourceCoachMessageId(SOURCE_TYPE_COACH.equals(sourceType) ? sourceRefId : null)
                .title(goal.getTitle())
                .timeframe(goal.getTimeframe())
                .goalType(goal.getGoalType())
                .detectedText(cleanText(detectedText))
                .reason(cleanText(reason))
                .confidence(decimal(confidence))
                .dedupeKey(dedupeKey)
                .status(SUGGESTION_STATUS_PENDING)
                .expiresAt(OffsetDateTime.now().plusHours(24))
                .build();
        goalSuggestionRepository.save(suggestion);
    }

    private GoalMatchDecision verifyGoalCompletionWithAi(User user, String utterance, List<GoalMatchCandidate> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        try {
            return router.analysis().verifyGoalMatch(new GoalMatchVerificationRequest(
                    utterance,
                    user.getPreferredLanguage(),
                    candidates
            ));
        } catch (Exception ex) {
            log.warn("Goal completion verification fell back to deterministic only: {}", ex.getMessage());
            return null;
        }
    }

    private Goal getSuggestionGoal(GoalSuggestion suggestion, UUID userId) {
        UUID goalId = suggestion.getGoal() != null ? suggestion.getGoal().getId() : null;
        if (goalId == null) {
            throw new IllegalStateException("Goal completion suggestion is missing a goal reference");
        }
        return goalRepository.findByIdAndUserIdAndStatusNot(goalId, userId, GoalStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
    }

    private void ensurePendingSuggestion(GoalSuggestion suggestion) {
        if (!SUGGESTION_STATUS_PENDING.equals(suggestion.getStatus())) {
            throw new IllegalStateException("Goal suggestion is not pending");
        }
        if (isExpired(suggestion)) {
            suggestion.setStatus(SUGGESTION_STATUS_EXPIRED);
            suggestion.setResolvedAt(OffsetDateTime.now());
            goalSuggestionRepository.save(suggestion);
            throw new IllegalStateException("Goal suggestion has expired");
        }
    }

    private void expireStaleSuggestions(UUID userId) {
        List<GoalSuggestion> stale = goalSuggestionRepository.findByUserIdAndStatusAndExpiresAtBefore(
                userId,
                SUGGESTION_STATUS_PENDING,
                OffsetDateTime.now()
        );
        if (stale.isEmpty()) {
            return;
        }
        for (GoalSuggestion suggestion : stale) {
            suggestion.setStatus(SUGGESTION_STATUS_EXPIRED);
            suggestion.setResolvedAt(OffsetDateTime.now());
        }
        goalSuggestionRepository.saveAll(stale);
    }

    private boolean wasRecentlyRejected(String title, List<GoalSuggestion> rejectedSuggestions) {
        String normalizedTitle = normalizeText(title);
        return rejectedSuggestions.stream()
                .filter(suggestion -> SUGGESTION_TYPE_CREATE_GOAL.equals(suggestion.getSuggestionType()))
                .map(GoalSuggestion::getTitle)
                .filter(StringUtils::hasText)
                .map(this::normalizeText)
                .anyMatch(rejectedTitle -> similarity(normalizedTitle, rejectedTitle) >= 0.75);
    }

    private boolean wasRecentCompletionSuggestionRejected(UUID userId, UUID goalId) {
        return !goalSuggestionRepository.findByUserIdAndStatusAndGoalIdAndCreatedAtAfterOrderByCreatedAtDesc(
                userId,
                SUGGESTION_STATUS_REJECTED,
                goalId,
                OffsetDateTime.now().minusHours(24)
        ).isEmpty();
    }

    private GoalSuggestionResponse toResponse(GoalSuggestion suggestion) {
        if (SUGGESTION_TYPE_COMPLETE_GOAL_CONFIRM.equals(suggestion.getSuggestionType())) {
            return GoalSuggestionResponse.from(
                    suggestion,
                    String.format("\"%s\" hedefini tamamladın gibi görünüyor. Tamamlandı olarak işaretleyeyim mi?", suggestion.getTitle()),
                    "Tamamlandı",
                    "Henüz değil"
            );
        }

        return GoalSuggestionResponse.from(
                suggestion,
                String.format("\"%s\" için hedef oluşturmamı ister misin?", suggestion.getTitle()),
                "Ekle",
                "Şimdilik hayır"
        );
    }

    private double scoreGoalMatch(String utterance, String normalizedUtterance, Goal goal) {
        String normalizedTitle = normalizeText(goal.getTitle());
        Set<String> utteranceTokens = tokenize(normalizedUtterance);
        Set<String> titleTokens = tokenize(normalizedTitle);

        if (titleTokens.isEmpty() || utteranceTokens.isEmpty()) {
            return 0.0;
        }

        double score = 0.20; // completion intent prerequisite already satisfied
        double overlap = tokenOverlap(utteranceTokens, titleTokens);
        score += overlap * 0.45;

        if (normalizedUtterance.contains(normalizedTitle) || normalizedTitle.contains(normalizedUtterance)) {
            score += 0.25;
        }

        double numberScore = numberScore(utterance, goal.getTitle());
        score += numberScore;
        if (numberScore < 0) {
            score -= 0.10;
        }

        if (sharesActionSignal(utteranceTokens, titleTokens)) {
            score += 0.10;
        }
        if (matchesTimeframe(normalizedUtterance, goal.getTimeframe())) {
            score += 0.05;
        }

        long daysOld = goal.getDetectedAt() != null
                ? Math.max(0, java.time.Duration.between(goal.getDetectedAt(), OffsetDateTime.now()).toDays())
                : 30;
        if (daysOld <= 7) {
            score += 0.05;
        } else if (daysOld <= 30) {
            score += 0.02;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    private double tokenOverlap(Set<String> utteranceTokens, Set<String> titleTokens) {
        long matches = titleTokens.stream()
                .filter(titleToken -> utteranceTokens.stream().anyMatch(utteranceToken -> fuzzyTokenMatch(utteranceToken, titleToken)))
                .count();
        return matches / (double) titleTokens.size();
    }

    private boolean fuzzyTokenMatch(String left, String right) {
        if (left.equals(right)) {
            return true;
        }
        if (left.length() >= 3 && right.length() >= 3) {
            String shorter = left.length() <= right.length() ? left : right;
            String longer = left.length() > right.length() ? left : right;
            return longer.startsWith(shorter) || commonPrefix(left, right) >= 3;
        }
        return false;
    }

    private int commonPrefix(String left, String right) {
        int max = Math.min(left.length(), right.length());
        int count = 0;
        while (count < max && left.charAt(count) == right.charAt(count)) {
            count++;
        }
        return count;
    }

    private boolean sharesActionSignal(Set<String> utteranceTokens, Set<String> titleTokens) {
        Set<String> utteranceActions = utteranceTokens.stream()
                .filter(this::isActionToken)
                .collect(Collectors.toSet());
        if (utteranceActions.isEmpty()) {
            return false;
        }
        return titleTokens.stream().anyMatch(titleToken -> utteranceActions.stream().anyMatch(action -> fuzzyTokenMatch(action, titleToken)));
    }

    private boolean isActionToken(String token) {
        return ACTION_KEYWORDS.stream().anyMatch(keyword -> fuzzyTokenMatch(token, normalizeText(keyword)));
    }

    private double numberScore(String utterance, String goalTitle) {
        List<String> utteranceNumbers = extractNumbers(utterance);
        List<String> goalNumbers = extractNumbers(goalTitle);
        if (goalNumbers.isEmpty()) {
            return 0.0;
        }
        if (utteranceNumbers.isEmpty()) {
            return -0.15;
        }
        return utteranceNumbers.equals(goalNumbers) ? 0.20 : -0.25;
    }

    private List<String> extractNumbers(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        List<String> numbers = new ArrayList<>();
        while (matcher.find()) {
            numbers.add(matcher.group().replace(',', '.'));
        }
        return numbers;
    }

    private boolean matchesTimeframe(String normalizedUtterance, String timeframe) {
        if (!StringUtils.hasText(timeframe)) {
            return false;
        }
        String normalizedTimeframe = normalizeText(timeframe);
        return normalizedUtterance.contains(normalizedTimeframe)
                || (normalizedTimeframe.contains("yarin") && normalizedUtterance.contains("bugun"))
                || (normalizedTimeframe.contains("tomorrow") && normalizedUtterance.contains("today"));
    }

    private Goal findSimilarGoal(List<Goal> goals, String title) {
        String normalizedTitle = normalizeText(title);
        return goals.stream()
                .filter(goal -> similarity(normalizedTitle, normalizeText(goal.getTitle())) >= 0.75)
                .findFirst()
                .orElse(null);
    }

    private double similarity(String left, String right) {
        Set<String> leftTokens = tokenize(left);
        Set<String> rightTokens = tokenize(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0.0;
        }
        long matches = leftTokens.stream()
                .filter(leftToken -> rightTokens.stream().anyMatch(rightToken -> fuzzyTokenMatch(leftToken, rightToken)))
                .count();
        int denominator = Math.max(leftTokens.size(), rightTokens.size());
        return matches / (double) denominator;
    }

    private Set<String> tokenize(String input) {
        if (!StringUtils.hasText(input)) {
            return Set.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (String token : input.split("\\s+")) {
            String cleaned = token.trim();
            if (cleaned.length() < 2 || STOP_WORDS.contains(cleaned)) {
                continue;
            }
            tokens.add(cleaned);
        }
        return tokens;
    }

    private boolean isMeaningfulGoalCandidate(AIGoal aiGoal) {
        if (aiGoal == null || !StringUtils.hasText(aiGoal.title())) {
            return false;
        }
        String normalizedTitle = normalizeText(aiGoal.title());
        String normalizedQuote = normalizeText(cleanText(aiGoal.sourceQuote()));
        String combined = (normalizedTitle + " " + normalizedQuote).trim();

        if (normalizedTitle.length() < 4 || tokenize(normalizedTitle).size() < 2) {
            return false;
        }
        if (containsAny(combined, WISH_ONLY_MARKERS) && !containsAny(combined, FUTURE_INTENT_MARKERS)) {
            return false;
        }
        if (containsAny(combined, VAGUE_MARKERS)) {
            return false;
        }
        if (!hasActionSignal(combined)) {
            return false;
        }
        if (!containsAny(combined, FUTURE_INTENT_MARKERS) && !StringUtils.hasText(aiGoal.timeframe())) {
            return false;
        }
        return goalConfidence(aiGoal) >= 0.55;
    }

    private boolean hasActionSignal(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        Set<String> tokens = tokenize(text);
        return tokens.stream().anyMatch(this::isActionToken);
    }

    private boolean hasCompletionIntent(String normalizedUtterance) {
        return containsAny(normalizedUtterance, COMPLETION_MARKERS);
    }

    private boolean hasNegation(String normalizedUtterance) {
        return containsAny(normalizedUtterance, NEGATION_MARKERS);
    }

    private boolean containsAny(String text, List<String> patterns) {
        return patterns.stream().map(this::normalizeText).anyMatch(text::contains);
    }

    private double goalConfidence(AIGoal aiGoal) {
        return aiGoal.confidence() != null ? Math.max(0.0, Math.min(1.0, aiGoal.confidence())) : 0.70;
    }

    private String compactTitle(String title) {
        String cleaned = cleanText(title);
        if (cleaned.length() <= 80) {
            return cleaned;
        }
        return cleaned.substring(0, 80).trim();
    }

    private String cleanGoalType(String goalType) {
        String cleaned = cleanText(goalType);
        return StringUtils.hasText(cleaned) ? cleaned : "general";
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace('ı', 'i')
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{Alnum}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private boolean isExpired(GoalSuggestion suggestion) {
        return suggestion.getExpiresAt() != null && suggestion.getExpiresAt().isBefore(OffsetDateTime.now());
    }

    private String normalizeSourceType(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return null;
        }
        return sourceType.trim().toUpperCase(Locale.ROOT);
    }

    private UUID sourceRefId(GoalSuggestion suggestion) {
        if (SOURCE_TYPE_JOURNAL.equals(suggestion.getSourceType())) {
            return suggestion.getSourceJournalEntryId();
        }
        return suggestion.getSourceCoachMessageId();
    }

    private String buildCreateSuggestionDedupeKey(UUID userId, UUID journalEntryId, String title) {
        return dedupeKey("create", userId + ":" + journalEntryId + ":" + normalizeText(title));
    }

    private String buildCompletionSuggestionDedupeKey(UUID goalId, String sourceType, UUID sourceRefId) {
        return dedupeKey("complete", goalId + ":" + sourceType + ":" + sourceRefId);
    }

    private String dedupeKey(String prefix, String payload) {
        UUID hash = UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8));
        return prefix + ":" + hash;
    }

    private record ScoredGoalCandidate(Goal goal, double score) {}
}
