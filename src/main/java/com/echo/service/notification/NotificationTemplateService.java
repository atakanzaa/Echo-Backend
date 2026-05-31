package com.echo.service.notification;

import com.echo.domain.notification.NotificationType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationTemplateService {

    private static final String LANG_TR = "tr";

    public RenderedNotification render(NotificationType type, String language, Map<String, String> vars) {
        return render(type, language, vars, 0);
    }

    public RenderedNotification render(NotificationType type,
                                       String language,
                                       Map<String, String> vars,
                                       int variationIndex) {
        boolean turkish = LANG_TR.equalsIgnoreCase(language);
        Map<String, String> safeVars = vars == null ? Map.of() : vars;

        return switch (type) {
            case ANALYSIS_COMPLETE -> single(turkish,
                    "Analizin Hazır",
                    "Günlük analizin tamamlandı. İçgörülerine göz atabilirsin.",
                    "Analysis Ready",
                    "Your journal analysis is ready. Tap to see the insights.");

            case ACHIEVEMENT_EARNED -> turkish
                    ? new RenderedNotification(
                            "Yeni Başarım",
                            interpolate("{{badge_title}} {{badge_emoji}} kazandın", safeVars))
                    : new RenderedNotification(
                            "Achievement Unlocked",
                            interpolate("You earned {{badge_title}} {{badge_emoji}}", safeVars));

            case ACHIEVEMENT_PROGRESS -> turkish
                    ? new RenderedNotification(
                            "Başarıma Yaklaştın",
                            interpolate("{{badge_title}} için son {{remaining}} adım kaldı.", safeVars))
                    : new RenderedNotification(
                            "Almost There",
                            interpolate("{{remaining}} more to unlock {{badge_title}}.", safeVars));

            case POST_COMMENTED -> {
                boolean anonymous = "true".equalsIgnoreCase(safeVars.getOrDefault("anonymous", "false"));
                yield turkish
                        ? new RenderedNotification("Yeni Yorum",
                                anonymous ? "Birisi gönderine yorum yaptı." : "Gönderine yeni bir yorum geldi.")
                        : new RenderedNotification("New Comment",
                                anonymous ? "Someone commented on your post." : "New comment on your post.");
            }

            case POST_LIKED -> single(turkish,
                    "Yeni Beğeni", "Gönderin beğenildi.",
                    "New Like", "Someone liked your post.");

            case COMMENT_REPLIED -> {
                boolean anonymous = "true".equalsIgnoreCase(safeVars.getOrDefault("anonymous", "false"));
                yield turkish
                        ? new RenderedNotification("Yeni Cevap",
                                anonymous ? "Birisi yorumunu yanıtladı." : "Yorumuna bir yanıt geldi.")
                        : new RenderedNotification("New Reply",
                                anonymous ? "Someone replied to your comment." : "You have a new reply.");
            }

            case CAPSULE_UNLOCKED -> single(turkish,
                    "Zaman Kapsülün Açıldı",
                    "Geçmişten bir kapsülün artık seninle. Aç ve gör.",
                    "Time Capsule Unlocked",
                    "One of your time capsules is ready to open.");

            case MOOD_ALERT -> single(turkish,
                    "Seni Düşünüyoruz",
                    "Son günler biraz zor olmuş olabilir. Coach ile konuşmak ister misin?",
                    "We're Thinking of You",
                    "The last few days may have been tough. Would you like to talk to your Coach?");

            case WEEKLY_REFLECTION -> single(turkish,
                    "Haftalık Yansıman Hazır",
                    "Bu haftaki içgörülerini gör.",
                    "Your Weekly Reflection",
                    "Your insights for this week are ready.");

            case INSIGHT_UNLOCKED -> single(turkish,
                    "Yeni İçgörü",
                    "Senin için yeni bir içgörü hazır.",
                    "New Insight",
                    "A new insight is ready for you.");

            case DAILY_REMINDER -> pickVariation(turkish, variationIndex, DAILY_TR, DAILY_EN);

            case FIRST_JOURNAL_REMINDER -> pickVariation(turkish, variationIndex, FIRST_JOURNAL_TR, FIRST_JOURNAL_EN);

            case INACTIVITY_REMINDER -> pickVariation(turkish, variationIndex, INACTIVITY_TR, INACTIVITY_EN);

            case STREAK_REMINDER -> turkish
                    ? new RenderedNotification(
                            "Serini Koru",
                            interpolate("{{streak}} günlük serini bozma. Bugün kısa bir kayıt nasıl olur?", safeVars))
                    : new RenderedNotification(
                            "Keep Your Streak",
                            interpolate("Your {{streak}}-day streak is on the line. A quick note keeps it alive.", safeVars));

            case COACH_FOLLOWUP -> single(turkish,
                    "Coach Seni Soruyor",
                    "Son seansından beri ne hissediyorsun? Birkaç satırla devam edebilirsin.",
                    "Coach Checking In",
                    "How are you feeling since the last session? A short note keeps the thread alive.");

            case SYSTEM_ANNOUNCEMENT -> turkish
                    ? new RenderedNotification(
                            safeVars.getOrDefault("title", "Echo'dan haber"),
                            safeVars.getOrDefault("body", ""))
                    : new RenderedNotification(
                            safeVars.getOrDefault("title", "From the Echo team"),
                            safeVars.getOrDefault("body", ""));
        };
    }

    /** Stable per-(user, day) rotation index over the available template variations. */
    public int rotationIndex(UUID userId, LocalDate localDate, int variationCount) {
        if (variationCount <= 1) return 0;
        long hash = userId.getMostSignificantBits()
                ^ userId.getLeastSignificantBits()
                ^ localDate.toEpochDay();
        return (int) Math.floorMod(hash, variationCount);
    }

    public int variationCount(NotificationType type, String language) {
        boolean turkish = LANG_TR.equalsIgnoreCase(language);
        return switch (type) {
            case DAILY_REMINDER -> (turkish ? DAILY_TR : DAILY_EN).size();
            case FIRST_JOURNAL_REMINDER -> (turkish ? FIRST_JOURNAL_TR : FIRST_JOURNAL_EN).size();
            case INACTIVITY_REMINDER -> (turkish ? INACTIVITY_TR : INACTIVITY_EN).size();
            default -> 1;
        };
    }

    public Map<String, String> achievementVars(String badgeTitle, String badgeEmoji) {
        Map<String, String> v = new HashMap<>();
        v.put("badge_title", badgeTitle == null ? "" : badgeTitle);
        v.put("badge_emoji", badgeEmoji == null ? "" : badgeEmoji);
        return v;
    }

    public Map<String, String> commentVars(boolean anonymous) {
        return Map.of("anonymous", String.valueOf(anonymous));
    }

    public Map<String, String> achievementProgressVars(String badgeTitle, int remaining) {
        Map<String, String> v = new HashMap<>();
        v.put("badge_title", badgeTitle == null ? "" : badgeTitle);
        v.put("remaining", String.valueOf(Math.max(remaining, 0)));
        return v;
    }

    public Map<String, String> streakVars(int streak) {
        return Map.of("streak", String.valueOf(Math.max(streak, 0)));
    }

    public Map<String, String> systemAnnouncementVars(String title, String body) {
        Map<String, String> v = new HashMap<>();
        if (title != null) v.put("title", title);
        if (body != null) v.put("body", body);
        return v;
    }

    private RenderedNotification single(boolean turkish,
                                        String trTitle, String trBody,
                                        String enTitle, String enBody) {
        return turkish
                ? new RenderedNotification(trTitle, trBody)
                : new RenderedNotification(enTitle, enBody);
    }

    private RenderedNotification pickVariation(boolean turkish,
                                               int index,
                                               List<RenderedNotification> tr,
                                               List<RenderedNotification> en) {
        List<RenderedNotification> pool = turkish ? tr : en;
        int safeIndex = Math.floorMod(index, pool.size());
        return pool.get(safeIndex);
    }

    private String interpolate(String template, Map<String, String> vars) {
        if (vars.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return result;
    }

    private static final List<RenderedNotification> DAILY_TR = List.of(
            new RenderedNotification("Bugün için 2 dakika", "Kendine 2 dakika ayır ve nasıl hissettiğini Echo'ya anlat."),
            new RenderedNotification("Günlük zamanı", "Bugün aklında ne var? Sesli olarak kaydet."),
            new RenderedNotification("Nasıl gidiyor?", "Bugünü kısa bir kayıtla kapatmaya ne dersin?"),
            new RenderedNotification("Bir nefes al", "Bir nefes al ve bugünü sesli olarak yansıt."),
            new RenderedNotification("Bugünü unutma", "Küçük bir kayıt, ileride büyük bir hatıra olacak.")
    );

    private static final List<RenderedNotification> DAILY_EN = List.of(
            new RenderedNotification("Two minutes for you", "Take two minutes and tell Echo how you're feeling today."),
            new RenderedNotification("Journal time", "What's on your mind today? Capture it in your voice."),
            new RenderedNotification("How's it going?", "Close out the day with a short reflection."),
            new RenderedNotification("Take a breath", "Pause for a moment and record what's true right now."),
            new RenderedNotification("Don't lose today", "A small note now becomes a real memory later.")
    );

    private static final List<RenderedNotification> FIRST_JOURNAL_TR = List.of(
            new RenderedNotification("İlk günlüğünü kaydet", "Echo'ya hoş geldin. Birkaç saniye konuş, yolculuğun başlasın."),
            new RenderedNotification("Sesini duy", "İlk günlüğünü ekle ve içgörülerin nasıl çalıştığını gör."),
            new RenderedNotification("Bir adım at", "Bugün nasıl hissediyorsun? Yalnızca birkaç cümle yeter."),
            new RenderedNotification("Echo seni bekliyor", "İlk kaydını yap, gerisini Echo halletsin."),
            new RenderedNotification("İlk hatıran burada", "Kısa bir kayıtla başla; geri dönüp bakman güzel olacak.")
    );

    private static final List<RenderedNotification> FIRST_JOURNAL_EN = List.of(
            new RenderedNotification("Record your first entry", "Welcome to Echo. A few seconds of talking is all you need to start."),
            new RenderedNotification("Hear yourself", "Add your first entry and see how the insights work."),
            new RenderedNotification("Take the first step", "How are you feeling today? A couple of sentences is enough."),
            new RenderedNotification("Echo is ready", "Make your first recording and let Echo handle the rest."),
            new RenderedNotification("Your first memory", "Start small — future you will thank present you.")
    );

    private static final List<RenderedNotification> INACTIVITY_TR = List.of(
            new RenderedNotification("Seni duymadık", "Son birkaç gündür bir kaydın yok. Bugün kısa bir tane ekler misin?"),
            new RenderedNotification("Geri dönüş zamanı", "Kısa bir kayıt seni tekrar yola sokar. Hadi başlayalım."),
            new RenderedNotification("Bir kahve molası", "Bir nefes al ve birkaç cümle ile günlüğüne dön."),
            new RenderedNotification("Echo seni özledi", "Son günlerden birini sesli olarak özetle."),
            new RenderedNotification("Devam etmek için", "Birkaç dakika içinde günlüğüne tekrar bağlanabilirsin.")
    );

    private static final List<RenderedNotification> INACTIVITY_EN = List.of(
            new RenderedNotification("We miss your voice", "It's been a few days. A short entry today is enough to keep going."),
            new RenderedNotification("Time to come back", "A small recording will get you back into the rhythm."),
            new RenderedNotification("Coffee break", "Pause for a moment and add a few sentences to your journal."),
            new RenderedNotification("Echo missed you", "Recap one of the last few days out loud."),
            new RenderedNotification("Pick it back up", "A couple of minutes is all it takes to reconnect with your journal.")
    );
}
