"""Generates backend/src/main/resources/db/migration/V3__seed_exercises_and_plans.sql.

Run from the repo root:  python ops/seed/generate_workout_seed.py
The SQL is committed; this script only exists so the exercise and plan data stay easy to edit.
IDs are deterministic (md5 of a stable key cast to uuid) so the app and server always agree.
"""

import pathlib
import sys

OUT = pathlib.Path(__file__).resolve().parents[2] / "backend/src/main/resources/db/migration/V3__seed_exercises_and_plans.sql"

# (name, primary, [secondary], equipment, [joint tags], compound, instructions)
EXERCISES = []


def ex(name, primary, secondary, equipment, joints, compound, instructions):
    EXERCISES.append((name, primary, secondary, equipment, joints, compound, instructions))


# ---------------------------------------------------------------- CHEST
ex("Barbell Bench Press", "CHEST", ["TRICEPS", "SHOULDERS"], "BARBELL", ["SHOULDER", "ELBOW", "WRIST"], True,
   "Lie with your eyes under the bar. Pull your shoulder blades back and down and grip slightly wider than shoulder width. Lower the bar to mid chest with elbows about 45 degrees from your torso, then press up and slightly back. Keep feet planted and hips on the bench.")
ex("Incline Barbell Bench Press", "CHEST", ["SHOULDERS", "TRICEPS"], "BARBELL", ["SHOULDER", "ELBOW", "WRIST"], True,
   "Set the bench to about 30 degrees. Unrack with shoulder blades pinched, lower the bar to the upper chest under control and press back up over your shoulders. Do not flare the elbows past 60 degrees.")
ex("Dumbbell Bench Press", "CHEST", ["TRICEPS", "SHOULDERS"], "DUMBBELL", ["SHOULDER", "ELBOW"], True,
   "Sit with dumbbells on your thighs, lie back and kick them up. Lower until elbows are just below the bench with forearms vertical, then press up and slightly together without clanking the weights.")
ex("Incline Dumbbell Press", "CHEST", ["SHOULDERS", "TRICEPS"], "DUMBBELL", ["SHOULDER", "ELBOW"], True,
   "Bench at 30 degrees. Press the dumbbells from shoulder level to full extension over the upper chest, then lower slowly for a full stretch. Keep your back against the pad.")
ex("Dumbbell Fly", "CHEST", ["SHOULDERS"], "DUMBBELL", ["SHOULDER", "ELBOW"], False,
   "Lie on a flat bench with a slight bend in the elbows. Open your arms in a wide arc until you feel a chest stretch, then squeeze the dumbbells back together as if hugging a tree. Do not go heavy on this one.")
ex("Cable Crossover", "CHEST", ["SHOULDERS"], "CABLE", ["SHOULDER"], False,
   "Set the pulleys high, step forward with a slight lean. With a soft bend in the elbows, sweep the handles down and together in front of your hips, squeeze, then return slowly until you feel the chest stretch.")
ex("Machine Chest Press", "CHEST", ["TRICEPS", "SHOULDERS"], "MACHINE", ["SHOULDER"], True,
   "Adjust the seat so the handles line up with mid chest. Keep your back flat on the pad and shoulder blades back. Press until the arms are almost straight, then return slowly.")
ex("Push-Up", "CHEST", ["TRICEPS", "SHOULDERS", "CORE"], "BODYWEIGHT", ["WRIST", "SHOULDER"], True,
   "Hands slightly wider than shoulders, body in a straight line from head to heels. Lower your chest to a fist above the floor with elbows at about 45 degrees, then press away. Do not let the hips sag.")
ex("Incline Push-Up", "CHEST", ["TRICEPS", "SHOULDERS"], "BODYWEIGHT", ["WRIST"], True,
   "Hands on a bench or sturdy surface, body straight. Lower your chest to the edge and press back up. The higher the surface, the easier it is, so progress to the floor over time.")
ex("Chest Dip", "CHEST", ["TRICEPS", "SHOULDERS"], "BODYWEIGHT", ["SHOULDER", "ELBOW"], True,
   "On parallel bars lean your torso forward about 30 degrees. Lower until upper arms are about parallel to the floor, then press up. Stop short of any pinching in the front of the shoulder.")
ex("Pec Deck", "CHEST", ["SHOULDERS"], "MACHINE", ["SHOULDER"], False,
   "Seat height so the handles sit at chest level. With a slight elbow bend, bring the handles together in front of you and pause, then return slowly to a comfortable stretch.")

