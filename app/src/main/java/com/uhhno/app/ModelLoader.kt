package com.uhhno.app

import android.content.Context
import org.vosk.Model
import java.io.*
import java.net.URL
import java.util.zip.ZipInputStream

object ModelLoader {

    private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"

    private fun modelDir(context: Context) = File(context.filesDir, MODEL_NAME)

    fun isReady(context: Context) = modelDir(context).exists()

    fun load(
        context: Context,
        onProgress: (String) -> Unit,
        onReady: (Model) -> Unit,
        onError: (Exception) -> Unit
    ) {
        Thread {
            try {
                val dir = modelDir(context)
                if (dir.exists()) {
                    onProgress("Loading speech model…")
                    try {
                        onReady(Model(dir.absolutePath))
                        return@Thread
                    } catch (_: Exception) {
                        // Partial or corrupted model left by a previous crashed extraction.
                        // Wipe it and fall through to re-download.
                        dir.deleteRecursively()
                    }
                }
                download(context, dir, onProgress)
                onReady(Model(dir.absolutePath))
            } catch (e: Exception) {
                onError(e)
            }
        }.start()
    }

    private fun download(context: Context, destDir: File, onProgress: (String) -> Unit) {
        val zipFile = File(context.cacheDir, "$MODEL_NAME.zip")
        try {
            val conn = URL(MODEL_URL).openConnection()
            val total = conn.contentLength.toLong()
            var received = 0L

            conn.getInputStream().use { input ->
                FileOutputStream(zipFile).use { out ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        received += n
                        if (total > 0) {
                            onProgress("Downloading speech model… ${(received * 100 / total).toInt()}%")
                        }
                    }
                }
            }

            onProgress("Extracting model…")
            destDir.mkdirs()
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val rel = entry.name.removePrefix("$MODEL_NAME/")
                    if (rel.isNotEmpty()) {
                        val file = File(destDir, rel)
                        if (entry.isDirectory) file.mkdirs()
                        else { file.parentFile?.mkdirs(); FileOutputStream(file).use { zis.copyTo(it) } }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            destDir.deleteRecursively()
            throw e
        } finally {
            zipFile.delete()
        }
    }
}
