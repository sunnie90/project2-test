package com.gemquest.puzzle.model

data class GameState(
    var score: Int = 0,
    var level: Int = 1,
    var moves: Int = 30,
    var goal: Int = 1000,
    var combo: Int = 0,
    var isGameOver: Boolean = false,
    var isLevelComplete: Boolean = false
) {
    fun reset() {
        score = 0
        level = 1
        moves = 30
        goal = 1000
        combo = 0
        isGameOver = false
        isLevelComplete = false
    }

    fun nextLevel() {
        level++
        goal = 1000 + (level - 1) * 500
        moves = 30
        isLevelComplete = false
    }

    fun addScore(points: Int, comboMultiplier: Int = 1) {
        score += points * comboMultiplier
    }

    fun useMove(): Boolean {
        if (moves > 0) {
            moves--
            return true
        }
        return false
    }

    fun checkWinCondition(): Boolean {
        if (score >= goal) {
            isLevelComplete = true
            return true
        }
        return false
    }

    fun checkLoseCondition(): Boolean {
        if (moves <= 0 && score < goal) {
            isGameOver = true
            return true
        }
        return false
    }
}