# ---------------------------------------------------------------- BACK
ex("Conventional Deadlift", "BACK", ["HAMSTRINGS", "GLUTES", "FOREARMS"], "BARBELL", ["LOWER_BACK"], True,
   "Bar over mid foot, hip width stance. Hinge down and grip just outside your legs, chest up, back flat. Push the floor away while keeping the bar against your legs, lock out by squeezing glutes, then lower with the same path. Never round your lower back.")
ex("Barbell Row", "BACK", ["BICEPS", "SHOULDERS"], "BARBELL", ["LOWER_BACK"], True,
   "Hinge to about 45 degrees with a flat back. Pull the bar to your lower ribs, driving elbows back, pause, then lower under control. Keep your torso still and do not jerk the weight.")
ex("One-Arm Dumbbell Row", "BACK", ["BICEPS"], "DUMBBELL", [], True,
   "Support one hand and knee on a bench. Pull the dumbbell toward your hip, elbow close to the body, squeeze the shoulder blade, then lower fully for a stretch.")
ex("Lat Pulldown", "BACK", ["BICEPS"], "CABLE", ["ELBOW"], True,
   "Grip slightly wider than shoulders, thighs secured under the pad. Lean back slightly, pull the bar to your upper chest by driving elbows down, then let it rise until your arms are straight.")
ex("Pull-Up", "BACK", ["BICEPS", "FOREARMS"], "BODYWEIGHT", ["SHOULDER", "ELBOW"], True,
   "Hang with an overhand grip a little wider than shoulders. Pull until your chin clears the bar, leading with your chest, then lower to a full hang. Use a band or assisted machine until you can do clean reps.")
ex("Chin-Up", "BACK", ["BICEPS"], "BODYWEIGHT", ["ELBOW", "SHOULDER"], True,
   "Underhand shoulder width grip. Pull your chin over the bar, keeping your core tight and not swinging, then lower under control to straight arms.")
ex("Seated Cable Row", "BACK", ["BICEPS"], "CABLE", ["LOWER_BACK"], True,
   "Sit tall with a slight knee bend. Pull the handle to your stomach, squeezing the shoulder blades together, then extend your arms without letting your torso rock.")
ex("T-Bar Row", "BACK", ["BICEPS", "SHOULDERS"], "BARBELL", ["LOWER_BACK"], True,
   "Straddle the bar, hinge with a flat back and pull the handle to your chest. Pause, then lower until the arms are straight. Keep your head in line with your spine.")
ex("Chest-Supported Machine Row", "BACK", ["BICEPS", "SHOULDERS"], "MACHINE", [], True,
   "Chest against the pad, handles in both hands. Pull your elbows back past your torso, squeeze, then return slowly. The pad removes the lower back from the movement.")
ex("Straight-Arm Pulldown", "BACK", [], "CABLE", ["SHOULDER"], False,
   "Stand facing a high pulley, arms nearly straight. Sweep the bar down to your thighs using your lats, pause, then return slowly overhead without bending the elbows much.")
ex("Inverted Row", "BACK", ["BICEPS"], "BODYWEIGHT", ["ELBOW"], True,
   "Lie under a low bar or sturdy table edge, body straight. Pull your chest to the bar and lower slowly. Bend your knees to make it easier.")
ex("Back Extension", "BACK", ["GLUTES", "HAMSTRINGS"], "BODYWEIGHT", ["LOWER_BACK"], False,
   "On a hyperextension bench, hinge at the hips until your torso is about level with your legs, then raise to a straight line. Do not hyperextend past neutral at the top.")
ex("Dumbbell Shrug", "BACK", ["FOREARMS"], "DUMBBELL", [], False,
   "Stand holding dumbbells at your sides. Lift your shoulders straight up toward your ears, hold for a second, then lower. Do not roll the shoulders.")

# ---------------------------------------------------------------- SHOULDERS
ex("Overhead Press", "SHOULDERS", ["TRICEPS", "CORE"], "BARBELL", ["SHOULDER", "WRIST", "LOWER_BACK"], True,
   "Bar at collarbone, grip just outside shoulders. Squeeze glutes, press the bar straight up as your head moves slightly back and then through, finishing with the bar over mid foot. Lower to the collarbone.")
ex("Dumbbell Shoulder Press", "SHOULDERS", ["TRICEPS"], "DUMBBELL", ["SHOULDER"], True,
   "Seated with back support or standing. Press from shoulder height to full extension over your head, then lower until your elbows are just below shoulder level.")
