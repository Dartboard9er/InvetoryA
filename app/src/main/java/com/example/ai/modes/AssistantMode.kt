package com.example.ai.modes

enum class AssistantMode(
    val id: String,
    val title: String,
    val emoji: String,
    val scopeBadge: String,
    val shortDescription: String,
    val placeholder: String
) {
    HOME_AI(
        id = "home_ai",
        title = "Home AI",
        emoji = "🧠",
        scopeBadge = "Everything",
        shortDescription = "General-purpose assistant with full household access.",
        placeholder = "Ask about your home..."
    ),
    COOKING(
        id = "cooking",
        title = "Cooking",
        emoji = "🍳",
        scopeBadge = "Kitchen only",
        shortDescription = "Find ingredients, plan meals & use what you have.",
        placeholder = "What can we make tonight?"
    ),
    GARAGE(
        id = "garage",
        title = "Garage",
        emoji = "🔧",
        scopeBadge = "Garage only",
        shortDescription = "Tools, equipment, supplies & workshop projects.",
        placeholder = "What are you working on?"
    ),
    MAINTENANCE(
        id = "maintenance",
        title = "Home Maintenance",
        emoji = "🏠",
        scopeBadge = "Whole home",
        shortDescription = "Fixes, service schedules, filters & warranties.",
        placeholder = "What needs fixing or maintaining?"
    ),
    FIND(
        id = "find",
        title = "Find Something",
        emoji = "📦",
        scopeBadge = "Your whole inventory",
        shortDescription = "Locate objects, find where you put things & check stock.",
        placeholder = "What are you looking for?"
    );

    companion object {
        fun fromId(id: String): AssistantMode = entries.find { it.id == id } ?: HOME_AI

        fun detectModeFromQuery(query: String): AssistantMode {
            val q = query.lowercase().trim()
            return when {
                q.contains("cook") || q.contains("dinner") || q.contains("recipe") || q.contains("eat") ||
                        q.contains("ingredient") || q.contains("chicken") || q.contains("pantry") || q.contains("fridge") ||
                        q.contains("meal") || q.contains("breakfast") || q.contains("lunch") -> COOKING

                q.contains("drill") || q.contains("garage") || q.contains("tool") || q.contains("workbench") ||
                        q.contains("pressure washer") || q.contains("socket") || q.contains("saw") || q.contains("screw") ||
                        q.contains("impact") || q.contains("toolbox") || q.contains("compressor") -> GARAGE

                q.contains("maintenance") || q.contains("due") || q.contains("service") || q.contains("filter") ||
                        q.contains("furnace") || q.contains("hvac") || q.contains("oil change") || q.contains("repair") ||
                        q.contains("fix") || q.contains("warranty") || q.contains("manual") || q.contains("overdue") -> MAINTENANCE

                q.contains("where") || q.contains("find") || q.contains("locate") || q.contains("do i have") ||
                        q.contains("where's") || q.contains("where did i put") -> FIND

                else -> HOME_AI
            }
        }
    }
}
