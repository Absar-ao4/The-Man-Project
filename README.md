# The Man Project

A fitness app: workouts, nutrition, progress, a social side and a few AI helpers. Android (Kotlin, Jetpack Compose) with a Spring Boot backend and PostgreSQL. Everything the user writes is saved on the phone first and synced in the background, so the app works offline.

> **Status: all 15 phases are written, and none of it has been compiled, run or tested.** The machine it was written on had no JDK, Gradle, Docker or database, so every file, test and script in this repository is unverified. Expect a first build to need a round of fixes: missing imports, a wrong signature, a migration typo. The tests are written to be run, not yet passing. `DECISIONS.md` records every choice and assumption phase by phase, and what each phase did not build.

```
/backend   Spring Boot 3, Java 21, Gradle Kotlin DSL: API, sync, optimiser, AI calls
/android   Android app (Kotlin, Compose, Material 3, Hilt, Room, WorkManager)
/ops       Backup, restore and a restore drill (PostgreSQL), plus a compose service for nightly backups
```

## What is in it

| Tab or place | What you get |
|---|---|
| Home | Calorie ring, macros, water glasses, daily goals, the week, level and XP, weekly league rank, weight trend, quick actions |
| Workout | Exercise library, plans, routines, a workout recorder with rest timer and PR detection, Lock-in mode (hardcore programs), a plan generator, progressive overload recommendations |
| Food | Food log with USDA and barcode search, saved meals, photo scan (AI), budget diet planner (OR-Tools), AI meal ideas for what is left of the day |
| Social | Friends by username, invite code or QR, weekly leagues, duels, a feed, privacy settings, anti-cheat for lifts |
| Profile | Targets, XP and badges, progress and body check (weight trend, BMI, waist to height, body fat), reminders, articles, PDF and CSV reports, data export, account deletion, sync status |

## Backend setup

Requirements: JDK 21, a PostgreSQL 16 database (local install, hosted, or `docker compose`).

1. Copy the env template and fill it in:
   ```bash
   cp .env.example .env
   ```
   | Variable | Purpose |
   |---|---|
   | `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/manproject` |
   | `DB_USER`, `DB_PASSWORD` | Database credentials |
   | `JWT_SECRET` | 32+ characters; the app refuses to start without it |
   | `ANTHROPIC_API_KEY` | Optional. Photo scans, plan explanations, the AI step of the diet planner, AI meal ideas and the written weekly summary. Without it the AI features answer 503 or fall back to simple rules, and nothing else is affected |
   | `USDA_API_KEY` | Optional. Live food search (free at fdc.nal.usda.gov); without it search serves your own foods and the cache |
   | `API_DOCS_ENABLED` | `false` hides Swagger and the API description (default `true`) |
   | `BACKUP_RETENTION_DAYS` | Days of nightly dumps to keep (default 14) |

   The app reads `.env` itself when started from `backend/` or the repo root. Real environment variables win over `.env`.

2. First time only, generate the Gradle wrapper (needs a local Gradle install):
   ```bash
   cd backend && gradle wrapper --gradle-version 8.14
   ```
3. Run:
   ```bash
   cd backend && ./gradlew bootRun
   ```
   Flyway applies the migrations on startup. API docs: http://localhost:8080/swagger-ui . Health: `/actuator/health`.

### With Docker

```bash
cp .env.example .env   # set DB_PASSWORD and JWT_SECRET
docker compose up --build
```
Starts PostgreSQL 16 (reachable from this machine only) and the backend on port 8080. The Android emulator reaches the host at `http://10.0.2.2:8080`. For nightly backups add `-f ops/docker-compose.backup.yml` (see `ops/README.md`).

### Tests

```bash
cd backend && ./gradlew test
```
Unit tests need nothing else. Integration tests start PostgreSQL through Testcontainers, so **Docker must be running**. `bash ops/test_backup.sh` checks the backup script without a database.

## Android setup

Requirements: Android Studio Meerkat Feature Drop (2024.3.2) or newer. The project uses Android Gradle Plugin 8.9.2; if your Studio is newer you may raise `agp` in `android/gradle/libs.versions.toml`. You also need an SDK that includes API 36, and JDK 17 or newer (the bundled JBR is fine).

