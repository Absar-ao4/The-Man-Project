-- Phase 14: fitness articles (global, read only for clients) and the cache of the weekly AI training summary.

CREATE TABLE articles (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug          VARCHAR(80)  NOT NULL,
    title         VARCHAR(160) NOT NULL,
    category      VARCHAR(20)  NOT NULL,
    summary       VARCHAR(300) NOT NULL,
    body_markdown TEXT         NOT NULL,
    read_minutes  INT          NOT NULL CHECK (read_minutes BETWEEN 1 AND 60),
    published_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT articles_category_chk CHECK (category IN ('TRAINING', 'NUTRITION', 'RECOVERY', 'LOCKIN_SAFETY'))
);
CREATE UNIQUE INDEX articles_slug_uq ON articles (slug);
CREATE INDEX articles_category_published_idx ON articles (category, published_at DESC);

-- One cached AI summary per user and training week. It is reused until the week's set counts change, so opening the
-- recommendations screen again does not pay for the same answer twice.
CREATE TABLE weekly_summaries (
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    week_start  DATE         NOT NULL,
    counts_hash VARCHAR(64)  NOT NULL,
    summary     VARCHAR(600) NOT NULL,
    tips        TEXT[]       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, week_start)
);


-- A meal logged from an AI or rules based suggestion is its own source, so it can be told apart from a diet plan meal.
ALTER TABLE meal_items DROP CONSTRAINT meal_items_source_chk;
ALTER TABLE meal_items ADD CONSTRAINT meal_items_source_chk CHECK (source IN
    ('MANUAL', 'SEARCH', 'BARCODE', 'PHOTO_DATABASE', 'PHOTO_ESTIMATE', 'DIET_PLAN', 'SUGGESTION'));

INSERT INTO articles (slug, title, category, summary, body_markdown, read_minutes, published_at) VALUES
('progressive-overload-basics',
 'Progressive overload: the one idea that makes you stronger',
 'TRAINING',
 'Your body adapts to what you ask of it. To keep improving you have to ask for a little more, in small steps.',
 $md$## The idea

Muscles and tendons get stronger when they are asked to do slightly more than they are used to. Do the same workout for months and your body has no reason to change.

## Ways to do more

- Add weight. This is the usual one, and the app suggests it for you.
- Add a rep or two with the same weight.
- Add a set.
- Use better control: a slower lowering phase, a clean pause at the bottom.

## A simple rule

Pick a rep range, for example 8 to 12. Start with a weight you can lift for 8 reps on every set. Each session, try to get one more rep on each set. When you reach 12 reps on every set, two sessions in a row, add a small amount of weight (2.5 kg for upper body lifts, 5 kg for lower body lifts) and start again at 8.

## When it stalls

Everyone stalls. Check sleep, food and stress first. If you miss the bottom of your rep range three sessions in a row, take about 10 percent off the bar and build back up. That is not failure, it is how long term progress works.$md$,
 3, TIMESTAMPTZ '2026-01-05 08:00:00+00'),
('how-many-sets-per-week',
 'How many sets per muscle each week?',
 'TRAINING',
 'More is not always better. Around 10 to 20 hard sets per muscle per week works for most people.',
 $md$## A useful range

Studies on muscle growth point to roughly **10 to 20 hard sets per muscle group per week** for most lifters. Beginners often grow well on the low end of that range or even below it.

## What counts as a hard set

A set that is close to failure, usually with 1 to 3 reps left in the tank, and done with good form. Warm up sets do not count.

## Spread it out

Training a muscle twice a week and splitting the sets between sessions tends to work better than one huge session, because the quality of each set stays high.

## How to use this

- Count your sets per muscle for a week. The progress screen and the weekly summary in the app show them.
- If a muscle is far below the others and you want it to grow, add two to four sets a week.
- If you are always sore, tired and not getting stronger, you may be doing too much. Reduce before you add.

Start modest, add slowly, and let the results tell you what you need.$md$,
 3, TIMESTAMPTZ '2026-01-12 08:00:00+00'),
