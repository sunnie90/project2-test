package com.project2.sudoku

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
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

// --- 음악 및 피드백 매니저 (기본 유지) ---
class BackgroundMusicManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    fun startMusic() {
        try {
            if (mediaPlayer == null) {
                val resId = context.resources.getIdentifier("lofi_study", "raw", context.packageName)
                if (resId != 0) {
                    mediaPlayer = MediaPlayer.create(context, resId).apply { isLooping = true; setVolume(0.3f, 0.3f) }
                }
            }
            mediaPlayer?.start()
        } catch (e: Exception) { e.printStackTrace() }
    }
    fun stopMusic() { try { mediaPlayer?.pause() } catch (e: Exception) {} }
    fun release() { try { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null } catch (e: Exception) {} }
}

class FeedbackManager(context: Context) {
    private val soundPool: SoundPool
    private var winSoundId: Int = -1
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else { @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    init {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
        winSoundId = soundPool.load(context, R.raw.win_sound, 1)
    }
    fun vibrateError() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(100)
    }
    fun playWinSound(isEnabled: Boolean) { if (isEnabled && winSoundId != -1) soundPool.play(winSoundId, 1f, 1f, 0, 0, 1f) }
}

enum class Difficulty(val label: String, val color: Color, val textColor: Color) {
    EASY("초급", Color(0xFFC8E6C9), Color(0xFF1B5E20)),
    MEDIUM("중급", Color(0xFFFFCC80), Color(0xFFE65100)),
    HARD("고급", Color(0xFFEF9A9A), Color(0xFFB71C1C))
}

class MainActivity : ComponentActivity() {
    private lateinit var musicManager: BackgroundMusicManager
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        musicManager = BackgroundMusicManager(this)
        setContent { SudokuTheme { Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), color = Color(0xFF1A1A1A)) { SudokuGameApp(musicManager) } } }
    }
    override fun onStop() { super.onStop(); musicManager.stopMusic() }
    override fun onStart() { super.onStart(); val prefs = getSharedPreferences("sudoku_prefs", Context.MODE_PRIVATE); if (prefs.getBoolean("sound_enabled", true)) musicManager.startMusic() }
    override fun onDestroy() { super.onDestroy(); musicManager.release() }
}

