package com.echo.ai;

import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import com.echo.exception.TranscriptionFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runtime'da restart olmadan AI provider değiştirmeyi sağlar.
 * Analysis / coach / synthesis için global provider kullanılır;
 * transcription gerekirse bağımsız provider ve fallback ile override edilebilir.
 *
 * Provider beans are auto-discovered via Spring DI lists. To add a new provider,
 * implement the relevant interface(s) and register the bean — no router changes needed.
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

    private final Map<String, AITranscriptionProvider> transcriptionProviders;
    private final Map<String, AIAnalysisProvider> analysisProviders;
    private final Map<String, AICoachProvider> coachProviders;
    private final Map<String, AISynthesisProvider> synthesisProviders;

    public AIProviderRouter(AppProperties props,
                            java.util.List<AITranscriptionProvider> transcriptionProviders,
                            java.util.List<AIAnalysisProvider> analysisProviders,
                            java.util.List<AICoachProvider> coachProviders,
                            java.util.List<AISynthesisProvider> synthesisProviders) {

        this.props = props;
        this.transcriptionProviders = indexByVendor(transcriptionProviders);
        this.analysisProviders = indexByVendor(analysisProviders);
        this.coachProviders = indexByVendor(coachProviders);
        this.synthesisProviders = indexByVendor(synthesisProviders);

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
        AITranscriptionProvider primary = required(transcriptionProviders, provider, "transcription");
        if (fallbackProvider == null) {
            return primary;
        }
        return wrappedTranscription(primary,
                required(transcriptionProviders, fallbackProvider, "transcription"),
                provider, fallbackProvider);
    }

    private AIAnalysisProvider resolveAnalysis(String provider, String fallbackProvider) {
        AIAnalysisProvider primary = required(analysisProviders, provider, "analysis");
        if (fallbackProvider == null) {
            return primary;
        }
        return wrappedAnalysis(primary,
                required(analysisProviders, fallbackProvider, "analysis"),
                provider, fallbackProvider);
    }

    private AICoachProvider resolveCoach(String provider, String fallbackProvider) {
        AICoachProvider primary = required(coachProviders, provider, "coach");
        if (fallbackProvider == null) {
            return primary;
        }
        return wrappedCoach(primary,
                required(coachProviders, fallbackProvider, "coach"),
                provider, fallbackProvider);
    }

    private AISynthesisProvider resolveSynthesis(String provider, String fallbackProvider) {
        AISynthesisProvider primary = synthesisProviders.getOrDefault(provider, unsupportedSynthesis(provider));
        if (fallbackProvider == null) {
            return primary;
        }
        AISynthesisProvider fallback = synthesisProviders.getOrDefault(fallbackProvider, unsupportedSynthesis(fallbackProvider));
        return wrappedSynthesis(primary, fallback, provider, fallbackProvider);
    }

    private AISynthesisProvider unsupportedSynthesis(String provider) {
        return req -> {
            throw new UnsupportedOperationException(
                    "Synthesis not supported for " + provider + ". Use openai or gemini.");
        };
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

    private static <T> Map<String, T> indexByVendor(java.util.List<T> beans) {
        return beans.stream().collect(Collectors.toUnmodifiableMap(
                AIProviderRouter::vendorKey,
                Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException(
                            "Duplicate AI provider beans for vendor: " + vendorKey(a));
                }
        ));
    }

    /**
     * Maps a provider bean to its vendor key by inspecting the simple class name prefix.
     * e.g. {@code OpenAITranscriptionProvider} → {@code openai}.
     */
    private static String vendorKey(Object bean) {
        Class<?> c = bean.getClass();
        while (c != null && c != Object.class) {
            String simple = c.getSimpleName().toLowerCase(Locale.ROOT);
            for (String vendor : VALID_PROVIDERS) {
                if (simple.startsWith(vendor)) {
                    return vendor;
                }
            }
            c = c.getSuperclass();
        }
        throw new IllegalStateException(
                "AI provider bean " + bean.getClass().getName()
                        + " does not start with a known vendor prefix (openai|gemini|claude)");
    }

    private static <T> T required(Map<String, T> registry, String vendor, String role) {
        T bean = registry.get(vendor);
        if (bean == null) {
            throw new IllegalStateException(
                    "No " + role + " provider bean registered for vendor: " + vendor);
        }
        return bean;
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