1. Open the `android/` folder in Android Studio and let Gradle sync. `android/gradle/wrapper/gradle-wrapper.properties` pins Gradle 8.14.3 (the Android Gradle Plugin 8.9 needs 8.11.1 or newer). If sync fails with "Minimum supported Gradle version", open that file and make sure `distributionUrl` ends in `gradle-8.14.3-bin.zip`, then sync again.
2. Copy `android/local.properties.example` to `android/local.properties` and set `api.baseUrl`:
   - emulator: `http://10.0.2.2:8080/` (the default)
   - physical device on the same Wi-Fi: `http://<your PC's LAN IP>:8080/`
   - a release build **must** use an `https://` address: `assembleRelease` and `bundleRelease` stop with a message otherwise.
3. Optional but recommended: add the font files listed in `app/src/main/java/com/example/fitapp/core/designsystem/Type.kt` to `app/src/main/res/font/` (Space Grotesk and DM Sans, both free on Google Fonts). Without them the system font is used.
4. Start the backend (see above), then run the `app` configuration.

Tests:
```bash
cd android
./gradlew testDebugUnitTest          # JVM unit tests
./gradlew connectedDebugAndroidTest  # Room, Compose UI, PDF and accessibility tests, needs an emulator or device
```
The accessibility test (`AccessibilityTest`) uses `androidx.compose.ui:ui-test-junit4-accessibility`, which needs a Compose BOM from 1.8 on; if yours is older, update the BOM or delete that dependency and the test.

After pulling a phase that changes the database, **clear the app's data**: the Room schema is still version 1 and is edited in place.

## Using the features

Guest mode: the login screen has a "Continue as guest" button. A guest sees the five tabs as a preview (what each feature does, an example day on Home) but every feature that saves data or needs the server is a locked card that asks the person to sign in; only the theme setting works. Guest mode never touches the database or the backend, so the app can be looked at before any backend is set up. It lives in memory, so the next launch starts at the login screen again.

Sync: the app keeps working offline and uploads in the background (30 seconds after a change, when it comes to the foreground, and every 6 hours). The Profile tab shows the sync state and has "Sync now" and "Restore from cloud".

Lock-in mode (Workout tab) is a full-screen focus mode. Do Not Disturb and screen pinning are optional and the user decides each time: Android asks for the "Do Not Disturb access" and shows its own pin confirmation. Leaving always works by holding the exit button for two seconds. The two Lottie animations in `res/raw` can be replaced by your own files.

Meal photo scan (Food tab, "Scan a photo" on a meal): the photo is taken with the system camera app or picked from the gallery, shrunk on the phone (1024 px, JPEG 80) and sent to the backend, which asks the AI model for the foods, matches them against USDA and returns editable items. Nothing is logged until the user confirms the review screen. To try it you need `ANTHROPIC_API_KEY` (and `USDA_API_KEY` for database matches, otherwise every item is an AI estimate) on the backend. To make someone an admin for the admin endpoints, run `update users set role = 'ADMIN' where email = '...'` and log in again (the role is in the token). Check the model prices in `backend/src/main/resources/application.yml` (`app.ai.prices`) against Anthropic's pricing page; they decide how fast the daily budget is used.

Budget diet planner (Food tab): needs daily targets in the profile. The optimiser runs inside the backend with Google OR-Tools (the Gradle dependency bundles the native library, nothing to install). Prices are in `V10__diet_planner.sql` (INR and USD); to add a country, insert one row per food for its currency. The AI step that writes meals and recipes is optional: without `ANTHROPIC_API_KEY` the foods are split into meals by simple rules. Limit per user per day: `app.ai.daily-limits.diet-arrange` in `application.yml`.

