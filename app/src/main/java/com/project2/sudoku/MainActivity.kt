package com.project2.sudoku

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.core.view.WindowCompat
import com.project2.sudoku.ui.theme.SudokuTheme
import kotlinx.coroutines.delay
import com.project2.sudoku.model.SudokuCell
import org.json.JSONArray
import org.json.JSONObject

// --- 피드백 매니저 ---
class FeedbackManager(context: Context) {
    private val soundPool: SoundPool
    private var winSoundId: Int = -1
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    init {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
        winSoundId = soundPool.load(context, R.raw.win_sound, 1)
    }
    fun vibrateError() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(100)
    }
    fun playWinSound(isEnabled: Boolean) {
        if (isEnabled && winSoundId != -1) soundPool.play(winSoundId, 1f, 1f, 0, 0, 1f)
    }
}

enum class Difficulty(val label: String, val color: Color, val textColor: Color) {
    EASY("초급 (Easy)", Color(0xFFC8E6C9), Color(0xFF1B5E20)),
    MEDIUM("중급 (Normal)", Color(0xFFFFCC80), Color(0xFFE65100)),
    HARD("고급 (Hard)", Color(0xFFEF9A9A), Color(0xFFB71C1C))
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent { SudokuTheme { Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1A1A1A)) { SudokuGameApp() } } }
    }
}

@Composable
fun SudokuGameApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sudoku_prefs", Context.MODE_PRIVATE) }
    val feedbackManager = remember { FeedbackManager(context) }
    var difficulty by remember { mutableStateOf<Difficulty?>(null) }
    var gameSize by remember { mutableIntStateOf(0) }
    var isInitialized by remember { mutableStateOf(false) }
    var isSoundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }

    LaunchedEffect(Unit) {
        val savedBoard = prefs.getString("saved_board", null)
        val savedSize = prefs.getInt("saved_size", 0)
        val savedDiffName = prefs.getString("saved_difficulty", null)
        if (savedBoard != null && savedSize != 0 && savedDiffName != null) {
            try { difficulty = Difficulty.valueOf(savedDiffName); gameSize = savedSize } catch (e: Exception) { clearSave(prefs) }
        }
        isInitialized = true
    }

    if (!isInitialized) return

    Box(modifier = Modifier.fillMaxSize()) {
        if (difficulty == null) {
            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("SUDOKU MASTER", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(40.dp))
                Difficulty.values().forEach { diff ->
                    Button(onClick = { difficulty = diff }, modifier = Modifier.fillMaxWidth(0.6f).padding(8.dp).height(65.dp), colors = ButtonDefaults.buttonColors(containerColor = diff.color, contentColor = diff.textColor)) { Text(diff.label, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                IconButton(onClick = { isSoundEnabled = !isSoundEnabled; prefs.edit().putBoolean("sound_enabled", isSoundEnabled).apply() }) { Text(if (isSoundEnabled) "🔊" else "🔇", color = Color.Gray, fontSize = 24.sp) }
            }
        } else if (gameSize == 0) {
            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("MODE: ${difficulty!!.label}", color = difficulty!!.color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("사이즈를 선택하세요", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(30.dp))
                listOf(5, 7, 9).forEach { size ->
                    val bestTime = prefs.getLong("best_time_${size}_${difficulty!!.name}", 0L)
                    val streak = prefs.getInt("streak_${size}_${difficulty!!.name}", 0)
                    OutlinedButton(onClick = { gameSize = size }, modifier = Modifier.fillMaxWidth(0.7f).padding(8.dp).height(85.dp), border = androidx.compose.foundation.BorderStroke(2.dp, difficulty!!.color), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${size} x ${size}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Row { if (bestTime > 0L) Text("🏆 ${formatTime(bestTime)} ", fontSize = 12.sp, color = Color.Yellow); if (streak > 0) Text(" 🔥 $streak", fontSize = 12.sp, color = Color.Cyan) }
                        }
                    }
                }
                TextButton(onClick = { difficulty = null }) { Text("난이도 다시 선택", color = Color.Gray) }
            }
        } else {
            SudokuGameScreen(size = gameSize, difficulty = difficulty!!, feedbackManager = feedbackManager, isSoundEnabled = isSoundEnabled, onBack = { gameSize = 0; difficulty = null })
        }
    }
}

