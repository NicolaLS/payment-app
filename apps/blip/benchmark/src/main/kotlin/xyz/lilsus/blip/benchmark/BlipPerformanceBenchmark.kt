package xyz.lilsus.blip.benchmark

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BlipPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun grantCameraPermission() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand(
            "pm grant $TARGET_PACKAGE ${Manifest.permission.CAMERA}"
        ).close()
    }

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun cameraStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            TraceSectionMetric(
                sectionName = "camera.start_to_ready",
                mode = TraceSectionMetric.Mode.First
            ),
            TraceSectionMetric(
                sectionName = "camera.start_to_first_frame",
                mode = TraceSectionMetric.Mode.First
            ),
            TraceSectionMetric(
                sectionName = "camera.preview_attach",
                mode = TraceSectionMetric.Mode.First
            )
        ),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait(
            Intent().setComponent(
                ComponentName(TARGET_PACKAGE, CAMERA_BENCHMARK_ACTIVITY)
            )
        )
        Thread.sleep(CAMERA_SETTLE_MILLIS)
    }

    private companion object {
        const val TARGET_PACKAGE = "xyz.lilsus.blip.benchmark"
        const val CAMERA_BENCHMARK_ACTIVITY = "xyz.lilsus.blip.CameraBenchmarkActivity"
        const val ITERATIONS = 5
        const val CAMERA_SETTLE_MILLIS = 3_000L
    }
}