ex("Arnold Press", "SHOULDERS", ["TRICEPS"], "DUMBBELL", ["SHOULDER"], True,
   "Start with palms facing you at chest height. Press up while rotating your palms to face forward, finishing like a normal press. Reverse the rotation on the way down.")
ex("Dumbbell Lateral Raise", "SHOULDERS", [], "DUMBBELL", ["SHOULDER"], False,
   "Slight bend in the elbows, raise the dumbbells out to the sides until your arms are about parallel to the floor, leading with the elbows. Lower slowly. Light weight and strict form win here.")
ex("Cable Lateral Raise", "SHOULDERS", [], "CABLE", ["SHOULDER"], False,
   "Stand side-on to a low pulley and raise the handle out to the side to shoulder height. The cable keeps tension at the bottom where dumbbells do not.")
ex("Rear Delt Fly", "SHOULDERS", ["BACK"], "DUMBBELL", ["SHOULDER"], False,
   "Hinge forward with a flat back, arms hanging. Raise the dumbbells out to the sides with a soft elbow bend, squeezing the rear shoulders, then lower slowly.")
ex("Reverse Pec Deck", "SHOULDERS", ["BACK"], "MACHINE", ["SHOULDER"], False,
   "Face the pad. Sweep the handles back in a wide arc until your arms are in line with your shoulders, then return slowly. Keep your chest on the pad.")
ex("Machine Shoulder Press", "SHOULDERS", ["TRICEPS"], "MACHINE", ["SHOULDER"], True,
   "Adjust the seat so the handles start at shoulder height. Press up without shrugging, stop just short of lockout, and lower slowly.")
ex("Pike Push-Up", "SHOULDERS", ["TRICEPS", "CHEST"], "BODYWEIGHT", ["SHOULDER", "WRIST"], True,
   "From a downward dog position with hips high, bend the elbows to lower your head toward the floor between your hands, then press back up. Raise your feet on a bench to make it harder.")
ex("Dumbbell Front Raise", "SHOULDERS", [], "DUMBBELL", ["SHOULDER"], False,
   "Raise the dumbbells in front of you to shoulder height with straight arms, pause, and lower with control. Do not swing your torso.")
ex("Face Pull", "SHOULDERS", ["BACK"], "CABLE", ["SHOULDER"], False,
   "Set a rope at face height. Pull it toward your forehead, splitting the rope and pulling your hands past your ears with elbows high. Great for shoulder health.")

# ---------------------------------------------------------------- BICEPS
ex("Barbell Curl", "BICEPS", ["FOREARMS"], "BARBELL", ["ELBOW", "WRIST"], False,
   "Stand tall, elbows at your sides. Curl the bar up without swinging, squeeze at the top, then lower fully. Use an EZ bar if straight bars bother your wrists.")
ex("Dumbbell Curl", "BICEPS", ["FOREARMS"], "DUMBBELL", ["ELBOW"], False,
   "Curl the dumbbells up, rotating the palms to face you as you lift. Keep your elbows pinned at your sides and lower slowly.")
ex("Hammer Curl", "BICEPS", ["FOREARMS"], "DUMBBELL", ["ELBOW"], False,
   "Palms face each other the whole time. Curl up to shoulder height and lower with control. Works the brachialis and forearms.")
ex("Incline Dumbbell Curl", "BICEPS", [], "DUMBBELL", ["ELBOW", "SHOULDER"], False,
   "Sit back on an incline bench with arms hanging behind your torso. Curl up without moving your upper arms, getting a deep stretch at the bottom.")
ex("Preacher Curl", "BICEPS", [], "MACHINE", ["ELBOW"], False,
   "Upper arms flat on the pad. Curl up and squeeze, then lower slowly but stop just short of locking out to protect the elbow.")
ex("Cable Curl", "BICEPS", ["FOREARMS"], "CABLE", ["ELBOW"], False,
   "Face a low pulley with a straight or EZ attachment. Curl up keeping your elbows by your sides. The cable gives constant tension through the whole range.")

# ---------------------------------------------------------------- TRICEPS
ex("Close-Grip Bench Press", "TRICEPS", ["CHEST", "SHOULDERS"], "BARBELL", ["ELBOW", "WRIST", "SHOULDER"], True,
   "Bench press with hands about shoulder width apart and elbows tucked closer to your body. Lower to the lower chest and press up. Do not go narrower than shoulder width.")
ex("Triceps Pushdown", "TRICEPS", [], "CABLE", ["ELBOW"], False,
   "Elbows pinned at your sides, push the bar or rope down until your arms are straight, squeeze, then let it rise until your forearms are just past parallel.")
