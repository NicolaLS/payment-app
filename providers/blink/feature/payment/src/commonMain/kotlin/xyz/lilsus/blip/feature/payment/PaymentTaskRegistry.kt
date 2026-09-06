package xyz.lilsus.blip.feature.payment

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PaymentTaskRegistry(private val scope: CoroutineScope) {
    private val mutableActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val active: kotlinx.coroutines.flow.StateFlow<Boolean> = mutableActive

    private var generation = 0L
    private var nextTaskId = 0L
    private val tasks = mutableMapOf<String, TrackedTask>()

    fun launch(block: suspend (PaymentTaskToken) -> Unit): Job {
        val taskId = nextTaskId++
        return launchReplacing("session-task-$taskId", block)
    }

    fun launchReplacing(key: String, block: suspend (PaymentTaskToken) -> Unit): Job {
        tasks.remove(key)?.job?.cancel()
        val token = PaymentTaskToken(this, generation, key)
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                block(token)
            }
        tasks[key] = TrackedTask(token, job)
        mutableActive.value = true
        job.invokeOnCompletion {
            if (tasks[key]?.job === job) {
                tasks.remove(key)
                mutableActive.value = tasks.isNotEmpty()
            }
        }
        job.start()
        return job
    }

    fun reset() {
        generation += 1
        val activeJobs = tasks.values.map(TrackedTask::job)
        tasks.clear()
        mutableActive.value = false
        activeJobs.forEach(Job::cancel)
    }

    fun isCurrent(token: PaymentTaskToken): Boolean = token.owner === this &&
        token.generation == generation &&
        tasks[token.key]?.token === token

    private data class TrackedTask(val token: PaymentTaskToken, val job: Job)
}

internal class PaymentTaskToken internal constructor(
    internal val owner: PaymentTaskRegistry,
    internal val generation: Long,
    internal val key: String
) {
    fun ensureCurrent() {
        if (!owner.isCurrent(this)) {
            throw CancellationException("Payment task is no longer current")
        }
    }
}
