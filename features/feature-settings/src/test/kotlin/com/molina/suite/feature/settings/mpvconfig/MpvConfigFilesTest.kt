package com.molina.suite.feature.settings.mpvconfig

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvConfigFilesTest {

    private fun tempDir(): File = Files.createTempDirectory("mpvcfg").toFile()

    private fun fileNameOf(check: MpvConfigFiles.NameCheck): String? =
        (check as? MpvConfigFiles.NameCheck.Valid)?.fileName

    @Test
    fun namaSkripDiberiEkstensiLua() {
        assertEquals("catat.lua", fileNameOf(MpvConfigFiles.checkScriptName("  catat ")))
        assertEquals("catat.lua", fileNameOf(MpvConfigFiles.checkScriptName("catat.LUA")))
        assertEquals("a-b_c.d.lua", fileNameOf(MpvConfigFiles.checkScriptName("a-b_c.d")))
    }

    @Test
    fun namaKosongTerlaluPanjangDanTerlarangDitolak() {
        assertTrue(MpvConfigFiles.checkScriptName("   ") === MpvConfigFiles.NameCheck.Empty)
        assertTrue(MpvConfigFiles.checkScriptName(".lua") === MpvConfigFiles.NameCheck.Empty)
        assertTrue(MpvConfigFiles.checkScriptName("a".repeat(49)) === MpvConfigFiles.NameCheck.TooLong)
        assertEquals("a".repeat(48) + ".lua", fileNameOf(MpvConfigFiles.checkScriptName("a".repeat(48))))
        for (bad in listOf("../x", "a/b", "a\\b", "-awal", ".awal", "a b", "a;b", "skrip?")) {
            assertTrue(bad, MpvConfigFiles.checkScriptName(bad) === MpvConfigFiles.NameCheck.Illegal)
        }
    }

    @Test
    fun daftarSkripHanyaLuaTingkatAtasDanUrutNama() {
        val dir = tempDir()
        try {
            val scripts = MpvConfigFiles.scriptsDir(dir)
            scripts.mkdirs()
            File(scripts, "b.lua").writeText("")
            File(scripts, "A.lua").writeText("")
            File(scripts, "catatan.txt").writeText("")
            File(scripts, "folder.lua").mkdirs()
            assertEquals(listOf("A.lua", "b.lua"), MpvConfigFiles.listScripts(dir).map { it.name })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun daftarSkripKosongBilaFolderBelumAda() {
        val dir = tempDir()
        try {
            assertTrue(MpvConfigFiles.listScripts(dir).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun ensureFileMembuatFolderDanIsiAwalHanyaUntukBerkasBaru() {
        val dir = tempDir()
        try {
            val file = File(MpvConfigFiles.scriptsDir(dir), "baru.lua")
            val first = MpvConfigFiles.ensureFile(file, "awal\n") as MpvConfigFiles.Ensure.Ready
            assertTrue(first.created)
            assertEquals("awal\n", file.readText())
            file.writeText("isi pengguna\n")
            val second = MpvConfigFiles.ensureFile(file, "awal\n") as MpvConfigFiles.Ensure.Ready
            assertFalse(second.created)
            assertEquals("isi pengguna\n", file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun ensureFileTanpaIsiAwalMembuatBerkasKosong() {
        val dir = tempDir()
        try {
            val file = File(dir, MpvConfigFiles.MPV_CONF)
            assertTrue(MpvConfigFiles.ensureFile(file) is MpvConfigFiles.Ensure.Ready)
            assertTrue(file.isFile)
            assertEquals(0L, file.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun ensureFileMenolakFolderBernamaSama() {
        val dir = tempDir()
        try {
            val clash = File(dir, "input.conf")
            clash.mkdirs()
            assertTrue(MpvConfigFiles.ensureFile(clash) is MpvConfigFiles.Ensure.Failed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun isiAwalSkripHanyaPenandaTanpaKode() {
        assertEquals(
            "-- x.lua: skrip mpv Molina, dimuat otomatis setiap mpv dibuka.\n",
            MpvConfigFiles.starterText("x.lua")
        )
    }
}