ex("Overhead Dumbbell Triceps Extension", "TRICEPS", [], "DUMBBELL", ["ELBOW", "SHOULDER"], False,
   "Hold one dumbbell with both hands overhead. Bend the elbows to lower it behind your head, keeping upper arms close to your ears, then extend to the top.")
ex("Skull Crusher", "TRICEPS", [], "BARBELL", ["ELBOW", "WRIST"], False,
   "Lying on a bench, lower an EZ bar toward your forehead by bending only at the elbows, then extend back up. Keep upper arms vertical.")
ex("Bench Dip", "TRICEPS", ["CHEST", "SHOULDERS"], "BODYWEIGHT", ["SHOULDER", "ELBOW", "WRIST"], True,
   "Hands on a bench behind you, legs out front. Lower your body until your elbows are at about 90 degrees, then press up. Keep your back close to the bench.")
ex("Diamond Push-Up", "TRICEPS", ["CHEST", "SHOULDERS"], "BODYWEIGHT", ["WRIST", "ELBOW", "SHOULDER"], True,
   "Hands together under your chest forming a diamond. Lower with elbows tracking back and press up. Drop to your knees if needed.")
ex("Cable Overhead Triceps Extension", "TRICEPS", [], "CABLE", ["ELBOW"], False,
   "Face away from a pulley with a rope overhead. Extend your arms forward and up until straight, then return slowly for a deep stretch.")

# ---------------------------------------------------------------- FOREARMS
ex("Dumbbell Wrist Curl", "FOREARMS", [], "DUMBBELL", ["WRIST"], False,
   "Forearms on your thighs, palms up. Let the dumbbell roll to your fingertips, then curl it up using only your wrists. Light weight, high reps.")
ex("Reverse Curl", "FOREARMS", ["BICEPS"], "BARBELL", ["ELBOW", "WRIST"], False,
   "Overhand grip on an EZ bar. Curl up with elbows at your sides, keeping wrists straight, and lower slowly.")
ex("Farmer's Carry", "FOREARMS", ["CORE", "BACK"], "DUMBBELL", [], True,
   "Hold heavy dumbbells at your sides, stand tall with shoulders back and walk with short quick steps. Reps are steps or seconds. Do not lean or let the weights swing.")

# ---------------------------------------------------------------- QUADS
ex("Barbell Back Squat", "QUADS", ["GLUTES", "HAMSTRINGS", "CORE"], "BARBELL", ["KNEE", "LOWER_BACK"], True,
   "Bar on your upper back, feet shoulder width, toes slightly out. Brace, sit down and back until thighs are at least parallel, knees tracking over toes, then drive up. Keep your chest up and back flat.")
ex("Front Squat", "QUADS", ["GLUTES", "CORE"], "BARBELL", ["KNEE", "WRIST", "LOWER_BACK"], True,
   "Bar rests on your front shoulders with elbows high. Squat straight down keeping an upright torso, then stand. Needs good wrist and shoulder mobility; use straps if the grip hurts.")
ex("Goblet Squat", "QUADS", ["GLUTES", "CORE"], "DUMBBELL", ["KNEE"], True,
   "Hold a dumbbell vertically at your chest. Squat down between your knees with an upright torso, elbows inside the knees, then stand. A great way to learn the squat pattern.")
ex("Leg Press", "QUADS", ["GLUTES", "HAMSTRINGS"], "MACHINE", ["KNEE", "LOWER_BACK"], True,
   "Feet shoulder width on the platform. Lower until your knees reach about 90 degrees without your hips curling off the pad, then press without locking the knees hard.")
ex("Leg Extension", "QUADS", [], "MACHINE", ["KNEE"], False,
   "Pad on your lower shins. Extend your knees until your legs are straight, squeeze the quads, then lower slowly. Avoid heavy swinging.")
ex("Bulgarian Split Squat", "QUADS", ["GLUTES"], "DUMBBELL", ["KNEE"], True,
   "Rear foot on a bench, front foot far enough forward that your shin stays near vertical. Lower until the back knee is just above the floor, then drive up through the front heel.")
ex("Walking Lunge", "QUADS", ["GLUTES", "HAMSTRINGS"], "DUMBBELL", ["KNEE"], True,
   "Step forward into a lunge until both knees are at about 90 degrees, then push off the front foot into the next step. Keep your torso upright.")
ex("Hack Squat", "QUADS", ["GLUTES"], "MACHINE", ["KNEE"], True,
   "Shoulders under the pads, feet forward on the platform. Lower with control until the thighs are parallel or below, then push back up.")
