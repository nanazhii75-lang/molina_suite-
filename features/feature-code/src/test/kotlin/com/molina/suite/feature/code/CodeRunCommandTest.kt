package com.molina.suite.feature.code

import com.molina.suite.core.common.ScriptRunTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeRunCommandTest {

    private fun debian(path: String, head: String = ""): String? =
        CodeRunCommand.lineFor(path, head, ScriptRunTarget.DEBIAN)

    @Test
    fun bahasaTambahanDikenaliSebagaiBisaDijalankan() {
        listOf("a.js", "a.php", "a.rb", "a.lua", "a.java", "a.kt", "a.c", "a.cpp", "a.go", "a.rs")
            .forEach { assertTrue("$it harus bisa dijalankan", CodeRunCommand.isRunnable(it)) }
    }

    @Test
    fun berkasBiasaTidakBisaDijalankan() {
        listOf("catatan.txt", "README.md", "data.json", "gambar.png", "tanpa_ekstensi")
            .forEach { assertFalse("$it tidak boleh bisa dijalankan", CodeRunCommand.isRunnable(it)) }
    }

    @Test
    fun jalurLamaShDanPyTetapBisaDijalankan() {
        assertTrue(CodeRunCommand.isRunnable("a.sh"))
        assertTrue(CodeRunCommand.isRunnable("a.py"))
        assertNotNull(debian("/sdcard/Molina/a.sh"))
        assertNotNull(debian("/sdcard/Molina/a.py"))
    }

    @Test
    fun languageOfHanyaMengembalikanBahasaTambahan() {
        assertEquals(CodeCheckLanguage.NODE, CodeRunCommand.languageOf("t.js"))
        assertEquals(CodeCheckLanguage.KOTLIN, CodeRunCommand.languageOf("Main.kt"))
        assertEquals(CodeCheckLanguage.RUST, CodeRunCommand.languageOf("main.rs"))
        assertNull(CodeRunCommand.languageOf("README.md"))
    }

    @Test
    fun bahasaTambahanHanyaUntukDebian() {
        assertNull(CodeRunCommand.lineFor("/sdcard/Molina/t.js", "", ScriptRunTarget.HOST))
        assertNull(CodeRunCommand.lineFor("/sdcard/Molina/a.c", "", ScriptRunTarget.HOST))
    }

    @Test
    fun berkasTidakDikenalMenghasilkanNull() {
        assertNull(debian("/sdcard/Molina/catatan.txt"))
        assertNull(debian("/sdcard/Molina/README.md"))
    }

    @Test
    fun jalurDenganBarisBaruDitolak() {
        assertNull(debian("/sdcard/Molina/a\nrm -rf x.js"))
        assertNull(debian("/sdcard/Molina/a\r.js"))
    }

    @Test
    fun namaKosongDitolak() {
        assertNull(debian("/sdcard/Molina/"))
    }

    @Test
    fun interpreterLangsungMemakaiPerintahYangBenar() {
        val expected = mapOf(
            "/p/t.js" to "node ",
            "/p/t.php" to "php ",
            "/p/t.rb" to "ruby ",
            "/p/t.lua" to "lua5.4 ",
            "/p/T.java" to "java ",
            "/p/t.go" to "go run "
        )
        expected.forEach { (path, fragment) ->
            val line = debian(path)
            assertNotNull("$path harus punya perintah", line)
            assertTrue("$path harus memuat '$fragment'", line!!.contains(fragment))
            assertTrue("$path harus pindah ke folder berkas", line.contains("cd "))
            assertFalse("$path tidak memakai folder sementara", line.contains("mktemp"))
        }
    }

    @Test
    fun bahasaKompilasiMemakaiFolderSementaraYangDibuang() {
        val expected = mapOf(
            "/p/a.c" to "gcc ",
            "/p/a.cpp" to "g++ ",
            "/p/a.rs" to "rustc ",
            "/p/a.kt" to "kotlinc "
        )
        expected.forEach { (path, compiler) ->
            val line = debian(path)
            assertNotNull("$path harus punya perintah", line)
            assertTrue("$path harus memuat '$compiler'", line!!.contains(compiler))
            assertTrue("$path harus membuat folder sementara", line.contains("mktemp -d"))
            assertTrue("$path harus membuangnya saat selesai", line.contains("trap"))
        }
    }

    @Test
    fun kotlinDikompilasiKeJarLaluDijalankan() {
        val line = debian("/p/a.kt")!!
        assertTrue(line.contains("-include-runtime"))
        assertTrue(line.contains("java -jar"))
        assertTrue(line.indexOf("kotlinc") < line.indexOf("java -jar"))
    }

    @Test
    fun rustMemakaiEdisi2021() {
        assertTrue(debian("/p/a.rs")!!.contains("--edition 2021"))
    }

    @Test
    fun namaBerawalanStripDiberiPrefiksAgarBukanOpsi() {
        val line = debian("/p/-evil.js")!!
        assertTrue("nama berawalan - harus jadi ./", line.contains("./-evil.js"))
    }

    @Test
    fun jalurDenganSpasiTetapMenghasilkanPerintah() {
        val line = debian("/p/folder saya/file saya.js")
        assertNotNull(line)
        assertTrue(line!!.contains("file saya.js"))
        assertFalse(line.contains("\n"))
    }

    @Test
    fun jalurDenganTandaKutipTunggalTidakMembuatPerintahPatah() {
        val line = debian("/p/it's/a'b.js")
        assertNotNull(line)
        assertFalse(line!!.contains("\n"))
    }

    @Test
    fun berkasDirektoriRootMemakaiSlash() {
        val line = debian("/a.js")
        assertNotNull(line)
        assertTrue(line!!.contains("cd "))
    }
}
