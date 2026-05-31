# Notifications Architecture

End-to-end push + in-app notifications for the Echo iOS app.

## Flow

```
event / cron / journal upload / community action
  │
  ▼
NotificationService.notifyTemplated(userId, type, vars, ...)
  │
  ├── NotificationTemplateService renders title/body in user's language
  ├── notifications row written (eventId UNIQUE → idempotency)
  └── publishes NotificationCreatedEvent

@Async @TransactionalEventListener(AFTER_COMMIT)
PushDeliveryService.onNotificationCreated()
  │
  ├── NotificationEligibilityService.evaluate(notification)
  │     ├── per-category toggle (NotificationPreference)
  │     ├── quiet-hours (timezone-aware)
  │     └── active push tokens
  │
  ├── NotificationRateLimiter (5/hr per user)
  │
  └── for each token:
        NotificationDelivery PENDING row
          ▼
        ApnsPushSender.send → Pushy ApnsClient → APNs HTTP/2
          ▼
        delivery row -> SENT / FAILED / REJECTED
        token.failureCount++; auto-deactivate after 3 or on BadDeviceToken
```

## Categories & Types

`NotificationType` (column `notifications.type`) → `NotificationCategory`
(used by the preference toggles):

| Type                       | Category         |
| -------------------------- | ---------------- |
| ANALYSIS_COMPLETE          | INSIGHTS         |
| INSIGHT_UNLOCKED           | INSIGHTS         |
| WEEKLY_REFLECTION          | INSIGHTS         |
| ACHIEVEMENT_EARNED         | ACHIEVEMENTS     |
| ACHIEVEMENT_PROGRESS       | ACHIEVEMENTS     |
| POST_COMMENTED             | COMMUNITY        |
| POST_LIKED                 | COMMUNITY        |
| COMMENT_REPLIED            | COMMUNITY        |
| CAPSULE_UNLOCKED           | CAPSULES         |
| MOOD_ALERT                 | MOOD_ALERTS      |
| COACH_FOLLOWUP             | COACH            |
| DAILY_REMINDER             | DAILY_REMINDERS  |
| FIRST_JOURNAL_REMINDER     | DAILY_REMINDERS  |
| INACTIVITY_REMINDER        | DAILY_REMINDERS  |
| STREAK_REMINDER            | DAILY_REMINDERS  |
| SYSTEM_ANNOUNCEMENT        | SYSTEM           |

## Schedulers

| Cron (UTC)      | Job                                            |
| --------------- | ---------------------------------------------- |
| `0 0 9 * * *`   | `NotificationService.notifyUnlockedCapsules`   |
| `0 0 10 * * *`  | `MoodAlertService.checkMoodPatterns`           |
| `0 0 9 * * SUN` | `MemoryUpdateScheduler.sendWeeklyReflections`  |
| `0 0 * * * *`   | `DailyReminderScheduler.runHourly`             |
| `0 30 * * * *`  | `CoachFollowupScheduler.runHourly`             |
| `0 30 3 * * *`  | `NotificationMaintenanceScheduler.runDailyCleanup` |

`DailyReminderScheduler` projects each user's `preferred_local_hour` through
their timezone, so a user with `preferred_local_hour=20` in
`Europe/Istanbul` gets evaluated at 17:00 UTC.

## APNs configuration

Required environment variables (see `.env.example`):

```
APNS_ENABLED=true
APNS_ENVIRONMENT=production           # production | development
APNS_TEAM_ID=ABCDE12345
APNS_KEY_ID=XYZ123ABCD
APNS_KEY_PATH=/run/secrets/apns_auth_key.p8
APNS_TOPIC=com.echo.app                # iOS bundle id
```

The `.p8` key is mounted as a docker secret; never commit it.
`ApnsConfig` is conditional on `APNS_ENABLED=true`, so CI / local dev can
run with the value `false` and exercise the rest of the pipeline (deliveries
will record `SKIPPED_NO_TOKEN` if no token, otherwise the sender returns a
rejected `APNS_DISABLED` result).

`PushToken.environment` is stored per token. A backend running in
`production` mode refuses to send to `SANDBOX` tokens (and vice-versa) with
the `APNS_ENV_MISMATCH` error code; that's the right call — sending sandbox
payloads to production tokens just produces `BadDeviceToken` errors and
churns tokens.

## Privacy / KVKK

- The APNs payload only ever carries:
  - `aps.alert.title` / `aps.alert.body` — generic templated strings
  - `aps.sound` / `aps.thread-id`
  - Routing keys: `target_type`, `target_id`, `group_key`, `category`,
    `notification_id`, `type`
- Journal text, mood scores, AI insight detail, and coach session content
  are NEVER serialized into a push payload. See
  `ApnsPayloadPrivacyTest` for the wire-level guarantee.
- The lock-screen text comes from `NotificationTemplateService`. The actual
  digest / AI text stays in-app and is loaded after the user taps the push.
- Push tokens that have been inactive for ≥ 90 days are deleted by
  `NotificationMaintenanceScheduler`.

## Observability

Micrometer metrics emitted by `PushDeliveryService`:

| Metric                                | Tags                                | Meaning                                |
| ------------------------------------- | ----------------------------------- | -------------------------------------- |
| `notifications.sent_total`            | `category`                          | APNs accepted the request              |
| `notifications.failed_total`          | `category`, `reason`                | Transport error or rejection           |
| `notifications.skipped_total`         | `category`, `reason`                | Disabled / quiet / no-token / rate-limited |
| `notifications.apns_request_duration` | `category`, `outcome`               | HTTP/2 round-trip duration             |

## Admin debug

`POST /api/v1/admin/notifications/test` (ROLE_ADMIN) enqueues a
SYSTEM_ANNOUNCEMENT push to the caller's own active tokens — useful for
end-to-end smoke tests in TestFlight and during APNs key rotation.

## Tables

- `notifications` — in-app feed rows (Phase 1)
- `push_tokens` — per-device APNs tokens (Phase 1, extended in V31)
- `notification_deliveries` — one row per (notification, token) attempt (Phase 1)
- `notification_preferences` — per-user toggle bundle + preferred hour + timezone (Phase 2)
- `notification_reminder_log` — `(user, reminder_type, local_date)` UNIQUE so the
  hourly scheduler can't double-send (Phase 2)

## End-to-end test plan

1. Production build: `APNS_ENABLED=true`, .p8 mounted.
2. New iOS install → first journal upload → soft prompt → grant permission.
3. Backend logs: `push_tokens` row inserted with the right `environment`.
4. From a second account, like the first user's community post.
5. iPhone lock screen shows the push within ~2s.
6. Tap → app opens to community post.
7. Settings → Notifications → toggle "Community notifications" off.
8. Repeat the like → in-app row is created, `notification_deliveries.status`
   is `SKIPPED_DISABLED`, no lock-screen push.
9. Logout → next push to that user gets `Unregistered`, token deactivated.