Social (Social tab): add friends by username, invite code or QR (scanned with Google's code scanner, no CAMERA permission). The league, duels and feed need friends, so to try them use two accounts. To review sets that anti-cheat flagged (a lift more than 15 percent above your recent best, or implausibly heavy for your level), make yourself admin as described above and call `GET /api/admin/flags`, then `POST /api/admin/flags/{setId}/confirm` or `reject` (see Swagger). A flagged set earns no XP and is not a PR until confirmed.

Dashboard and progress (Home and Progress): the Progress screen (from Home) has the weekly and monthly summary, charts and the body check. Weigh-ins and tape measurements (Home, Log weight, or the Body screen) work offline and sync later; weights show in pounds and inches when the profile units say so. The summary, league and achievements need the backend, and the last answer is kept for offline. Charts are drawn in Compose (no chart library). Add your height, and sex for the body fat estimate, in the profile to get BMI and body fat.

Reminders and notifications (Profile, Reminders): workout, water, meal, weigh-in, goal and streak reminders are alarms on the phone, so they work offline; only your choices are synced. The first time you switch one on, Android asks for notification permission. For reminders on the minute, allow Alarms and reminders for the app in the system settings (the screen tells you when that is needed). Friend requests, duel invitations and duel results become notifications through an hourly background check (there is no push service in this project, so they can be late), and XP, level ups and badges earned by a background sync are announced too. Each kind can be switched off.

Meal ideas, recommendations, articles and reports: Food tab, Meal ideas (needs daily targets), Workout tab, Recommendations, Profile, Articles and Reports and data. Meal ideas and the written weekly summary use the AI when `ANTHROPIC_API_KEY` is set and fall back to simple rules without it; the daily tries are `app.ai.daily-limits.meal-suggestions` and `weekly-summary` in `application.yml`. Reports (PDF and CSV) are made on the phone and shared with the system share sheet. Deleting your account asks for your password, removes everything on the server and on the phone, and cannot be undone.

## Privacy, security and accessibility in short

- **Data:** the app never requests location. Permissions: INTERNET, notifications and exact alarms (rest timer and reminders), restart notification (to set reminders again after a reboot), vibrate, and the notification policy permission that lets you grant Do Not Disturb access for Lock-in. The camera is used through the system camera app, so there is no CAMERA permission. Photos of meals are never stored. Cloud backup and device transfer are switched off for the app's data. Users can export everything and delete their account.
- **Server:** user id comes from the JWT only and every query is scoped to it; friend-visible data goes through privacy settings and blocks (a block looks like "not found"). Passwords are hashed (BCrypt). Rate limits per client address on login and registration, user search, invite code redeeming, deleting an account (it takes a password) and the paid or heavy endpoints (AI, diet plans, export); AI calls also have per user daily limits and one global daily budget. Errors are RFC 7807 with a stable `code` and never carry stack traces. HTTPS is expected in front of the backend; the app only allows plain HTTP to the emulator host and `localhost`.
- **Accessibility:** touch targets of at least 48 dp, labels on every icon button, headings for TalkBack, text sizes in sp (font scaling), a contrast checked colour set with a light, dark and Lock-in theme, an in-app and system "reduce motion", and charts that describe themselves in words. `AccessibilityTest` runs the Android accessibility checks on the main screens.
## API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register` `/login` `/refresh` `/logout` | Public, rate limited |
| GET / PUT | `/api/me/profile` | |
| GET / PUT | `/api/me/targets` | Latest effective targets |
| POST | `/api/me/targets/suggest` | Mifflin-St Jeor suggestion, not saved |
| GET | `/api/exercises?muscle=&equipment=&q=&page=&size=` | 90 seeded exercises plus your own |
| POST / PUT / DELETE | `/api/exercises[/{id}]` | Custom exercises only |
| GET | `/api/plans?level=&split=`, `/api/plans/{id}` | 12 seeded plans |
| GET / POST / PUT / DELETE | `/api/routines[/{id}]` | POST with an existing id updates |
| GET / POST / PUT / DELETE | `/api/sessions[/{id}]` | Server sets PR flags |
| GET | `/api/records`, `/api/records/{exerciseId}/history` | Derived personal records |
| GET | `/api/foods/search?q=`, `/api/foods/barcode/{code}` | USDA proxy (cached), Open Food Facts |
| GET / POST / PUT / DELETE | `/api/meals/items?date=` (or `from`/`to`) | The food log |
| GET / POST / PUT / DELETE | `/api/custom-foods`, `/api/custom-meals` | Your own foods and saved meals |
| GET / POST / DELETE | `/api/favorites` | Favorite foods |
| GET / PUT | `/api/water?date=` | Glasses per day |
| GET | `/api/nutrition/summary?from=&to=` | Daily totals, averages, targets |
| POST | `/api/sync/push` | One entity per call, up to 200 rows, last write wins on `updatedAt` |
| GET | `/api/sync/pull?since=&limit=` | Changes since the last `serverTime`; omit `since` to restore everything |
| GET | `/api/me/stats` | Level, title, XP bar, streaks, XP today (read only, decided by the server) |
| GET | `/api/me/xp?from=&to=&limit=` | The XP ledger, newest first (default last 30 days) |
| GET | `/api/badges`, `/api/me/badges` | All badges, and the ones you earned |
| POST | `/api/plans/generate` | Rules-based weekly plan from the questionnaire (deterministic, nothing saved but your answers) |
| GET | `/api/plans/generate/inputs` | Your last questionnaire answers (404 if none) |
| POST | `/api/plans/generate/explain` | Optional AI explanation with tweak ideas, limited per day; needs `ANTHROPIC_API_KEY` |
| GET | `/api/lockin/programs`, `/api/lockin/programs/{id}` | The 8 Lock-in programs (days only on the detail call); `available` depends on your fitness level |
| POST | `/api/nutrition/photo-scan` | Meal photo (base64 JPEG, PNG or WebP, at most 4 MB) to editable food items with nutrition per 100 g; limited per user per day; needs `ANTHROPIC_API_KEY`; the image is never stored |
| GET | `/api/users/search?username=` | Username prefix search (2+ characters, rate limited); shows only username, name and level |
| POST / GET | `/api/friends/requests`, `/api/friends/requests/{id}/accept\|decline\|cancel` | Friend requests (`GET ?direction=incoming\|outgoing`) |
| GET / DELETE | `/api/friends`, `/api/friends/{userId}` | Friends list, remove a friend |
| POST / GET / DELETE | `/api/blocks`, `/api/blocks/{userId}` | Block, list, unblock (a block looks like "not found" to the other person) |
| GET / POST | `/api/invite-codes`, `/api/invite-codes/{code}/redeem` | Your invite code (QR content `manproject://friend/CODE`), a new code, and redeeming a friend's code |
| GET / PUT | `/api/me/privacy` | Feed, PR weights and leaderboard visibility for friends |
| GET | `/api/leaderboard?metric=xp\|workouts\|streak\|consistency\|improvement&week=2026-W11` | Weekly league among friends, ranked by effort, never by weights |
| POST / GET | `/api/duels`, `/api/duels/{id}/accept\|decline\|cancel` | 7 day duels (workouts, XP or Lock-in sessions); winner gets 100 XP and a badge |
| GET | `/api/feed?cursor=&limit=` | Friends' activity, newest first, respecting their privacy settings |
| GET / POST | `/api/admin/flags[/{setId}/confirm\|reject]` | Admin only: sets held back by anti-cheat |
| GET | `/api/diet/foods?dietType=&currency=` | The price list in your currency (INR and USD are seeded), with your own prices marked |
| PUT / DELETE | `/api/diet/prices/{foodId}` | Set or clear your own price for a food |
| POST / GET / DELETE | `/api/diet/plans[/{id}]` | Build and save a plan (OR-Tools minimises cost for your targets), list, read, delete |
| POST | `/api/diet/plans/{id}/log-meal` | Add one planned meal to the food log |
| GET | `/api/diet/cheapest-protein?dietType=&currency=` | Foods ranked by protein per unit of currency |
| GET / POST / PUT / DELETE | `/api/weights[/{id}]` | Weigh-ins, one per day (logging the day again replaces it), 20 to 500 kg; the app uses sync instead |
| GET / POST / PUT / DELETE | `/api/measurements[/{id}]` | Tape measurements (arms, chest, waist, hips, thighs, neck, shoulders), one per part per day, 5 to 300 cm |
| GET | `/api/body/summary` | Trend weight, BMI (from the trend, with its caveat), waist to height, Navy body fat, and notes on what is missing |
| GET | `/api/body/trend?period=week\|month\|3months\|year` | Weigh-ins with the 7 day trend as of each day |
| GET | `/api/progress/summary?period=week\|month` | This week or month so far against the one before, in your time zone |
| GET | `/api/progress/activity?days=` | Workouts, sets and XP per day (days with something only), up to 365 days |
| GET | `/api/progress/achievements` | Workout count, streak and weight milestones, each earned once |
| GET | `/api/me/reminders` | The reminder choices you changed (a type not listed is on its default, off) |
| PUT / DELETE | `/api/me/reminders/{type}` | Set or reset one reminder (WORKOUT, WATER, MEAL_BREAKFAST, MEAL_LUNCH, MEAL_DINNER, WEIGH_IN, DAILY_GOAL, STREAK); the phone schedules the alarms |
| POST | `/api/ai/meal-suggestions` | Meal ideas for what is left of today's targets (AI picks from our food list when configured, simple rules otherwise; numbers always come from the list); limited per user per day |
| GET | `/api/ai/workout-recommendations` | Progressive overload advice per exercise (+2.5 kg upper, +5 kg lower, 10 percent deload) and a weekly muscle balance summary (AI written when configured) |
| GET | `/api/articles?category=`, `/api/articles/{slug}` | The 10 seeded fitness articles (Markdown) |
| GET | `/api/me/export` | Everything the account holds as one JSON download (never passwords or tokens) |
| DELETE | `/api/me` | Permanently deletes the account and all its data; needs `{"password": "..."}` |
| GET / PUT | `/api/admin/photo-scan/config` | Admin only: on/off, free and premium daily limits, global daily budget (USD), primary and fallback model, escalation threshold |
| GET | `/api/admin/photo-scan/stats` | Admin only: today's AI spend against the budget, scans, fallbacks, average confidence, last 7 days |

XP is awarded by the server after a sync push and returned in the push response (`celebrations`: new XP, badges, level change). The rules are rows in the `xp_rules` table, so they can be tuned without a release.

The Android app writes everything the user creates (profile, targets, exercises, routines, workouts, meal log, foods, saved meals, water) through the two sync endpoints; the per-resource write endpoints above remain for API clients and tests. Synced entities: `profiles`, `daily_targets`, `exercises`, `routines`, `workout_sessions`, `custom_foods`, `favorite_foods`, `custom_meals`, `meal_items`, `water_logs`, `weight_entries`, `body_measurements`, `reminder_preferences`. Protocol details are in `DECISIONS.md` (Phase 5).

Set `USDA_API_KEY` (free at fdc.nal.usda.gov) for live food search; without it search serves your own foods and the cache.

Regenerate the seed after editing exercises or plans: `python ops/seed/generate_workout_seed.py`.

Errors are RFC 7807 with a stable `code` (`VALIDATION_ERROR`, `NOT_FOUND`, `UNAUTHORIZED`, `FORBIDDEN`, `LIMIT_REACHED`, `CONFLICT`, ...) and field-level `errors` for validation failures.

## Deployment notes

A checklist before real users:

1. **Build and run** the backend from `backend/Dockerfile` (a non-root user, graceful shutdown, health at `/actuator/health`), with the environment variables above. Never commit `.env`.
2. **TLS in front.** Terminate HTTPS at a reverse proxy or the platform's load balancer and forward `X-Forwarded-*` headers (the app trusts them, so per-client rate limits see the real address). Build the Android release with `api.baseUrl=https://...`.
3. **Secrets.** A long random `JWT_SECRET` (`openssl rand -base64 48`). Changing it signs everyone out. Set `API_DOCS_ENABLED=false` if the API description should not be public.
4. **Database.** PostgreSQL 16 with a user that owns only this database, not reachable from the internet. Flyway applies the migrations on start.
5. **Backups.** Turn on the nightly dump (`ops/README.md`), copy it off the machine, and run `ops/restore_drill.sh` after the first night and every month.
6. **AI costs.** Check `app.ai.prices` against Anthropic's pricing page, and look at the per user limits (`app.ai.daily-limits`) and the global daily budget (`GET /api/admin/photo-scan/config`, admin only) before enabling `ANTHROPIC_API_KEY`.
7. **Content.** The seeded articles are general information and the diet and food prices are rough; have someone qualified review the articles, and update prices for your market (`V10__diet_planner.sql`).
8. **Open Food Facts.** Put a real contact in `app.openfoodfacts.user-agent`.
9. **Before the first release,** run everything once: `./gradlew test` in both projects, a restore drill, and a pass through the app on a real phone with TalkBack on. Nothing here has been run yet.

Scaling note: sync, feed and leagues are plain SQL against one PostgreSQL database; the leaderboard is computed live per request for the user's friends (at most 200), which is fine at this size and is the first thing to cache if it grows.