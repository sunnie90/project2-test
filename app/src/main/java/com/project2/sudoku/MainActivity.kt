package com.project2.sudoku

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project2.sudoku.ui.theme.SudokuTheme
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlin.random.Random
import com.project2.sudoku.model.SudokuCell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SudokuTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1A1A1A)) {
                    SudokuGameApp()
                }
            }
        }
    }
}

@Composable
fun SudokuGameApp() {
    var gameSize by remember { mutableIntStateOf(0) }
    if (gameSize == 0) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("SUDOKU MASTER", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(40.dp))
            listOf(5, 7, 9).forEach { size ->
                Button(onClick = { gameSize = size }, Modifier.fillMaxWidth(0.6f).padding(8.dp).height(60.dp)) {
                    val context = LocalContext.current
                    val prefs = context.getSharedPreferences("sudoku_prefs", Context.MODE_PRIVATE)
                    val bestTime = prefs.getLong("best_time_$size", Long.MAX_VALUE)
                    val timeStr = if (bestTime == Long.MAX_VALUE) "No Record" else formatTime(bestTime)
                    Text("${size}x${size} (Best: $timeStr)", fontSize = 16.sp)
                }
            }
        }
    } else {
        SudokuGameScreen(size = gameSize, onBack = { gameSize = 0 })
    }
}

// 시간 포맷 함수 (초 -> 00:00)
fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@Composable
fun SudokuGameScreen(size: Int, onBack: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sudoku_prefs", Context.MODE_PRIVATE) }

    var cells by remember { mutableStateOf<List<SudokuCell>>(loadGame(prefs, size) ?: generateValidSudoku(size)) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var lives by remember { mutableIntStateOf(prefs.getInt("saved_lives_$size", 3)) }
    var currentStreak by remember { mutableIntStateOf(prefs.getInt("current_streak_$size", 0)) }

    // 타이머 관련 상태
    var timerSeconds by remember { mutableLongStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(true) }
    var showWinDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var isNewBest by remember { mutableStateOf(false) }

    // 타이머 루프
    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning) {
            delay(1000L)
            timerSeconds++
        }
    }

    BackHandler(enabled = selectedIndex != -1) {
        selectedIndex = -1
        focusManager.clearFocus(force = true)
    }

    fun startNewGame() {
        cells = generateValidSudoku(size)
        lives = 3
        timerSeconds = 0L
        isTimerRunning = true
        isNewBest = false
        saveGame(prefs, size, cells, lives)
    }

    fun handleInput(input: String) {
        if (selectedIndex !in cells.indices || cells[selectedIndex].isFixed || !isTimerRunning) return

        if (input.isEmpty() || input == " ") {
            val newList = cells.toMutableList()
            newList[selectedIndex] = newList[selectedIndex].copy(value = 0, isError = false)
            cells = newList
            return
        }

        val lastChar = input.last()
        if (lastChar.isDigit()) {
            val num = lastChar.toString().toInt()
            if (num in 1..size) {
                val newList = cells.toMutableList()
                newList[selectedIndex] = newList[selectedIndex].copy(value = num)

                cells = checkBoardValidity(newList, selectedIndex, size) {
                    lives--
                    if (lives <= 0) {
                        currentStreak = 0
                        isTimerRunning = false
                        prefs.edit().putInt("current_streak_$size", 0).apply()
                    }
                }

                // 클리어 판정
                if (cells.all { it.value != 0 && !it.isError }) {
                    isTimerRunning = false
                    currentStreak++

                    // 최단 기록 갱신 체크
                    val bestTime = prefs.getLong("best_time_$size", Long.MAX_VALUE)
                    if (timerSeconds < bestTime) {
                        isNewBest = true
                        prefs.edit().putLong("best_time_$size", timerSeconds).apply()
                    }

                    prefs.edit().putInt("current_streak_$size", currentStreak).apply()
                    showWinDialog = true
                }

                if (!cells[selectedIndex].isError) {
                    selectedIndex = -1
                    focusManager.clearFocus(force = true)
                }
                saveGame(prefs, size, cells, lives)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        TextField(
            value = " ",
            onValueChange = { handleInput(it) },
            modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // 상단 바 (홈, 타이머, 연승)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("🏠", color = Color.White) }

                // [추가] 중앙 시계 배치
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TIME", fontSize = 10.sp, color = Color.Gray)
                    Text(formatTime(timerSeconds), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Text("🔥 $currentStreak", color = Color.Yellow, fontWeight = FontWeight.Bold)
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceBetween) {
                Text("Mode: ${size}x${size}", fontSize = 12.sp, color = Color.Gray)
                Text("LIVES: ${"❤️".repeat(lives.coerceAtLeast(0))}", fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))

            // 스도쿠 판
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                LazyVerticalGrid(columns = GridCells.Fixed(size), Modifier.fillMaxWidth().aspectRatio(1f).border(2.dp, Color.White)) {
                    itemsIndexed(cells) { index, cell ->
                        val r = index / size; val c = index % size
                        val boxColor = if (size == 9 && ((r/3 + c/3) % 2 == 0)) Color(0xFF2A2A2A) else Color.Transparent
                        Box(
                            Modifier.aspectRatio(1f).border(0.5.dp, Color(0xFF444444))
                                .background(if (index == selectedIndex) Color(0xFF555555) else boxColor)
                                .clickable {
                                    if (isTimerRunning) {
                                        focusManager.clearFocus(force = true)
                                        selectedIndex = index
                                        focusRequester.requestFocus()
                                    }
                                },
                            Alignment.Center
                        ) {
                            if (cell.value != 0) {
                                Text(
                                    text = cell.value.toString(),
                                    fontSize = if(size == 9) 20.sp else 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cell.isError) Color.Red else if (cell.isFixed) Color.White else Color.Cyan
                                )
                            }
                        }
                    }
                }
            }

            Button(onClick = {
                if (currentStreak > 0) showResetConfirm = true
                else startNewGame()
            }, Modifier.fillMaxWidth().padding(vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))) {
                Text("NEW GAME / RESET")
            }
        }

        // --- 다이얼로그들 ---
        if (showWinDialog) {
            AlertDialog(onDismissRequest = {},
                title = { Text(if(isNewBest) "🎊 기록 경신! 🎊" else "🎉 SUCCESS!") },
                text = { Text("시간: ${formatTime(timerSeconds)}\n현재 연승: $currentStreak") },
                confirmButton = { Button(onClick = { showWinDialog = false; startNewGame() }) { Text("NEXT GAME") } }
            )
        }

        if (showResetConfirm) {
            AlertDialog(onDismissRequest = { showResetConfirm = false },
                title = { Text("⚠️ 연승 포기") },
                text = { Text("지금 새 게임을 시작하면 $currentStreak 연승이 깨집니다.") },
                confirmButton = { TextButton(onClick = { showResetConfirm = false; currentStreak = 0; startNewGame() }) { Text("확인", color = Color.Red) } },
                dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("취소") } }
            )
        }

        if (lives <= 0) {
            AlertDialog(onDismissRequest = {}, title = { Text("💀 GAME OVER") },
                text = { Text("기록이 초기화되었습니다.") },
                confirmButton = { Button(onClick = { startNewGame() }) { Text("RETRY") } }
            )
        }
    }
}

