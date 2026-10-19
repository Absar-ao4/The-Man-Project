-- Phase 7: Lock-in mode programs. A program is a few training days; each day lists exercises with
-- sets, rep range, rest, RPE target, the method used (straight sets, drop sets, supersets, EMOM)
-- and technique cues. Exercises are referenced by their exact library name; the API resolves them
-- to ids. min_level is the lowest FITNESS level (BEGINNER, INTERMEDIATE, ADVANCED) allowed to start it.

CREATE TABLE lockin_programs (
    id               UUID PRIMARY KEY,
    name             VARCHAR(80)  NOT NULL UNIQUE,
    description      VARCHAR(400) NOT NULL,
    instructions     TEXT         NOT NULL,
    min_level        VARCHAR(20)  NOT NULL,
    duration_minutes INTEGER      NOT NULL,
    sort_order       INTEGER      NOT NULL,
    days             JSONB        NOT NULL,
    CONSTRAINT lockin_programs_level_chk CHECK (min_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'))
);

INSERT INTO lockin_programs (id, name, description, instructions, min_level, duration_minutes, sort_order, days) VALUES

-- ---------------------------------------------------------------------------------------------
('a1000000-0000-4000-8000-000000000001',
 'German Volume Training',
 'Ten sets of ten on one lift per muscle group. Brutal volume, short rest, same weight every set.',
 'Pick a weight you could lift about 20 times, roughly 60% of your max. Do 10 sets of 10 with the same weight and only 60 seconds of rest. The first sets feel easy and the last ones are a fight: that is the point. Only add weight (2.5 kg) once you finish all 10 sets of 10 with clean form. Do the three days in order with at least a day of rest between them.',
 'INTERMEDIATE', 60, 1,
 $json$[
  {"name":"Chest and Back","focus":"10x10 bench and row, then finish with 3x12 accessories","estimatedMinutes":60,"exercises":[
    {"exercise":"Barbell Bench Press","sets":10,"repMin":10,"repMax":10,"restSeconds":60,"rpe":7.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Same weight every set, about 60% of your max.","Lower for two seconds, press hard, never bounce off the chest.","Add 2.5 kg only after a clean 10x10."]},
    {"exercise":"Barbell Row","sets":10,"repMin":10,"repMax":10,"restSeconds":60,"rpe":7.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Flat back, pull the bar to your lower ribs.","Squeeze the shoulder blades together at the top.","If form breaks, drop the weight, not the reps."]},
    {"exercise":"Dumbbell Fly","sets":3,"repMin":12,"repMax":12,"restSeconds":60,"rpe":8,"method":"STRAIGHT","group":null,"drops":0,"cues":["Soft elbows, open wide until you feel the chest stretch.","Control the way down for three seconds."]},
    {"exercise":"Lat Pulldown","sets":3,"repMin":12,"repMax":12,"restSeconds":60,"rpe":8,"method":"STRAIGHT","group":null,"drops":0,"cues":["Pull the elbows down and back, chest up to the bar.","Do not lean back to swing the weight."]}
  ]},
  {"name":"Legs","focus":"10x10 squat and leg curl, then calves","estimatedMinutes":60,"exercises":[
    {"exercise":"Barbell Back Squat","sets":10,"repMin":10,"repMax":10,"restSeconds":90,"rpe":7.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Brace your core before every rep, knees track over the toes.","Hit at least parallel on every set, even the tired ones.","Breathe in at the top, out as you drive up."]},
    {"exercise":"Lying Leg Curl","sets":10,"repMin":10,"repMax":10,"restSeconds":60,"rpe":7.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Hips stay pressed into the pad.","Curl up fast, lower slowly."]},
    {"exercise":"Standing Calf Raise","sets":3,"repMin":15,"repMax":15,"restSeconds":45,"rpe":8,"method":"STRAIGHT","group":null,"drops":0,"cues":["Full stretch at the bottom, pause at the top."]}
  ]},
  {"name":"Shoulders and Arms","focus":"10x10 press and chin-up, then arms","estimatedMinutes":60,"exercises":[
    {"exercise":"Dumbbell Shoulder Press","sets":10,"repMin":10,"repMax":10,"restSeconds":60,"rpe":7.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Ribs down, do not arch the lower back.","Press in a slight arc until the arms are straight."]},
    {"exercise":"Chin-Up","sets":10,"repMin":10,"repMax":10,"restSeconds":60,"rpe":8,"method":"STRAIGHT","group":null,"drops":0,"cues":["Start every rep from a full hang.","Use a band or the assisted machine if you cannot do ten."]},
    {"exercise":"Dumbbell Curl","sets":3,"repMin":12,"repMax":12,"restSeconds":45,"rpe":8,"method":"STRAIGHT","group":null,"drops":0,"cues":["Elbows pinned to your sides."]},
    {"exercise":"Triceps Pushdown","sets":3,"repMin":12,"repMax":12,"restSeconds":45,"rpe":8,"method":"STRAIGHT","group":null,"drops":0,"cues":["Lock the elbows in place, only the forearms move."]}
  ]}
 ]$json$::jsonb),

-- ---------------------------------------------------------------------------------------------
('a1000000-0000-4000-8000-000000000002',
 'Drop-Set Hypertrophy Day',
 'One full-body day where the last set of every exercise is taken past failure with weight drops.',
 'Do your normal working sets, then on the final set of each exercise go to failure, cut the weight by about 20%, and keep going to failure again. Repeat for the listed number of drops without resting between them. Keep the first sets clean and only go to failure on the last one. Rest as listed between exercises.',
 'INTERMEDIATE', 55, 2,
 $json$[
  {"name":"Drop-Set Day","focus":"Upper, legs and arms, last set of each lift is a drop set","estimatedMinutes":55,"exercises":[
    {"exercise":"Incline Dumbbell Press","sets":3,"repMin":8,"repMax":10,"restSeconds":90,"rpe":9,"method":"DROP_SET","group":null,"drops":2,"cues":["Sets 1 and 2 stop one rep short of failure.","On the last set drop 20% twice without rest.","Shoulder blades pinched back on the bench."]},
    {"exercise":"Lat Pulldown","sets":3,"repMin":10,"repMax":12,"restSeconds":90,"rpe":9,"method":"DROP_SET","group":null,"drops":2,"cues":["Pull with the elbows, not the hands.","On the drops keep the same strict form, do not swing."]},
    {"exercise":"Dumbbell Lateral Raise","sets":3,"repMin":12,"repMax":15,"restSeconds":60,"rpe":9,"method":"DROP_SET","group":null,"drops":2,"cues":["Lead with the elbows, stop at shoulder height.","Drop to lighter dumbbells quickly: grab them before the set starts."]},
    {"exercise":"Leg Press","sets":3,"repMin":12,"repMax":15,"restSeconds":90,"rpe":9,"method":"DROP_SET","group":null,"drops":2,"cues":["Do not lock out the knees at the top.","Pull the pin and strip plates fast between drops."]},
    {"exercise":"Cable Curl","sets":2,"repMin":10,"repMax":12,"restSeconds":60,"rpe":9.5,"method":"DROP_SET","group":null,"drops":2,"cues":["Constant tension, no resting at the bottom."]},
    {"exercise":"Triceps Pushdown","sets":2,"repMin":10,"repMax":12,"restSeconds":60,"rpe":9.5,"method":"DROP_SET","group":null,"drops":2,"cues":["Elbows stay locked at your sides, squeeze at the bottom."]}
  ]}
 ]$json$::jsonb),

-- ---------------------------------------------------------------------------------------------
('a1000000-0000-4000-8000-000000000003',
 'Superset Blitz',
 'Pairs of exercises back to back with almost no rest. Twice the work in half the time.',
 'Exercises with the same group letter are a superset: do one set of the first, go straight to one set of the second, then take the rest listed and repeat. Pick weights you can handle for the full rep range on every round. If you cannot finish the reps on later rounds, lower the weight rather than resting longer.',
 'INTERMEDIATE', 50, 3,
 $json$[
  {"name":"Push and Pull Blitz","focus":"Chest, back and shoulders in paired sets","estimatedMinutes":50,"exercises":[
    {"exercise":"Dumbbell Bench Press","sets":4,"repMin":8,"repMax":10,"restSeconds":60,"rpe":8.5,"method":"SUPERSET","group":"A","drops":0,"cues":["Go straight to the row, no rest inside the pair.","Wrists stacked over the elbows."]},
    {"exercise":"One-Arm Dumbbell Row","sets":4,"repMin":8,"repMax":10,"restSeconds":60,"rpe":8.5,"method":"SUPERSET","group":"A","drops":0,"cues":["Pull the elbow to the hip, do not twist the torso.","Do all reps on one side then switch, or alternate sides each round."]},
    {"exercise":"Dumbbell Shoulder Press","sets":3,"repMin":8,"repMax":12,"restSeconds":60,"rpe":8.5,"method":"SUPERSET","group":"B","drops":0,"cues":["Press straight overhead, ribs down."]},
    {"exercise":"Chin-Up","sets":3,"repMin":6,"repMax":10,"restSeconds":60,"rpe":8.5,"method":"SUPERSET","group":"B","drops":0,"cues":["Full hang at the bottom, chin over the bar at the top."]},
    {"exercise":"Dumbbell Curl","sets":3,"repMin":10,"repMax":12,"restSeconds":45,"rpe":9,"method":"SUPERSET","group":"C","drops":0,"cues":["Control the lowering phase."]},
    {"exercise":"Triceps Pushdown","sets":3,"repMin":10,"repMax":12,"restSeconds":45,"rpe":9,"method":"SUPERSET","group":"C","drops":0,"cues":["Squeeze the triceps hard at the bottom."]}
  ]},
  {"name":"Legs and Core Blitz","focus":"Quads, hamstrings and abs in paired sets","estimatedMinutes":50,"exercises":[
    {"exercise":"Goblet Squat","sets":4,"repMin":10,"repMax":12,"restSeconds":60,"rpe":8.5,"method":"SUPERSET","group":"A","drops":0,"cues":["Elbows inside the knees at the bottom.","Chest tall the whole way."]},
    {"exercise":"Dumbbell Romanian Deadlift","sets":4,"repMin":10,"repMax":12,"restSeconds":60,"rpe":8.5,"method":"SUPERSET","group":"A","drops":0,"cues":["Push the hips back, soft knees, dumbbells slide down the thighs."]},
    {"exercise":"Reverse Lunge","sets":3,"repMin":10,"repMax":12,"restSeconds":60,"rpe":8.5,"method":"SUPERSET","group":"B","drops":0,"cues":["Step back far enough that the front shin stays vertical."]},
    {"exercise":"Hanging Leg Raise","sets":3,"repMin":8,"repMax":12,"restSeconds":60,"rpe":8.5,"method":"SUPERSET","group":"B","drops":0,"cues":["Curl the pelvis up, do not swing."]},
    {"exercise":"Standing Calf Raise","sets":3,"repMin":12,"repMax":15,"restSeconds":45,"rpe":9,"method":"SUPERSET","group":"C","drops":0,"cues":["Full stretch then full squeeze."]},
    {"exercise":"Cable Crunch","sets":3,"repMin":12,"repMax":15,"restSeconds":45,"rpe":9,"method":"SUPERSET","group":"C","drops":0,"cues":["Round the spine, bring the ribs to the hips."]}
  ]}
 ]$json$::jsonb),

-- ---------------------------------------------------------------------------------------------
('a1000000-0000-4000-8000-000000000004',
 'EMOM Density Circuit',
 'Every minute on the minute: do the reps, rest for whatever is left of the minute, go again.',
 'Start the clock. At the top of every minute do the listed reps of that minute''s exercise as fast as you can with good form, then rest until the next minute starts. After four minutes the circuit repeats. If you cannot finish the reps inside the minute twice in a row, lower the reps or the weight. The rest shown is a guide: the minute decides it.',
 'INTERMEDIATE', 30, 4,
 $json$[
  {"name":"20 Minute EMOM","focus":"Five rounds of four movements, one per minute","estimatedMinutes":25,"exercises":[
    {"exercise":"Kettlebell Swing","sets":5,"repMin":15,"repMax":15,"restSeconds":25,"rpe":8,"method":"EMOM","group":"Minute 1","drops":0,"cues":["Hinge, do not squat: the hips drive the bell.","Arms stay loose, the bell floats to chest height."]},
    {"exercise":"Push-Up","sets":5,"repMin":12,"repMax":12,"restSeconds":25,"rpe":8,"method":"EMOM","group":"Minute 2","drops":0,"cues":["Body in one straight line, chest to the floor."]},
    {"exercise":"Goblet Squat","sets":5,"repMin":12,"repMax":12,"restSeconds":25,"rpe":8,"method":"EMOM","group":"Minute 3","drops":0,"cues":["Sit between the heels, drive up through the whole foot."]},
    {"exercise":"Inverted Row","sets":5,"repMin":10,"repMax":10,"restSeconds":25,"rpe":8,"method":"EMOM","group":"Minute 4","drops":0,"cues":["Pull the chest to the bar, keep the hips up."]}
  ]},
  {"name":"Strength EMOM","focus":"Heavier singles and doubles, one lift per minute","estimatedMinutes":30,"exercises":[
    {"exercise":"Barbell Back Squat","sets":6,"repMin":3,"repMax":3,"restSeconds":30,"rpe":8,"method":"EMOM","group":"Minute 1","drops":0,"cues":["Use about 75% of your max: three crisp reps leave you with time to rest."]},
    {"exercise":"Barbell Bench Press","sets":6,"repMin":3,"repMax":3,"restSeconds":30,"rpe":8,"method":"EMOM","group":"Minute 2","drops":0,"cues":["Touch the chest, press with intent, keep the shoulders pinned back."]},
    {"exercise":"Pull-Up","sets":6,"repMin":4,"repMax":4,"restSeconds":30,"rpe":8,"method":"EMOM","group":"Minute 3","drops":0,"cues":["Dead hang to chin over the bar."]}
  ]}
 ]$json$::jsonb),

-- ---------------------------------------------------------------------------------------------
('a1000000-0000-4000-8000-000000000005',
 'Arnold-Style Chest and Back',
 'Chest and back trained together, antagonist supersets, high volume. The classic golden-era split.',
 'Pair every chest exercise with a back exercise and alternate between them with the listed rest after each pair. The stretch from the chest movement helps the back movement and the other way round. Use full range of motion, pause briefly in the stretched position, and focus on the muscle, not the weight. This is a long session: eat first.',
 'ADVANCED', 75, 5,
 $json$[
  {"name":"Chest and Back A","focus":"Heavy compound pairs, then isolation pairs","estimatedMinutes":75,"exercises":[
    {"exercise":"Barbell Bench Press","sets":5,"repMin":6,"repMax":10,"restSeconds":90,"rpe":8.5,"method":"SUPERSET","group":"A","drops":0,"cues":["Pyramid the weight up across the five sets.","Pause for a beat on the chest."]},
    {"exercise":"Pull-Up","sets":5,"repMin":8,"repMax":12,"restSeconds":90,"rpe":8.5,"method":"SUPERSET","group":"A","drops":0,"cues":["Full stretch at the bottom, chest to the bar at the top.","Add weight with a belt once you can do 12 clean."]},
    {"exercise":"Incline Dumbbell Press","sets":4,"repMin":8,"repMax":10,"restSeconds":75,"rpe":9,"method":"SUPERSET","group":"B","drops":0,"cues":["Deep stretch at the bottom, do not flare the elbows past 60 degrees."]},
    {"exercise":"Barbell Row","sets":4,"repMin":8,"repMax":10,"restSeconds":75,"rpe":9,"method":"SUPERSET","group":"B","drops":0,"cues":["Torso about 45 degrees, bar to the lower ribs."]},
    {"exercise":"Dumbbell Fly","sets":3,"repMin":10,"repMax":12,"restSeconds":60,"rpe":9,"method":"SUPERSET","group":"C","drops":0,"cues":["Think of hugging a barrel."]},
    {"exercise":"Straight-Arm Pulldown","sets":3,"repMin":10,"repMax":12,"restSeconds":60,"rpe":9,"method":"SUPERSET","group":"C","drops":0,"cues":["Arms nearly straight, sweep the bar to the thighs with the lats."]}
  ]},
  {"name":"Chest and Back B","focus":"Volume day with dips and rows","estimatedMinutes":70,"exercises":[
    {"exercise":"Incline Barbell Bench Press","sets":4,"repMin":6,"repMax":10,"restSeconds":90,"rpe":8.5,"method":"SUPERSET","group":"A","drops":0,"cues":["Bar to the upper chest, elbows about 45 degrees from the torso."]},
    {"exercise":"Chin-Up","sets":4,"repMin":8,"repMax":12,"restSeconds":90,"rpe":8.5,"method":"SUPERSET","group":"A","drops":0,"cues":["Lead with the chest, pull the elbows to your pockets."]},
    {"exercise":"Chest Dip","sets":4,"repMin":8,"repMax":12,"restSeconds":75,"rpe":9,"method":"SUPERSET","group":"B","drops":0,"cues":["Lean forward to hit the chest, go to a deep but comfortable stretch."]},
    {"exercise":"Seated Cable Row","sets":4,"repMin":10,"repMax":12,"restSeconds":75,"rpe":9,"method":"SUPERSET","group":"B","drops":0,"cues":["Chest up, pull to the belly button, hold the squeeze for a second."]},
    {"exercise":"Cable Crossover","sets":3,"repMin":12,"repMax":15,"restSeconds":60,"rpe":9.5,"method":"SUPERSET","group":"C","drops":0,"cues":["Slight forward lean, bring the hands together below the chin."]},
    {"exercise":"Lat Pulldown","sets":3,"repMin":10,"repMax":12,"restSeconds":60,"rpe":9.5,"method":"SUPERSET","group":"C","drops":0,"cues":["Stretch fully at the top, then pull the elbows down."]}
  ]}
 ]$json$::jsonb),

-- ---------------------------------------------------------------------------------------------
('a1000000-0000-4000-8000-000000000006',
 'Heavy 5x5 Strength Day',
 'Five sets of five on the big lifts with long rest. Simple, heavy, effective.',
 'Work up to five sets of five with the same heavy weight and take the full rest between sets: the point is bar speed and force, not exhaustion. Add 2.5 kg to upper body lifts and 5 kg to squats and deadlifts each time you complete all 25 reps. If you miss reps two sessions in a row, take 10% off and build back up. Alternate day A and day B.',
 'INTERMEDIATE', 65, 6,
 $json$[
  {"name":"Day A","focus":"Squat, bench press, row","estimatedMinutes":65,"exercises":[
    {"exercise":"Barbell Back Squat","sets":5,"repMin":5,"repMax":5,"restSeconds":180,"rpe":8.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Warm up with lighter sets first, then five working sets.","Brace, sit between the hips, drive the floor away.","Rest the full three minutes."]},
    {"exercise":"Barbell Bench Press","sets":5,"repMin":5,"repMax":5,"restSeconds":180,"rpe":8.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Feet planted, shoulder blades pinched, touch and press.","Use a spotter or safety arms."]},
    {"exercise":"Barbell Row","sets":5,"repMin":5,"repMax":5,"restSeconds":150,"rpe":8,"method":"STRAIGHT","group":null,"drops":0,"cues":["Dead stop on the floor between reps if it helps keep the back flat."]}
  ]},
  {"name":"Day B","focus":"Squat, overhead press, deadlift","estimatedMinutes":65,"exercises":[
    {"exercise":"Barbell Back Squat","sets":5,"repMin":5,"repMax":5,"restSeconds":180,"rpe":8.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Same weight rule as day A."]},
    {"exercise":"Overhead Press","sets":5,"repMin":5,"repMax":5,"restSeconds":150,"rpe":8.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Squeeze the glutes, ribs down, push your head through at the top."]},
    {"exercise":"Conventional Deadlift","sets":1,"repMin":5,"repMax":5,"restSeconds":240,"rpe":9,"method":"STRAIGHT","group":null,"drops":0,"cues":["One heavy set of five: tight lats, push the floor away.","Reset every rep, no touch-and-go."]}
  ]}
 ]$json$::jsonb),

-- ---------------------------------------------------------------------------------------------
('a1000000-0000-4000-8000-000000000007',
 'Arms Annihilation',
 'Biceps and triceps supersets finished with drop sets and forearms. Pump or bust.',
 'Alternate biceps and triceps in supersets with the listed rest after each pair. On the exercises marked as drop sets, take the last set to failure, cut the weight by 20% and keep going, twice. Keep the elbows still, use controlled reps and do not use momentum: the weight should be light enough to feel every rep.',
 'INTERMEDIATE', 45, 7,
 $json$[
  {"name":"Arm Day","focus":"Supersets, then drop sets, then forearms","estimatedMinutes":45,"exercises":[
    {"exercise":"Barbell Curl","sets":4,"repMin":8,"repMax":10,"restSeconds":60,"rpe":8.5,"method":"SUPERSET","group":"A","drops":0,"cues":["Elbows pinned, no swinging the torso."]},
    {"exercise":"Skull Crusher","sets":4,"repMin":8,"repMax":10,"restSeconds":60,"rpe":8.5,"method":"SUPERSET","group":"A","drops":0,"cues":["Lower the bar to the forehead, keep the upper arms still."]},
    {"exercise":"Incline Dumbbell Curl","sets":3,"repMin":10,"repMax":12,"restSeconds":60,"rpe":9,"method":"DROP_SET","group":"B","drops":2,"cues":["Let the arms hang for a full stretch at the bottom."]},
    {"exercise":"Cable Overhead Triceps Extension","sets":3,"repMin":10,"repMax":12,"restSeconds":60,"rpe":9,"method":"DROP_SET","group":"B","drops":2,"cues":["Elbows point forward, reach a deep stretch behind the head."]},
    {"exercise":"Hammer Curl","sets":3,"repMin":10,"repMax":12,"restSeconds":45,"rpe":9,"method":"SUPERSET","group":"C","drops":0,"cues":["Neutral grip, bring the dumbbell to the shoulder."]},
    {"exercise":"Triceps Pushdown","sets":3,"repMin":12,"repMax":15,"restSeconds":45,"rpe":9,"method":"SUPERSET","group":"C","drops":0,"cues":["Squeeze at the bottom for a second."]},
    {"exercise":"Dumbbell Wrist Curl","sets":3,"repMin":15,"repMax":20,"restSeconds":30,"rpe":9,"method":"STRAIGHT","group":null,"drops":0,"cues":["Forearms rest on the thighs, only the wrists move."]}
  ]}
 ]$json$::jsonb),

-- ---------------------------------------------------------------------------------------------
('a1000000-0000-4000-8000-000000000008',
 'Leg Day Gauntlet',
 'Heavy squats, hinges, single-leg work and burnout sets. You will feel it for days.',
 'Warm up properly: five minutes of cycling plus light sets of squats. Take the heavy lifts at the listed RPE (leave one or two reps in the tank), then push the accessory work close to failure. Rest as listed. Eat well beforehand, have water ready and do not plan anything demanding after.',
 'ADVANCED', 80, 8,
 $json$[
  {"name":"Leg Day","focus":"Squat, hinge, single leg, machines, calves","estimatedMinutes":80,"exercises":[
    {"exercise":"Barbell Back Squat","sets":5,"repMin":6,"repMax":8,"restSeconds":150,"rpe":8.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Ramp up over the first two sets, then hold the weight.","Depth below parallel on every rep.","Brace hard, never round the lower back."]},
    {"exercise":"Romanian Deadlift","sets":4,"repMin":8,"repMax":10,"restSeconds":120,"rpe":8.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Hips back, bar slides along the thighs, feel the hamstring stretch.","Stop when the lower back would round."]},
    {"exercise":"Bulgarian Split Squat","sets":3,"repMin":10,"repMax":10,"restSeconds":90,"rpe":9,"method":"STRAIGHT","group":null,"drops":0,"cues":["Torso slightly forward, front knee tracks the toes.","Do all reps on one leg, rest, then the other."]},
    {"exercise":"Leg Press","sets":3,"repMin":12,"repMax":15,"restSeconds":90,"rpe":9,"method":"DROP_SET","group":null,"drops":2,"cues":["Last set: drop the weight twice without resting."]},
    {"exercise":"Lying Leg Curl","sets":3,"repMin":12,"repMax":15,"restSeconds":60,"rpe":9,"method":"STRAIGHT","group":null,"drops":0,"cues":["Squeeze at the top, lower for three seconds."]},
    {"exercise":"Walking Lunge","sets":2,"repMin":20,"repMax":20,"restSeconds":60,"rpe":9,"method":"STRAIGHT","group":null,"drops":0,"cues":["Ten steps each leg, long strides, upright torso."]},
    {"exercise":"Standing Calf Raise","sets":4,"repMin":12,"repMax":15,"restSeconds":45,"rpe":9,"method":"STRAIGHT","group":null,"drops":0,"cues":["Pause two seconds at the top and at the bottom."]},
    {"exercise":"Seated Calf Raise","sets":3,"repMin":15,"repMax":20,"restSeconds":45,"rpe":9.5,"method":"STRAIGHT","group":null,"drops":0,"cues":["Slow and controlled, burn them out."]}
  ]}
 ]$json$::jsonb);
