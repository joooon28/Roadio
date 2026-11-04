package com.joons.testdrive.domain

enum class DataSource { USER, SAMPLE, VEHICLE_SDK, AI }
enum class DriveScreen { HOME, CONTEXT, POINTS, CHECKLIST, DRIVING, EVALUATION, RESULT }
enum class DrivePhase { PREPARING, DRIVING, EVALUATING, FINISHED }
enum class VehicleMotion { UNKNOWN, PARKED, MOVING }

data class SourcedText(val value: String, val source: DataSource)
data class UserContext(val description: SourcedText)
data class Vehicle(val name: SourcedText)
data class DrivePoint(
    val id: String,
    val title: SourcedText,
    val selected: Boolean = true,
)
data class PointEvaluation(
    val pointId: String,
    val rating: Int? = null,
    val note: SourcedText = SourcedText("", DataSource.USER),
    val notObserved: Boolean = false,
)
data class DriveSession(
    val id: String,
    val vehicle: Vehicle,
    val context: UserContext,
    val points: List<DrivePoint>,
    val evaluations: List<PointEvaluation> = emptyList(),
    val phase: DrivePhase = DrivePhase.PREPARING,
    val motion: VehicleMotion = VehicleMotion.UNKNOWN,
)

/** 화면 전환은 앱 흐름에만 적용한다. 차량의 실제 주행 상태는 별도 입력으로 보관한다. */
data class DriveState(
    val screen: DriveScreen = DriveScreen.HOME,
    val session: DriveSession? = null,
) {
    fun start(session: DriveSession): DriveState = copy(screen = DriveScreen.CONTEXT, session = session)

    fun toPoints(): DriveState = if (session != null && session.context.description.value.isNotBlank()) {
        copy(screen = DriveScreen.POINTS)
    } else this

    fun toChecklist(): DriveState = if (session?.points?.any { it.selected } == true) {
        copy(screen = DriveScreen.CHECKLIST)
    } else this

    fun beginDrive(): DriveState = if (screen == DriveScreen.CHECKLIST && session != null) {
        copy(screen = DriveScreen.DRIVING, session = session.copy(phase = DrivePhase.DRIVING))
    } else this

    fun beginEvaluation(): DriveState = if (screen == DriveScreen.DRIVING && session != null) {
        copy(screen = DriveScreen.EVALUATION, session = session.copy(phase = DrivePhase.EVALUATING))
    } else this

    fun finish(): DriveState = if (screen == DriveScreen.EVALUATION && session != null) {
        copy(screen = DriveScreen.RESULT, session = session.copy(phase = DrivePhase.FINISHED))
    } else this

    fun back(): DriveState = when (screen) {
        DriveScreen.CONTEXT -> copy(screen = DriveScreen.HOME)
        DriveScreen.POINTS -> copy(screen = DriveScreen.CONTEXT)
        DriveScreen.CHECKLIST -> copy(screen = DriveScreen.POINTS)
        DriveScreen.EVALUATION -> copy(screen = DriveScreen.DRIVING, session = session?.copy(phase = DrivePhase.DRIVING))
        DriveScreen.RESULT -> copy(screen = DriveScreen.HOME)
        else -> this
    }
}
