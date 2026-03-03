package com.gemquest.puzzle.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemquest.puzzle.engine.GameEngine
import com.gemquest.puzzle.model.GameState
import com.gemquest.puzzle.model.Gem
import com.gemquest.puzzle.model.Position
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {
    private val gameEngine = GameEngine()
    private val _gameState = MutableLiveData(GameState())
    val gameState: LiveData<GameState> = _gameState

    private val _grid = MutableLiveData<Array<Array<Gem?>>>()
    val grid: LiveData<Array<Array<Gem?>>> = _grid

    private val _comboMessage = MutableLiveData<String?>()
    val comboMessage: LiveData<String?> = _comboMessage

    private var isProcessing = false

    init {
        startNewGame()
    }

    fun startNewGame() {
        _gameState.value = GameState()
        gameEngine.initializeGrid()
        _grid.value = gameEngine.getGrid()
    }

    fun nextLevel() {
        _gameState.value?.nextLevel()
        gameEngine.initializeGrid()
        _grid.value = gameEngine.getGrid()
    }

    fun selectGem(position: Position): Boolean {
        if (isProcessing) return false

        val currentState = _gameState.value ?: return false
        if (currentState.isGameOver || currentState.isLevelComplete) return false

        return true
    }

    fun swapGems(pos1: Position, pos2: Position) {
        if (isProcessing) return
        if (!pos1.isAdjacentTo(pos2)) return

        viewModelScope.launch {
            isProcessing = true

            val swapped = gameEngine.swapGems(pos1, pos2)
            _grid.value = gameEngine.getGrid()

            if (swapped) {
                _gameState.value?.useMove()
                delay(300)
                processMatches()
            }

            isProcessing = false
            checkGameState()
        }
    }

    private suspend fun processMatches() {
        var combo = 0
        var matches = gameEngine.findAllMatches()

        while (matches.isNotEmpty()) {
            combo++
            val currentState = _gameState.value ?: return

            // Calculate score
            val points = matches.size * 10
            currentState.addScore(points, combo)
            currentState.combo = combo

            if (combo > 1) {
                _comboMessage.value = "${combo}x COMBO!"
                delay(1000)
                _comboMessage.value = null
            }

            // Remove matches
            gameEngine.removeMatches(matches)
            _grid.value = gameEngine.getGrid()
            delay(200)

            // Drop gems
            gameEngine.dropGems()
            _grid.value = gameEngine.getGrid()
            delay(300)

            // Fill empty spaces
            gameEngine.fillEmptySpaces()
            _grid.value = gameEngine.getGrid()
            delay(300)

            // Check for new matches
            matches = gameEngine.findAllMatches()
        }

        val currentState = _gameState.value ?: return
        currentState.combo = 0
        _gameState.value = currentState
    }

    private fun checkGameState() {
        val currentState = _gameState.value ?: return

        if (currentState.checkWinCondition()) {
            _gameState.value = currentState
        } else if (currentState.checkLoseCondition()) {
            _gameState.value = currentState
        }
    }

    fun resetComboMessage() {
        _comboMessage.value = null
    }
}
