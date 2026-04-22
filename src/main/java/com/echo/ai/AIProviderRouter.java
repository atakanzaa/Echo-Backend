package com.echo.ai;

import com.echo.ai.claude.ClaudeAnalysisProvider;
import com.echo.ai.claude.ClaudeCoachProvider;
import com.echo.ai.claude.ClaudeTranscriptionProvider;
import com.echo.ai.gemini.GeminiAnalysisProvider;
import com.echo.ai.gemini.GeminiCoachProvider;
import com.echo.ai.gemini.GeminiSynthesisProvider;
import com.echo.ai.gemini.GeminiTranscriptionProvider;
import com.echo.ai.openai.OpenAIAnalysisProvider;
import com.echo.ai.openai.OpenAICoachProvider;
import com.echo.ai.openai.OpenAISynthesisProvider;
import com.echo.ai.openai.OpenAITranscriptionProvider;
import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import com.echo.exception.TranscriptionFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime'da restart olmadan AI provider değiştirmeyi sağlar.
 * Analysis / coach / synthesis için global provider kullanılır;
 * transcription gerekirse bağımsız provider ve fallback ile override edilebilir.
 */
@Slf4j
@Component
public class AIProviderRouter {

    private static final Set<String> VALID_PROVIDERS = Set.of("openai", "gemini", "claude");

    private final AtomicReference<AITranscriptionProvider> transcriptionRef = new AtomicReference<>();
    private final AtomicReference<AIAnalysisProvider> analysisRef = new AtomicReference<>();
    private final AtomicReference<AICoachProvider> coachRef = new AtomicReference<>();
    private final AtomicReference<AISynthesisProvider> synthesisRef = new AtomicReference<>();

    private final AppProperties props;

    private volatile String activeProvider;
    private volatile String activeTranscriptionProvider;

    // All concrete provider beans — held for resolve() and fallback wiring
    private final OpenAITranscriptionProvider openAITranscription;
    private final OpenAIAnalysisProvider openAIAnalysis;
    private final OpenAICoachProvider openAICoach;
    private final OpenAISynthesisProvider openAISynthesis;
    private final GeminiTranscriptionProvider geminiTranscription;
    private final GeminiAnalysisProvider geminiAnalysis;
    private final GeminiCoachProvider geminiCoach;
    private final GeminiSynthesisProvider geminiSynthesis;
    private final ClaudeTranscriptionProvider claudeTranscription;
    private final ClaudeAnalysisProvider claudeAnalysis;
    private final ClaudeCoachProvider claudeCoach;

    public AIProviderRouter(AppProperties props,
                            OpenAITranscriptionProvider openAITranscription,
                            OpenAIAnalysisProvider openAIAnalysis,
                            OpenAICoachProvider openAICoach,
                            OpenAISynthesisProvider openAISynthesis,
                            GeminiTranscriptionProvider geminiTranscription,
                            GeminiAnalysisProvider geminiAnalysis,
                            GeminiCoachProvider geminiCoach,
                            GeminiSynthesisProvider geminiSynthesis,
                            ClaudeTranscriptionProvider claudeTranscription,
                            ClaudeAnalysisProvider claudeAnalysis,
                            ClaudeCoachProvider claudeCoach) {

        this.props = props;
        this.openAITranscription = openAITranscription;
        this.openAIAnalysis = openAIAnalysis;
        this.openAICoach = openAICoach;
        this.openAISynthesis = openAISynthesis;
        this.geminiTranscription = geminiTranscription;
        this.geminiAnalysis = geminiAnalysis;
        this.geminiCoach = geminiCoach;
        this.geminiSynthesis = geminiSynthesis;
        this.claudeTranscription = claudeTranscription;
        this.claudeAnalysis = claudeAnalysis;
        this.claudeCoach = claudeCoach;

        applyConfiguration(normalizeProvider(props.getAi().getProvider()));
    }

