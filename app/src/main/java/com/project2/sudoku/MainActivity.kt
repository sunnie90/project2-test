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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project2.sudoku.ui.theme.SudokuTheme
import com.project2.sudoku.model.SudokuCell
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

// --- [1] 유틸리티 및 데이터 관리 ---
fun formatTime(s: Long): String = "%02d:%02d".format(s / 60, s % 60)

fun clearSave(prefs: android.content.SharedPreferences) {
    prefs.edit()
        .remove("saved_board").remove("saved_solution")
        .remove("saved_size").remove("saved_difficulty")
        .remove("saved_lives").remove("saved_timer")
        .apply()
}

fun serializeBoard(cells: List<SudokuCell>): String {
    val array = JSONArray()
    cells.forEach { cell ->
        val obj = JSONObject().apply {
            put("v", cell.value); put("f", cell.isFixed); put("e", cell.isError)
            put("n", JSONArray(cell.notes.toList()))
        }; array.put(obj)
    }
    return array.toString()
}

fun deserializeBoard(json: String, size: Int): List<SudokuCell> {
    val array = JSONArray(json)
    return List(array.length()) { i ->
        val obj = array.getJSONObject(i)
        val nArray = obj.optJSONArray("n")
        val nSet = mutableSetOf<Int>()
        nArray?.let { for (j in 0 until it.length()) nSet.add(it.getInt(j)) }
        SudokuCell(i/size, i%size, obj.getInt("v"), obj.getBoolean("f"), obj.getBoolean("e"), nSet)
    }
}

fun serializeSolution(solution: Array<IntArray>): String {
    val outerArray = JSONArray()
    solution.forEach { row ->
        val innerArray = JSONArray()
        row.forEach { innerArray.put(it) }
        outerArray.put(innerArray)
    }
    return outerArray.toString()
}

fun deserializeSolution(json: String, size: Int): Array<IntArray> {
    val outerArray = JSONArray(json)
    return Array(size) { r ->
        val innerArray = outerArray.getJSONArray(r)
        IntArray(size) { c -> innerArray.getInt(c) }
    }
}

// --- [2] 멀티미디어 ---
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
    fun vibrateError(isEnabled: Boolean) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(100)
    }
    fun playWinSound(isEnabled: Boolean) { if (isEnabled && winSoundId != -1) soundPool.play(winSoundId, 1f, 1f, 0, 0, 1f) }
}

// --- [3] 난이도 ---
enum class Difficulty(val labelKo: String, val labelEn: String, val color: Color, val textColor: Color) {
    EASY("초급", "EASY", Color(0xFFC8E6C9), Color(0xFF1B5E20)),
    MEDIUM("중급", "MEDIUM", Color(0xFFFFCC80), Color(0xFFE65100)),
    HARD("고급", "HARD", Color(0xFFEF9A9A), Color(0xFFB71C1C))
}