ex("Bodyweight Squat", "QUADS", ["GLUTES"], "BODYWEIGHT", ["KNEE"], True,
   "Feet shoulder width, arms forward for balance. Sit back and down until your thighs are parallel, then stand. Keep your heels on the floor.")
ex("Dumbbell Step-Up", "QUADS", ["GLUTES"], "DUMBBELL", ["KNEE"], True,
   "Place one foot fully on a box or bench and drive through that heel to stand, then lower slowly. Do all reps on one leg before switching.")
ex("Reverse Lunge", "QUADS", ["GLUTES", "HAMSTRINGS"], "DUMBBELL", ["KNEE"], True,
   "Step back and lower until both knees are about 90 degrees, then push through the front foot to return. Easier on the knees than a forward lunge.")

# ---------------------------------------------------------------- HAMSTRINGS
ex("Romanian Deadlift", "HAMSTRINGS", ["GLUTES", "BACK"], "BARBELL", ["LOWER_BACK"], True,
   "Hold the bar at hip level, soft knees. Push your hips back, sliding the bar down your thighs until you feel a deep hamstring stretch, then drive the hips forward. Keep your back flat.")
ex("Dumbbell Romanian Deadlift", "HAMSTRINGS", ["GLUTES", "BACK"], "DUMBBELL", ["LOWER_BACK"], True,
   "Dumbbells in front of your thighs. Hinge at the hips, lowering them down your legs with a flat back, then stand tall by squeezing your glutes.")
ex("Lying Leg Curl", "HAMSTRINGS", ["CALVES"], "MACHINE", ["KNEE"], False,
   "Lie face down with the pad just above your heels. Curl your heels toward your glutes, pause, and lower slowly. Keep your hips pressed into the bench.")
ex("Seated Leg Curl", "HAMSTRINGS", [], "MACHINE", ["KNEE"], False,
   "Pad above your ankles, thigh pad snug. Curl your legs back under the seat, squeeze, and return slowly through a full stretch.")
ex("Good Morning", "HAMSTRINGS", ["GLUTES", "BACK"], "BARBELL", ["LOWER_BACK"], True,
   "Bar on your upper back, soft knees. Push your hips back until your torso is near parallel with the floor, back flat, then return. Use light weight and strict form.")
ex("Nordic Hamstring Curl", "HAMSTRINGS", ["CALVES"], "BODYWEIGHT", ["KNEE"], False,
   "Kneel with your ankles anchored. Lower your torso toward the floor as slowly as you can, catching yourself with your hands, then push back up. Very demanding, so use as many assists as needed.")
ex("Single-Leg Romanian Deadlift", "HAMSTRINGS", ["GLUTES", "CORE"], "DUMBBELL", [], True,
   "Balance on one leg with a dumbbell in the opposite hand. Hinge forward while the free leg extends behind you, then return. Keep your hips square to the floor.")

# ---------------------------------------------------------------- GLUTES
ex("Barbell Hip Thrust", "GLUTES", ["HAMSTRINGS"], "BARBELL", [], True,
   "Upper back on a bench, bar over your hips with a pad. Drive through your heels until your hips are fully extended, squeeze the glutes at the top with ribs down, then lower.")
ex("Glute Bridge", "GLUTES", ["HAMSTRINGS"], "BODYWEIGHT", [], False,
   "Lie on your back, knees bent. Drive your hips up by squeezing your glutes until your body forms a straight line, pause, and lower.")
ex("Cable Pull-Through", "GLUTES", ["HAMSTRINGS"], "CABLE", ["LOWER_BACK"], True,
   "Face away from a low pulley with a rope between your legs. Hinge back, then snap your hips forward to stand tall. It is a hip hinge, not a squat.")
ex("Cable Glute Kickback", "GLUTES", [], "CABLE", [], False,
   "Ankle strap on a low pulley. Kick the leg straight back by squeezing the glute, without arching your lower back, then return slowly.")
ex("Sumo Deadlift", "GLUTES", ["QUADS", "HAMSTRINGS", "BACK"], "BARBELL", ["LOWER_BACK", "KNEE"], True,
   "Wide stance, toes out, hands inside your knees. Push the floor apart, keep the chest up and lock out with your hips under the bar.")
ex("Dumbbell Hip Thrust", "GLUTES", ["HAMSTRINGS"], "DUMBBELL", [], True,
   "Same as the barbell hip thrust with a dumbbell on your hips. A good starting point before loading the bar.")

