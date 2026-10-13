package jp.linkserver.shutter

import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class ShutterCommandTest {
    private fun args(mode: String): List<String> {
        val suffix = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) ".exe" else ""
        val java = File(System.getProperty("java.home"), "bin/java$suffix").absolutePath
        val classes = File(checkNotNull(DumpCommandTestProcess::class.java.protectionDomain).codeSource.location.toURI()).absolutePath
        return listOf(java, "-cp", classes, DumpCommandTestProcess::class.java.name, mode)
    }
    @Test fun largeStdoutAndStderrAreDrainedWithoutDeadlocking() {
        val output = ShutterCommand(timeoutMs = 10_000).run(args("large"))
        assertEquals(0, output.exitCode)
        assertEquals(2 * 1024 * 1024, output.stdout.length)
        assertEquals(256 * 1024, output.stderr.length)
    }
    @Test fun nonzeroExitIsPreservedForTheMetadataValidator() {
        val output = ShutterCommand(timeoutMs = 10_000).run(args("fail"))
        assertEquals(7, output.exitCode)
        assertNull(GalaxyCscDump.fromCommand(output, 0))
    }
    @Test fun aTimeoutTerminatesTheChild() {
        var child: Process? = null
        val runner = ShutterCommand(timeoutMs = 500, start = { ProcessBuilder(it).start().also { child = it } })
        assertTrue(runCatching { runner.run(args("slow")) }.isFailure)
        assertTrue(child!!.waitFor(3, TimeUnit.SECONDS))
        assertFalse(child!!.isAlive)
    }
    @Test fun anOutputLimitFailureTerminatesTheChild() {
        var child: Process? = null
        val runner = ShutterCommand(timeoutMs = 10_000, maxOutputBytes = 1024,
            start = { ProcessBuilder(it).start().also { child = it } })
        assertTrue(runCatching { runner.run(args("large")) }.isFailure)
        assertTrue(child!!.waitFor(3, TimeUnit.SECONDS))
        assertFalse(child!!.isAlive)
    }
    @Test fun interruptionPreservesCancellationAndTerminatesTheChild() {
        val started = CountDownLatch(1)
        var child: Process? = null
        val interrupted = AtomicBoolean()
        val thread = Thread {
            val runner = ShutterCommand(timeoutMs = 10_000, start = {
                ProcessBuilder(it).start().also { process -> child = process; started.countDown() }
            })
            runCatching { runner.run(args("slow")) }
            interrupted.set(Thread.currentThread().isInterrupted)
        }
        thread.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        thread.interrupt()
        thread.join(5000)
        assertFalse(thread.isAlive)
        assertTrue(interrupted.get())
        assertTrue(child!!.waitFor(3, TimeUnit.SECONDS))
    }
    @Test fun launchFailureIsNotAnAbsentKey() {
        assertTrue(runCatching {
            ShutterCommand(start = { throw IOException("launch failed") }).run(args("fail"))
        }.isFailure)
    }
}
