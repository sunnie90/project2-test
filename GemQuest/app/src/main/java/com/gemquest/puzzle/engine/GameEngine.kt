package com.gemquest.puzzle.engine

import com.gemquest.puzzle.model.Gem
import com.gemquest.puzzle.model.GemType
import com.gemquest.puzzle.model.Position

class GameEngine(private val gridSize: Int = 8) {
    private var grid: Array<Array<Gem?>> = Array(gridSize) { arrayOfNulls(gridSize) }

    init {
        initializeGrid()
    }

    fun initializeGrid() {
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                grid[row][col] = Gem(GemType.random(), row, col)
            }
        }
        // Remove initial matches
        var attempts = 0
        while (findAllMatches().isNotEmpty() && attempts < 20) {
            findAllMatches().forEach { pos ->
                grid[pos.row][pos.col] = Gem(GemType.random(), pos.row, pos.col)
            }
            attempts++
        }
    }

    fun getGrid(): Array<Array<Gem?>> = grid

    fun getGemAt(row: Int, col: Int): Gem? {
        return if (isValidPosition(row, col)) grid[row][col] else null
    }

    fun swapGems(pos1: Position, pos2: Position): Boolean {
        if (!isValidPosition(pos1.row, pos1.col) || !isValidPosition(pos2.row, pos2.col)) {
            return false
        }

        val gem1 = grid[pos1.row][pos1.col]
        val gem2 = grid[pos2.row][pos2.col]

        if (gem1 == null || gem2 == null) return false

        // Swap
        grid[pos1.row][pos1.col] = gem2.copy(row = pos1.row, col = pos1.col)
        grid[pos2.row][pos2.col] = gem1.copy(row = pos2.row, col = pos2.col)

        // Check if swap creates matches
        val matches = findAllMatches()
        if (matches.isNotEmpty()) {
            return true
        } else {
            // Swap back
            grid[pos1.row][pos1.col] = gem1
            grid[pos2.row][pos2.col] = gem2
            return false
        }
    }

    fun findAllMatches(): List<Position> {
        val matches = mutableSetOf<Position>()

        // Horizontal matches
        for (row in 0 until gridSize) {
            var col = 0
            while (col < gridSize - 2) {
                val gem = grid[row][col] ?: continue
                var matchLength = 1

                while (col + matchLength < gridSize &&
                    grid[row][col + matchLength]?.type == gem.type) {
                    matchLength++
                }

                if (matchLength >= 3) {
                    for (i in 0 until matchLength) {
                        matches.add(Position(row, col + i))
                    }
                }
                col += matchLength
            }
        }

        // Vertical matches
        for (col in 0 until gridSize) {
            var row = 0
            while (row < gridSize - 2) {
                val gem = grid[row][col] ?: continue
                var matchLength = 1

                while (row + matchLength < gridSize &&
                    grid[row + matchLength][col]?.type == gem.type) {
                    matchLength++
                }

                if (matchLength >= 3) {
                    for (i in 0 until matchLength) {
                        matches.add(Position(row + i, col))
                    }
                }
                row += matchLength
            }
        }

        return matches.toList()
    }

    fun removeMatches(matches: List<Position>) {
        matches.forEach { pos ->
            grid[pos.row][pos.col] = null
        }
    }

    fun dropGems(): Boolean {
        var dropped = false

        for (col in 0 until gridSize) {
            var emptyRow = gridSize - 1
            for (row in gridSize - 1 downTo 0) {
                grid[row][col]?.let { gem ->
                    if (row != emptyRow) {
                        grid[emptyRow][col] = gem.copy(row = emptyRow)
                        grid[row][col] = null
                        dropped = true
                    }
                    emptyRow--
                }
            }
        }

        return dropped
    }

    fun fillEmptySpaces() {
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                if (grid[row][col] == null) {
                    grid[row][col] = Gem(GemType.random(), row, col)
                }
            }
        }
    }

    private fun isValidPosition(row: Int, col: Int): Boolean {
        return row in 0 until gridSize && col in 0 until gridSize
    }

    fun hasValidMoves(): Boolean {
        // Check if any swap would create a match
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Try swapping with adjacent gems
                val positions = listOf(
                    Position(row - 1, col),
                    Position(row + 1, col),
                    Position(row, col - 1),
                    Position(row, col + 1)
                )

                positions.forEach { pos ->
                    if (isValidPosition(pos.row, pos.col)) {
                        // Simulate swap
                        val currentPos = Position(row, col)
                        if (wouldCreateMatch(currentPos, pos)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun wouldCreateMatch(pos1: Position, pos2: Position): Boolean {
        val gem1 = grid[pos1.row][pos1.col]
        val gem2 = grid[pos2.row][pos2.col]

        if (gem1 == null || gem2 == null) return false

        // Temporarily swap
        grid[pos1.row][pos1.col] = gem2
        grid[pos2.row][pos2.col] = gem1

        val hasMatch = findAllMatches().isNotEmpty()

        // Swap back
        grid[pos1.row][pos1.col] = gem1
        grid[pos2.row][pos2.col] = gem2

        return hasMatch
    }
}
