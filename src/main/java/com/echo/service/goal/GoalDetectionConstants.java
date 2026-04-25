package com.echo.service.goal;

import java.util.List;
import java.util.Set;

/**
 * Hardcoded markers, keywords and thresholds used by the deterministic
 * goal-detection pipeline. Kept separate from the integration service so the
 * lists can be reviewed, tuned, or eventually moved to configuration without
 * touching workflow code.
 */
public final class GoalDetectionConstants {

    private GoalDetectionConstants() {}

    public static final Set<String> STOP_WORDS = Set.of(
            "ve", "ile", "icin", "için", "bir", "bu", "su", "şu", "o", "da", "de", "the", "a", "an",
            "to", "for", "of", "my", "me", "ben", "bunu", "onu", "hedef", "goal"
    );

    public static final Set<String> ACTION_KEYWORDS = Set.of(
            "kos", "koş", "spor", "basla", "başla", "git", "bitir", "coz", "çöz", "rapor", "yaz",
            "teslim", "calis", "çalış", "oku", "ara", "toparla", "tamamla", "run", "exercise", "start",
            "finish", "fix", "ship", "submit", "write", "study", "call"
    );

    public static final List<String> FUTURE_INTENT_MARKERS = List.of(
            "yarin", "yarın", "haftaya", "aksam", "akşam", "bugun", "bugün", "dusunuyorum", "düşünüyorum",
            "yapacagim", "yapacağım", "edecegim", "edeceğim", "planliyorum", "planlıyorum", "baslayacagim",
            "başlayacağım", "need to", "going to", "will", "plan to", "tomorrow", "next week"
    );

    public static final List<String> WISH_ONLY_MARKERS = List.of(
            "keske", "keşke", "umarim", "umarım", "isterdim", "wish", "hopefully", "maybe someday"
    );

    public static final List<String> VAGUE_MARKERS = List.of(
            "bir gun", "bir gün", "hayatimi toparlayacagim", "hayatımı toparlayacağım", "daha iyi olacagim",
            "daha iyi olacağım", "fit olacagim", "fit olacağım", "better person", "get my life together"
    );

    public static final List<String> COMPLETION_MARKERS = List.of(
            "yaptim", "yaptım", "bitirdim", "cozdum", "çözdüm", "tamamladim", "tamamladım", "kostum", "koştum",
            "gittim", "hallettim", "bitti", "tamam", "finished", "fixed", "completed", "done", "shipped", "ran"
    );

    public static final List<String> NEGATION_MARKERS = List.of(
            "yapamadim", "yapamadım", "cozemedim", "çözemedim", "bitiremedim", "tamamlayamadim", "tamamlayamadım",
            "baslamadim", "başlamadım", "henüz değil", "henüz degil", "not yet", "didnt", "didn't", "couldnt",
            "couldn't", "yarin yapacagim", "yarın yapacağım", "deneyecegim", "deneyeceğim", "try tomorrow"
    );

    /** Score above which deterministic match auto-completes the goal. */
    public static final double DETERMINISTIC_AUTO_THRESHOLD = 0.85;
    /** Second-best score must stay below this for auto-complete (prevents ambiguity). */
    public static final double SECONDARY_CANDIDATE_MAX = 0.55;
    /** Below this AI-confirm is also skipped. */
    public static final double AI_CONFIRM_MIN = 0.60;
    /** AI-decision score above which the goal is auto-completed. */
    public static final double AI_AUTO_MIN = 0.90;
    /** Cap on suggestions surfaced from a single journal entry. */
    public static final int MAX_SUGGESTIONS_PER_JOURNAL = 2;
}
