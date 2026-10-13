package jp.linkserver.shutter

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal data class ShutterCommandOutput(val stdout: String, val stderr: String, val exitCode: Int)

/** Drains both pipes while the process runs, with one deadline and bounded memory. */
internal class ShutterCommand(
    private val timeoutMs: Long = 4_000,
    private val maxOutputBytes: Int = 8 * 1024 * 1024,
    private val start: (List<String>) -> Process = { ProcessBuilder(it).start() }
) {
    fun run(args: List<String>): ShutterCommandOutput {
        val process = start(args)
        val readers = Executors.newFixedThreadPool(2) { action ->
            Thread(action, "shutter-dump-reader").apply { isDaemon = true }
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var stdout: Future<String>? = null
        var stderr: Future<String>? = null
        try {
            stdout = readers.submit<String> { readBounded(process.inputStream, process) }
            stderr = readers.submit<String> { readBounded(process.errorStream, process) }
            check(process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) { "Settings dump timed out" }
            fun await(reader: Future<String>): String {
                val remaining = deadline - System.nanoTime()
                check(remaining > 0) { "Settings dump timed out" }
                return reader.get(remaining, TimeUnit.NANOSECONDS)
            }
            return ShutterCommandOutput(await(stdout), await(stderr), process.exitValue())
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } finally {
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            stdout?.cancel(true)
            stderr?.cancel(true)
            readers.shutdownNow()
            // Bounded cleanup, including on a cancelled/failed command.
            runCatching { process.waitFor(250, TimeUnit.MILLISECONDS) }
        }
    }

    private fun readBounded(input: InputStream, process: Process): String = input.use {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = it.read(buffer)
            if (count == -1) break
            if (bytes.size() > maxOutputBytes - count) {
                process.destroyForcibly()
                error("Settings dump exceeded the output limit")
            }
            bytes.write(buffer, 0, count)
        }
        bytes.toString("UTF-8")
    }
}
