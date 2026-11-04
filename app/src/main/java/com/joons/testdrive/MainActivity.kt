package com.joons.testdrive

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joons.testdrive.ui.theme.TestDriveTheme
import org.json.JSONArray
import org.json.JSONObject

private val points = listOf(
    "운전석 시야와 착좌감",
    "가속·제동할 때의 느낌",
    "승차감과 실내 소음",
    "실내 공간과 수납",
    "버튼과 화면 조작의 편리함"
)

private data class DriveRecord(
    val vehicle: String,
    val notes: Map<Int, String>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("dev02_probe", false)) {
            startActivity(Intent(this, Dev02ProbeActivity::class.java).apply {
                putExtra("dev02_action", intent.getStringExtra("dev02_action"))
            })
            finish()
            return
        }
        if (intent.getBooleanExtra("sdk_probe", false)) {
            startActivity(Intent(this, SdkProbeActivity::class.java))
            finish()
            return
        }
        setContent {
            TestDriveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    DriveFitApp(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun DriveFitApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val records = remember { mutableStateListOf<DriveRecord>().apply { addAll(loadRecords(context)) } }
    var screen by remember { mutableIntStateOf(0) }
    var vehicleName by remember { mutableStateOf("") }
    var selectedPoints by remember { mutableStateOf(setOf<Int>()) }
    var notes by remember { mutableStateOf(mapOf<Int, String>()) }
    var confirmParked by remember { mutableStateOf(false) }

    if (confirmParked) {
        AlertDialog(
            onDismissRequest = { confirmParked = false },
            title = { Text("정차한 상태인가요?") },
            text = { Text("안전한 곳에 정차한 뒤 느낌을 기록해 주세요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmParked = false
                    screen = 3
                }) { Text("정차했어요") }
            },
            dismissButton = {
                TextButton(onClick = { confirmParked = false }) { Text("취소") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("DriveFit", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))

        when (screen) {
            0 -> {
                Text("어떤 차량을 시승하시나요?", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = vehicleName,
                    onValueChange = { vehicleName = it },
                    label = { Text("차량명") },
                    placeholder = { Text("예: 아이오닉 9") },
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { screen = 1 }, enabled = vehicleName.isNotBlank()) {
                    Text("이 차량으로 시작")
                }
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { screen = 4 }) { Text("지난 시승 기록 보기") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { context.startActivity(Intent(context, Dev03FlowActivity::class.java)) }) {
                    Text("DEV-03 새 시승 흐름")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { context.startActivity(Intent(context, Dev02ProbeActivity::class.java)) }) {
                    Text("DEV-02 SDK 검증")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { context.startActivity(Intent(context, SdkProbeActivity::class.java)) }) {
                    Text("추가 SDK 연결 확인")
                }
            }

            1 -> {
                Text("$vehicleName 시승에서 확인할 점", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                points.forEachIndexed { index, point ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = index in selectedPoints,
                            onCheckedChange = { checked ->
                                selectedPoints = if (checked) selectedPoints + index else selectedPoints - index
                            }
                        )
                        Text(point)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("선택한 항목: ${selectedPoints.size}개")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { screen = 2 }, enabled = selectedPoints.isNotEmpty()) {
                    Text("시승 시작")
                }
            }

            2 -> {
                Text("$vehicleName 시승 중", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text("이번 시승에서 확인할 점")
                Spacer(Modifier.height(12.dp))
                selectedPoints.sorted().forEach { index ->
                    Text("• ${points[index]}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("주행 중에는 화면을 조작하지 마세요.")
                Spacer(Modifier.height(24.dp))
                Button(onClick = { confirmParked = true }) { Text("정차 후 기록하기") }
            }

            3 -> {
                Text("$vehicleName 시승 기록", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("선택한 항목에서 느낀 점을 적어주세요. 비워둬도 저장할 수 있어요.")
                Spacer(Modifier.height(16.dp))
                selectedPoints.sorted().forEach { index ->
                    OutlinedTextField(
                        value = notes[index].orEmpty(),
                        onValueChange = { value -> notes = notes + (index to value) },
                        label = { Text(points[index]) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Button(onClick = {
                    records.add(DriveRecord(vehicleName.trim(), selectedPoints.associateWith { notes[it].orEmpty() }))
                    saveRecords(context, records)
                    screen = 4
                }) { Text("기록 저장") }
            }

            4 -> {
                Text("시승 기록", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                if (records.isEmpty()) Text("아직 저장한 기록이 없어요.")
                records.forEachIndexed { index, record ->
                    Text("${index + 1}. ${record.vehicle}", style = MaterialTheme.typography.titleMedium)
                    record.notes.forEach { (pointIndex, note) ->
                        Text("${points[pointIndex]}: ${note.ifBlank { "미기록" }}")
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (records.size >= 2) {
                    Button(onClick = { screen = 5 }) { Text("최근 두 차량 비교") }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedButton(onClick = {
                    vehicleName = ""
                    selectedPoints = emptySet()
                    notes = emptyMap()
                    screen = 0
                }) { Text("새 시승 시작") }
            }

            5 -> {
                val first = records[records.lastIndex - 1]
                val second = records.last()
                Text("시승 비교", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("${first.vehicle} ↔ ${second.vehicle}")
                Spacer(Modifier.height(16.dp))
                (first.notes.keys + second.notes.keys).sorted().forEach { index ->
                    Text(points[index], style = MaterialTheme.typography.titleMedium)
                    Text("${first.vehicle}: ${first.notes[index]?.ifBlank { "미기록" } ?: "확인 안 함"}")
                    Text("${second.vehicle}: ${second.notes[index]?.ifBlank { "미기록" } ?: "확인 안 함"}")
                    Spacer(Modifier.height(16.dp))
                }
                OutlinedButton(onClick = { screen = 4 }) { Text("기록 목록으로") }
            }
        }
    }
}

private fun loadRecords(context: android.content.Context): List<DriveRecord> = try {
    val raw = context.getSharedPreferences("drivefit", 0).getString("records", "[]") ?: "[]"
    val array = JSONArray(raw)
    List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val savedNotes = item.getJSONObject("notes")
        val notes = mutableMapOf<Int, String>()
        savedNotes.keys().forEach { key -> notes[key.toInt()] = savedNotes.getString(key) }
        DriveRecord(item.getString("vehicle"), notes)
    }
} catch (_: Exception) {
    emptyList()
}

private fun saveRecords(context: android.content.Context, records: List<DriveRecord>) {
    val array = JSONArray()
    records.forEach { record ->
        val savedNotes = JSONObject()
        record.notes.forEach { (index, note) -> savedNotes.put(index.toString(), note) }
        array.put(JSONObject().put("vehicle", record.vehicle).put("notes", savedNotes))
    }
    context.getSharedPreferences("drivefit", 0)
        .edit().putString("records", array.toString()).apply()
}

@Preview(showBackground = true)
@Composable
fun DriveFitPreview() {
    TestDriveTheme { DriveFitApp() }
}
