package io.github.jakub_m_arch.voicemator.model

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** A single file that makes up a speech model, with the size and checksum it must match. */
data class ModelFile(val name: String, val sizeBytes: Long, val sha256: String)

/**
 * Moondream's Parakeet Ultra, a post-trained NVIDIA Parakeet TDT 0.6B v3 covering 25 European
 * languages including Polish, converted to int8 ONNX in the layout sherpa-onnx expects.
 *
 * The revision is pinned so every install downloads exactly the same weights.
 */
object ParakeetModel {
    const val ID = "parakeet-ultra-int8"

    private const val REPOSITORY = "leogaudin/sherpa-onnx-nemo-parakeet-ultra-int8"
    private const val REVISION = "f7bc472677ff70be95cdb0ef9df509fee6f5ec0b"

    val encoder = ModelFile(
        name = "encoder.int8.onnx",
        sizeBytes = 652_282_340,
        sha256 = "8bd3b8dc07056def6dfd19910e9f90c9e1a4e7e20901f58bdfe2a570a8081a2a",
    )
    val decoder = ModelFile(
        name = "decoder.int8.onnx",
        sizeBytes = 11_845_275,
        sha256 = "94373c6405c8149d1c9d1e9862514e0e3e88dab1c7de18fac9d5df9c6ba5efdd",
    )
    val joiner = ModelFile(
        name = "joiner.int8.onnx",
        sizeBytes = 6_355_277,
        sha256 = "8a71aaccfdba3d451775507a889afd2257d2e754463fd81095a8c8f290f924c2",
    )
    val tokens = ModelFile(
        name = "tokens.txt",
        sizeBytes = 93_939,
        sha256 = "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d",
    )

    val files = listOf(encoder, decoder, joiner, tokens)

    val totalSizeBytes = files.sumOf { it.sizeBytes }

    fun downloadUrl(file: ModelFile): String =
        "https://huggingface.co/$REPOSITORY/resolve/$REVISION/${file.name}"
}

/**
 * Locates model files in app-specific external storage, which needs no storage permission.
 *
 * Files are downloaded under a temporary name and only renamed to their final name once their
 * checksum is verified, so a file under its final name is always complete.
 */
class ModelStorage(context: Context) {

    private val modelsRoot = File(context.getExternalFilesDir(null), MODELS_DIR)

    val directory: File = File(modelsRoot, ParakeetModel.ID)

    fun fileFor(file: ModelFile): File = File(directory, file.name)

    fun partialFileFor(file: ModelFile): File = File(directory, file.name + PARTIAL_SUFFIX)

    /** Path of the partial file relative to the app's external files dir, as DownloadManager expects. */
    fun partialRelativePath(file: ModelFile): String =
        "$MODELS_DIR/${ParakeetModel.ID}/${file.name}$PARTIAL_SUFFIX"

    fun isComplete(file: ModelFile): Boolean = fileFor(file).length() == file.sizeBytes

    fun isInstalled(): Boolean = ParakeetModel.files.all(::isComplete)

    /** Deletes the model, partial downloads included. */
    fun delete() {
        directory.deleteRecursively()
    }

    /** Frees the space taken by models from earlier app versions. */
    fun deleteOtherModels() {
        modelsRoot.listFiles()
            ?.filter { it.name != ParakeetModel.ID }
            ?.forEach { it.deleteRecursively() }
    }

    /** Verifies a finished download and moves it into place. Returns false if it is corrupt. */
    fun install(file: ModelFile): Boolean {
        val partial = partialFileFor(file)
        val isValid = partial.length() == file.sizeBytes && sha256(partial) == file.sha256
        if (!isValid || !partial.renameTo(fileFor(file))) {
            partial.delete()
            return false
        }
        return true
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MODELS_DIR = "models"
        const val PARTIAL_SUFFIX = ".part"
    }
}
