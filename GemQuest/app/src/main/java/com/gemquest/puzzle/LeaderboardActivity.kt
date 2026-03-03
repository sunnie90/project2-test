package com.gemquest.puzzle

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gemquest.puzzle.databinding.ActivityLeaderboardBinding
import org.json.JSONArray
import org.json.JSONObject

class LeaderboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLeaderboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadLeaderboard()

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadLeaderboard() {
        val prefs = getSharedPreferences("GemQuest", Context.MODE_PRIVATE)
        val leaderboardJson = prefs.getString("leaderboard", "[]")
        
        try {
            val jsonArray = JSONArray(leaderboardJson)
            val leaderboardText = StringBuilder()
            
            for (i in 0 until minOf(jsonArray.length(), 10)) {
                val entry = jsonArray.getJSONObject(i)
                val score = entry.getInt("score")
                val level = entry.getInt("level")
                val date = entry.getString("date")
                
                leaderboardText.append("#${i + 1}  ")
                leaderboardText.append("Lv.$level  |  ")
                leaderboardText.append("$date  |  ")
                leaderboardText.append("$score\n\n")
            }
            
            if (jsonArray.length() == 0) {
                binding.leaderboardText.text = "아직 기록이 없습니다"
            } else {
                binding.leaderboardText.text = leaderboardText.toString()
            }
        } catch (e: Exception) {
            binding.leaderboardText.text = "리더보드를 불러올 수 없습니다"
        }
    }

    companion object {
        fun saveScore(context: Context, score: Int, level: Int) {
            val prefs = context.getSharedPreferences("GemQuest", Context.MODE_PRIVATE)
            val leaderboardJson = prefs.getString("leaderboard", "[]")
            
            try {
                val jsonArray = JSONArray(leaderboardJson)
                val newEntry = JSONObject().apply {
                    put("score", score)
                    put("level", level)
                    put("date", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(java.util.Date()))
                }
                
                jsonArray.put(newEntry)
                
                // Sort by score
                val sortedList = mutableListOf<JSONObject>()
                for (i in 0 until jsonArray.length()) {
                    sortedList.add(jsonArray.getJSONObject(i))
                }
                sortedList.sortByDescending { it.getInt("score") }
                
                // Keep top 10
                val newArray = JSONArray()
                for (i in 0 until minOf(sortedList.size, 10)) {
                    newArray.put(sortedList[i])
                }
                
                prefs.edit().putString("leaderboard", newArray.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
