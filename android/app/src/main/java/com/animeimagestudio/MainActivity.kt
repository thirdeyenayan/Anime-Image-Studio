package com.animeimagestudio

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.OutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var imageConvert: Button
    private var source: Bitmap? = null
    private var imageResult: Bitmap? = null
    private var showOriginal = false
    private var strength = 65
    private var busy = false

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 20)
            setBackgroundColor(Color.rgb(12, 16, 29))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "Anime Image Studio"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 4)
        }
        val subtitle = TextView(this).apply {
            text = "Turn your photos into anime-inspired artwork"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        preview = ImageView(this).apply {
            setBackgroundColor(Color.rgb(28, 35, 54))
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            minimumHeight = 280
        }
        status = TextView(this).apply {
            text = "Choose a photo to begin."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 10)
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        val chooseImage = makeButton("Choose Photo") { if (!busy) imagePicker.launch("image/*") }
        val strengthLabel = TextView(this).apply {
            text = "Anime effect strength: 65%"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(4, 12, 4, 2)
        }
        val strengthBar = SeekBar(this).apply {
            max = 100
            progress = strength
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    strength = value.coerceAtLeast(10)
                    strengthLabel.text = "Anime effect strength: $strength%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        imageConvert = makeButton("Create Anime Image") { convertImage() }.apply { isEnabled = false }
        val compareImage = makeButton("Before / After") {
            val original = source
            if (original != null) {
                showOriginal = !showOriginal
                preview.setImageBitmap(if (showOriginal) original else imageResult ?: original)
                status.text = if (showOriginal) "Showing original photo." else if (imageResult != null) "Showing converted artwork." else "Create the anime photo first."
            }
        }
        val saveImage = makeButton("Save Image to Gallery") { saveImageResult() }
        val shareImage = makeButton("Share Image") { shareImageResult() }

        content.addView(title)
        content.addView(subtitle)
        content.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 430))
        content.addView(status)
        content.addView(progress)
        content.addView(chooseImage)
        content.addView(strengthLabel)
        content.addView(strengthBar)
        content.addView(imageConvert)
        content.addView(compareImage)
        content.addView(saveImage)
        content.addView(shareImage)
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setOnClickListener { action() }
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 5, 0, 5)
            layoutParams = p
        }

    private fun loadImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { stream ->
                val decoded = BitmapFactory.decodeStream(stream) ?: error("Unsupported image")
                source = resize(decoded, 1600)
                imageResult?.recycle()
                imageResult = null
                showOriginal = false
                preview.setImageBitmap(source)
                imageConvert.isEnabled = true
                status.text = "Photo ready. Adjust the strength and create your artwork."
            }
        } catch (e: Exception) {
            showError("Could not open image: ${e.message}")
        }
    }

    private fun resize(input: Bitmap, maxSide: Int): Bitmap {
        val scale = min(1f, maxSide.toFloat() / max(input.width, input.height))
        if (scale >= 1f) return input.copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(input, (input.width * scale).toInt().coerceAtLeast(1),
            (input.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun convertImage() {
        val input = source ?: return
        setBusy(true, "Creating anime-style image…")
        executor.execute {
            try {
                val output = cartoonize(input, strength)
                runOnUiThread {
                    imageResult?.recycle()
                    imageResult = output
                    showOriginal = false
                    preview.setImageBitmap(output)
                    setBusy(false, "Image ready. Save it to your gallery or share it.")
                }
            } catch (e: Exception) {
                runOnUiThread { setBusy(false, "Image conversion failed: ${e.message}") }
            }
        }
    }

    private fun cartoonize(input: Bitmap, amount: Int): Bitmap {
        val w = input.width
        val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        val smooth = IntArray(pixels.size)
        fun lum(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        // Small edge-aware neighborhood smoothing reduces photo texture before color quantization.
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            var rr = 0; var gg = 0; var bb = 0; var n = 0
            val center = pixels[i]
            for (dy in -1..1) for (dx in -1..1) {
                val xx = (x + dx).coerceIn(0, w - 1)
                val yy = (y + dy).coerceIn(0, h - 1)
                val c = pixels[yy * w + xx]
                if (abs(lum(c) - lum(center)) < 48) {
                    rr += Color.red(c); gg += Color.green(c); bb += Color.blue(c); n++
                }
            }
            smooth[i] = Color.rgb(rr / n.coerceAtLeast(1), gg / n.coerceAtLeast(1), bb / n.coerceAtLeast(1))
        }
        val out = IntArray(pixels.size)
        val step = if (amount > 75) 24 else if (amount > 45) 32 else 40
        val edgeThreshold = 150 - amount
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = smooth[i]
            val r = (Color.red(c) / step) * step + step / 2
            val g = (Color.green(c) / step) * step + step / 2
            val b = (Color.blue(c) / step) * step + step / 2
            var edge = 0
            if (x in 1 until w - 1 && y in 1 until h - 1) {
                edge = abs(lum(pixels[i + 1]) - lum(pixels[i - 1])) +
                    abs(lum(pixels[i + w]) - lum(pixels[i - w]))
            }
            val ink = if (edge > edgeThreshold) (1f - amount / 145f).coerceIn(0.22f, 0.72f) else 1f
            out[i] = Color.rgb((r * ink).toInt().coerceIn(0,255),
                (g * ink).toInt().coerceIn(0,255), (b * ink).toInt().coerceIn(0,255))
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun saveImageResult() {
        val bitmap = imageResult ?: run { toast("Create an image first."); return }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "AnimeStudio_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AnimeImageStudio")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create gallery image")
            contentResolver.openOutputStream(uri).use { stream: OutputStream? ->
                if (stream == null) error("Could not write image")
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            toast("Saved to Pictures/AnimeImageStudio")
        } catch (e: Exception) { showError("Save failed: ${e.message}") }
    }

    private fun shareImageResult() {
        val bitmap = imageResult ?: run { toast("Create an image first."); return }
        try {
            val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "Anime Image Studio", null)
            if (path == null) error("Could not prepare image")
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"; putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share anime artwork"))
        } catch (e: Exception) { showError("Share failed: ${e.message}") }
    }

    private fun setBusy(value: Boolean, message: String) {
        busy = value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        if (!value) progress.progress = 0
        imageConvert.isEnabled = !value && source != null
        status.text = message
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun showError(message: String) { status.text = message; toast(message) }

    override fun onDestroy() {
        executor.shutdown()
        source?.recycle()
        imageResult?.recycle()
        super.onDestroy()
    }
}