('warm-up-that-works',
 'A warm-up that actually helps',
 'TRAINING',
 'Five to ten minutes of the right preparation makes your first heavy set safer and better.',
 $md$## What a warm-up is for

It raises your temperature, wakes up the muscles you are about to use and lets you practise the movement before the weight is heavy. It is not meant to tire you out.

## A simple structure

1. **Two to five minutes of easy cardio**, such as a brisk walk or a bike, until you feel slightly warm.
2. **A few joint movements** for the areas you will train: arm circles, hip circles, bodyweight squats.
3. **Ramp-up sets** for your first lift. For a 100 kg target, for example: 10 reps at 40 kg, 5 reps at 60 kg, 3 reps at 80 kg, then your working sets.

## Tips

- Ramp-up sets use fewer reps as the weight goes up, so you are not tired when the real sets start.
- Mark them as warm-up sets in the app. They are not counted for records, volume or XP.
- Hold stretches for after the session. Long static stretches before heavy lifting can feel flat.

If something feels sharp or wrong during a warm-up, stop and do not push through it.$md$,
 3, TIMESTAMPTZ '2026-01-19 08:00:00+00'),
('protein-how-much',
 'How much protein do you need?',
 'NUTRITION',
 'For people who lift, about 1.6 to 2.2 grams of protein per kilogram of body weight a day is a good target.',
 $md$## The short answer

If you train with weights and want to build or keep muscle, aim for about **1.6 to 2.2 grams of protein per kilogram of body weight per day**. For an 80 kg person that is roughly 130 to 175 grams.

## Spreading it out

Three to five meals with 25 to 40 grams of protein each is a practical pattern. Total daily protein matters most, timing matters less.

## Where it comes from

- Meat, fish, eggs and dairy
- Tofu, tempeh, soya chunks, lentils, chickpeas and other beans
- Protein powder, if you struggle to reach your target with food

## On a budget

Eggs, lentils, chickpeas, soya chunks, curd and chicken legs tend to give the most protein per unit of money. The budget diet planner in the app ranks foods for you.

## A note

People with kidney disease or other medical conditions should ask their doctor before raising protein. This article is general information, not medical advice.$md$,
 3, TIMESTAMPTZ '2026-01-26 08:00:00+00'),
('eating-for-fat-loss',
 'Eating for fat loss without feeling miserable',
 'NUTRITION',
 'A modest calorie deficit, enough protein and food you enjoy beat crash diets every time.',
 $md$## Keep the deficit modest

A deficit of about **300 to 500 kcal per day** below what you burn usually means losing around 0.25 to 0.75 kg per week. Bigger deficits cost muscle, energy and patience.

## Protect your muscle

- Keep lifting, and try to keep your weights up.
- Keep protein high, around 1.6 to 2.2 g per kg of body weight.
- Do not cut calories below a level you can sustain.

## Make it livable

- Fill your plate with vegetables, fruit and other foods that are filling for few calories.
- Keep foods you love in your plan in sensible portions instead of banning them.
- Plan for weekends and events, since one big meal does not undo a week.

## Judge by the trend

Daily weight jumps by a kilogram or more from water, salt and food in your gut. Look at the 7 day trend line in the app, not a single morning. If the trend has been flat for three weeks, reduce intake a little or add some walking.

If you have a history of disordered eating, talk to a professional before dieting.$md$,
 3, TIMESTAMPTZ '2026-02-02 08:00:00+00'),
('eating-to-build-muscle',
 'Eating to build muscle',
 'NUTRITION',
 'A small surplus, plenty of protein and patience. Gaining slowly keeps most of the gain as muscle.',
 $md$## A small surplus

To gain weight you need to eat more than you burn. A surplus of **200 to 300 kcal per day** is enough for most people. Gaining about 0.25 to 0.5 percent of your body weight per week keeps fat gain low.

## What matters most

1. **Train hard and progressively.** Food only helps if there is a reason to grow.
2. **Protein**, about 1.6 to 2.2 g per kg of body weight.
3. **Enough carbohydrate** around training so you can lift well.
4. **Sleep.**

## If you struggle to eat enough

- Add energy-dense foods: nuts, peanut butter, oats with milk, olive oil, dried fruit.
- Drink some of your calories, for example a smoothie or milk.
- Eat a little more at each meal rather than adding a huge one.

## Track the trend

If your 7 day trend weight is not going up after three weeks, add about 150 kcal a day. If it is climbing faster than planned, trim a little.$md$,
 3, TIMESTAMPTZ '2026-02-09 08:00:00+00'),