# ---------------------------------------------------------------- CALVES
ex("Standing Calf Raise", "CALVES", [], "MACHINE", [], False,
   "Balls of your feet on the platform. Lower your heels for a full stretch, then rise as high as you can and pause for a second at the top.")
ex("Seated Calf Raise", "CALVES", [], "MACHINE", [], False,
   "With the pad on your thighs, lower the heels deep and raise them as high as possible. The bent knee targets the soleus.")
ex("Bodyweight Calf Raise", "CALVES", [], "BODYWEIGHT", [], False,
   "Stand on the edge of a step, lower your heels below the step and rise onto your toes. Hold something for balance and go slowly.")

# ---------------------------------------------------------------- CORE
ex("Plank", "CORE", ["SHOULDERS"], "BODYWEIGHT", ["WRIST"], False,
   "Forearms on the floor, body in a straight line from head to heels. Squeeze glutes and abs, breathe steadily. Reps are seconds.")
ex("Hanging Leg Raise", "CORE", ["FOREARMS"], "BODYWEIGHT", ["SHOULDER"], False,
   "Hang from a bar and raise your legs, curling your pelvis up toward your ribs. Lower slowly without swinging. Bend the knees to make it easier.")
ex("Cable Crunch", "CORE", [], "CABLE", [], False,
   "Kneel facing a high pulley with a rope at your head. Crunch your ribs toward your pelvis, rounding the spine, then return slowly.")
ex("Ab Wheel Rollout", "CORE", ["SHOULDERS", "BACK"], "OTHER", ["LOWER_BACK", "SHOULDER"], False,
   "From your knees, roll the wheel forward while keeping your abs braced and back flat, then pull it back. Only go as far as you can control.")
ex("Russian Twist", "CORE", [], "BODYWEIGHT", ["LOWER_BACK"], False,
   "Sit leaning back with your feet raised or on the floor. Rotate your torso side to side, touching the floor beside your hips. Hold a weight to progress.")
ex("Dead Bug", "CORE", [], "BODYWEIGHT", [], False,
   "On your back with arms up and knees at 90 degrees. Lower the opposite arm and leg while keeping your lower back pressed into the floor, then switch sides.")
ex("Bicycle Crunch", "CORE", [], "BODYWEIGHT", [], False,
   "Hands behind your head, bring opposite elbow and knee together while the other leg extends. Move slowly and keep your lower back down.")
ex("Side Plank", "CORE", ["SHOULDERS"], "BODYWEIGHT", ["SHOULDER", "WRIST"], False,
   "On one forearm with your body in a straight line, hips lifted. Hold without letting the hips sag. Reps are seconds per side.")
ex("Pallof Press", "CORE", ["SHOULDERS"], "CABLE", [], False,
   "Stand side-on to a cable at chest height. Press the handle straight out and resist the pull trying to rotate you, then bring it back to your chest.")
ex("Mountain Climber", "CORE", ["SHOULDERS", "QUADS"], "BODYWEIGHT", ["WRIST"], False,
   "In a high plank, drive your knees toward your chest alternately at a steady pace. Keep your hips level and shoulders over your hands.")

# ---------------------------------------------------------------- KETTLEBELL
ex("Kettlebell Swing", "GLUTES", ["HAMSTRINGS", "BACK", "CORE"], "KETTLEBELL", ["LOWER_BACK"], True,
   "Hike the kettlebell between your legs, then snap your hips forward to swing it to chest height. It is a hip hinge, so the arms just guide. Keep your back flat.")
ex("Kettlebell Goblet Squat", "QUADS", ["GLUTES", "CORE"], "KETTLEBELL", ["KNEE"], True,
   "Hold the kettlebell by the horns at your chest. Squat deep with an upright torso and drive up through your heels.")

# ------------------------------------------------------------------ plans
NAMES = {e[0] for e in EXERCISES}
IS_COMPOUND = {e[0]: e[5] for e in EXERCISES}
PRIMARY = {e[0]: e[1] for e in EXERCISES}
TIMED = {"Plank", "Side Plank", "Farmer's Carry"}

