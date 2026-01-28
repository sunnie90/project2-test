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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project2.sudoku.ui.theme.SudokuTheme
import kotlinx.coroutines.delay
import com.project2.sudoku.model.SudokuCell
import org.json.JSONArray
import org.json.JSONObject

enum class Difficulty(val label: String, val color: Color, val textColor: Color) {
    EASY("초급 (Easy)", Color(0xFFC8E6C9), Color(0xFF1B5E20)),
    MEDIUM("중급 (Normal)", Color(0xFFFFCC80), Color(0xFFE65100)),
    HARD("고급 (Hard)", Color(0xFFEF9A9A), Color(0xFFB71C1C))
}

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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sudoku_prefs", Context.MODE_PRIVATE) }

    // 1. 상태 선언 (초기화는 나중에)
    var difficulty by remember { mutableStateOf<Difficulty?>(null) }
    var gameSize by remember { mutableIntStateOf(0) }
    var isInitialized by remember { mutableStateOf(false) }

    // 2. 앱 실행 시 저장된 게임이 있는지 단 한 번만 체크
    LaunchedEffect(Unit) {
        val savedBoard = prefs.getString("saved_board", null)
        val savedSize = prefs.getInt("saved_size", 0)
        val savedDiffName = prefs.getString("saved_difficulty", null)

        if (savedBoard != null && savedSize != 0 && savedDiffName != null) {
            difficulty = Difficulty.valueOf(savedDiffName)
            gameSize = savedSize
        }
        isInitialized = true
    }

    if (!isInitialized) return // 로딩 중에는 아무것도 안 보여줌

    if (difficulty == null) {
        // [메인 화면: 난이도 선택]
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("SUDOKU MASTER", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(40.dp))
            Difficulty.values().forEach { diff ->
                Button(
                    onClick = { difficulty = diff },
                    modifier = Modifier.fillMaxWidth(0.6f).padding(8.dp).height(65.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = diff.color, contentColor = diff.textColor)
                ) {
                    Text(diff.label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else if (gameSize == 0) {
        // [사이즈 선택 화면]
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("MODE: ${difficulty!!.label}", color = difficulty!!.color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("사이즈를 선택하세요", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(30.dp))
            listOf(5, 7, 9).forEach { size ->
                val bestTime = prefs.getLong("best_time_${size}_${difficulty!!.name}", 0L)
                OutlinedButton(
                    onClick = { gameSize = size },
                    modifier = Modifier.fillMaxWidth(0.7f).padding(8.dp).height(80.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, difficulty!!.color),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${size} x ${size}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (bestTime > 0L) Text("🏆 BEST: ${formatTime(bestTime)}", fontSize = 12.sp, color = Color.Yellow)
                        else Text("기록 없음", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
            TextButton(onClick = { difficulty = null }) { Text("난이도 다시 선택", color = Color.Gray) }
        }
    } else {
        // [실제 게임 화면]
        SudokuGameScreen(size = gameSize, difficulty = difficulty!!, onBack = {
            gameSize = 0
            difficulty = null
        })
    }
}

@Composable
fun SudokuGameScreen(size: Int, difficulty: Difficulty, onBack: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sudoku_prefs", Context.MODE_PRIVATE) }

    // 데이터 복구 로직
    val savedBoardJson = prefs.getString("saved_board", null)
    val isResume = savedBoardJson != null && prefs.getInt("saved_size", 0) == size

    val initialBoard = remember {
        if (isResume) deserializeBoard(savedBoardJson!!)
        else generateValidSudoku(size, difficulty)
    }

    var cells by remember { mutableStateOf(initialBoard) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var lives by remember { mutableIntStateOf(if (isResume) prefs.getInt("saved_lives", 5) else 5) }
    var timerSeconds by remember { mutableLongStateOf(if (isResume) prefs.getLong("saved_timer", 0L) else 0L) }
    var currentStreak by remember { mutableIntStateOf(prefs.getInt("current_streak_$size", 0)) }
    var bestTime by remember { mutableLongStateOf(prefs.getLong("best_time_${size}_${difficulty.name}", 0L)) }

    var isTimerRunning by remember { mutableStateOf(true) }
    var showWinDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var isNewBest by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(" ", selection = TextRange(1))) }

    fun autoSave() {
        prefs.edit().apply {
            putString("saved_board", serializeBoard(cells))
            putInt("saved_size", size)
            putString("saved_difficulty", difficulty.name)
            putInt("saved_lives", lives)
            putLong("saved_timer", timerSeconds)
            apply()
        }
    }

    fun clearSave() {
        prefs.edit().remove("saved_board").remove("saved_size").remove("saved_difficulty").remove("saved_lives").remove("saved_timer").apply()
    }

    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning) {
            delay(1000L)
            timerSeconds++
            if (timerSeconds % 3 == 0L) autoSave() // 3초마다 자동 저장
        }
    }

    BackHandler {
        if (selectedIndex != -1) {
            selectedIndex = -1
            focusManager.clearFocus()
        } else showExitDialog = true
    }

    fun handleInput(input: String) {
        if (selectedIndex !in cells.indices || cells[selectedIndex].isFixed || !isTimerRunning) return
        val newList = cells.toMutableList()
        var processed = false

        if (input.isEmpty()) {
            newList[selectedIndex] = newList[selectedIndex].copy(value = 0, isError = false)
            cells = newList
            processed = true
        } else {
            val char = input.last()
            if (char.isDigit()) {
                val num = char.toString().toInt()
                if (num in 1..size) {
                    newList[selectedIndex] = newList[selectedIndex].copy(value = num)
                    cells = checkBoardValidity(newList, selectedIndex, size) {
                        lives--
                        if (lives <= 0) {
                            currentStreak = 0
                            prefs.edit().putInt("current_streak_$size", 0).apply()
                            isTimerRunning = false
                            clearSave()
                        }
                    }
                    processed = true
                }
            }
        }

        if (processed) {
            autoSave()
            selectedIndex = -1
            focusManager.clearFocus()
            if (cells.all { it.value != 0 && !it.isError }) {
                isTimerRunning = false
                currentStreak++
                prefs.edit().putInt("current_streak_$size", currentStreak).apply()
                if (bestTime == 0L || timerSeconds < bestTime) {
                    bestTime = timerSeconds
                    isNewBest = true
                    prefs.edit().putLong("best_time_${size}_${difficulty.name}", timerSeconds).apply()
                }
                clearSave()
                showWinDialog = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = textFieldValue,
            onValueChange = { nv ->
                if (nv.text.length < textFieldValue.text.length) handleInput("")
                else if (nv.text.length > 1) handleInput(nv.text)
                textFieldValue = TextFieldValue(" ", selection = TextRange(1))
            },
            modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
        )

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = { showExitDialog = true }) { Text("🏠", color = Color.White) }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(if(size==9)1f else 0.8f).padding(bottom=8.dp), Arrangement.SpaceBetween, Alignment.Bottom) {
                        Column {
                            Text("TIME ${formatTime(timerSeconds)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(if (bestTime > 0L) "🏆 BEST: ${formatTime(bestTime)}" else "🏆 BEST: --:--", fontSize = 12.sp, color = Color.Yellow)
                            Text(difficulty.label, color = difficulty.color, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${"❤️".repeat(lives.coerceAtLeast(0))}", fontSize = 16.sp)
                            Text("🔥 STREAK: $currentStreak", fontSize = 10.sp, color = Color.Cyan)
                        }
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(size), modifier = Modifier.fillMaxWidth(if(size==9)1f else 0.8f).aspectRatio(1f).border(2.dp, Color.White)) {
                        itemsIndexed(cells) { index, cell ->
                            val r = index / size; val c = index % size
                            val boxColor = if (size == 9 && ((r/3+c/3)%2==0)) Color(0xFF2A2A2A) else Color.Transparent
                            Box(modifier = Modifier.aspectRatio(1f).border(0.5.dp, Color(0xFF444444)).background(if (index == selectedIndex) Color(0xFF555555) else boxColor).clickable { if (isTimerRunning) { focusManager.clearFocus(); selectedIndex = index; focusRequester.requestFocus() } }, contentAlignment = Alignment.Center) {
                                if (cell.value != 0) Text(cell.value.toString(), fontSize = if (size == 9) 20.sp else 26.sp, fontWeight = FontWeight.Bold, color = if (cell.isError) Color.Red else if (cell.isFixed) Color.White else Color.Cyan)
                            }
                        }
                    }
                }
            }
            Button(onClick = { clearSave(); onBack() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))) {
                Text("RESET & QUIT")
            }
        }

        if (showExitDialog) {
            AlertDialog(onDismissRequest = { showExitDialog = false }, title = { Text("알림") }, text = { Text("진행 상황은 자동 저장됩니다.") },
                confirmButton = { Button(onClick = { showExitDialog = false; onBack() }) { Text("확인") } },
                dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("취소") } }
            )
        }
        if (showWinDialog) {
            AlertDialog(onDismissRequest = {}, title = { Text("🎉 SUCCESS!") }, text = { Text("시간: ${formatTime(timerSeconds)}") },
                confirmButton = { Button(onClick = { clearSave(); onBack() }) { Text("확인") } }
            )
        }
        if (lives <= 0) {
            AlertDialog(onDismissRequest = {}, title = { Text("💀 GAME OVER") }, confirmButton = { Button(onClick = { clearSave(); onBack() }) { Text("확인") } })
        }
    }
}

