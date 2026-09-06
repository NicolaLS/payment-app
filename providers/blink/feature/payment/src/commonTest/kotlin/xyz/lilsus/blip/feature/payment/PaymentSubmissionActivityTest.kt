package xyz.lilsus.blip.feature.payment

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

class PaymentSubmissionActivityTest {
    @Test
    fun submissionRemainsActiveUntilEveryExecutingTaskFinishes() = runTest {
        val tasks = PaymentTaskRegistry(this)
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val firstJob = tasks.launchReplacing("first") { first.await() }
        val secondJob = tasks.launchReplacing("second") { second.await() }
        assertTrue(tasks.active.value)
        first.complete(Unit)
        firstJob.join()
        assertTrue(tasks.active.value)
        second.complete(Unit)
        secondJob.join()
        assertFalse(tasks.active.value)
    }
}
