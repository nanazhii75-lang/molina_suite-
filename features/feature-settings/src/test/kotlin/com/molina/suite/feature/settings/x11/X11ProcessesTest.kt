package com.molina.suite.feature.settings.x11

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class X11ProcessesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun entry(
        pid: Int,
        ppid: Int = 1,
        sid: Int = 1,
        state: Char = 'S',
        comm: String = "proc",
        vararg args: String
    ) = ProcEntry(pid, ppid, sid, state, comm, args.toList())

    private fun <T> runSuspending(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return outcome!!.getOrThrow()
    }

    @Test
    fun statDenganNamaBerkurungDanSpasiTetapTerbaca() {
        val fields = ProcParser.parseStat("123 (open (box) x) S 45 123 123 0 -1 4194560")
        assertEquals(StatFields('S', 45, 123), fields)
    }

    @Test
    fun statRusakDitolak() {
        assertEquals(null, ProcParser.parseStat("123 tanpa kurung"))
        assertEquals(null, ProcParser.parseStat("123 (x) S 1"))
    }

    @Test
    fun rssDibacaDariStatus() {
        val status = "Name:\topenbox\nVmPeak:\t  9000 kB\nVmRSS:\t   2048 kB\nThreads:\t1\n"
        assertEquals(2048L, ProcParser.parseRssKb(status))
        assertEquals(0L, ProcParser.parseRssKb("Name:\tx\n"))
    }

    @Test
    fun desktopMencakupKeturunanDanAnggotaSesi() {
        val table = listOf(
            entry(100, ppid = 1, sid = 100, comm = "dbus-launch", args = arrayOf("dbus-launch", "openbox-session")),
            entry(101, ppid = 100, sid = 100, comm = "openbox"),
            entry(102, ppid = 101, sid = 100, comm = "xterm"),
            entry(103, ppid = 1, sid = 100, comm = "app-yatim"),
            entry(200, ppid = 1, sid = 200, comm = "bash")
        )
        assertEquals(setOf(100, 101, 102, 103), X11Selection.desktop(table, 100, 1))
    }

    @Test
    fun akarBukanPemimpinSesiHanyaMengambilKeturunan() {
        val table = listOf(
            entry(50, ppid = 1, sid = 7, comm = "bash"),
            entry(100, ppid = 50, sid = 7, comm = "dbus-launch", args = arrayOf("dbus-launch")),
            entry(101, ppid = 100, sid = 7, comm = "xterm"),
            entry(300, ppid = 50, sid = 7, comm = "vim")
        )
        assertEquals(setOf(100, 101), X11Selection.desktop(table, 100, 1))
    }

    @Test
    fun pidBasiTidakMengenaiProsesLain() {
        val table = listOf(
            entry(100, comm = "bash", args = arrayOf("bash")),
            entry(101, ppid = 100, comm = "vim")
        )
        assertTrue(X11Selection.desktop(table, 100, 1).isEmpty())
    }

    @Test
    fun sisaDesktopDikenaliLewatNamaTermasukDbusSesi() {
        val table = listOf(
            entry(10, comm = "pcmanfm"),
            entry(11, comm = "dbus-daemon", args = arrayOf("dbus-daemon", "--session")),
            entry(12, comm = "dbus-daemon", args = arrayOf("dbus-daemon", "--system")),
            entry(13, comm = "xterm")
        )
        assertEquals(setOf(10, 11), X11Selection.desktop(table, null, 1))
    }

    @Test
    fun zombieDanProsesSendiriDiabaikan() {
        val table = listOf(
            entry(10, comm = "openbox", state = 'Z'),
            entry(11, comm = "openbox"),
            entry(12, comm = "openbox")
        )
        assertEquals(setOf(11), X11Selection.desktop(table, null, 12))
    }

    @Test
    fun serverDikenaliDariArgumenTitikMasuk() {
        val table = listOf(
            entry(
                20, comm = "app_process",
                args = arrayOf("app_process", "/", "com.molina.suite.x11.CmdEntryPoint", ":0")
            ),
            entry(21, comm = "app_process", args = arrayOf("app_process", "/", "lain"))
        )
        assertEquals(setOf(20), X11Selection.server(table, 1))
    }

    @Test
    fun stopperMengirimTermLaluKillSampaiTuntas() {
        var table = listOf(
            entry(100, sid = 100, comm = "dbus-launch", args = arrayOf("dbus-launch")),
            entry(101, ppid = 100, sid = 100, comm = "openbox"),
            entry(102, ppid = 101, sid = 100, comm = "xkeras")
        )
        val sent = ArrayList<Pair<Int, Int>>()
        val stopper = X11Stopper(
            snapshot = { table },
            signal = { pid, sig ->
                sent += pid to sig
                // xkeras mengabaikan SIGTERM, hanya mati oleh SIGKILL.
                table = table.filterNot { (it.pid != 102 && sig == 15 && it.pid == pid) || (sig == 9 && it.pid == pid) }
            },
            pause = { }
        )
        val outcome = runSuspending { stopper.stop { X11Selection.desktop(it, 100, 1) } }
        assertEquals(3, outcome.stopped)
        assertTrue(outcome.remaining.isEmpty())
        assertTrue(sent.contains(102 to 15))
        assertTrue(sent.contains(102 to 9))
        assertFalse(sent.contains(100 to 9))
    }

    @Test
    fun stopperTetapMemantauAnakYatimSetelahAkarMati() {
        var table = listOf(
            entry(100, sid = 100, comm = "dbus-launch", args = arrayOf("dbus-launch")),
            entry(101, ppid = 100, sid = 100, comm = "xterm")
        )
        val stopper = X11Stopper(
            snapshot = { table },
            signal = { pid, sig ->
                if (pid == 100) {
                    // Akar mati dan anaknya menjadi yatim (ppid 1, sesi lain) yang bandel.
                    table = listOf(entry(101, ppid = 1, sid = 1, comm = "xterm"))
                } else if (sig == 9) {
                    table = table.filterNot { it.pid == pid }
                }
            },
            pause = { }
        )
        val outcome = runSuspending { stopper.stop { X11Selection.desktop(it, 100, 999) } }
        assertTrue(outcome.remaining.isEmpty())
        assertEquals(2, outcome.stopped)
    }

    @Test
    fun stopperMelaporkanSisaBilaProsesTidakBisaDimatikan() {
        val table = listOf(entry(7, comm = "openbox"))
        val stopper = X11Stopper(snapshot = { table }, signal = { _, _ -> }, pause = { })
        val outcome = runSuspending { stopper.stop { X11Selection.desktop(it, null, 1) } }
        assertEquals(setOf(7), outcome.remaining)
        assertEquals(0, outcome.stopped)
    }

    @Test
    fun scanMembacaPohonProcPalsu() {
        val root = tmp.newFolder("proc")
        fun make(pid: Int, stat: String, comm: String, cmd: String, rss: Int) {
            val dir = File(root, pid.toString()).apply { mkdirs() }
            File(dir, "stat").writeText(stat)
            File(dir, "comm").writeText(comm + "\n")
            File(dir, "cmdline").writeText(cmd)
            File(dir, "status").writeText("VmRSS:\t $rss kB\n")
        }
        make(300, "300 (openbox) S 1 300 300 0", "openbox", "openbox\u0000--sm-disable\u0000", 4096)
        File(root, "self").mkdirs()
        val table = ProcTable.scan(root)
        assertEquals(1, table.size)
        assertEquals(listOf("openbox", "--sm-disable"), table[0].args)
        assertEquals(300, table[0].sid)
        assertEquals(4096L, ProcTable.rssKb(300, root))
        assertEquals(0L, ProcTable.rssKb(999, root))
    }

    @Test
    fun usageMenjumlahMemoriDesktopDanServer() {
        val table = listOf(
            entry(10, comm = "openbox"),
            entry(11, comm = "pcmanfm"),
            entry(20, comm = "app_process", args = arrayOf("com.molina.suite.x11.CmdEntryPoint"))
        )
        val rss = mapOf(10 to 3000L, 11 to 2000L, 20 to 50_000L)
        val usage = X11Usage.read(table, null, 1) { rss.getValue(it) }
        assertEquals(2, usage.desktopCount)
        assertEquals(5000L, usage.desktopKb)
        assertEquals(1, usage.serverCount)
        assertEquals(55_000L, usage.totalKb)
        assertFalse(usage.isIdle)
        assertTrue(X11Usage.NONE.isIdle)
    }

    @Test
    fun formatMemoriKbDanMb() {
        assertEquals("512 KB", X11Usage.format(512))
        assertEquals("128 MB", X11Usage.format(131_072))
    }
}