// --- [4] 메인 액티비티 ---
class MainActivity : ComponentActivity() {
    private lateinit var musicManager: BackgroundMusicManager
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        musicManager = BackgroundMusicManager(this)
        setContent {
            SudokuTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), color = Color(0xFF1A1A1A)) {
                    SudokuGameApp(musicManager)
                }
            }
        }
    }
    override fun onStop() { super.onStop(); musicManager.stopMusic() }
    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("sudoku_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("sound_enabled", true)) musicManager.startMusic()
    }
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

    var savedDifficultyName by remember { mutableStateOf<String?>(null) }
    var savedSizeValue by remember { mutableIntStateOf(0) }
    var savedTimerValue by remember { mutableLongStateOf(0L) }

    var isSoundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }
    var isEffectEnabled by remember { mutableStateOf(prefs.getBoolean("effect_enabled", true)) }
    var currentBgColor by remember { mutableStateOf(Color(prefs.getInt("bg_color", 0xFF1A1A1A.toInt()))) }
    var isEnglish by remember { mutableStateOf(prefs.getBoolean("is_english", false)) }
    var useButtonPad by remember { mutableStateOf(prefs.getBoolean("use_button_pad", false)) }
    var showMainSettingsDialog by remember { mutableStateOf(false) }
    var showMainHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isSoundEnabled) { if (isSoundEnabled) musicManager.startMusic() else musicManager.stopMusic() }

    fun refreshSaveInfo() {
        val board = prefs.getString("saved_board", null)
        if (board != null) {
            savedDifficultyName = prefs.getString("saved_difficulty", null)
            savedSizeValue = prefs.getInt("saved_size", 0)
            savedTimerValue = prefs.getLong("saved_timer", 0L)
        } else {
            savedDifficultyName = null; savedSizeValue = 0
        }
    }

    LaunchedEffect(Unit) { refreshSaveInfo(); isInitialized = true }

    if (!isInitialized) return

    Box(modifier = Modifier.fillMaxSize().background(currentBgColor)) {
        val titleColor = if(currentBgColor.luminance() > 0.5f) Color(0xFF121212) else Color.White

        if (difficulty == null) {
            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("SUDOKU MASTER", fontSize = 36.sp, fontWeight = FontWeight.Black, color = titleColor)
                Spacer(Modifier.height(30.dp))

                if (savedDifficultyName != null && savedSizeValue != 0) {
                    val savedDiff = try { Difficulty.valueOf(savedDifficultyName!!) } catch(e: Exception) { null }
                    if (savedDiff != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.85f).padding(bottom = 32.dp).clickable { difficulty = savedDiff; gameSize = savedSizeValue },
                            shape = RoundedCornerShape(50.dp),
                            color = savedDiff.color.copy(alpha = 0.85f),
                            border = BorderStroke(2.dp, savedDiff.color)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.PlayArrow, null, tint = savedDiff.textColor, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(if(isEnglish) "RESUME" else "이어서 하기", fontWeight = FontWeight.ExtraBold, color = savedDiff.textColor)
                                Spacer(Modifier.width(8.dp))
                                Text("(${savedSizeValue}x${savedSizeValue} · ${formatTime(savedTimerValue)})", color = savedDiff.textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Text(if(isEnglish) "NEW GAME" else "새 게임 시작", fontSize = 16.sp, color = titleColor.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Difficulty.entries.forEach { diff ->
                    ElevatedButton(onClick = { difficulty = diff }, modifier = Modifier.fillMaxWidth(0.6f).padding(8.dp).height(60.dp), colors = ButtonDefaults.elevatedButtonColors(containerColor = diff.color, contentColor = diff.textColor)) {
                        Text(if(isEnglish) diff.labelEn else diff.labelKo, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                // [추가] 메인 화면 설정 + 도움말 버튼
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showMainSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = titleColor.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if(isEnglish) "Settings" else "설정", color = titleColor.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                    TextButton(onClick = { showMainHelpDialog = true }) {
                        Text("❓", fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(if(isEnglish) "How to Play" else "도움말", color = titleColor.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                }
            }
        } else if (gameSize == 0) {
            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Text(if(isEnglish) "Difficulty: ${difficulty!!.labelEn}" else "난이도: ${difficulty!!.labelKo}", color = difficulty!!.color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if(isEnglish) "Select Grid Size" else "상자 크기 선택", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = titleColor)
                Spacer(Modifier.height(24.dp))
                listOf(5, 7, 9).forEach { size ->
                    val comboKey = "${size}_${difficulty!!.name}"
                    val bTime = prefs.getLong("best_time_$comboKey", 0L)
                    val streak = prefs.getInt("streak_$comboKey", 0)
                    ElevatedButton(
                        onClick = { gameSize = size },
                        modifier = Modifier.fillMaxWidth(0.75f).padding(vertical = 8.dp),
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = if(currentBgColor.luminance() > 0.5f) Color.White else Color(0xFF2D2D2D)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, difficulty!!.color)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text("${size} x ${size}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if(currentBgColor.luminance() > 0.5f) Color.Black else Color.White)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if(isEnglish) "BEST: " else "최고: ", fontSize = 11.sp, color = titleColor.copy(alpha = 0.5f))
                                Text("${if(bTime == 0L) "00:00" else formatTime(bTime)}", fontSize = 12.sp, color = titleColor.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(16.dp))
                                Text(if(isEnglish) "STREAK: " else "연승: ", fontSize = 11.sp, color = titleColor.copy(alpha = 0.5f))
                                Text("$streak", fontSize = 12.sp, color = titleColor.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                TextButton(onClick = { difficulty = null }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(if(isEnglish) "Back" else "돌아가기", color = titleColor.copy(alpha = 0.8f))
                }
            }
        } else {
            SudokuGameScreen(
                size = gameSize, difficulty = difficulty!!, feedbackManager = feedbackManager,
                isSoundEnabled = isSoundEnabled, isEffectEnabled = isEffectEnabled, isEnglish = isEnglish,
                bgColor = currentBgColor, useButtonPad = useButtonPad,
                onSettingsChange = { sound, effect, bg, lang, buttonPad ->
                    isSoundEnabled = sound; isEffectEnabled = effect; currentBgColor = bg; isEnglish = lang; useButtonPad = buttonPad
                    prefs.edit().apply {
                        putBoolean("sound_enabled", sound); putBoolean("effect_enabled", effect)
                        putInt("bg_color", bg.value.toInt()); putBoolean("is_english", lang)
                        putBoolean("use_button_pad", buttonPad); apply()
                    }
                },
                onBack = { gameSize = 0; difficulty = null; refreshSaveInfo() }
            )
        }
    }

    // [추가] 메인 화면 설정 다이얼로그 (게임 밖에서도 설정 가능)
    if (showMainSettingsDialog) {
        val bgColors = listOf(
            Color(0xFF1A1A1A),  // 깊은 검정
            Color(0xFF0D1B2A),  // 네이비 다크
            Color(0xFF1B2838),  // 스팀 다크 블루
            Color(0xFF1A2A1A),  // 다크 포레스트 그린
            Color(0xFF2A1A2A),  // 다크 퍼플
            Color(0xFFF5F0E8),  // 따뜻한 아이보리 (밝은 배경)
        )
        AlertDialog(
            onDismissRequest = { showMainSettingsDialog = false },
            title = { Text(if(isEnglish) "Settings" else "게임 설정") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(if(isEnglish) "Audio" else "소리", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(if(isEnglish) "Music" else "음악")
                        Switch(checked = isSoundEnabled, onCheckedChange = {
                            isSoundEnabled = it; prefs.edit().putBoolean("sound_enabled", it).apply()
                        })
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(if(isEnglish) "Effects" else "효과음")
                        Switch(checked = isEffectEnabled, onCheckedChange = {
                            isEffectEnabled = it; prefs.edit().putBoolean("effect_enabled", it).apply()
                        })
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(if(isEnglish) "Input Method" else "입력 방식", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(if(useButtonPad) (if(isEnglish) "Button Pad" else "버튼패드") else (if(isEnglish) "Keyboard" else "키보드"))
                        Switch(checked = useButtonPad, onCheckedChange = {
                            useButtonPad = it; prefs.edit().putBoolean("use_button_pad", it).apply()
                        })
                    }
                    Spacer(Modifier.height(12.dp))
                    // [수정] 배경색 — 차분한 다크 계열 + 밝은 1가지
                    Text(if(isEnglish) "Background" else "배경색", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bgColors.forEach { color ->
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(color)
                                    .border(if(currentBgColor == color) 3.dp else 1.dp,
                                        if(currentBgColor == color) Color(0xFFFFD700) else Color.Gray,
                                        CircleShape)
                                    .clickable {
                                        currentBgColor = color
                                        prefs.edit().putInt("bg_color", color.value.toInt()).apply()
                                    }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(if(isEnglish) "Language" else "언어", fontWeight = FontWeight.Bold)
                    Row {
                        FilterChip(selected = !isEnglish, onClick = {
                            isEnglish = false; prefs.edit().putBoolean("is_english", false).apply()
                        }, label = { Text("한국어") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = isEnglish, onClick = {
                            isEnglish = true; prefs.edit().putBoolean("is_english", true).apply()
                        }, label = { Text("English") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showMainSettingsDialog = false }) {
                    Text(if(isEnglish) "Close" else "닫기")
                }
            }
        )
    }

    // [추가] 메인 화면 도움말 다이얼로그
    if (showMainHelpDialog) {
        val helpSections = if (isEnglish) listOf(
            "🎯 Goal" to "Fill every row, column, and box with numbers 1–N without repeats.",
            "🖱️ How to Play" to "1. Tap a cell to select it.\n2. Enter a number using the button pad or keyboard.\n3. Tap X / Backspace to erase.",
            "✏️ Memo Mode" to "Tap MEMO ON to write candidate numbers.\nConfirming a number auto-removes conflicting memos in the same row, column, and box.",
            "💡 Hint" to "Reveals the correct answer for the selected cell. Costs 1 ❤.",
            "❤️ Lives" to "5 hearts per game. Wrong number or hint = -1 heart. Game over at 0.",
            "⭐ Highlight" to "Selected → Blue  |  Same number → Green  |  Same row/col/box → Grey",
            "🏆 Records" to "Best time and win streak saved per grid size and difficulty.",
            "💾 Auto-Save" to "Progress saves every 10 seconds. Tap RESUME to continue.",
            "📐 Grid Sizes" to "5×5 Quick warm-up\n7×7 Medium (rows & columns only)\n9×9 Classic with 3×3 boxes"
        ) else listOf(
            "🎯 목표" to "모든 행·열·박스에 1~N을 중복 없이 채우세요.",
            "🖱️ 플레이 방법" to "1. 셀을 탭하여 선택합니다.\n2. 버튼패드 또는 키보드로 숫자를 입력합니다.\n3. X / 백스페이스로 지웁니다.",
            "✏️ 메모 모드" to "메모 켬 버튼으로 후보 숫자를 적을 수 있습니다.\n숫자 확정 시 같은 행·열·박스의 관련 메모가 자동 제거됩니다.",
            "💡 힌트" to "선택한 셀의 정답을 알려줍니다. ❤ 1개 소모.",
            "❤️ 목숨" to "하트 5개로 시작합니다. 오답·힌트 = -1개. 0개 = 게임 오버.",
            "⭐ 하이라이트" to "선택 → 파란색  |  같은 숫자 → 초록색  |  같은 행·열·박스 → 회색",
            "🏆 기록" to "격자 크기·난이도별 최고 기록과 연승이 저장됩니다.",
            "💾 자동 저장" to "10초마다 저장됩니다. 이어서 하기로 이어 플레이하세요.",
            "📐 격자 크기" to "5×5 빠른 워밍업\n7×7 중간 단계 (행·열만 적용)\n9×9 3×3 박스 포함 클래식"
        )
        AlertDialog(
            onDismissRequest = { showMainHelpDialog = false },
            title = { Text(if(isEnglish) "📖 How to Play" else "📖 게임 설명서", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF121212)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    helpSections.forEach { (title, body) ->
                        Spacer(Modifier.height(10.dp))
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF121212))
                        Spacer(Modifier.height(3.dp))
                        Text(body, fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFF333333))
                        Divider(modifier = Modifier.padding(top = 10.dp), color = Color.Gray.copy(alpha = 0.3f))
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showMainHelpDialog = false }) {
                    Text(if(isEnglish) "Got it!" else "확인")
                }
            }
        )
    }
}

@Composable
fun SudokuGameScreen(
    size: Int, difficulty: Difficulty, feedbackManager: FeedbackManager,
    isSoundEnabled: Boolean, isEffectEnabled: Boolean, isEnglish: Boolean,
    bgColor: Color, useButtonPad: Boolean,
    onSettingsChange: (Boolean, Boolean, Color, Boolean, Boolean) -> Unit, onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("sudoku_prefs", Context.MODE_PRIVATE) }
    val comboKey = "${size}_${difficulty.name}"
    val scope = rememberCoroutineScope()

    var cells by remember { mutableStateOf<List<SudokuCell>?>(null) }
    var solutionBoard by remember { mutableStateOf<Array<IntArray>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedIndex by remember { mutableIntStateOf(-1) }
    var isNoteMode by remember { mutableStateOf(false) }
    var lives by remember { mutableIntStateOf(5) }
    var timerSeconds by remember { mutableLongStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var currentStreak by remember { mutableIntStateOf(prefs.getInt("streak_$comboKey", 0)) }
    var bestTime by remember { mutableLongStateOf(prefs.getLong("best_time_$comboKey", 0L)) }

    var showWinDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showQuitConfirmDialog by remember { mutableStateOf(false) }
    var showHintWarningDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(" ", selection = TextRange(1))) }

    val isLightBg = bgColor.luminance() > 0.5f
    val mainTextColor = if(isLightBg) Color.Black else Color.White
    val subTextColor = if(isLightBg) Color(0xFF333333) else Color(0xFFCCCCCC)
    val gridLineColor = if(isLightBg) Color(0xFF333333) else Color(0xFF888888)

    // =====================================================================
    // [추가] 하이라이트용 색상 상수 — 배경 밝기에 따라 자동 조정
    // =====================================================================
    val hlSelectedColor  = Color(0xFF378ADD).copy(alpha = if (isLightBg) 0.45f else 0.55f)  // 선택된 셀: 파란색
    val hlSameNumColor   = Color(0xFF4CAF50).copy(alpha = if (isLightBg) 0.30f else 0.35f)  // 같은 숫자: 초록색
    val hlRelatedColor   = mainTextColor.copy(alpha = if (isLightBg) 0.10f else 0.13f)       // 같은 행/열/박스: 연한 회색
    val hlDefaultColor   = mainTextColor.copy(alpha = 0.05f)                                  // 9x9 기본 박스 음영

    fun autoSave() {
        if (lives > 0 && cells != null && solutionBoard != null) {
            prefs.edit().apply {
                putString("saved_board", serializeBoard(cells!!))
                putString("saved_solution", serializeSolution(solutionBoard!!))
                putInt("saved_size", size); putString("saved_difficulty", difficulty.name)
                putInt("saved_lives", lives); putLong("saved_timer", timerSeconds)
                apply()
            }
        }
    }

    fun handleInputLogic(inputNumber: Int?) {
        if (cells == null || selectedIndex !in cells!!.indices || cells!![selectedIndex].isFixed || !isTimerRunning) return
        val currentCell = cells!![selectedIndex]
        val newList = cells!!.toMutableList()
        var shouldClearSelection = false

        if (inputNumber == null || inputNumber == 0) {
            // 지우기
            newList[selectedIndex] = currentCell.copy(value = 0, isError = false, notes = emptySet())
            cells = newList
            shouldClearSelection = true
        } else {
            if (inputNumber in 1..size) {
                if (isNoteMode) {
                    // 메모 모드: 토글
                    val newNotes = if (currentCell.notes.contains(inputNumber))
                        currentCell.notes - inputNumber
                    else
                        currentCell.notes + inputNumber
                    newList[selectedIndex] = currentCell.copy(value = 0, notes = newNotes, isError = false)
                    cells = newList
                } else {
                    // 숫자 확정 모드
                    newList[selectedIndex] = currentCell.copy(value = inputNumber, notes = emptySet())
                    cells = checkBoardValidity(newList, selectedIndex, size) {
                        feedbackManager.vibrateError(isEffectEnabled); lives--
                        if (lives <= 0) { isTimerRunning = false; clearSave(prefs) }
                    }

                    // =====================================================
                    // [추가] 스마트 메모 자동제거
                    // 숫자를 확정했을 때, 같은 행/열/박스에 있는
                    // 다른 셀의 메모에서 해당 숫자를 자동으로 제거합니다.
                    // =====================================================
                    val confirmedList = cells!!.toMutableList()
                    val r = selectedIndex / size
                    val c = selectedIndex % size
                    val boxRowStart = (r / 3) * 3
                    val boxColStart = (c / 3) * 3

                    for (i in confirmedList.indices) {
                        if (i == selectedIndex) continue
                        val ir = i / size
                        val ic = i % size
                        // 같은 행 OR 같은 열 OR 같은 박스(9x9 전용)
                        val inSameBox = size == 9 &&
                            ir in boxRowStart until boxRowStart + 3 &&
                            ic in boxColStart until boxColStart + 3
                        val isRelated = ir == r || ic == c || inSameBox
                        if (isRelated && confirmedList[i].notes.contains(inputNumber)) {
                            confirmedList[i] = confirmedList[i].copy(
                                notes = confirmedList[i].notes - inputNumber
                            )
                        }
                    }
                    cells = confirmedList
                    // =====================================================

                    shouldClearSelection = true
                }
            }
        }

        if (shouldClearSelection) {
            selectedIndex = -1
            focusManager.clearFocus()
            keyboardController?.hide()
        }

        if (cells!!.all { it.value != 0 } && cells!!.none { it.isError }) {
            isTimerRunning = false; feedbackManager.playWinSound(isEffectEnabled)
            currentStreak++
            if (bestTime == 0L || timerSeconds < bestTime) {
                bestTime = timerSeconds
                prefs.edit().putLong("best_time_$comboKey", bestTime).apply()
            }
            prefs.edit().putInt("streak_$comboKey", currentStreak).apply()
            clearSave(prefs); showWinDialog = true
        } else { autoSave() }
    }

    fun startNewGameSameConditions() {
        scope.launch {
            isLoading = true; isTimerRunning = false; selectedIndex = -1
            val result = withContext(Dispatchers.Default) { createAdvancedGameWithRetry(size, difficulty) }
            cells = result.first; solutionBoard = result.second
            lives = 5; timerSeconds = 0L; isLoading = false; isTimerRunning = true; clearSave(prefs)
        }
    }

    LaunchedEffect(size, difficulty) {
        isLoading = true
        val savedBoardJson = prefs.getString("saved_board", null)
        val savedSolutionJson = prefs.getString("saved_solution", null)
        val isResume = savedBoardJson != null && savedSolutionJson != null &&
                prefs.getInt("saved_size", 0) == size && prefs.getString("saved_difficulty", "") == difficulty.name
        if (isResume) {
            cells = deserializeBoard(savedBoardJson!!, size)
            solutionBoard = deserializeSolution(savedSolutionJson!!, size)
            lives = prefs.getInt("saved_lives", 5); timerSeconds = prefs.getLong("saved_timer", 0L)
        } else {
            val result = withContext(Dispatchers.Default) { createAdvancedGameWithRetry(size, difficulty) }
            cells = result.first; solutionBoard = result.second; lives = 5; timerSeconds = 0L
        }
        isLoading = false; isTimerRunning = true
    }

    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning) { delay(1000L); timerSeconds++; if (timerSeconds % 10 == 0L) autoSave() }
    }

    BackHandler {
        if (selectedIndex != -1) { selectedIndex = -1; focusManager.clearFocus(); keyboardController?.hide() }
        else { isTimerRunning = false; showQuitConfirmDialog = true }
    }

    fun executeHint() {
        if (selectedIndex == -1 || cells!![selectedIndex].isFixed || !isTimerRunning) return
        lives--
        val correctValue = solutionBoard!![selectedIndex / size][selectedIndex % size]
        val newList = cells!!.toMutableList()
        newList[selectedIndex] = cells!![selectedIndex].copy(value = correctValue, isError = false, notes = emptySet())

        // =====================================================================
        // [추가] 힌트 사용 시에도 스마트 메모 자동제거 적용
        // =====================================================================
        val r = selectedIndex / size
        val c = selectedIndex % size
        val boxRowStart = (r / 3) * 3
        val boxColStart = (c / 3) * 3
        for (i in newList.indices) {
            if (i == selectedIndex) continue
            val ir = i / size; val ic = i % size
            val inSameBox = size == 9 &&
                ir in boxRowStart until boxRowStart + 3 &&
                ic in boxColStart until boxColStart + 3
            if ((ir == r || ic == c || inSameBox) && newList[i].notes.contains(correctValue)) {
                newList[i] = newList[i].copy(notes = newList[i].notes - correctValue)
            }
        }
        // =====================================================================

        cells = newList
        if (lives <= 0) { isTimerRunning = false; clearSave(prefs) } else { autoSave() }
        selectedIndex = -1; focusManager.clearFocus(); keyboardController?.hide()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = if(isLightBg) Color.Black else Color.Yellow)
                Spacer(Modifier.height(16.dp))
                Text(if(isEnglish) "Generating..." else "생성 중...", color = mainTextColor)
            }
        } else {
            cells?.let { currentCells ->
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {

                    if (!useButtonPad) {
                        TextField(value = textFieldValue, onValueChange = { nv ->
                            if (nv.text.length < textFieldValue.text.length) handleInputLogic(null)
                            else if (nv.text.length > 1) {
                                val char = nv.text.last()
                                if(char.isDigit()) handleInputLogic(char.toString().toInt())
                            }
                            textFieldValue = TextFieldValue(" ", selection = TextRange(1))
                        }, modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
                    }

                    Row(Modifier.fillMaxWidth(if(size==9) 1f else 0.85f), Arrangement.SpaceBetween, Alignment.Bottom) {
                        Column {
                            Text("${if(isEnglish) "TIME" else "시간"} ${formatTime(timerSeconds)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = mainTextColor)
                            Text("${if(isEnglish) "BEST: " else "최고: "}${formatTime(bestTime)}", fontSize = 12.sp, color = if(isLightBg) Color(0xFFB8860B) else Color.Yellow, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row { repeat(5) { i -> Icon(Icons.Default.Favorite, null, tint = if (i < lives) Color.Red else Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(18.dp).padding(horizontal = 1.dp)) } }
                            Text("${if(isEnglish) "STREAK: " else "연승: "}$currentStreak", fontSize = 11.sp, color = if(isLightBg) Color(0xFF008B8B) else Color.Cyan, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth(if(size==9) 1f else 0.85f).aspectRatio(1f).border(2.dp, mainTextColor)) {
                        LazyVerticalGrid(columns = GridCells.Fixed(size), userScrollEnabled = false, modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(currentCells) { index, cell ->
                                val r = index / size
                                val c = index % size

                                // =============================================================
                                // [추가] 셀 하이라이트 색상 계산
                                // 우선순위: 선택됨 > 같은 숫자 > 같은 행/열/박스 > 기본 박스 음영
                                // =============================================================
                                val selectedR = if (selectedIndex >= 0) selectedIndex / size else -1
                                val selectedC = if (selectedIndex >= 0) selectedIndex % size else -1
                                val selectedVal = if (selectedIndex >= 0 && selectedIndex < currentCells.size)
                                    currentCells[selectedIndex].value else 0

                                val inSameBox9 = size == 9 &&
                                    selectedR >= 0 &&
                                    r / 3 == selectedR / 3 &&
                                    c / 3 == selectedC / 3

                                val cellBgColor = when {
                                    index == selectedIndex ->
                                        hlSelectedColor
                                    selectedVal != 0 && cell.value == selectedVal && !cell.isError ->
                                        hlSameNumColor
                                    selectedIndex >= 0 && (r == selectedR || c == selectedC || inSameBox9) ->
                                        hlRelatedColor
                                    size == 9 && ((r / 3 + c / 3) % 2 == 0) ->
                                        hlDefaultColor
                                    else -> Color.Transparent
                                }
                                // =============================================================

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .drawBehindGrid(r, c, size, gridLineColor, isLightBg)
                                        .background(cellBgColor)   // ← 기존 조건식 대신 위에서 계산한 색 적용
                                        .clickable {
                                            if (isTimerRunning) {
                                                selectedIndex = index
                                                if (useButtonPad) {
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                } else {
                                                    focusRequester.requestFocus()
                                                    keyboardController?.show()
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cell.value != 0) {
                                        Text(
                                            cell.value.toString(),
                                            fontSize = if(size==9) 22.sp else 30.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (cell.isError) Color.Red
                                                    else if (cell.isFixed) mainTextColor
                                                    else if(isLightBg) Color(0xFF0055AA)
                                                    else Color.Cyan
                                        )
                                    } else {
                                        // 메모 표시 (3x3 그리드)
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            for (row in 0..2) {
                                                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                                    for (col in 0..2) {
                                                        val nNum = row * 3 + col + 1
                                                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                                            if (cell.notes.contains(nNum)) {
                                                                Text(
                                                                    text = nNum.toString(),
                                                                    fontSize = if(size == 9) 11.sp else 13.sp,
                                                                    color = subTextColor,
                                                                    fontWeight = FontWeight.Bold,
                                                                    textAlign = TextAlign.Center,
                                                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                                                )
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

                    if (useButtonPad) {
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (num in 1..size) {
                                Surface(
                                    modifier = Modifier.weight(1f).aspectRatio(0.8f).clickable { handleInputLogic(num) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if(isLightBg) Color(0xFFE0E0E0) else Color(0xFF333333),
                                    border = BorderStroke(1.dp, mainTextColor.copy(alpha = 0.2f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) { Text(num.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = mainTextColor) }
                                }
                            }
                            Surface(modifier = Modifier.weight(1f).aspectRatio(0.8f).clickable { handleInputLogic(null) }, shape = RoundedCornerShape(8.dp), color = if(isLightBg) Color(0xFFFFEBEE) else Color(0xFF422222)) {
                                Box(contentAlignment = Alignment.Center) { Text("X", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.Red) }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // 1줄: ⚙ 설정 · 힌트 · 메모 · 나가기
                    val bCol = if(isLightBg) Color(0xFFD0D0D0) else Color(0xFF333333)
                    val bText = if(isLightBg) Color.Black else Color.White
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { isTimerRunning = false; keyboardController?.hide(); showSettingsDialog = true },
                            modifier = Modifier.size(48.dp).background(bCol, CircleShape).clip(CircleShape)
                        ) { Icon(Icons.Default.Settings, "Settings", tint = bText, modifier = Modifier.size(22.dp)) }
                        Button(
                            onClick = { if (selectedIndex != -1) { if (lives > 1) executeHint() else if (lives == 1) showHintWarningDialog = true } },
                            modifier = Modifier.height(48.dp).weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(if(isEnglish) "HINT" else "힌트", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Button(
                            onClick = { isNoteMode = !isNoteMode },
                            modifier = Modifier.height(48.dp).weight(1.3f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isNoteMode) Color(0xFF185FA5) else bCol,
                                contentColor = if (isNoteMode) Color.White else bText
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                if (isNoteMode) (if(isEnglish) "MEMO ON" else "메모 켬")
                                else (if(isEnglish) "MEMO OFF" else "메모 끔"),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1
                            )
                        }
                        Button(
                            onClick = { isTimerRunning = false; keyboardController?.hide(); showQuitConfirmDialog = true },
                            modifier = Modifier.height(48.dp).weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(if(isEnglish) "QUIT" else "나가기", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    // --- 다이얼로그 ---
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false; isTimerRunning = true },
            title = { Text(if(isEnglish) "Settings" else "게임 설정") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(if(isEnglish) "Audio" else "소리", fontWeight = FontWeight.Bold)
                    // [수정] Music/Effects → 한국어 지원
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(if(isEnglish) "Music" else "음악")
                        Switch(checked = isSoundEnabled, onCheckedChange = { onSettingsChange(it, isEffectEnabled, bgColor, isEnglish, useButtonPad) })
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(if(isEnglish) "Effects" else "효과음")
                        Switch(checked = isEffectEnabled, onCheckedChange = { onSettingsChange(isSoundEnabled, it, bgColor, isEnglish, useButtonPad) })
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(if(isEnglish) "Input Method" else "입력 방식", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(if(useButtonPad) (if(isEnglish) "Button Pad" else "버튼패드") else (if(isEnglish) "Keyboard" else "키보드"))
                        Switch(checked = useButtonPad, onCheckedChange = { onSettingsChange(isSoundEnabled, isEffectEnabled, bgColor, isEnglish, it) })
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(if(isEnglish) "Background" else "배경색", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Color(0xFF1A1A1A),  // 깊은 검정
                            Color(0xFF0D1B2A),  // 네이비 다크
                            Color(0xFF1B2838),  // 다크 블루
                            Color(0xFF1A2A1A),  // 다크 그린
                            Color(0xFF2A1A2A),  // 다크 퍼플
                            Color(0xFFF5F0E8),  // 아이보리 (밝은)
                        ).forEach { color ->
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(color)
                                    .border(if(bgColor == color) 3.dp else 1.dp,
                                        if(bgColor == color) Color(0xFFFFD700) else Color.Gray,
                                        CircleShape)
                                    .clickable { onSettingsChange(isSoundEnabled, isEffectEnabled, color, isEnglish, useButtonPad) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(if(isEnglish) "Language" else "언어", fontWeight = FontWeight.Bold)
                    Row {
                        FilterChip(selected = !isEnglish, onClick = { onSettingsChange(isSoundEnabled, isEffectEnabled, bgColor, false, useButtonPad) }, label = { Text("한국어") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = isEnglish, onClick = { onSettingsChange(isSoundEnabled, isEffectEnabled, bgColor, true, useButtonPad) }, label = { Text("English") })
                    }
                }
            },
            confirmButton = { Button(onClick = { showSettingsDialog = false; isTimerRunning = true }) { Text(if(isEnglish) "Close" else "닫기") } }
        )
    }

    if (showQuitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showQuitConfirmDialog = false; isTimerRunning = true },
            title = { Text(if(isEnglish) "Pause & Exit" else "일시정지 및 나가기") },
            text = { Text(if(isEnglish) "Progress saved." else "현재 상태가 저장되었습니다.") },
            confirmButton = { Button(onClick = { autoSave(); activity?.finish() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF660000))) { Text(if(isEnglish) "Exit App" else "앱 종료") } },
            dismissButton = { Row { TextButton(onClick = { autoSave(); showQuitConfirmDialog = false; onBack() }) { Text(if(isEnglish) "Menu" else "메뉴로") }; TextButton(onClick = { showQuitConfirmDialog = false; isTimerRunning = true }) { Text(if(isEnglish) "Resume" else "계속하기") } } }
        )
    }

    if (showWinDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(if(isEnglish) "🎉 SUCCESS!" else "🎉 성공!", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
            text = { Column { Text("${if(isEnglish) "Time" else "기록"}: ${formatTime(timerSeconds)}"); Text("${if(isEnglish) "Streak" else "연승"}: $currentStreak"); Spacer(Modifier.height(12.dp)); Text(if(isEnglish) "Challenge again?" else "다시 도전하시겠습니까?") } },
            confirmButton = { Button(onClick = { showWinDialog = false; startNewGameSameConditions() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), shape = RoundedCornerShape(12.dp)) { Text(if(isEnglish) "New Game" else "새 게임", fontWeight = FontWeight.Bold) } },
            dismissButton = { OutlinedButton(onClick = { showWinDialog = false; onBack() }, border = BorderStroke(1.5.dp, Color.Black), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)) { Text(if(isEnglish) "Main Menu" else "메인 메뉴로", fontWeight = FontWeight.Medium) } }
        )
    }

    if (showHintWarningDialog) {
        AlertDialog(
            onDismissRequest = { showHintWarningDialog = false },
            title = { Text(if(isEnglish) "Warning" else "마지막 기회!") },
            text = { Text(if(isEnglish) "Hint = Game Over?" else "하트가 1개뿐입니다! 힌트를 쓰면 게임이 종료됩니다.") },
            confirmButton = { Button(onClick = { showHintWarningDialog = false; executeHint() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text(if(isEnglish) "Use" else "사용") } },
            dismissButton = { TextButton(onClick = { showHintWarningDialog = false }) { Text(if(isEnglish) "Cancel" else "취소") } }
        )
    }

    if (showHelpDialog) {
        val helpSections = if (isEnglish) listOf(
            "🎯 Goal" to "Fill every row, column, and box with numbers 1–N (N = grid size) without repeats.",
            "🖱️ How to Play" to "1. Tap a cell to select it.\n2. Enter a number using the button pad or keyboard.\n3. Tap X / Backspace to erase.",
            "✏️ Memo Mode" to "Tap MEMO ON to write small candidate numbers in a cell.\nWhen you confirm a number, conflicting memos in the same row, column, and box are removed automatically.",
            "💡 Hint" to "Tap HINT to reveal the correct answer for the selected cell.\nCosts 1 ❤. Cannot be used on the last heart without a warning.",
            "❤️ Lives" to "You start with 5 hearts. Each wrong number costs 1 heart.\nHints also cost 1 heart. Game over at 0 hearts.",
            "⭐ Highlight" to "Selected cell → Blue\nSame number cells → Green\nSame row / column / box → Light grey",
            "🏆 Records" to "Best time and win streak are saved per grid size and difficulty.\nStreak resets on game over.",
            "💾 Auto-Save" to "Progress is saved automatically every 10 seconds.\nTap RESUME on the main screen to continue.",
            "📐 Grid Sizes" to "5×5: Quick and easy warm-up\n7×7: Medium challenge (rows & columns only)\n9×9: Classic sudoku with 3×3 boxes"
        ) else listOf(
            "🎯 목표" to "모든 행·열·박스에 1~N(N=격자 크기)을 중복 없이 채우세요.",
            "🖱️ 플레이 방법" to "1. 셀을 탭하여 선택합니다.\n2. 버튼패드 또는 키보드로 숫자를 입력합니다.\n3. X / 백스페이스로 지웁니다.",
            "✏️ 메모 모드" to "메모 켬 버튼을 탭하면 셀 안에 작은 후보 숫자를 적을 수 있습니다.\n숫자를 확정하면 같은 행·열·박스의 관련 메모가 자동으로 제거됩니다.",
            "💡 힌트" to "선택한 셀의 정답을 알려줍니다.\n❤ 1개를 소모합니다. 마지막 하트일 때는 경고창이 표시됩니다.",
            "❤️ 목숨" to "하트 5개로 시작합니다. 틀린 숫자를 입력하면 1개 감소합니다.\n힌트도 1개를 소모합니다. 0개가 되면 게임 오버입니다.",
            "⭐ 하이라이트" to "선택한 셀 → 파란색\n같은 숫자 셀 → 초록색\n같은 행·열·박스 → 연한 회색",
            "🏆 기록" to "격자 크기·난이도별로 최고 기록과 연승이 저장됩니다.\n게임 오버 시 연승이 초기화됩니다.",
            "💾 자동 저장" to "10초마다 진행 상황이 자동으로 저장됩니다.\n메인 화면의 이어서 하기 버튼으로 이어 플레이할 수 있습니다.",
            "📐 격자 크기" to "5×5: 가볍게 즐기는 워밍업\n7×7: 행·열만 적용되는 중간 단계\n9×9: 3×3 박스까지 있는 클래식 스도쿠"
        )
        AlertDialog(
            onDismissRequest = { showHelpDialog = false; isTimerRunning = true },
            title = { Text(if(isEnglish) "📖 How to Play" else "📖 게임 설명서", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF121212)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    helpSections.forEach { (title, body) ->
                        Spacer(Modifier.height(10.dp))
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF121212))
                        Spacer(Modifier.height(3.dp))
                        Text(body, fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFF333333))
                        Divider(modifier = Modifier.padding(top = 10.dp), color = Color.Gray.copy(alpha = 0.3f))
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showHelpDialog = false; isTimerRunning = true }) {
                    Text(if(isEnglish) "Got it!" else "확인")
                }
            }
        )
    }

}

// --- [6] 게임 생성 및 기타 로직 ---
suspend fun createAdvancedGameWithRetry(size: Int, diff: Difficulty): Pair<List<SudokuCell>, Array<IntArray>> {
    val targetEmpty = when(size) {
        5 -> when(diff) { Difficulty.EASY -> 8;  Difficulty.MEDIUM -> 12; else -> 15 }
        // 7x7은 49칸, 행/열 제약만 있어 유일해 보장이 어려움 → 제거 수 보수적으로 설정
        7 -> when(diff) { Difficulty.EASY -> 18; Difficulty.MEDIUM -> 24; else -> 30 }
        else -> when(diff) { Difficulty.EASY -> 36; Difficulty.MEDIUM -> 46; else -> 54 }
    }
    var attempts = 0
    val maxAttempts = if (size == 7) 20 else 10
    while (attempts < maxAttempts) {
        attempts++
        val solution = generateFullSolution(size)
        val board = Array(size) { r -> solution[r].copyOf() }
        val positions = (0 until size * size).shuffled()
        var removed = 0
        for (pos in positions) {
            if (removed >= targetEmpty) break
            val r1 = pos / size; val c1 = pos % size
            if (board[r1][c1] == 0) continue
            val useSymmetry = (1..100).random() <= 70
            val r2 = (size - 1) - r1; val c2 = (size - 1) - c1
            val v1 = board[r1][c1]; val v2 = board[r2][c2]
            if (useSymmetry && v2 != 0) {
                board[r1][c1] = 0; board[r2][c2] = 0
                if (countSolutions(board, size) == 1) { removed += if (r1 == r2 && c1 == c2) 1 else 2 }
                else { board[r1][c1] = v1; board[r2][c2] = v2 }
            } else {
                board[r1][c1] = 0
                if (countSolutions(board, size) == 1) removed += 1 else board[r1][c1] = v1
            }
        }
        if (removed >= targetEmpty) return Pair(List(size * size) { i -> SudokuCell(i/size, i%size, board[i/size][i%size], board[i/size][i%size] != 0, false, emptySet()) }, solution)
    }
    val sol = generateFullSolution(size)
    return Pair(List(size*size){SudokuCell(it/size, it%size, sol[it/size][it%size], true, false, emptySet())}, sol)
}

fun generateFullSolution(size: Int): Array<IntArray> {
    val b = Array(size) { IntArray(size) { 0 } }
    fun solve(r: Int, c: Int): Boolean {
        if (r == size) return true
        val nR = if (c == size - 1) r + 1 else r
        val nC = (c + 1) % size
        val numbers = (1..size).shuffled()
        for (n in numbers) { if (isSafe(b, r, c, n, size)) { b[r][c] = n; if (solve(nR, nC)) return true; b[r][c] = 0 } }
        return false
    }
    solve(0, 0); return b
}

fun countSolutions(board: Array<IntArray>, size: Int, limit: Int = 2): Int {
    var count = 0
    fun solve(b: Array<IntArray>): Boolean {
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (b[r][c] == 0) {
                    for (n in 1..size) { if (isSafe(b, r, c, n, size)) { b[r][c] = n; if (solve(b)) { if (count >= limit) return true }; b[r][c] = 0 } }
                    return false
                }
            }
        }
        count++; return count >= limit
    }
    solve(Array(size) { r -> board[r].copyOf() }); return count
}

fun isSafe(b: Array<IntArray>, r: Int, c: Int, n: Int, s: Int): Boolean {
    // 행/열 체크 (모든 크기 공통)
    for (i in 0 until s) if (b[r][i] == n || b[i][c] == n) return false
    // 박스 체크
    when (s) {
        9 -> {
            // 9x9: 3x3 박스 9개
            val br = (r / 3) * 3; val bc = (c / 3) * 3
            for (i in 0..2) for (j in 0..2) if (b[br + i][bc + j] == n) return false
        }
        7 -> {
            // 7x7: 표준 스도쿠 박스가 없으므로 박스 체크 생략
            // (행/열 제약만으로 고유해 보장 — 7x7은 비표준 그리드)
        }
        // 5x5도 박스 없음 (행/열만)
    }
    return true
}

fun checkBoardValidity(curr: List<SudokuCell>, last: Int, s: Int, onW: () -> Unit): List<SudokuCell> {
    val cell = curr[last]; val r = last / s; val c = last % s
    val isD = curr.filterIndexed { i, o ->
        if (i == last || o.value == 0) return@filterIndexed false
        val ir = i/s; val ic = i%s
        val sameB = s == 9 && (ir/3 == r/3 && ic/3 == c/3)
        (ir == r || ic == c || sameB) && o.value == cell.value
    }.isNotEmpty()
    if (isD) onW()
    return curr.mapIndexed { i, item -> if (i == last) item.copy(isError = isD) else item }
}

fun Modifier.drawBehindGrid(r: Int, c: Int, gridSize: Int, color: Color, isLight: Boolean): Modifier = this.drawBehind {
    val sW = if (isLight) 2.dp.toPx() else 1.dp.toPx()
    val tW = if (isLight) 5.dp.toPx() else 3.5.dp.toPx()
    val w = size.width; val h = size.height
    val rW = if (gridSize == 9 && c % 3 == 2 && c != 8) tW else sW
    drawLine(color, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w, h), rW)
    val bW = if (gridSize == 9 && r % 3 == 2 && r != 8) tW else sW
    drawLine(color, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(w, h), bW)
}
