package com.joons.testdrive

import android.os.Bundle
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joons.testdrive.domain.DataSource
import com.joons.testdrive.domain.DriveScreen
import com.joons.testdrive.domain.DriveState
import com.joons.testdrive.domain.PointEvaluation
import com.joons.testdrive.domain.SamplePointSource
import com.joons.testdrive.domain.SourcedText
import com.joons.testdrive.domain.UserContext
import com.joons.testdrive.domain.Vehicle
import com.joons.testdrive.domain.DriveSession
import com.joons.testdrive.domain.familySampleSession
import com.joons.testdrive.ui.theme.TestDriveTheme
import java.util.UUID

class Dev03FlowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TestDriveTheme { Dev03Flow(onExit = { finish() }) } }
    }
}

@Composable
private fun Dev03Flow(onExit: () -> Unit) {
    val pointSource = remember { SamplePointSource() }
    var state by remember { mutableStateOf(DriveState()) }
    var vehicleName by remember { mutableStateOf("") }
    var contextText by remember { mutableStateOf("") }
    val session = state.session

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("DriveFit · ${state.screen.label}", style = MaterialTheme.typography.headlineSmall)
        when (state.screen) {
            DriveScreen.HOME -> {
                OutlinedTextField(vehicleName, { vehicleName = it }, label = { Text("시승 차량") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val vehicle = Vehicle(SourcedText(vehicleName.trim(), DataSource.USER))
                    val context = UserContext(SourcedText("", DataSource.USER))
                    state = state.start(DriveSession(UUID.randomUUID().toString(), vehicle, context, pointSource.suggest(context, vehicle)))
                }, enabled = vehicleName.isNotBlank()) { Text("새 시승 시작") }
                OutlinedButton(onClick = {
                    state = state.start(familySampleSession())
                    vehicleName = state.session!!.vehicle.name.value
                    contextText = state.session!!.context.description.value
                }) { Text("4인 가족 샘플로 시작") }
                OutlinedButton(onClick = onExit) { Text("기존 화면으로") }
            }
            DriveScreen.CONTEXT -> {
                Text("차량: ${session!!.vehicle.name.value} · ${session.vehicle.name.source.label}")
                OutlinedTextField(contextText, { value ->
                    contextText = value
                    state = state.copy(session = session.copy(context = UserContext(SourcedText(value, DataSource.USER))))
                }, label = { Text("시승 상황") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Button(onClick = { state = state.toPoints() }, enabled = contextText.isNotBlank()) { Text("확인할 항목 보기") }
            }
            DriveScreen.POINTS -> {
                Text("입력 상황: ${session!!.context.description.value}")
                session.points.forEach { point ->
                    Row {
                        Checkbox(point.selected, { selected ->
                            state = state.copy(session = session.copy(points = session.points.map {
                                if (it.id == point.id) it.copy(selected = selected) else it
                            }))
                        })
                        OutlinedTextField(point.title.value, { title ->
                            state = state.copy(session = session.copy(points = session.points.map {
                                if (it.id == point.id) it.copy(title = SourcedText(title, DataSource.USER)) else it
                            }))
                        }, label = { Text("${point.title.source.label} 항목") })
                    }
                }
                Button(onClick = { state = state.toChecklist() }, enabled = session.points.any { it.selected }) { Text("출발 전 확인") }
            }
            DriveScreen.CHECKLIST -> {
                Text("선택한 항목 ${session!!.points.count { it.selected }}개를 시승 중 확인합니다.")
                Text("출발 전에 항목을 확인하고, 주행 중에는 화면을 조작하지 마세요.")
                Button(onClick = { state = state.beginDrive() }) { Text("시승 시작") }
            }
            DriveScreen.DRIVING -> {
                Text("앱 진행 상태: 시승 중")
                Text("실제 차량 상태: ${session!!.motion.name} (미연결)")
                Text("주행 중에는 화면을 조작하지 마세요.")
                Button(onClick = { state = state.beginEvaluation() }) { Text("정차 후 평가하기") }
            }
            DriveScreen.EVALUATION -> {
                Text("실제 정차 상태는 아직 차량 SDK로 확인하지 않습니다.")
                session!!.points.filter { it.selected }.forEach { point ->
                    val evaluation = session.evaluations.find { it.pointId == point.id } ?: PointEvaluation(point.id)
                    Text(point.title.value, style = MaterialTheme.typography.titleMedium)
                    Row {
                        (1..5).forEach { score ->
                            OutlinedButton(onClick = {
                                state = state.copy(session = session.withEvaluation(evaluation.copy(rating = score, notObserved = false)))
                            }) { Text(if (evaluation.rating == score) "[$score]" else "$score") }
                        }
                    }
                    Row {
                        Checkbox(evaluation.notObserved, { checked ->
                            state = state.copy(session = session.withEvaluation(evaluation.copy(notObserved = checked, rating = if (checked) null else evaluation.rating)))
                        })
                        Text("확인하지 못함")
                    }
                    OutlinedTextField(evaluation.note.value, { note ->
                        state = state.copy(session = session.withEvaluation(evaluation.copy(note = SourcedText(note, DataSource.USER))))
                    }, label = { Text("메모") }, modifier = Modifier.fillMaxWidth())
                }
                Button(onClick = { state = state.finish() }) { Text("결과 보기") }
            }
            DriveScreen.RESULT -> {
                Text("${session!!.vehicle.name.value} · ${session.vehicle.name.source.label}")
                Text("상황: ${session.context.description.value} · ${session.context.description.source.label}")
                session.points.filter { it.selected }.forEach { point ->
                    val evaluation = session.evaluations.find { it.pointId == point.id }
                    Text("${point.title.value} (${point.title.source.label}): ${if (evaluation?.notObserved == true) "확인하지 못함" else evaluation?.rating?.let { "$it/5" } ?: "미평가"}")
                    if (!evaluation?.note?.value.isNullOrBlank()) Text("메모: ${evaluation!!.note.value}")
                }
                OutlinedButton(onClick = {
                    state = DriveState()
                    vehicleName = ""
                    contextText = ""
                }) { Text("새 시승") }
            }
        }
        if (state.screen in setOf(DriveScreen.CONTEXT, DriveScreen.POINTS, DriveScreen.CHECKLIST, DriveScreen.EVALUATION, DriveScreen.RESULT)) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { state = state.back() }) { Text("뒤로") }
        }
    }
}

private fun DriveSession.withEvaluation(value: PointEvaluation): DriveSession =
    copy(evaluations = evaluations.filterNot { it.pointId == value.pointId } + value)

private val DriveScreen.label: String get() = when (this) {
    DriveScreen.HOME -> "시작"
    DriveScreen.CONTEXT -> "상황 입력"
    DriveScreen.POINTS -> "시승 항목"
    DriveScreen.CHECKLIST -> "출발 전 확인"
    DriveScreen.DRIVING -> "시승 중"
    DriveScreen.EVALUATION -> "평가"
    DriveScreen.RESULT -> "결과"
}

private val DataSource.label: String get() = when (this) {
    DataSource.USER -> "사용자 입력"
    DataSource.SAMPLE -> "샘플"
    DataSource.VEHICLE_SDK -> "차량 SDK"
    DataSource.AI -> "AI"
}
