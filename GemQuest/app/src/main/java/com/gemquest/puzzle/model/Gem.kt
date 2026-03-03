package com.gemquest.puzzle.model

import androidx.annotation.ColorRes
import com.gemquest.puzzle.R

enum class GemType(val emoji: String, @ColorRes val colorRes: Int) {
    RUBY("💎", R.color.ruby),
    SAPPHIRE("💙", R.color.sapphire),
    EMERALD("💚", R.color.emerald),
    TOPAZ("⭐", R.color.topaz),
    AMETHYST("💜", R.color.amethyst),
    DIAMOND("✨", R.color.diamond);

    companion object {
        fun random(): GemType {
            return values().random()
        }
    }
}

data class Gem(
    val type: GemType,
    var row: Int,
    var col: Int,
    var isMatched: Boolean = false,
    var isSpecial: Boolean = false
)

data class Position(
    val row: Int,
    val col: Int
) {
    fun isAdjacentTo(other: Position): Boolean {
        val rowDiff = kotlin.math.abs(row - other.row)
        val colDiff = kotlin.math.abs(col - other.col)
        return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)
    }
}