@Composable
fun SudokuGameScreen(size: Int, difficulty: Difficulty, feedbackManager: FeedbackManager, isSoundEnabled: Boolean, onBack: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sudoku_prefs", Context.MODE_PRIVATE) }
    val comboKey = "${size}_${difficulty.name}"
    val streakKey = "streak_$comboKey"
    val bestTimeKey = "best_time_$comboKey"

    val savedBoardJson = prefs.getString("saved_board", null)
    val isResume = savedBoardJson != null && prefs.getInt("saved_size", 0) == size && prefs.getString("saved_difficulty", "") == difficulty.name

    var cells by remember { mutableStateOf(if (isResume) deserializeBoard(savedBoardJson!!) else generateValidSudoku(size, difficulty)) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var lives by remember { mutableIntStateOf(if (isResume) prefs.getInt("saved_lives", 5) else 5) }
    var timerSeconds by remember { mutableLongStateOf(if (isResume) prefs.getLong("saved_timer", 0L) else 0L) }
    var currentStreak by remember { mutableIntStateOf(prefs.getInt(streakKey, 0)) }
    var bestTime by remember { mutableLongStateOf(prefs.getLong(bestTimeKey, 0L)) }
    var isTimerRunning by remember { mutableStateOf(true) }
    var showWinDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(" ", selection = TextRange(1))) }

    fun doClearSave() { prefs.edit().remove("saved_board").remove("saved_size").remove("saved_difficulty").remove("saved_lives").remove("saved_timer").apply() }
    fun autoSave() { prefs.edit().apply { putString("saved_board", serializeBoard(cells)); putInt("saved_size", size); putString("saved_difficulty", difficulty.name); putInt("saved_lives", lives); putLong("saved_timer", timerSeconds); apply() } }

    LaunchedEffect(isTimerRunning) { while (isTimerRunning) { delay(1000L); timerSeconds++; if (timerSeconds % 5 == 0L) autoSave() } }
    BackHandler { if (selectedIndex != -1) { selectedIndex = -1; focusManager.clearFocus() } else showExitDialog = true }

    // 힌트 사용 (수정됨: 틀린 칸도 힌트 적용 가능)
    fun useHint() {
        if (selectedIndex == -1 || !isTimerRunning || lives <= 1) return
        if (cells[selectedIndex].isFixed) return
        for (num in 1..size) {
            if (isValidHint(cells, selectedIndex, num, size)) {
                val newList = cells.toMutableList()
                newList[selectedIndex] = newList[selectedIndex].copy(value = num, isError = false)
                cells = newList; lives--; selectedIndex = -1; focusManager.clearFocus(); autoSave(); break
            }
        }
    }

    fun handleInput(input: String) {
        if (selectedIndex !in cells.indices || cells[selectedIndex].isFixed || !isTimerRunning) return
        val newList = cells.toMutableList(); var processed = false
        if (input.isEmpty()) { newList[selectedIndex] = newList[selectedIndex].copy(value = 0, isError = false); cells = newList; processed = true
        } else {
            val char = input.last()
            if (char.isDigit()) {
                val num = char.toString().toInt()
                if (num in 1..size) {
                    newList[selectedIndex] = newList[selectedIndex].copy(value = num)
                    cells = checkBoardValidity(newList, selectedIndex, size) {
                        feedbackManager.vibrateError(); lives--
                        if (lives <= 0) { isTimerRunning = false; currentStreak = 0; prefs.edit().putInt(streakKey, 0).apply(); doClearSave() }
                    }; processed = true
                }
            }
        }
        if (processed) {
            autoSave(); selectedIndex = -1; focusManager.clearFocus()
            if (cells.all { it.value != 0 && !it.isError }) {
                isTimerRunning = false; feedbackManager.playWinSound(isSoundEnabled)
                currentStreak++; prefs.edit().putInt(streakKey, currentStreak).apply()
                if (bestTime == 0L || timerSeconds < bestTime) { bestTime = timerSeconds; prefs.edit().putLong(bestTimeKey, timerSeconds).apply() }
                doClearSave(); showWinDialog = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
        TextField(value = textFieldValue, onValueChange = { nv -> if (nv.text.length < textFieldValue.text.length) handleInput("") else if (nv.text.length > 1) handleInput(nv.text); textFieldValue = TextFieldValue(" ", selection = TextRange(1)) }, modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = { showExitDialog = true }) { Text("🏠", color = Color.White) }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(if(size==9)1f else 0.8f).padding(bottom=8.dp), Arrangement.SpaceBetween, Alignment.Bottom) {
                        Column { Text("TIME ${formatTime(timerSeconds)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White); Text(if (bestTime > 0L) "🏆 BEST: ${formatTime(bestTime)}" else "🏆 BEST: --:--", fontSize = 12.sp, color = Color.Yellow) }
                        Column(horizontalAlignment = Alignment.End) { Text("${"❤️".repeat(lives.coerceAtLeast(0))}", fontSize = 16.sp); Text("🔥 STREAK: $currentStreak", fontSize = 11.sp, color = Color.Cyan) }
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(size), modifier = Modifier.fillMaxWidth(if(size==9)1f else 0.8f).aspectRatio(1f).border(2.dp, Color.White)) {
                        itemsIndexed(cells) { index, cell ->
                            val r = index / size; val c = index % size; val boxColor = if (size == 9 && ((r/3+c/3)%2==0)) Color(0xFF2A2A2A) else Color.Transparent
                            Box(modifier = Modifier.aspectRatio(1f).border(0.5.dp, Color(0xFF444444)).background(if (index == selectedIndex) Color(0xFF555555) else boxColor).clickable { if (isTimerRunning) { focusManager.clearFocus(); selectedIndex = index; focusRequester.requestFocus() } }, contentAlignment = Alignment.Center) { if (cell.value != 0) Text(cell.value.toString(), fontSize = if (size == 9) 20.sp else 26.sp, fontWeight = FontWeight.Bold, color = if (cell.isError) Color.Red else if (cell.isFixed) Color.White else Color.Cyan) }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { useHint() }, enabled = selectedIndex != -1 && lives > 1 && !cells[selectedIndex].isFixed, modifier = Modifier.fillMaxWidth(0.5f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black, disabledContainerColor = Color(0xFF333333))) { Text("HINT (❤️-1)", fontWeight = FontWeight.Bold) }
                }
            }
            Button(onClick = { doClearSave(); onBack() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))) { Text("QUIT") }
        }
        if (showWinDialog) {
            AlertDialog(onDismissRequest = {}, title = { Text("🎉 SUCCESS!") }, text = { Text("시간: ${formatTime(timerSeconds)}\n현재 연승: $currentStreak 🔥") },
                confirmButton = { Button(onClick = { showWinDialog = false; doClearSave(); cells = generateValidSudoku(size, difficulty); timerSeconds = 0L; lives = 5; isTimerRunning = true }) { Text("새 게임") } },
                dismissButton = { TextButton(onClick = { showWinDialog = false; doClearSave(); onBack() }) { Text("메뉴로") } }
            )
        }
        if (showExitDialog) { AlertDialog(onDismissRequest = { showExitDialog = false }, title = { Text("알림") }, text = { Text("진행 상황은 자동 저장됩니다.") }, confirmButton = { Button(onClick = { showExitDialog = false; onBack() }) { Text("확인") } }, dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("취소") } }) }
        if (lives <= 0) { AlertDialog(onDismissRequest = {}, title = { Text("💀 GAME OVER") }, text = { Text("목숨을 모두 잃었습니다.") }, confirmButton = { Button(onClick = { doClearSave(); onBack() }) { Text("확인") } }) }
    }
}

