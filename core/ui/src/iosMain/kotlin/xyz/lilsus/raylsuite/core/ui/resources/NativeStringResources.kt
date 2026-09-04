@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class
)

package xyz.lilsus.raylsuite.core.ui.resources

import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.localizedStringWithFormat

/** A string in an Apple String Catalog compiled into the owning application's main bundle. */
data class NativeStringResource(val table: String, val key: String)

fun LocalizedText.resolveNative(): String = argument?.let {
    nativeString(NativeStringResource(resource.table, resource.key), it)
} ?: nativeString(NativeStringResource(resource.table, resource.key))

fun nativeString(resource: NativeStringResource): String = resource.localizedFormat()

fun nativeString(resource: NativeStringResource, argument: String): String =
    NSString.localizedStringWithFormat(
        resource.localizedFormat(),
        NSString.create(string = argument)
    )

fun nativeString(resource: NativeStringResource, first: String, second: String): String =
    NSString.localizedStringWithFormat(
        resource.localizedFormat(),
        NSString.create(string = first),
        NSString.create(string = second)
    )

fun nativeString(resource: NativeStringResource, argument: Int): String =
    NSString.localizedStringWithFormat(resource.localizedFormat(), argument.toLong())

fun nativeString(resource: NativeStringResource, first: Int, second: Int): String =
    NSString.localizedStringWithFormat(
        resource.localizedFormat(),
        first.toLong(),
        second.toLong()
    )

fun nativeString(resource: NativeStringResource, argument: Long): String =
    NSString.localizedStringWithFormat(resource.localizedFormat(), argument)

fun nativePluralString(resource: NativeStringResource, quantity: Int): String =
    NSString.localizedStringWithFormat(resource.localizedFormat(), quantity.toLong())

private fun NativeStringResource.localizedFormat(): String =
    NSBundle.mainBundle.localizedStringForKey(
        key = key,
        value = key,
        table = table
    )
