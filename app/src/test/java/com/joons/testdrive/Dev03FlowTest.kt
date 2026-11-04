package com.joons.testdrive

import com.joons.testdrive.domain.DataSource
import com.joons.testdrive.domain.DrivePhase
import com.joons.testdrive.domain.DriveScreen
import com.joons.testdrive.domain.DriveState
import com.joons.testdrive.domain.PointEvaluation
import com.joons.testdrive.domain.SourcedText
import com.joons.testdrive.domain.familySampleSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Dev03FlowTest {
    @Test fun sampleDataContinuesToResultWithSourceAndEvaluation() {
        val sample = familySampleSession()
        val edited = sample.copy(
            points = sample.points.mapIndexed { index, point ->
                if (index == 0) point.copy(title = SourcedText("뒷좌석 공간", DataSource.USER)) else point
            },
            evaluations = listOf(PointEvaluation("family-0", rating = 4, note = SourcedText("넉넉함", DataSource.USER))),
        )
        val result = DriveState().start(edited).toPoints().toChecklist().beginDrive().beginEvaluation().finish()

        assertEquals(DriveScreen.RESULT, result.screen)
        assertEquals(DrivePhase.FINISHED, result.session?.phase)
        assertEquals(sample.context, result.session?.context)
        assertEquals("뒷좌석 공간", result.session?.points?.first()?.title?.value)
        assertEquals(DataSource.USER, result.session?.points?.first()?.title?.source)
        assertEquals(4, result.session?.evaluations?.first()?.rating)
        assertEquals("넉넉함", result.session?.evaluations?.first()?.note?.value)
        assertEquals(sample.motion, result.session?.motion)
    }

    @Test fun flowRejectsMissingContextOrSelection() {
        val sample = familySampleSession()
        val blankContext = sample.copy(context = sample.context.copy(description = SourcedText("", DataSource.USER)))
        assertEquals(DriveScreen.CONTEXT, DriveState().start(blankContext).toPoints().screen)
        val noSelection = sample.copy(points = sample.points.map { it.copy(selected = false) })
        assertEquals(DriveScreen.POINTS, DriveState().start(noSelection).toPoints().toChecklist().screen)
        assertFalse(noSelection.points.any { it.selected })
        assertTrue(sample.points.all { it.title.source == DataSource.SAMPLE })
    }
}
