package com.gemquest.puzzle

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gemquest.puzzle.adapter.GemAdapter
import com.gemquest.puzzle.databinding.ActivityGameBinding
import com.gemquest.puzzle.model.Position
import com.gemquest.puzzle.viewmodel.GameViewModel

class GameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()
    private lateinit var gemAdapter: GemAdapter
    private var selectedPosition: Position? = null
    private lateinit var vibrator: Vibrator
    private lateinit var soundPool: SoundPool
    private var matchSound: Int = 0
    private var comboSound: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAudio()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupAudio() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load sounds (you would need actual sound files)
        // matchSound = soundPool.load(this, R.raw.match_sound, 1)
        // comboSound = soundPool.load(this, R.raw.combo_sound, 1)
    }

    private fun setupRecyclerView() {
        gemAdapter = GemAdapter { position ->
            handleGemClick(position)
        }

        binding.recyclerViewGrid.apply {
            layoutManager = GridLayoutManager(this@GameActivity, 8)
            adapter = gemAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        viewModel.grid.observe(this) { grid ->
            val flatList = grid.flatten()
            gemAdapter.submitList(flatList)
        }

        viewModel.gameState.observe(this) { state ->
            binding.textScore.text = state.score.toString()
            binding.textGoal.text = state.goal.toString()
            binding.textMoves.text = state.moves.toString()
            binding.textLevel.text = state.level.toString()

            if (state.isLevelComplete) {
                showLevelCompleteDialog()
            } else if (state.isGameOver) {
                showGameOverDialog()
            }
        }

        viewModel.comboMessage.observe(this) { message ->
            if (message != null) {
                showComboMessage(message)
                playSound(comboSound)
            }
        }
    }

    private fun handleGemClick(position: Position) {
        if (selectedPosition == null) {
            selectedPosition = position
            gemAdapter.setSelectedPosition(position)
            vibrate(50)
        } else {
            val firstPosition = selectedPosition!!
            gemAdapter.clearSelection()

            if (firstPosition == position) {
                selectedPosition = null
            } else if (firstPosition.isAdjacentTo(position)) {
                viewModel.swapGems(firstPosition, position)
                selectedPosition = null
                vibrate(50)
            } else {
                selectedPosition = position
                gemAdapter.setSelectedPosition(position)
            }
        }
    }

    private fun showComboMessage(message: String) {
        binding.textCombo.apply {
            text = message
            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0.5f
            scaleY = 0.5f

            animate()
                .alpha(1f)
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(300)
                .withEndAction {
                    animate()
                        .alpha(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(700)
                        .withEndAction {
                            visibility = View.GONE
                        }
                }
        }
    }

    private fun showLevelCompleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("레벨 완료! 🎉")
            .setMessage("점수: ${viewModel.gameState.value?.score}\n\n다음 레벨로 진행하시겠습니까?")
            .setPositiveButton("다음 레벨") { _, _ ->
                viewModel.nextLevel()
            }
            .setNegativeButton("메인 메뉴") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showGameOverDialog() {
        val score = viewModel.gameState.value?.score ?: 0
        val level = viewModel.gameState.value?.level ?: 1

        AlertDialog.Builder(this)
            .setTitle("게임 오버")
            .setMessage("최종 점수: $score\n도달 레벨: $level")
            .setPositiveButton("다시 시작") { _, _ ->
                viewModel.startNewGame()
            }
            .setNegativeButton("메인 메뉴") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun vibrate(duration: Long) {
        if (vibrator.hasVibrator()) {
            val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        }
    }

    private fun playSound(soundId: Int) {
        if (soundId != 0) {
            soundPool.play(soundId, 0.3f, 0.3f, 1, 0, 1f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}