// --- 보조 함수 (JSON) ---
fun serializeBoard(cells: List<SudokuCell>): String {
    val array = JSONArray()
    cells.forEach { cell ->
        val obj = JSONObject().apply { put("v", cell.value); put("f", cell.isFixed); put("e", cell.isError) }
        array.put(obj)
    }
    return array.toString()
}

fun deserializeBoard(json: String): List<SudokuCell> {
    val array = JSONArray(json)
    return List(array.length()) { i ->
        val obj = array.getJSONObject(i)
        SudokuCell(0, 0, obj.getInt("v"), obj.getBoolean("f"), obj.getBoolean("e"), emptySet())
    }
}

fun formatTime(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

fun generateValidSudoku(size: Int, difficulty: Difficulty): List<SudokuCell> {
    val board = Array(size) { IntArray(size) { 0 } }
    fun solve(r: Int, c: Int): Boolean {
        if (r == size) return true
        val nR = if (c == size - 1) r + 1 else r
        val nC = (c + 1) % size
        val nums = (1..size).shuffled()
        for (n in nums) {
            if (isSafe(board, r, c, n, size)) {
                board[r][c] = n; if (solve(nR, nC)) return true; board[r][c] = 0
            }
        }
        return false
    }
    solve(0, 0)
    val targetHints = when(size) { 5 -> 12; 7 -> 24; else -> 35 }
    val isFixed = MutableList(size * size) { true }
    val indices = (0 until size * size).shuffled()
    var currentCount = size * size
    for (idx in indices) { if (currentCount <= targetHints) break; isFixed[idx] = false; currentCount-- }
    return List(size * size) { i -> SudokuCell(i/size, i%size, if(isFixed[i]) board[i/size][i%size] else 0, isFixed[i], false, emptySet()) }
}

fun isSafe(board: Array<IntArray>, r: Int, c: Int, n: Int, size: Int): Boolean {
    for (i in 0 until size) if (board[r][i] == n || board[i][c] == n) return false
    if (size == 9) {
        val br = (r/3)*3; val bc = (c/3)*3
        for (i in 0..2) for (j in 0..2) if (board[br+i][bc+j] == n) return false
    }
    return true
}

fun checkBoardValidity(current: List<SudokuCell>, lastIdx: Int, size: Int, onWrong: () -> Unit): List<SudokuCell> {
    val cell = current[lastIdx]
    val r = lastIdx / size; val c = lastIdx % size
    val isDup = current.filterIndexed { i, other ->
        if (i == lastIdx || other.value == 0) return@filterIndexed false
        val ir = i/size; val ic = i%size
        val sameBox = size == 9 && (ir/3 == r/3 && ic/3 == c/3)
        (ir == r || ic == c || sameBox) && other.value == cell.value
    }.isNotEmpty()
    if (isDup) onWrong()
    return current.mapIndexed { i, item -> if (i == lastIdx) item.copy(isError = isDup) else item }
}