    /** Switches the global provider atomically. Explicit transcription override remains intact. */
    public synchronized void switchProvider(String provider) {
        applyConfiguration(normalizeProvider(provider));
    }

    public AITranscriptionProvider transcription() { return transcriptionRef.get(); }
    public AIAnalysisProvider analysis() { return analysisRef.get(); }
    public AICoachProvider coach() { return coachRef.get(); }
    public AISynthesisProvider synthesis() { return synthesisRef.get(); }
    public String activeProvider() { return activeProvider; }
    public String activeTranscriptionProvider() { return activeTranscriptionProvider; }

    private void applyConfiguration(String provider) {
        String transcriptionProvider = resolveConfiguredTranscriptionProvider(provider);
        String analysisFallbackProvider = resolveFallbackProvider(provider, props.getAi().getFallbackProvider());
        String transcriptionFallbackProvider = resolveTranscriptionFallbackProvider(provider, transcriptionProvider);

        transcriptionRef.set(resolveTranscription(transcriptionProvider, transcriptionFallbackProvider));
        analysisRef.set(resolveAnalysis(provider, analysisFallbackProvider));
        coachRef.set(resolveCoach(provider, analysisFallbackProvider));
        synthesisRef.set(resolveSynthesis(provider, analysisFallbackProvider));

        this.activeProvider = provider;
        this.activeTranscriptionProvider = transcriptionProvider;

        log.info("AI providers configured: analysis={} analysisFallback={} transcription={} transcriptionFallback={}",
                provider, analysisFallbackProvider, transcriptionProvider, transcriptionFallbackProvider);
    }

    private AITranscriptionProvider resolveTranscription(String provider, String fallbackProvider) {
        AITranscriptionProvider primary = transcriptionProviderBean(provider);
        if (fallbackProvider == null) {
            return primary;
        }
        return wrappedTranscription(primary, transcriptionProviderBean(fallbackProvider), provider, fallbackProvider);
    }

    private AIAnalysisProvider resolveAnalysis(String provider, String fallbackProvider) {
        AIAnalysisProvider primary = analysisProviderBean(provider);
        if (fallbackProvider == null) {
            return primary;
        }
        return wrappedAnalysis(primary, analysisProviderBean(fallbackProvider), provider, fallbackProvider);
    }

    private AICoachProvider resolveCoach(String provider, String fallbackProvider) {
        AICoachProvider primary = coachProviderBean(provider);
        if (fallbackProvider == null) {
            return primary;
        }
        return wrappedCoach(primary, coachProviderBean(fallbackProvider), provider, fallbackProvider);
    }

    private AISynthesisProvider resolveSynthesis(String provider, String fallbackProvider) {
        AISynthesisProvider primary = synthesisProviderBean(provider);
        if (fallbackProvider == null) {
            return primary;
        }
        return wrappedSynthesis(primary, synthesisProviderBean(fallbackProvider), provider, fallbackProvider);
    }

    private String resolveConfiguredTranscriptionProvider(String provider) {
        String configured = props.getAi().getTranscriptionProvider();
        return StringUtils.hasText(configured) ? normalizeProvider(configured) : provider;
    }

    private String resolveTranscriptionFallbackProvider(String provider, String transcriptionProvider) {
        String configured = props.getAi().getTranscriptionFallbackProvider();
        if (StringUtils.hasText(configured)) {
            return resolveFallbackProvider(transcriptionProvider, configured);
        }
        if (!StringUtils.hasText(props.getAi().getTranscriptionProvider())) {
            return resolveFallbackProvider(transcriptionProvider, props.getAi().getFallbackProvider());
        }
        return null;
    }

