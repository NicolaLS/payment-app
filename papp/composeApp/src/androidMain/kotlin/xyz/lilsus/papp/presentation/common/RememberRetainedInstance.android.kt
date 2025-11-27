package xyz.lilsus.papp.presentation.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
actual fun <T : Any> rememberRetainedInstance(
    key: String?,
    factory: () -> T,
    onDispose: (T) -> Unit
): T {
    val owner = LocalViewModelStoreOwner.current
        ?: error("rememberRetainedInstance requires a ViewModelStoreOwner")

    val holder = viewModel<RetainedHolder<T>>(
        viewModelStoreOwner = owner,
        key = key,
        factory = object : ViewModelProvider.Factory {
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                @Suppress("UNCHECKED_CAST")
                return RetainedHolder(factory(), onDispose) as VM
            }
        }
    )

    return holder.delegate
}

private class RetainedHolder<T : Any>(val delegate: T, private val onDispose: (T) -> Unit) :
    ViewModel() {
    override fun onCleared() {
        onDispose(delegate)
        super.onCleared()
    }
}