fun isValidHint(current: List<SudokuCell>, idx: Int, num: Int, size: Int): Boolean {
    val r = idx / size; val c = idx % size
    for (i in 0 until size * size) {
        val ir = i / size; val ic = i % size
        if (i == idx || current[i].value != num || current[i].isError) continue
        val sameBox = size == 9 && (ir / 3 == r / 3 && ic / 3 == c / 3)
        if (ir == r || ic == c || sameBox) return false
    }
    return true
}

fun clearSave(prefs: android.content.SharedPreferences) { prefs.edit().remove("saved_board").remove("saved_size").remove("saved_difficulty").remove("saved_lives").remove("saved_timer").apply() }
fun serializeBoard(cells: List<SudokuCell>): String { val array = JSONArray(); cells.forEach { cell -> val obj = JSONObject().apply { put("v", cell.value); put("f", cell.isFixed); put("e", cell.isError) }; array.put(obj) }; return array.toString() }
fun deserializeBoard(json: String): List<SudokuCell> { val array = JSONArray(json); return List(array.length()) { i -> val obj = array.getJSONObject(i); SudokuCell(0, 0, obj.getInt("v"), obj.getBoolean("f"), obj.getBoolean("e"), emptySet()) } }
fun formatTime(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
fun generateValidSudoku(size: Int, difficulty: Difficulty): List<SudokuCell> {
    val board = Array(size) { IntArray(size) { 0 } }
    fun solve(r: Int, c: Int): Boolean {
        if (r == size) return true
        val nR = if (c == size - 1) r + 1 else r
        val nC = (c + 1) % size
        val nums = (1..size).shuffled()
        for (n in nums) { if (isSafe(board, r, c, n, size)) { board[r][c] = n; if (solve(nR, nC)) return true; board[r][c] = 0 } }
        return false
    }
    solve(0, 0)
    val targetHints = when(size) { 5 -> when(difficulty) { Difficulty.EASY -> 14; Difficulty.MEDIUM -> 11; Difficulty.HARD -> 8 }; 7 -> when(difficulty) { Difficulty.EASY -> 28; Difficulty.MEDIUM -> 22; Difficulty.HARD -> 16 }; else -> when(difficulty) { Difficulty.EASY -> 42; Difficulty.MEDIUM -> 33; Difficulty.HARD -> 25 } }
    val isFixed = MutableList(size * size) { true }
    val indices = (0 until size * size).shuffled()
    var currentCount = size * size
    for (idx in indices) { if (currentCount <= targetHints) break; isFixed[idx] = false; currentCount-- }
    return List(size * size) { i -> SudokuCell(i/size, i%size, if(isFixed[i]) board[i/size][i%size] else 0, isFixed[i], false, emptySet()) }
}
fun isSafe(board: Array<IntArray>, r: Int, c: Int, n: Int, size: Int): Boolean {
    for (i in 0 until size) if (board[r][i] == n || board[i][c] == n) return false
    if (size == 9) { val br = (r/3)*3; val bc = (c/3)*3; for (i in 0..2) for (j in 0..2) if (board[br+i][bc+j] == n) return false }
    return true
}
fun checkBoardValidity(current: List<SudokuCell>, lastIdx: Int, size: Int, onWrong: () -> Unit): List<SudokuCell> {
    val cell = current[lastIdx]; val r = lastIdx / size; val c = lastIdx % size
    val isDup = current.filterIndexed { i, other -> if (i == lastIdx || other.value == 0) return@filterIndexed false; val ir = i/size; val ic = i%size; val sameBox = size == 9 && (ir/3 == r/3 && ic/3 == c/3); (ir == r || ic == c || sameBox) && other.value == cell.value }.isNotEmpty()
    if (isDup) onWrong()
    return current.mapIndexed { i, item -> if (i == lastIdx) item.copy(isError = isDup) else item }
}