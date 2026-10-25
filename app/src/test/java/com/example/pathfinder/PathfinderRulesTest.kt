package com.example.pathfinder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PathfinderRulesTest {
    @Test fun levelsAdvanceAtEachHundredXp() {
        assertEquals(1, PathfinderRules.levelFor(0))
        assertEquals(1, PathfinderRules.levelFor(99))
        assertEquals(2, PathfinderRules.levelFor(100))
        assertEquals(0.5f, PathfinderRules.levelProgress(150), 0.001f)
    }

    @Test fun streakUsesUniqueConsecutiveLocalDatesAndAllowsYesterday() {
        val today = LocalDate.of(2026, 10, 23)
        assertEquals(2, PathfinderRules.currentStreak(setOf(today.minusDays(2), today.minusDays(1)), today))
        assertEquals(1, PathfinderRules.currentStreak(setOf(today), today))
        assertEquals(0, PathfinderRules.currentStreak(setOf(today.minusDays(2)), today))
    }

    @Test fun dailyMissionPrefersGoalAndAvoidsCompletedMissions() {
        val day = LocalDate.of(2026, 10, 23)
        val first = PathfinderRules.missionForDay(day, emptySet(), setOf("Ser más productiva"))
        org.junit.Assert.assertTrue(first.category in setOf("Productividad", "Planificación", "Enfoque", "Organización"))
        val next = PathfinderRules.missionForDay(day, setOf(first.id), setOf("Ser más productiva"))
        org.junit.Assert.assertNotEquals(first.id, next.id)
    }

    @Test fun quizScoreIsClampedAndHandlesEmptyQuizzes() {
        assertEquals(100, PathfinderRules.quizScore(2, 2))
        assertEquals(50, PathfinderRules.quizScore(1, 2))
        assertEquals(0, PathfinderRules.quizScore(0, 0))
        assertEquals(100, PathfinderRules.quizScore(4, 2))
    }
}
