package com.gemquest.puzzle

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gemquest.puzzle.databinding.ActivityTutorialBinding

class TutorialActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTutorialBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnStartGame.setOnClickListener {
            // Start game and close this activity
            finish()
        }
    }
}