('sleep-and-training',
 'Sleep is part of the program',
 'RECOVERY',
 'Most of your recovery happens while you sleep. Seven to nine hours is a good target for most adults.',
 $md$## Why it matters

Hard training is the signal, but recovery is where you actually get stronger. Poor sleep makes workouts feel harder, hurts your focus and makes it harder to control your appetite.

## What to aim for

Most adults do best with **7 to 9 hours** a night. Some people need a little more when they train hard.

## Habits that help

- Keep a regular bedtime and wake time, even on weekends.
- Get daylight in the morning and keep the evening dim.
- Stop caffeine about 8 hours before bed.
- Keep the room cool, dark and quiet.
- Avoid a heavy meal or a hard session right before bed if it keeps you awake.

## A bad night

One short night is not a disaster. Train as planned if you feel fine, or lower the intensity if you do not. Several bad nights in a row are a sign to lighten your week.

If you snore loudly, stop breathing at night or feel exhausted despite long sleep, speak to a doctor.$md$,
 3, TIMESTAMPTZ '2026-02-16 08:00:00+00'),
('deloads-and-rest-days',
 'Deloads and rest days',
 'RECOVERY',
 'Rest is not lost time. Planned easy weeks and days off keep you progressing for years.',
 $md$## Rest days

Muscles need roughly 48 hours before they are trained hard again. Most plans give each muscle two sessions a week with at least a day between them. One or two full rest days a week is normal, and a walk on those days is fine.

## What a deload is

A deload is a short period, usually a week, where you train with **about 10 percent less weight** or about half the sets. You keep the habit and the movement practice, but your body gets a chance to catch up.

## When to deload

- You have missed the bottom of your rep range for three sessions in a row on the same lift (the app suggests this).
- Your joints ache, your motivation is low and your sleep is poor.
- You have trained hard for about 6 to 10 weeks without a break.

## Active recovery

Walking, easy cycling and mobility work help many people feel better. They should leave you fresher, not more tired.

Listen to your body, and see a professional about pain that does not go away.$md$,
 3, TIMESTAMPTZ '2026-02-23 08:00:00+00'),
('lockin-mode-safely',
 'Lock-in mode: how to use it safely',
 'LOCKIN_SAFETY',
 'Lock-in mode is a hard, focused session. It is meant to be demanding, not reckless.',
 $md$## What it is for

Lock-in mode is a full screen focus mode with a timer and a streak counter. It helps you commit to a session and stay off your phone. It does **not** mean you should ignore how you feel.

## Before you start

- Read the program and pick one that matches your level. Programs marked as too advanced are locked on purpose.
- Warm up properly.
- Eat and drink something beforehand, and keep water within reach.
- Make sure you can leave at any moment. In the app you can always hold the exit button for two seconds.

## During the session

- Keep your form. Dropping form to protect a combo is not worth an injury.
- Take the rest the program gives you.
- If you feel dizzy, sick, short of breath in a worrying way or feel pain, **stop**. No streak is worth it.

## Do Not Disturb and screen pinning

Both are optional. They silence interruptions, but you should still be able to get help in an emergency. You can turn them off in the Lock-in settings.

If you have a health condition, check with your doctor before intense training.$md$,
 3, TIMESTAMPTZ '2026-03-02 08:00:00+00'),
('know-when-to-stop',
 'Know when to stop: pain, dizziness and other red flags',
 'LOCKIN_SAFETY',
 'Sore muscles are normal. Sharp pain, chest pain and dizziness are not. Here is how to tell the difference.',
 $md$## Normal

- Muscle burn during a set
- Soreness for a day or two after a hard session (it can peak around the second day)
- Feeling tired after a big workout

## Stop and rest

- **Sharp, stabbing or pinching pain**, especially in a joint
- Pain that gets worse as you continue
- Numbness or tingling
- Feeling faint or very dizzy
- A pop or snap followed by pain or swelling

## Get medical help straight away

- Chest pain or pressure
- Trouble breathing that does not settle with rest
- Fainting
- Pain that spreads to the arm, neck or jaw
- Dark brown urine and severe muscle pain after extreme exercise

## Afterwards

Sore muscle pain should ease within a few days. Pain that lasts more than a week, wakes you at night or limits normal movement is worth showing to a doctor or physiotherapist.

This is general information and not a substitute for medical advice. When in doubt, stop and ask.$md$,
 3, TIMESTAMPTZ '2026-03-09 08:00:00+00');
