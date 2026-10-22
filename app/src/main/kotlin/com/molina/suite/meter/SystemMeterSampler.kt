package com.molina.suite.meter

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException

/** Cakupan angka CPU: seluruh sistem, atau (jika /proc/stat diblokir) hanya proses milik Molina Suite. */
enum class CpuScope { SYSTEM, APP }

/**
 * Satu pembacaan meter. Kecepatan jaringan bernilai -1 bila perangkat tidak mendukung
 * statistik lalu lintas.
 */
data class MeterReading(
    val rxBytesPerSec: Long,
    val txBytesPerSec: Long,
    val cpuPercent: Int,
    val cpuScope: CpuScope,
    val ramUsedBytes: Long,
    val ramTotalBytes: Long,
    val ramSystemLow: Boolean = false
) {
    val ramPercent: Int
        get() = if (ramTotalBytes > 0) ((ramUsedBytes * 100) / ramTotalBytes).toInt().coerceIn(0, 100) else 0
}

/**
 * Pengukur real time: jaringan (seluruh perangkat), CPU, dan RAM.
 *
 * - Jaringan: [TrafficStats] total semua antarmuka (selisih per detik).
 * - RAM: PSS seluruh proses milik UID aplikasi ini (proses utama, Terminal, proot dan
 *   turunannya); total adalah RAM perangkat. Sistem yang hampir kehabisan RAM ditandai
 *   lewat [MeterReading.ramSystemLow]. PSS dibaca tiap [RAM_EVERY_TICKS] detik karena
 *   membaca smaps tiap proses cukup mahal.
 * - CPU: /proc/stat bila bisa dibaca. Android 8 memblokir berkas itu untuk aplikasi biasa,
 *   maka dipakai cadangan: jumlah waktu CPU semua proses milik UID aplikasi ini
 *   (mpv, terminal, proot dan turunannya), dibagi kapasitas seluruh inti.
 *
 * Aliran hanya berjalan selama dikoleksi, sehingga berhenti saat aplikasi di latar belakang.
 */
class SystemMeterSampler(context: Context, private val intervalMs: Long = 1000L) {

    private val activityManager =
        context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private val ticksPerSecond: Long =
        (try { Os.sysconf(OsConstants._SC_CLK_TCK) } catch (e: Exception) { 100L }).let { if (it > 0) it else 100L }

    fun readings(): Flow<MeterReading> = flow {
        var prevTime = System.nanoTime()
        var prevRx = TrafficStats.getTotalRxBytes()
        var prevTx = TrafficStats.getTotalTxBytes()
        var prevSys = readSystemCpu()
        var prevApp: Long? = if (prevSys == null) readOwnUidCpuTicks() else null
        var appRamBytes = readAppRamBytes()
        var ramTick = 1

        while (true) {
            delay(intervalMs)
            val now = System.nanoTime()
            val dtNanos = (now - prevTime).coerceAtLeast(1L)

            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val rxRate = rate(prevRx, rx, dtNanos)
            val txRate = rate(prevTx, tx, dtNanos)

            var cpuPercent = 0
            var scope = CpuScope.SYSTEM
            val sys = readSystemCpu()
            if (sys != null && prevSys != null && sys.second > prevSys.second) {
                cpuPercent = (((sys.first - prevSys.first) * 100) / (sys.second - prevSys.second)).toInt()
                prevApp = null
            } else {
                scope = CpuScope.APP
                val app = readOwnUidCpuTicks()
                val before = prevApp
                if (app != null && before != null) {
                    val delta = (app - before).coerceAtLeast(0L).toDouble()
                    val capacity = (dtNanos / 1_000_000_000.0) * ticksPerSecond * cores
                    cpuPercent = if (capacity > 0.0) ((delta * 100.0) / capacity).toInt() else 0
                }
                prevApp = app
            }
            prevSys = sys

            if (ramTick++ % RAM_EVERY_TICKS == 0) appRamBytes = readAppRamBytes()
            val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            emit(
                MeterReading(
                    rxBytesPerSec = rxRate,
                    txBytesPerSec = txRate,
                    cpuPercent = cpuPercent.coerceIn(0, 100),
                    cpuScope = scope,
                    ramUsedBytes = appRamBytes,
                    ramTotalBytes = memory.totalMem,
                    ramSystemLow = memory.lowMemory
                )
            )

            prevTime = now
            prevRx = rx
            prevTx = tx
        }
    }.flowOn(Dispatchers.IO)

