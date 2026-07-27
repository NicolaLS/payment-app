import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import javax.inject.Inject

/** Reports release signing readiness without printing secret material. */
abstract class PrintReleaseSigningConfigTask : DefaultTask() {
    @get:Input
    abstract val statusLines: ListProperty<String>

    @TaskAction
    fun printStatus() {
        statusLines.get().forEach(logger::lifecycle)
    }
}

/**
 * Builds a signed APK set from the release bundle using `bundletool`.
 *
 * Passwords are never passed on the command line: an existing password file is referenced
 * directly, otherwise the password is written to an owner-only scratch file that is deleted
 * once `bundletool` exits.
 */
abstract class BundletoolBuildApksTask : DefaultTask() {
    @get:InputFile
    abstract val bundleFile: RegularFileProperty

    @get:OutputFile
    abstract val apksFile: RegularFileProperty

    /** Universal mode produces one APK for sideloading; otherwise splits for a device. */
    @get:Input
    abstract val universal: Property<Boolean>

    @get:Internal
    abstract val keystore: Property<String>

    @get:Internal
    abstract val keyAlias: Property<String>

    @get:Internal
    abstract val storePassword: Property<String>

    @get:Internal
    abstract val storePasswordFile: Property<String>

    @get:Internal
    abstract val keyPassword: Property<String>

    @get:Internal
    abstract val keyPasswordFile: Property<String>

    @get:Internal
    abstract val adbExecutable: Property<String>

    @get:Internal
    abstract val passwordScratchDir: DirectoryProperty

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    fun buildApks() {
        val output = apksFile.get().asFile
        output.parentFile.mkdirs()

        val scratchDir = passwordScratchDir.get().asFile
        val scratchFiles = mutableListOf<File>()
        try {
            val storePasswordArgument =
                passwordArgument(storePasswordFile, storePassword, scratchDir, "store", scratchFiles)
            val keyPasswordArgument =
                passwordArgument(keyPasswordFile, keyPassword, scratchDir, "key", scratchFiles)

            val arguments = mutableListOf(
                "build-apks",
                "--bundle=${bundleFile.get().asFile}",
                "--output=$output",
                "--overwrite",
                "--ks=${keystore.get()}",
                "--ks-key-alias=${keyAlias.get()}",
                "--ks-pass=$storePasswordArgument",
                "--key-pass=$keyPasswordArgument",
            )
            if (universal.get()) {
                arguments += "--mode=universal"
            } else {
                arguments += "--connected-device"
                adbExecutable.orNull?.let { arguments += "--adb=$it" }
            }

            execOperations.exec {
                executable = BUNDLETOOL
                args(arguments)
            }
        } finally {
            scratchFiles.forEach { it.delete() }
        }
    }
}

/** Installs a previously built APK set on the connected device. */
abstract class BundletoolInstallApksTask : DefaultTask() {
    @get:InputFile
    abstract val apksFile: RegularFileProperty

    @get:Internal
    abstract val adbExecutable: Property<String>

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    fun installApks() {
        val arguments = mutableListOf("install-apks", "--apks=${apksFile.get().asFile}")
        adbExecutable.orNull?.let { arguments += "--adb=$it" }

        execOperations.exec {
            executable = BUNDLETOOL
            args(arguments)
        }
    }
}

internal const val BUNDLETOOL = "bundletool"

private fun passwordArgument(
    fileProperty: Property<String>,
    valueProperty: Property<String>,
    scratchDir: File,
    name: String,
    scratchFiles: MutableList<File>,
): String {
    val suppliedFile = fileProperty.orNull?.takeIf { it.isNotBlank() }?.let(::File)
    if (suppliedFile != null && suppliedFile.isFile) {
        return "file:${suppliedFile.absolutePath}"
    }

    val password = requireNotNull(valueProperty.orNull?.takeIf { it.isNotBlank() }) {
        "No $name password available for bundletool signing."
    }
    val scratchFile = writeOwnerOnlyFile(scratchDir, "$name-password.txt", password)
    scratchFiles += scratchFile
    return "file:${scratchFile.absolutePath}"
}

private fun writeOwnerOnlyFile(scratchDir: File, fileName: String, content: String): File {
    val directory = scratchDir.toPath()
    Files.createDirectories(directory)
    runCatching {
        Files.setPosixFilePermissions(
            directory,
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ),
        )
    }

    val target = directory.resolve(fileName)
    Files.deleteIfExists(target)
    val ownerOnly = PosixFilePermissions.asFileAttribute(
        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
    )
    Files.createFile(target, ownerOnly)
    // No trailing newline: bundletool reads the password file verbatim.
    target.toFile().writeText(content)
    return target.toFile()
}