TEMPLATES = {
    "PPL": [
        ("Push", "Chest, shoulders, triceps",
         ["Barbell Bench Press", "Overhead Press", "Incline Dumbbell Press", "Dumbbell Lateral Raise",
          "Triceps Pushdown", "Overhead Dumbbell Triceps Extension", "Cable Crossover"]),
        ("Pull", "Back, rear delts, biceps",
         ["Barbell Row", "Lat Pulldown", "Seated Cable Row", "Face Pull", "Barbell Curl", "Hammer Curl",
          "Dumbbell Shrug"]),
        ("Legs", "Quads, hamstrings, calves, core",
         ["Barbell Back Squat", "Romanian Deadlift", "Leg Press", "Lying Leg Curl", "Standing Calf Raise",
          "Leg Extension", "Cable Crunch"]),
    ],
    "BRO_SPLIT": [
        ("Chest", "Chest with triceps finishers",
         ["Barbell Bench Press", "Incline Dumbbell Press", "Machine Chest Press", "Cable Crossover",
          "Dumbbell Fly", "Chest Dip", "Push-Up"]),
        ("Back", "Back thickness and width",
         ["Conventional Deadlift", "Barbell Row", "Lat Pulldown", "Seated Cable Row", "One-Arm Dumbbell Row",
          "Straight-Arm Pulldown", "Back Extension"]),
        ("Shoulders", "All three delt heads and traps",
         ["Overhead Press", "Dumbbell Lateral Raise", "Rear Delt Fly", "Dumbbell Shoulder Press", "Face Pull",
          "Dumbbell Front Raise", "Dumbbell Shrug"]),
        ("Arms", "Biceps and triceps",
         ["Close-Grip Bench Press", "Barbell Curl", "Triceps Pushdown", "Incline Dumbbell Curl", "Skull Crusher",
          "Hammer Curl", "Cable Curl"]),
        ("Legs", "Quads, hamstrings, calves",
         ["Barbell Back Squat", "Leg Press", "Romanian Deadlift", "Leg Extension", "Lying Leg Curl",
          "Standing Calf Raise", "Walking Lunge"]),
    ],
    "UPPER_LOWER": [
        ("Upper A", "Horizontal push and pull emphasis",
         ["Barbell Bench Press", "Barbell Row", "Overhead Press", "Lat Pulldown", "Dumbbell Curl",
          "Triceps Pushdown", "Dumbbell Lateral Raise"]),
        ("Lower A", "Squat emphasis",
         ["Barbell Back Squat", "Romanian Deadlift", "Leg Press", "Lying Leg Curl", "Standing Calf Raise",
          "Cable Crunch"]),
        ("Upper B", "Vertical push and pull emphasis",
         ["Incline Dumbbell Press", "Pull-Up", "Dumbbell Shoulder Press", "Seated Cable Row", "Hammer Curl",
          "Overhead Dumbbell Triceps Extension", "Face Pull"]),
        ("Lower B", "Hinge emphasis",
         ["Front Squat", "Barbell Hip Thrust", "Bulgarian Split Squat", "Seated Leg Curl", "Seated Calf Raise",
          "Hanging Leg Raise"]),
    ],
    "FULL_BODY": [
        ("Full Body A", "Squat, press, row",
         ["Barbell Back Squat", "Barbell Bench Press", "Barbell Row", "Dumbbell Lateral Raise", "Plank"]),
        ("Full Body B", "Hinge, press, pull",
         ["Romanian Deadlift", "Overhead Press", "Lat Pulldown", "Leg Press", "Dumbbell Curl",
          "Triceps Pushdown"]),
        ("Full Body C", "Single leg, incline press, row",
         ["Goblet Squat", "Incline Dumbbell Press", "Seated Cable Row", "Barbell Hip Thrust", "Face Pull",
          "Hanging Leg Raise"]),
    ],
}

SPLIT_LABEL = {"PPL": "Push Pull Legs", "BRO_SPLIT": "Bro Split", "UPPER_LOWER": "Upper / Lower", "FULL_BODY": "Full Body"}
LEVELS = ["BEGINNER", "INTERMEDIATE", "ADVANCED"]
LEVEL_LABEL = {"BEGINNER": "Beginner", "INTERMEDIATE": "Intermediate", "ADVANCED": "Advanced"}
EXERCISES_PER_DAY = {"BEGINNER": 5, "INTERMEDIATE": 6, "ADVANCED": 7}
DAYS_PER_WEEK = {
    "PPL": {"BEGINNER": 3, "INTERMEDIATE": 6, "ADVANCED": 6},
    "BRO_SPLIT": {"BEGINNER": 5, "INTERMEDIATE": 5, "ADVANCED": 5},
    "UPPER_LOWER": {"BEGINNER": 4, "INTERMEDIATE": 4, "ADVANCED": 4},
    "FULL_BODY": {"BEGINNER": 3, "INTERMEDIATE": 3, "ADVANCED": 3},
}
DESCRIPTION = {
    "PPL": "Three sessions (push, pull, legs). Beginners run them once a week; intermediate and advanced lifters repeat the cycle for six days.",
    "BRO_SPLIT": "One muscle group per day across five days, with plenty of volume per session.",
    "UPPER_LOWER": "Four days alternating upper and lower body, each muscle trained twice a week.",
    "FULL_BODY": "Three full body sessions a week, ideal when time is short.",
}