    private String resolveFallbackProvider(String provider, String configuredFallback) {
        if (!StringUtils.hasText(configuredFallback)) {
            return null;
        }
        String normalized = normalizeProvider(configuredFallback);
        return normalized.equals(provider) ? null : normalized;
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            throw new IllegalArgumentException("AI provider must not be blank");
        }
        String normalized = provider.toLowerCase(Locale.ROOT).trim();
        if (!VALID_PROVIDERS.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid provider: " + provider + ". Valid: openai, gemini, claude");
        }
        return normalized;
    }

    private AITranscriptionProvider transcriptionProviderBean(String provider) {
        return switch (provider) {
            case "openai" -> openAITranscription;
            case "gemini" -> geminiTranscription;
            case "claude" -> claudeTranscription;
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }

    private AIAnalysisProvider analysisProviderBean(String provider) {
        return switch (provider) {
            case "openai" -> openAIAnalysis;
            case "gemini" -> geminiAnalysis;
            case "claude" -> claudeAnalysis;
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }

    private AICoachProvider coachProviderBean(String provider) {
        return switch (provider) {
            case "openai" -> openAICoach;
            case "gemini" -> geminiCoach;
            case "claude" -> claudeCoach;
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }

    private AISynthesisProvider synthesisProviderBean(String provider) {
        return switch (provider) {
            case "openai" -> openAISynthesis;
            case "gemini" -> geminiSynthesis;
            case "claude" -> req -> {
                throw new UnsupportedOperationException(
                        "Synthesis not supported for claude. Use openai or gemini.");
            };
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }

    private AICoachProvider wrappedCoach(AICoachProvider primary,
                                         AICoachProvider fallback,
                                         String primaryName,
                                         String fallbackName) {
        return request -> {
            try {
                return primary.chat(request);
            } catch (ServiceUnavailableException e) {
                log.warn("ai_fallback op=COACH primary={} switching_to={} reason={}",
                        primaryName, fallbackName, e.getMessage());
                return fallback.chat(request);
            }
        };
    }

    private AIAnalysisProvider wrappedAnalysis(AIAnalysisProvider primary,
                                               AIAnalysisProvider fallback,
                                               String primaryName,
                                               String fallbackName) {
        return new AIAnalysisProvider() {
            @Override
            public AIAnalysisResponse analyze(AIAnalysisRequest request) {
                try {
                    return primary.analyze(request);
                } catch (ServiceUnavailableException e) {
                    log.warn("ai_fallback op=ANALYSIS primary={} switching_to={} reason={}",
                            primaryName, fallbackName, e.getMessage());
                    return fallback.analyze(request);
                }
            }

            @Override
            public GoalMatchDecision verifyGoalMatch(GoalMatchVerificationRequest request) {
                try {
                    return primary.verifyGoalMatch(request);
                } catch (ServiceUnavailableException e) {
                    log.warn("ai_fallback op=GOAL_MATCH primary={} switching_to={} reason={}",
                            primaryName, fallbackName, e.getMessage());
                    return fallback.verifyGoalMatch(request);
                }
            }
        };
    }

    private AISynthesisProvider wrappedSynthesis(AISynthesisProvider primary,
                                                 AISynthesisProvider fallback,
                                                 String primaryName,
                                                 String fallbackName) {
        return request -> {
            try {
                return primary.synthesize(request);
            } catch (ServiceUnavailableException e) {
                log.warn("ai_fallback op=SYNTHESIS primary={} switching_to={} reason={}",
                        primaryName, fallbackName, e.getMessage());
                return fallback.synthesize(request);
            }
        };
    }

    private AITranscriptionProvider wrappedTranscription(AITranscriptionProvider primary,
                                                         AITranscriptionProvider fallback,
                                                         String primaryName,
                                                         String fallbackName) {
        return request -> {
            try {
                return primary.transcribe(request);
            } catch (TranscriptionFailedException e) {
                log.warn("ai_fallback op=TRANSCRIPTION primary={} switching_to={} reason={} code={}",
                        primaryName, fallbackName, e.getMessage(), e.getCode());
                return fallback.transcribe(request);
            } catch (ServiceUnavailableException e) {
                log.warn("ai_fallback op=TRANSCRIPTION primary={} switching_to={} reason={}",
                        primaryName, fallbackName, e.getMessage());
                return fallback.transcribe(request);
            }
        };
    }
}