// 아래 로직(generateValidSudoku, isSafe, checkBoardValidity, saveGame, loadGame)은 이전과 동일
fun generateValidSudoku(size: Int): List<SudokuCell> {
    val board = Array(size) { IntArray(size) { 0 } }
    fun solve(r: Int, c: Int): Boolean {
        if (r == size) return true
        val nextR = if (c == size - 1) r + 1 else r
        val nextC = (c + 1) % size
        val nums = (1..size).shuffled()
        for (n in nums) {
            if (isSafe(board, r, c, n, size)) {
                board[r][c] = n
                if (solve(nextR, nextC)) return true
                board[r][c] = 0
            }
        }
        return false
    }
    solve(0, 0)
    val hintCount = when(size) { 5 -> 10; 7 -> 20; else -> 32 }
    val showIndices = (0 until size * size).shuffled().take(hintCount)
    return List(size * size) { i ->
        val r = i / size; val c = i % size
        val isFixed = i in showIndices
        SudokuCell(r, c, if (isFixed) board[r][c] else 0, isFixed, false, emptySet())
    }
}

fun isSafe(board: Array<IntArray>, r: Int, c: Int, n: Int, size: Int): Boolean {
    for (i in 0 until size) if (board[r][i] == n || board[i][c] == n) return false
    if (size == 9) {
        val br = (r / 3) * 3; val bc = (c / 3) * 3
        for (i in 0..2) for (j in 0..2) if (board[br + i][bc + j] == n) return false
    }
    return true
}

fun checkBoardValidity(current: List<SudokuCell>, lastIdx: Int, size: Int, onWrong: () -> Unit): List<SudokuCell> {
    val cell = current[lastIdx]
    val r = lastIdx / size; val c = lastIdx % size
    val isDup = current.filterIndexed { i, other ->
        if (i == lastIdx || other.value == 0) return@filterIndexed false
        val ir = i / size; val ic = i % size
        val sameRow = ir == r
        val sameCol = ic == c
        val sameBox = size == 9 && (ir/3 == r/3 && ic/3 == c/3)
        (sameRow || sameCol || sameBox) && other.value == cell.value
    }.isNotEmpty()
    if (isDup) onWrong()
    return current.mapIndexed { i, item -> if (i == lastIdx) item.copy(isError = isDup) else item }
}

fun saveGame(prefs: android.content.SharedPreferences, size: Int, cells: List<SudokuCell>, lives: Int) {
    val array = JSONArray()
    cells.forEach { cell ->
        val obj = JSONObject().put("v", cell.value).put("f", cell.isFixed).put("e", cell.isError)
        array.put(obj)
    }
    prefs.edit().putString("saved_board_$size", array.toString()).putInt("saved_lives_$size", lives).apply()
}

fun loadGame(prefs: android.content.SharedPreferences, size: Int): List<SudokuCell>? {
    val json = prefs.getString("saved_board_$size", null) ?: return null
    return try {
        val array = JSONArray(json)
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            SudokuCell(i / size, i % size, obj.getInt("v"), obj.getBoolean("f"), obj.getBoolean("e"), emptySet())
        }
    } catch (e: Exception) { null }
}