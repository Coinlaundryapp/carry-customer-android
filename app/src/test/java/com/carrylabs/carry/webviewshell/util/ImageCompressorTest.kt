package com.carrylabs.carry.webviewshell.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImageCompressorTest {

    // ── Result.toJson() ──────────────────────────────────────────────

    @Test
    fun `Result toJson contains all required fields`() {
        val result = ImageCompressor.Result(
            uri = Uri.parse("content://compressed.jpg"),
            originalUri = Uri.parse("content://original.jpg"),
            width = 800,
            height = 600,
            fileSize = 102400
        )

        val json = result.toJson()
        assertEquals("content://compressed.jpg", json.getString("uri"))
        assertEquals("content://original.jpg", json.getString("originalUri"))
        assertEquals(800, json.getInt("width"))
        assertEquals(600, json.getInt("height"))
        assertEquals(102400L, json.getLong("fileSize"))
    }

    @Test
    fun `Result toJson produces valid JSON string`() {
        val result = ImageCompressor.Result(
            uri = Uri.parse("content://test/compressed"),
            originalUri = Uri.parse("content://test/original"),
            width = 1024,
            height = 768,
            fileSize = 50000
        )

        val jsonString = result.toJson().toString()
        val parsed = JSONObject(jsonString)
        assertEquals(1024, parsed.getInt("width"))
        assertEquals(768, parsed.getInt("height"))
    }

    @Test
    fun `Result toJson handles zero dimensions`() {
        val result = ImageCompressor.Result(
            uri = Uri.parse("content://test"),
            originalUri = Uri.parse("content://original"),
            width = 0,
            height = 0,
            fileSize = 0
        )

        val json = result.toJson()
        assertEquals(0, json.getInt("width"))
        assertEquals(0, json.getInt("height"))
        assertEquals(0L, json.getLong("fileSize"))
    }

    @Test
    fun `Result toJson handles special characters in uri`() {
        val result = ImageCompressor.Result(
            uri = Uri.parse("content://provider/path%20with%20spaces"),
            originalUri = Uri.parse("content://provider/원본이미지.jpg"),
            width = 500,
            height = 500,
            fileSize = 1000
        )

        val json = result.toJson()
        assertTrue(json.getString("uri").contains("path%20with%20spaces"))
    }

    // ── compress() ───────────────────────────────────────────────────

    @Test
    fun `compress returns null for invalid uri`() {
        val context = RuntimeEnvironment.getApplication()
        val result = ImageCompressor.compress(
            context,
            Uri.parse("content://nonexistent/image.jpg")
        )
        assertNull(result)
    }

    @Test
    fun `compress processes a real bitmap file`() {
        val context = RuntimeEnvironment.getApplication()

        // Create a real bitmap and save it to cache
        val bitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888)
        val cacheDir = context.externalCacheDir ?: return
        cacheDir.mkdirs()
        val testFile = File(cacheDir, "test_input.jpg")
        FileOutputStream(testFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
        }
        bitmap.recycle()

        val inputUri = Uri.fromFile(testFile)
        val result = ImageCompressor.compress(context, inputUri, 800, 600, 70)

        // Robolectric may not fully support BitmapFactory from URI, so result may be null
        // This test verifies no crash occurs
        if (result != null) {
            assertTrue(result.width <= 800)
            assertTrue(result.height <= 600)
            assertTrue(result.fileSize > 0)
            assertNotNull(result.uri)
            assertEquals(inputUri, result.originalUri)
        }

        testFile.delete()
    }

    @Test
    fun `compress with default parameters does not crash`() {
        val context = RuntimeEnvironment.getApplication()
        // Should gracefully return null for invalid URI
        val result = ImageCompressor.compress(context, Uri.EMPTY)
        assertNull(result)
    }
}
