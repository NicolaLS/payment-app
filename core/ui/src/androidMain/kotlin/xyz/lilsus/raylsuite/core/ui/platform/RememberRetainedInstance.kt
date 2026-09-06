package xyz.lilsus.raylsuite.core.ui.platform

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun <T : Any> rememberRetainedInstance(
    key: String?,
    factory: () -> T,
    onDispose: (T) -> Unit,
    releaseOnLeave: Boolean = false
): T {
    val owner =
        LocalViewModelStoreOwner.current
            ?: error("rememberRetainedInstance requires a ViewModelStoreOwner")
    val holder =
        viewModel<RetainedHolder<T>>(
            viewModelStoreOwner = owner,
            key = key,
            factory =
                object : ViewModelProvider.Factory {
                    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                        @Suppress("UNCHECKED_CAST")
                        return RetainedHolder(factory(), onDispose) as VM
                    }
                }
        )

    val context = LocalContext.current
    DisposableEffect(holder, context, releaseOnLeave) {
        onDispose {
            val activity = generateSequence(context) {
                (it as? ContextWrapper)?.baseContext
            }
                .filterIsInstance<Activity>().firstOrNull()
            if (releaseOnLeave && activity?.isChangingConfigurations != true) holder.release()
        }
    }
    return holder.getOrCreate(factory)
}

private class RetainedHolder<T : Any>(delegate: T, private val onDispose: (T) -> Unit) :
    ViewModel() {
    private var instance: T? = delegate

    fun getOrCreate(factory: () -> T): T = instance ?: factory().also { instance = it }

    fun release() {
        val delegate = instance ?: return
        // The activity retains the holder, but a removed connection must be collectable.
        instance = null
        onDispose(delegate)
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }
}
