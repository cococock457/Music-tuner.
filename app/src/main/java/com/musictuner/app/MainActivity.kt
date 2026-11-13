package com.musictuner.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TunerTheme { TunerApp() } }
    }
}

data class Instrument(val label: String, val shift: Int)

val instruments = listOf(
    Instrument("C (концертный)", 0),
    Instrument("Альт сакс E♭", 9),
    Instrument("Тенор сакс B♭", 14),
    Instrument("Сопр. сакс B♭", 2),
    Instrument("Бари сакс E♭", 21),
    Instrument("Кларнет B♭", 2),
    Instrument("Бас-кларнет B♭", 14),
    Instrument("Труба B♭", 2),
    Instrument("Валторна F", 7),
    Instrument("Англ. рожок F", 7),
    Instrument("Флейта C", 0),
    Instrument("Гобой C", 0),
    Instrument("Фагот C", 0),
    Instrument("Тромбон C", 0),
    Instrument("Блокфлейта C", -12)
)

private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private fun neighbor(n: NoteInfo, d: Int): String {
    val total = n.octave * 12 + NAMES.indexOf(n.name) + d
    return NAMES[((total % 12) + 12) % 12] + Math.floorDiv(total, 12)
}

@Composable
fun RowScope.Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) bg else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

@Composable
fun InstrumentChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        color = fg,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) bg else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
fun TunerApp() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    var tab by remember { mutableStateOf(0) }
    var instrument by remember { mutableStateOf(instruments[1]) }
    var refA by remember { mutableStateOf(440f) }
    var note by remember { mutableStateOf<NoteInfo?>(null) }
    var freq by remember { mutableStateOf(0f) }

    val shiftState = rememberUpdatedState(instrument.shift)
    val refState = rememberUpdatedState(refA)

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val sampleRate = 44100
            val size = 4096
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, size * 2)
            )
            val buf = ShortArray(size)
            try {
                recorder.startRecording()
                while (isActive) {
                    val read = recorder.read(buf, 0, size)
                    if (read > 0) {
                        val f = PitchDetector.detect(buf, read, sampleRate)
                        if (f > 0) {
                            freq = f
                            note = PitchDetector.noteInfo(f, refState.value, shiftState.value)
                        } else {
                            note = null
                        }
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
            }
        }
    }

    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Surface(modifier = Modifier.fillMaxSize(), color = cs.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Тюнер", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = cs.onBackground)
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                instruments.forEach { ins ->
                    InstrumentChip(ins.label, ins == instrument) { instrument = ins }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (tab != 0) {
                Text(
                    "Скоро",
                    color = cs.onSurfaceVariant,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp)
                )
            } else if (!hasPermission) {
                Text(
                    "Разреши доступ к микрофону",
                    color = cs.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp)
                )
            } else {
                val n = note
                val cents = n?.cents ?: 0
                val needle by animateFloatAsState(
                    targetValue = cents.toFloat(),
                    animationSpec = tween(150),
                    label = "needle"
                )
                val primary = cs.primary
                val secondary = cs.secondary
                val muted = cs.onSurfaceVariant
                val bg = cs.surface

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cs.surface, shape)
                        .border(1.dp, cs.outline, shape)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height - 12.dp.toPx()
                        val pivot = Offset(cx, cy)
                        val radius = minOf(size.width / 2f - 8.dp.toPx(), cy - 6.dp.toPx())
                        for (i in -10..10) {
                            val a = Math.toRadians(i * 7.0)
                            val major = i % 5 == 0
                            val r1 = radius - (if (major) 18.dp.toPx() else 12.dp.toPx())
                            val s = sin(a).toFloat()
                            val c = cos(a).toFloat()
                            drawLine(
                                color = if (i == 0) primary else muted,
                                start = Offset(cx + r1 * s, cy - r1 * c),
                                end = Offset(cx + radius * s, cy - radius * c),
                                strokeWidth = (if (i == 0) 3.dp else if (major) 2.dp else 1.dp).toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        if (n != null) {
                            val na = Math.toRadians(needle.coerceIn(-50f, 50f) / 50.0 * 70.0)
                            val len = radius - 24.dp.toPx()
                            drawLine(
                                color = secondary,
                                start = pivot,
                                end = Offset(cx + len * sin(na).toFloat(), cy - len * cos(na).toFloat()),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        drawCircle(primary, 8.dp.toPx(), pivot)
                        drawCircle(bg, 3.dp.toPx(), pivot)
                    }

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = n?.name ?: "–",
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.primary
                        )
                        if (n != null) {
                            Text(
                                text = n.octave.toString(),
                                fontSize = 24.sp,
                                color = cs.secondary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    val inTune = n != null && abs(cents) <= 5
                    val statusText = when {
                        n == null -> "Играй ноту"
                        inTune -> "В строе"
                        abs(cents) <= 25 -> (if (cents > 0) "Чуть выше +$cents" else "Чуть ниже $cents")
                        else -> (if (cents > 0) "Выше +$cents" else "Ниже $cents")
                    }
                    val pillBg = when {
                        n == null -> cs.outline
                        inTune -> cs.primary
                        else -> cs.secondary
                    }
                    val pillFg = when {
                        n == null -> cs.onSurface
                        inTune -> cs.onPrimary
                        else -> cs.onSecondary
                    }
                    Text(
                        text = statusText,
                        color = pillFg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(pillBg)
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                    Spacer(Modifier.height(6.dp))

                    val sub = if (n != null) {
                        val sounding = PitchDetector.noteInfo(freq, refA, 0).name
                        "${freq.toInt()} Гц · звучит $sounding"
                    } else {
                        "A = ${refA.toInt()} Гц"
                    }
                    Text(sub, fontSize = 12.sp, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))

                    val ladder = if (n != null) {
                        listOf(neighbor(n, -1), "${n.name}${n.octave}", neighbor(n, 1))
                    } else {
                        listOf("–", "–", "–")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ladder.forEachIndexed { idx, t ->
                            val on = idx == 1 && n != null
                            Text(
                                text = t,
                                fontSize = 13.sp,
                                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                                color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) cs.primary else cs.surface)
                                    .border(1.dp, if (on) cs.primary else cs.outline, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(415f, 440f, 442f).forEach { a ->
                            Chip("A=${a.toInt()}", a == refA) { refA = a }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, cs.outline, RoundedCornerShape(10.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Реклама", fontSize = 11.sp, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                listOf("Тюнер", "Метроном", "Ноты").forEachIndexed { idx, label ->
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (tab == idx) FontWeight.Medium else FontWeight.Normal,
                        color = if (tab == idx) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.clickable { tab = idx }.padding(8.dp)
                    )
                }
            }
        }
    }
}
