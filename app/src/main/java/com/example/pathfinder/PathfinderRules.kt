package com.example.pathfinder

import java.time.LocalDate

/** Shared progression rules: each mission awards its configured XP once; one level is 100 XP. */
object PathfinderRules {
    fun levelFor(xp: Int): Int = (xp.coerceAtLeast(0) / 100) + 1
    fun levelProgress(xp: Int): Float = (xp.coerceAtLeast(0) % 100) / 100f

    /** Counts consecutive active local calendar dates, allowing activity from yesterday to remain active today. */
    fun currentStreak(activityDates: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        var cursor = when {
            today in activityDates -> today
            today.minusDays(1) in activityDates -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (cursor in activityDates) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun missionForDay(day: LocalDate, completedIds: Set<Int>, goals: Set<String>): Mission {
        val wanted = when {
            goals.any { it.contains("hábito", true) } -> setOf("Hábitos")
            goals.any { it.contains("productiv", true) } -> setOf("Productividad", "Planificación", "Enfoque", "Organización")
            goals.any { it.contains("estrés", true) } -> setOf("Bienestar", "Reflexión")
            else -> emptySet()
        }
        val preferred = missions.filter { it.category in wanted && it.id !in completedIds }
        val remaining = missions.filter { it.id !in completedIds }
        val candidates = preferred.ifEmpty { remaining.ifEmpty { missions } }
        return candidates[Math.floorMod(day.toEpochDay().toInt(), candidates.size)]
    }

    fun quizScore(correctAnswers: Int, totalQuestions: Int): Int =
        if (totalQuestions <= 0) 0 else ((correctAnswers.coerceIn(0, totalQuestions) * 100f) / totalQuestions).toInt()
}
