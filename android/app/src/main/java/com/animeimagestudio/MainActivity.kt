package com.animeimagestudio

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var convertButton: Button
    private var source: Bitmap? = null
    private var result: Bitmap? = null

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 26, 22, 24)
            setBackgroundColor(Color.rgb(13, 17, 30))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "Anime Image Studio"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 6)
        }
        val subtitle = TextView(this).apply {
            text = "Turn your photos into anime-inspired artwork"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        }
        preview = ImageView(this).apply {
            setBackgroundColor(Color.rgb(29, 36, 55))
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            minimumHeight = 300
        }
        status = TextView(this).apply {
            text = "Choose a photo to get started."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        val choose = makeButton("Choose Photo") { picker.launch("image/*") }
        convertButton = makeButton("Create Anime Image") { convert() }.apply { isEnabled = false }
        val save = makeButton("Save PNG to Gallery") { saveResult() }
        val share = makeButton("Share Image") { shareResult() }

        content.addView(title)
        content.addView(subtitle)
        content.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { height = 460 })
        content.addView(status)
        content.addView(choose)
        content.addView(convertButton)
        content.addView(save)
        content.addView(share)
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setOnClickListener { action() }
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 6, 0, 6)
            layoutParams = p
        }

    private fun loadImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { stream ->
                val decoded = BitmapFactory.decodeStream(stream) ?: error("Unsupported image")
                source = resize(decoded, 1400)
                result = null
                preview.setImageBitmap(source)
                convertButton.isEnabled = true
                status.text = "Photo loaded. Tap Create Anime Image."
            }
        } catch (e: Exception) {
            status.text = "Could not open this image."
            Toast.makeText(this, e.message ?: "Image error", Toast.LENGTH_LONG).show()
        }
    }

    private fun resize(input: Bitmap, maxSide: Int): Bitmap {
        val scale = min(1f, maxSide.toFloat() / max(input.width, input.height))
        if (scale >= 1f) return input.copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(input, (input.width * scale).toInt().coerceAtLeast(1),
            (input.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun convert() {
        val input = source ?: return
        convertButton.isEnabled = false
        status.text = "Creating your artwork…"
        executor.execute {
            try {
                val output = cartoonize(input)
                runOnUiThread {
                    result = output
                    preview.setImageBitmap(output)
                    status.text = "Done! Save the PNG or share it."
                    convertButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Conversion failed: ${e.message}"
                    convertButton.isEnabled = true
                }
            }
        }
    }

    // Lightweight on-device posterization and ink-edge pass; no network or GPU required.
    private fun cartoonize(input: Bitmap): Bitmap {
        val w = input.width
        val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(pixels.size)
        fun luminance(c: Int): Int = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = pixels[i]
                val r = (Color.red(c) / 32) * 32 + 16
                val g = (Color.green(c) / 32) * 32 + 16
                val b = (Color.blue(c) / 32) * 32 + 16
                var edge = 0
                if (x > 0 && x < w - 1 && y > 0 && y < h - 1) {
                    val gx = abs(luminance(pixels[i + 1]) - luminance(pixels[i - 1]))
                    val gy = abs(luminance(pixels[i + w]) - luminance(pixels[i - w]))
                    edge = gx + gy
                }
                val ink = if (edge > 95) 0.30f else 1f
                out[i] = Color.rgb((r * ink).toInt().coerceIn(0,255),
                    (g * ink).toInt().coerceIn(0,255), (b * ink).toInt().coerceIn(0,255))
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun saveResult() {
        val bitmap = result ?: run {
            Toast.makeText(this, "Create an image first.", Toast.LENGTH_SHORT).show(); return
        }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "AnimeStudio_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AnimeImageStudio")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create gallery file")
            contentResolver.openOutputStream(uri).use { stream: OutputStream? ->
                if (stream == null) error("Could not write image")
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            Toast.makeText(this, "Saved to Pictures/AnimeImageStudio", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Save failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareResult() {
        val bitmap = result ?: run {
            Toast.makeText(this, "Create an image first.", Toast.LENGTH_SHORT).show(); return
        }
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "Anime Image Studio", null)
        if (path == null) {
            Toast.makeText(this, "Could not prepare image to share.", Toast.LENGTH_SHORT).show(); return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share anime artwork"))
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