    /** Total PSS (byte) semua proses milik UID aplikasi ini; 0 bila tidak terbaca. */
    private fun readAppRamBytes(): Long {
        val pids = ownPids()
        val infos = try {
            activityManager.getProcessMemoryInfo(pids)
        } catch (e: RuntimeException) {
            try {
                activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            } catch (e2: RuntimeException) {
                return 0L
            }
        }
        var kb = 0L
        for (info in infos) kb += info.totalPss.toLong()
        return kb * 1024L
    }

    /** Pid proses milik UID ini yang terlihat di /proc (selalu memuat proses utama). */
    private fun ownPids(): IntArray {
        val own = Process.myPid()
        val uid = Process.myUid()
        val entries = File("/proc").listFiles() ?: return intArrayOf(own)
        val pids = ArrayList<Int>(entries.size)
        for (entry in entries) {
            val pid = entry.name.toIntOrNull() ?: continue
            if (pid <= 0) continue
            val sameUid = try {
                Os.stat(entry.path).st_uid == uid
            } catch (e: ErrnoException) {
                false
            }
            if (sameUid) pids.add(pid)
        }
        if (!pids.contains(own)) pids.add(own)
        return pids.toIntArray()
    }

    /** Byte per detik; -1 bila statistik tidak didukung, 0 bila penghitung di-reset. */
    private fun rate(previous: Long, current: Long, dtNanos: Long): Long {
        if (previous < 0 || current < 0) return -1L
        val delta = current - previous
        if (delta <= 0) return 0L
        return (delta * 1_000_000_000.0 / dtNanos).toLong()
    }

    /** Pasangan (waktu sibuk, waktu total) dari baris "cpu" di /proc/stat, atau null bila tidak terbaca. */
    private fun readSystemCpu(): Pair<Long, Long>? {
        val line = try {
            File("/proc/stat").bufferedReader().use { it.readLine() }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
        if (line == null || !line.startsWith("cpu ")) return null
        val fields = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (fields.size < 5) return null
        val idle = fields[3] + fields[4]
        val total = fields.take(8).sum()
        return (total - idle) to total
    }

    /** Jumlah utime+stime (satuan tick) seluruh proses yang terlihat (hanya UID sendiri), atau null. */
    private fun readOwnUidCpuTicks(): Long? {
        val entries = File("/proc").listFiles() ?: return null
        var sum = 0L
        var found = false
        for (entry in entries) {
            val name = entry.name
            if (name.isEmpty() || !name[0].isDigit()) continue
            val ticks = readProcessTicks(File(entry, "stat")) ?: continue
            sum += ticks
            found = true
        }
        return if (found) sum else null
    }

    private fun readProcessTicks(statFile: File): Long? {
        val text = try {
            statFile.readText()
        } catch (e: IOException) {
            return null
        } catch (e: SecurityException) {
            return null
        }
        // Nama proses (field 2) boleh memuat spasi dan kurung, jadi parse dari ')' terakhir.
        val close = text.lastIndexOf(')')
        if (close < 0 || close + 2 > text.length) return null
        val fields = text.substring(close + 2).split(' ')
        val utime = fields.getOrNull(11)?.toLongOrNull() ?: return null
        val stime = fields.getOrNull(12)?.toLongOrNull() ?: return null
        return utime + stime
    }

    private companion object {
        /** RAM (PSS) dibaca tiap berapa kali sampel; sampel lain memakai nilai terakhir. */
        const val RAM_EVERY_TICKS = 3
    }
}