@Composable
fun SudokuGameApp(musicManager: BackgroundMusicManager) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sudoku_prefs", Context.MODE_PRIVATE) }
    val feedbackManager = remember { FeedbackManager(context) }
    var difficulty by remember { mutableStateOf<Difficulty?>(null) }
    var gameSize by remember { mutableIntStateOf(0) }
    var isInitialized by remember { mutableStateOf(false) }
    var isSoundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }

    LaunchedEffect(isSoundEnabled) { if (isSoundEnabled) musicManager.startMusic() else musicManager.stopMusic() }
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
                Difficulty.entries.forEach { diff ->
                    Button(onClick = { difficulty = diff }, modifier = Modifier.fillMaxWidth(0.6f).padding(8.dp).height(65.dp), colors = ButtonDefaults.buttonColors(containerColor = diff.color, contentColor = diff.textColor)) { Text(diff.label, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                IconButton(onClick = { isSoundEnabled = !isSoundEnabled; prefs.edit().putBoolean("sound_enabled", isSoundEnabled).apply() }) { Text(if (isSoundEnabled) "🔊" else "🔇", color = Color.Gray, fontSize = 24.sp) }
            }
        } else if (gameSize == 0) {
            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("난이도: ${difficulty!!.label}", color = difficulty!!.color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("사이즈 선택", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(30.dp))
                listOf(5, 7, 9).forEach { size ->
                    val comboKey = "${size}_${difficulty!!.name}"
                    val bestTime = prefs.getLong("best_time_$comboKey", 0L)
                    val streak = prefs.getInt("streak_$comboKey", 0)
                    OutlinedButton(onClick = { gameSize = size }, modifier = Modifier.fillMaxWidth(0.7f).padding(8.dp).height(85.dp), border = androidx.compose.foundation.BorderStroke(2.dp, difficulty!!.color)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${size} x ${size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Row {
                                if (bestTime > 0L) Text("🏆 ${formatTime(bestTime)} ", fontSize = 12.sp, color = Color.Yellow)
                                if (streak > 0) Text(" 🔥 $streak", fontSize = 12.sp, color = Color.Cyan)
                            }
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
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("sudoku_prefs", Context.MODE_PRIVATE) }
    val comboKey = "${size}_${difficulty.name}"
    val scrollState = rememberScrollState()

    val savedBoardJson = prefs.getString("saved_board", null)
    val isResume = savedBoardJson != null && prefs.getInt("saved_size", 0) == size && prefs.getString("saved_difficulty", "") == difficulty.name

    var cells by remember { mutableStateOf(if (isResume) deserializeBoard(savedBoardJson!!) else generateValidSudoku(size, difficulty)) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var isNoteMode by remember { mutableStateOf(false) }
    var lives by remember { mutableIntStateOf(if (isResume) prefs.getInt("saved_lives", 5) else 5) }
    var timerSeconds by remember { mutableLongStateOf(if (isResume) prefs.getLong("saved_timer", 0L) else 0L) }
    var currentStreak by remember { mutableIntStateOf(prefs.getInt("streak_$comboKey", 0)) }
    var bestTime by remember { mutableLongStateOf(prefs.getLong("best_time_$comboKey", 0L)) }
    var isTimerRunning by remember { mutableStateOf(true) }
    var showWinDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(" ", selection = TextRange(1))) }

    fun doClearSave() { prefs.edit().remove("saved_board").remove("saved_size").remove("saved_difficulty").remove("saved_lives").remove("saved_timer").apply() }
    fun autoSave() { prefs.edit().apply { putString("saved_board", serializeBoard(cells)); putInt("saved_size", size); putString("saved_difficulty", difficulty.name); putInt("saved_lives", lives); putLong("saved_timer", timerSeconds); apply() } }

    LaunchedEffect(isTimerRunning) { while (isTimerRunning) { delay(1000L); timerSeconds++; if (timerSeconds % 5 == 0L) autoSave() } }

    BackHandler {
        if (selectedIndex != -1) { selectedIndex = -1; focusManager.clearFocus() }
        else { showExitDialog = true }
    }

    fun handleInput(input: String) {
        if (selectedIndex !in cells.indices || cells[selectedIndex].isFixed || !isTimerRunning) return
        val currentCell = cells[selectedIndex]
        val newList = cells.toMutableList()
        var shouldCloseKeyboard = false

        if (input.isEmpty()) {
            newList[selectedIndex] = currentCell.copy(value = 0, isError = false, notes = emptySet())
            cells = newList; shouldCloseKeyboard = true
        } else {
            val char = input.last()
            if (char.isDigit()) {
                val num = char.toString().toInt()
                if (num in 1..size) {
                    if (isNoteMode) {
                        val newNotes = if (currentCell.notes.contains(num)) currentCell.notes - num else currentCell.notes + num
                        newList[selectedIndex] = currentCell.copy(value = 0, notes = newNotes, isError = false)
                        cells = newList
                    } else {
                        newList[selectedIndex] = currentCell.copy(value = num, notes = emptySet())
                        cells = checkBoardValidity(newList, selectedIndex, size) {
                            feedbackManager.vibrateError(); lives--
                            if (lives <= 0) {
                                isTimerRunning = false; currentStreak = 0;
                                prefs.edit().putInt("streak_$comboKey", 0).apply(); doClearSave()
                            }
                        }
                        shouldCloseKeyboard = true
                    }
                }
            }
        }
        if (shouldCloseKeyboard) { selectedIndex = -1; focusManager.clearFocus() }

        // --- [성공 판정 로직] ---
        val allFilled = cells.all { it.value != 0 }
        val noErrors = cells.none { it.isError }
        if (allFilled && noErrors) {
            isTimerRunning = false
            feedbackManager.playWinSound(isSoundEnabled)
            currentStreak++
            prefs.edit().putInt("streak_$comboKey", currentStreak).apply()
            if (bestTime == 0L || timerSeconds < bestTime) {
                bestTime = timerSeconds
                prefs.edit().putLong("best_time_$comboKey", timerSeconds).apply()
            }
            doClearSave()
            showWinDialog = true
        } else {
            autoSave()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
        TextField(value = textFieldValue, onValueChange = { nv ->
            if (nv.text.length < textFieldValue.text.length) handleInput("")
            else if (nv.text.length > 1) handleInput(nv.text)
            textFieldValue = TextFieldValue(" ", selection = TextRange(1))
        }, modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(scrollState)) {
            Spacer(Modifier.height(48.dp))
            Spacer(Modifier.weight(0.8f))

            Row(Modifier.fillMaxWidth(if(size==9)1f else 0.85f).align(Alignment.CenterHorizontally), Arrangement.SpaceBetween, Alignment.Bottom) {
                Column {
                    Text("TIME ${formatTime(timerSeconds)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(if (bestTime > 0L) "🏆 BEST: ${formatTime(bestTime)}" else "🏆 BEST: --:--", fontSize = 12.sp, color = Color.Yellow)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${"❤️".repeat(lives.coerceAtLeast(0))}", fontSize = 16.sp)
                    Text("🔥 STREAK: $currentStreak", fontSize = 11.sp, color = Color.Cyan)
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth(if(size==9) 1f else 0.85f).aspectRatio(1f).align(Alignment.CenterHorizontally).border(2.dp, Color.White)) {
                LazyVerticalGrid(columns = GridCells.Fixed(size), userScrollEnabled = false, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(cells) { index, cell ->
                        val isSelected = index == selectedIndex
                        val r = index / size; val c = index % size
                        val boxColor = if (size == 9 && ((r/3+c/3)%2==0)) Color(0xFF2A2A2A) else Color.Transparent

                        Box(modifier = Modifier.aspectRatio(1f).border(0.5.dp, Color(0xFF444444)).background(if (isSelected) Color(0xFF555555) else boxColor).clickable {
                            if (isTimerRunning) {
                                focusManager.clearFocus()
                                selectedIndex = index
                                focusRequester.requestFocus()
                            }
                        }, contentAlignment = Alignment.Center) {
                            if (cell.value != 0) {
                                Text(cell.value.toString(), fontSize = if(size==9) 20.sp else 28.sp, fontWeight = FontWeight.Bold, color = if (cell.isError) Color.Red else if (cell.isFixed) Color.White else Color.Cyan)
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    for (row in 0..2) {
                                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                            for (col in 0..2) {
                                                val noteNum = row * 3 + col + 1
                                                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                                    if (cell.notes.contains(noteNum)) {
                                                        Text(text = noteNum.toString(), fontSize = if(size == 9) 11.sp else 14.sp, color = Color(0xFFCCCCCC), style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false), lineHeight = 0.sp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- 4단 통합 하단 버튼 바 ---
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), Arrangement.SpaceEvenly) {
                Button(onClick = { showExitDialog = true }, modifier = Modifier.weight(1f).height(50.dp).padding(horizontal = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))) {
                    Text("MENU", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = {
                    if (selectedIndex != -1 && !cells[selectedIndex].isFixed && lives > 1) {
                        for (num in 1..size) {
                            if (isValidHint(cells, selectedIndex, num, size)) {
                                val newList = cells.toMutableList()
                                newList[selectedIndex] = newList[selectedIndex].copy(value = num, isError = false, notes = emptySet())
                                cells = newList; lives--; selectedIndex = -1; focusManager.clearFocus(); autoSave(); break
                            }
                        }
                    }
                }, enabled = selectedIndex != -1 && lives > 1 && !cells[selectedIndex].isFixed, modifier = Modifier.weight(1f).height(50.dp).padding(horizontal = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black, disabledContainerColor = Color(0xFF555522))) {
                    Text("HINT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { isNoteMode = !isNoteMode }, modifier = Modifier.weight(1f).height(50.dp).padding(horizontal = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isNoteMode) Color.Yellow else Color(0xFF444444))) {
                    Text(if (isNoteMode) "✎ ON" else "✎ OFF", color = if (isNoteMode) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { activity?.finish() }, modifier = Modifier.weight(1f).height(50.dp).padding(horizontal = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B0000))) {
                    Text("QUIT", color = Color(0xFFCCCCCC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(modifier = Modifier.imePadding())
            Spacer(Modifier.height(20.dp))
        }

        // --- [다이얼로그 영역] ---

        // 성공 다이얼로그 (2가지 버튼 복구)
        if (showWinDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("🎉 SUCCESS!") },
                text = { Text("기록: ${formatTime(timerSeconds)}\n연승: $currentStreak 🔥\n\n한 판 더 하시겠습니까?") },
                confirmButton = {
                    Button(onClick = {
                        // 새 게임 시작 로직
                        cells = generateValidSudoku(size, difficulty)
                        lives = 5
                        timerSeconds = 0
                        isTimerRunning = true
                        showWinDialog = false
                    }) { Text("새 게임") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showWinDialog = false
                        onBack() // 메뉴로 이동
                    }) { Text("메뉴로") }
                }
            )
        }

        if (showExitDialog) {
            AlertDialog(onDismissRequest = { showExitDialog = false }, title = { Text("MENU") }, text = { Text("진행 상황은 자동 저장됩니다. 메뉴로 돌아가시겠습니까?") },
                confirmButton = { Button(onClick = { showExitDialog = false; onBack() }) { Text("확인") } },
                dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("취소") } }
            )
        }

        if (lives <= 0) {
            AlertDialog(onDismissRequest = {}, title = { Text("💀 GAME OVER") }, text = { Text("목숨을 모두 잃었습니다.") },
                confirmButton = { Button(onClick = { doClearSave(); onBack() }) { Text("메뉴로") } }
            )
        }
    }
}

// --- 나머지 헬퍼 함수들은 이전과 동일 (isValidHint, generateValidSudoku 등) ---
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

fun serializeBoard(cells: List<SudokuCell>): String {
    val array = JSONArray()
    cells.forEach { cell ->
        val obj = JSONObject().apply {
            put("v", cell.value); put("f", cell.isFixed); put("e", cell.isError)
            put("n", JSONArray(cell.notes.toList()))
        }
        array.put(obj)
    }
    return array.toString()
}

fun deserializeBoard(json: String): List<SudokuCell> {
    val array = JSONArray(json)
    return List(array.length()) { i ->
        val obj = array.getJSONObject(i)
        val notesArray = obj.optJSONArray("n")
        val notesSet = mutableSetOf<Int>()
        if (notesArray != null) { for (j in 0 until notesArray.length()) notesSet.add(notesArray.getInt(j)) }
        SudokuCell(0, 0, obj.getInt("v"), obj.getBoolean("f"), obj.getBoolean("e"), notesSet)
    }
}

fun formatTime(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

fun generateValidSudoku(size: Int, difficulty: Difficulty): List<SudokuCell> {
    val board = Array(size) { IntArray(size) { 0 } }
    fun solve(r: Int, c: Int): Boolean {
        if (r == size) return true
        val nR = if (c == size - 1) r + 1 else r
        val nC = (c + 1) % size
        for (n in (1..size).shuffled()) {
            if (isSafe(board, r, c, n, size)) {
                board[r][c] = n; if (solve(nR, nC)) return true; board[r][c] = 0
            }
        }
        return false
    }
    solve(0, 0)
    val targetHints = when(size) {
        5 -> when(difficulty) { Difficulty.EASY -> 14; Difficulty.MEDIUM -> 11; Difficulty.HARD -> 8 }
        7 -> when(difficulty) { Difficulty.EASY -> 28; Difficulty.MEDIUM -> 22; Difficulty.HARD -> 16 }
        else -> when(difficulty) { Difficulty.EASY -> 42; Difficulty.MEDIUM -> 33; Difficulty.HARD -> 25 }
    }
    val isFixed = MutableList(size * size) { true }
    (0 until size * size).shuffled().take(size * size - targetHints).forEach { isFixed[it] = false }
    return List(size * size) { i -> SudokuCell(i/size, i%size, if(isFixed[i]) board[i/size][i%size] else 0, isFixed[i], false, emptySet()) }
}

fun isSafe(board: Array<IntArray>, r: Int, c: Int, n: Int, size: Int): Boolean {
    for (i in 0 until size) if (board[r][i] == n || board[i][c] == n) return false
    if (size == 9) { val br = (r/3)*3; val bc = (c/3)*3; for (i in 0..2) for (j in 0..2) if (board[br+i][bc+j] == n) return false }
    return true
}

fun checkBoardValidity(current: List<SudokuCell>, lastIdx: Int, size: Int, onWrong: () -> Unit): List<SudokuCell> {
    val cell = current[lastIdx]; val r = lastIdx / size; val c = lastIdx % size
    val isDup = current.filterIndexed { i, other ->
        if (i == lastIdx || other.value == 0) return@filterIndexed false
        val ir = i/size; val ic = i%size
        val sameBox = size == 9 && (ir/3 == r/3 && ic/3 == c/3)
        (ir == r || ic == c || sameBox) && other.value == cell.value
    }.isNotEmpty()
    if (isDup) onWrong()
    return current.mapIndexed { i, item -> if (i == lastIdx) item.copy(isError = isDup) else item }
}

fun clearSave(prefs: android.content.SharedPreferences) { prefs.edit().remove("saved_board").remove("saved_size").remove("saved_difficulty").remove("saved_lives").remove("saved_timer").apply() }