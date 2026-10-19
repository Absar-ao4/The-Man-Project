package com.example.fitapp.feature.guest.domain

/** The five tabs a guest can look at. They mirror the tabs of the real app. */
enum class GuestSection { HOME, WORKOUT, FOOD, SOCIAL, PROFILE }

/** One feature of the app, shown to a guest as a locked card. */
data class GuestFeature(
    val title: String,
    val description: String,
)

data class GuestSectionContent(
    val headline: String,
    val intro: String,
    val features: List<GuestFeature>,
)

/** A made-up progress bar for the example day on the guest home screen. */
data class ExampleMeter(
    val label: String,
    val value: Int,
    val target: Int,
    val unit: String,
) {
    val fraction: Float get() = if (target <= 0) 0f else (value.toFloat() / target).coerceIn(0f, 1f)

    val text: String get() = "$value / $target $unit"
}

/**
 * What a guest sees. Every feature here saves data to an account or reads it from the server, so each one is locked;
 * the guest screen only describes it. Nothing in this file touches a database or the network.
 */
object GuestCatalog {
    val exampleDay: List<ExampleMeter> =
        listOf(
            ExampleMeter("Calories", 1850, 2200, "kcal"),
            ExampleMeter("Protein", 96, 130, "g"),
            ExampleMeter("Water", 6, 8, "glasses"),
        )

    fun content(section: GuestSection): GuestSectionContent =
        when (section) {
            GuestSection.HOME ->
                GuestSectionContent(
                    headline = "Home",
                    intro = "Your day in one place: how much of today's goal is done, your streak and quick ways to log.",
                    features =
                        listOf(
                            GuestFeature("Daily goal", "One number for calories, protein, water and a workout, updated as you log."),
                            GuestFeature("Streak, XP and badges", "Earn XP for every workout and meal, level up and collect badges."),
                            GuestFeature("Quick actions", "Start a workout, log food or scan a meal in one tap."),
                        ),
                )
            GuestSection.WORKOUT ->
                GuestSectionContent(
                    headline = "Workout",
                    intro = "Log sets fast, follow a plan and watch your strength go up.",
                    features =
                        listOf(
                            GuestFeature("Start a workout", "Log sets, reps and weight with a rest timer, and see new records the moment you hit them."),
                            GuestFeature("Routines and plans", "Build your own routines or follow a ready made training plan."),
                            GuestFeature("Exercise library", "Search exercises by muscle and equipment, and add your own."),
                            GuestFeature("History and records", "Every past session and your personal records in one list."),
                            GuestFeature("Lock-in programs", "Guided programs that tell you what to lift next and predict the XP you will earn."),
                            GuestFeature("Plan generator", "Answer a few questions and get a plan built for your level and schedule."),
                            GuestFeature("Coach", "A weekly summary of your training and the next steps, based on what you logged."),
                        ),
                )
            GuestSection.FOOD ->
                GuestSectionContent(
                    headline = "Food",
                    intro = "Know what you ate and what is left for the day.",
                    features =
                        listOf(
                            GuestFeature("Log meals", "Search foods, pick a portion and see calories and macros add up."),
                            GuestFeature("Barcode and photo scan", "Scan a package or a plate and review the result before it is saved."),
                            GuestFeature("Diet planner", "A daily plan that hits your targets for the least money."),
                            GuestFeature("Meal ideas", "Suggestions that fit what you still have left to eat today."),
                            GuestFeature("Water", "Tap a glass each time you drink and watch the day fill up."),
                            GuestFeature("Custom foods and meals", "Save the foods and meals you eat often."),
                        ),
                )
            GuestSection.SOCIAL ->
                GuestSectionContent(
                    headline = "Social",
                    intro = "Training is easier with someone to beat.",
                    features =
                        listOf(
                            GuestFeature("Weekly league", "See where you rank against your friends this week."),
                            GuestFeature("Friends", "Add friends by username or invite code and answer requests."),
                            GuestFeature("Duels", "Challenge a friend on workouts, XP or lock-ins for a week."),
                            GuestFeature("Friends feed", "See their workouts and records, only what they choose to share."),
                            GuestFeature("Privacy", "Choose what friends and the leaderboard can see about you."),
                        ),
                )
            GuestSection.PROFILE ->
                GuestSectionContent(
                    headline = "Profile",
                    intro = "Your numbers, goals and settings.",
                    features =
                        listOf(
                            GuestFeature("Goals and daily targets", "Targets for calories, protein, carbs, fat and water that match your goal."),
                            GuestFeature("Body tracking", "Weight trend, BMI, waist-to-height and body fat estimate from your measurements."),
                            GuestFeature("Progress charts", "Strength, volume and a calendar of the days you trained."),
                            GuestFeature("Reminders", "Gentle nudges for workouts, meals and water, kept on your phone."),
                            GuestFeature("Articles", "Short reads on training, food and recovery."),
                            GuestFeature("Reports and data", "Weekly and monthly reports as PDF or CSV, and a full export of your data."),
                        ),
                )
        }

    /** What the dialog says when a guest taps a locked feature. */
    fun lockedMessage(feature: GuestFeature): String =
        "${feature.title} saves your data to an account, so it is turned off in guest mode. " +
            "Create a free account or sign in to use it. You can keep looking around until then."
}