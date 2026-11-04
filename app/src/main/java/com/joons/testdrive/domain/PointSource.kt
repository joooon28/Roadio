package com.joons.testdrive.domain

interface PointSource {
    fun suggest(context: UserContext, vehicle: Vehicle): List<DrivePoint>
}

/** DEV-08에서 AI 공급자로 교체할 수 있도록 시승 항목 생성 경계를 둔다. */
class SamplePointSource : PointSource {
    override fun suggest(context: UserContext, vehicle: Vehicle): List<DrivePoint> = listOf(
        "가족 4명이 앉았을 때 좌석과 다리 공간",
        "아이와 대화할 때 실내 소음",
        "유모차와 여행 짐을 넣을 수 있는 적재 공간",
        "가족이 함께 탔을 때 승차감",
    ).mapIndexed { index, title ->
        DrivePoint("family-$index", SourcedText(title, DataSource.SAMPLE))
    }
}

fun familySampleSession(pointSource: PointSource = SamplePointSource()): DriveSession {
    val vehicle = Vehicle(SourcedText("아이오닉 9", DataSource.SAMPLE))
    val context = UserContext(SourcedText("부부와 아이 둘, 총 4명이 주말에 이동합니다. 유모차와 여행 짐을 싣고 장거리 주행을 합니다.", DataSource.SAMPLE))
    return DriveSession("family-demo-01", vehicle, context, pointSource.suggest(context, vehicle))
}