def q(s):
    return "'" + s.replace("'", "''") + "'"


def arr(items):
    if not items:
        return "ARRAY[]::text[]"
    return "ARRAY[" + ", ".join(q(i) for i in items) + "]"


def scheme(name, position, level):
    """Sets, rep range and rest for one exercise slot."""
    compound = IS_COMPOUND[name]
    muscle = PRIMARY[name]
    if name in TIMED:
        return 3, 30, 60, 60
    if muscle == "CALVES":
        return 3, 12, 20, 60
    if muscle == "CORE":
        return 3, 10, 15, 60
    if compound:
        sets = 4 if (level == "ADVANCED" or (level == "INTERMEDIATE" and position == 0)) else 3
        lo, hi = {"BEGINNER": (8, 12), "INTERMEDIATE": (6, 10), "ADVANCED": (5, 8)}[level]
        if position > 1:
            lo, hi = lo + 2, hi + 2
        return sets, lo, hi, (120 if level == "BEGINNER" else 150)
    return 3, 10, 15, 75


def main():
    lines = [
        "-- Phase 3 seed: generated by ops/seed/generate_workout_seed.py. Do not edit by hand.",
        "-- IDs are md5-derived UUIDs so the same exercise has the same id on every environment.",
        "",
    ]

    lines.append("INSERT INTO exercises (id, user_id, name, instructions, primary_muscle, secondary_muscles, equipment, joint_tags, compound) VALUES")
    rows = []
    for name, primary, secondary, equipment, joints, compound, instructions in EXERCISES:
        rows.append(
            f"(md5({q('exercise:' + name)})::uuid, NULL, {q(name)}, {q(instructions)}, {q(primary)}, "
            f"{arr(secondary)}, {q(equipment)}, {arr(joints)}, {'TRUE' if compound else 'FALSE'})"
        )
    lines.append(",\n".join(rows) + ";")
    lines.append("")

    plan_rows, day_rows, pde_rows = [], [], []
    for split, days in TEMPLATES.items():
        for level in LEVELS:
            plan_key = f"{split}:{level}"
            plan_name = f"{SPLIT_LABEL[split]} ({LEVEL_LABEL[level]})"
            plan_rows.append(
                f"(md5({q('plan:' + plan_key)})::uuid, {q(plan_name)}, {q(split)}, {q(level)}, "
                f"{q(DESCRIPTION[split])}, {DAYS_PER_WEEK[split][level]})"
            )
            for day_index, (day_name, focus, names) in enumerate(days):
                day_key = f"planday:{plan_key}:{day_index}"
                day_rows.append(
                    f"(md5({q(day_key)})::uuid, md5({q('plan:' + plan_key)})::uuid, {day_index}, {q(day_name)}, {q(focus)})"
                )
                for position, name in enumerate(names[: EXERCISES_PER_DAY[level]]):
                    assert name in NAMES, f"unknown exercise in template: {name}"
                    sets, lo, hi, rest = scheme(name, position, level)
                    pde_rows.append(
                        f"(md5({q('pde:' + day_key + ':' + str(position))})::uuid, md5({q(day_key)})::uuid, "
                        f"md5({q('exercise:' + name)})::uuid, {position}, {sets}, {lo}, {hi}, {rest})"
                    )

    lines.append("INSERT INTO workout_plans (id, name, split, level, description, days_per_week) VALUES")
    lines.append(",\n".join(plan_rows) + ";")
    lines.append("")
    lines.append("INSERT INTO plan_days (id, plan_id, day_index, name, focus) VALUES")
    lines.append(",\n".join(day_rows) + ";")
    lines.append("")
    lines.append("INSERT INTO plan_day_exercises (id, plan_day_id, exercise_id, order_index, sets, rep_min, rep_max, rest_seconds) VALUES")
    lines.append(",\n".join(pde_rows) + ";")
    lines.append("")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(EXERCISES)} exercises, {len(plan_rows)} plans, {len(day_rows)} days, {len(pde_rows)} slots)")
    if len(EXERCISES) < 80:
        sys.exit("fewer than 80 exercises")


if __name__ == "__main__":
    main()
