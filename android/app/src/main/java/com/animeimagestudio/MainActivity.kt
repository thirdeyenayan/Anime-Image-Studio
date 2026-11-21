package com.animeimagestudio

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var createButton: Button
    private lateinit var serverField: EditText
    private lateinit var styleSpinner: Spinner
    private var source: Bitmap? = null
    private var reference: Bitmap? = null
    private var imageResult: Bitmap? = null
    private var showOriginal = false
    private var busy = false

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri, false)
    }
    private val referencePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri, true)
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
            text = "Premium AI Anime Studio"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 4)
        }
        val subtitle = TextView(this).apply {
            text = "AI redraw • Anime art • Optional style reference"
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
            text = "Choose a photo. AI processing needs your deployed server."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 10)
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        serverField = EditText(this).apply {
            setSingleLine(true)
            setText("https://anime-image-studio-api.onrender.com")
            hint = "Your Render API URL"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.rgb(28, 35, 54))
        }
        styleSpinner = Spinner(this).apply {
            val styles = listOf(
                "Japanese Anime" to "anime",
                "Anime Character" to "anime_character",
                "Anime Game Art" to "anime_game_style",
                "Hand-drawn Animation" to "hand_drawn_animation"
            )
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, styles.map { it.first })
            tag = styles
        }
        val chooseImage = makeButton("Choose Your Photo") { if (!busy) imagePicker.launch("image/*") }
        val chooseReference = makeButton("Add Optional Reference Image") { if (!busy) referencePicker.launch("image/*") }
        createButton = makeButton("Generate AI Anime Artwork") { convertImage() }.apply { isEnabled = false }
        val compareImage = makeButton("Before / After") {
            val original = source
            if (original != null) {
                showOriginal = !showOriginal
                preview.setImageBitmap(if (showOriginal) original else imageResult ?: original)
                status.text = if (showOriginal) "Showing original photo." else if (imageResult != null) "Showing AI artwork." else "Generate the AI artwork first."
            }
        }
        val saveImage = makeButton("Save Artwork to Gallery") { saveImageResult() }
        val shareImage = makeButton("Share Artwork") { shareImageResult() }

        content.addView(title)
        content.addView(subtitle)
        content.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 430))
        content.addView(status)
        content.addView(progress)
        content.addView(label("AI server address"))
        content.addView(serverField)
        content.addView(label("Anime style"))
        content.addView(styleSpinner)
        content.addView(chooseImage)
        content.addView(chooseReference)
        content.addView(createButton)
        content.addView(compareImage)
        content.addView(saveImage)
        content.addView(shareImage)
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(Color.LTGRAY)
        setPadding(4, 10, 4, 2)
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

    private fun loadImage(uri: Uri, isReference: Boolean) {
        try {
            contentResolver.openInputStream(uri).use { stream ->
                val decoded = BitmapFactory.decodeStream(stream) ?: error("Unsupported image")
                val resized = resize(decoded, 1536)
                if (isReference) {
                    reference?.recycle()
                    reference = resized
                    status.text = "Style reference added. It will guide the anime look."
                } else {
                    source?.recycle()
                    source = resized
                    imageResult?.recycle()
                    imageResult = null
                    showOriginal = false
                    preview.setImageBitmap(resized)
                    createButton.isEnabled = true
                    status.text = "Photo ready. Choose an anime style and generate AI artwork."
                }
            }
        } catch (e: Exception) {
            showError("Could not open image: ${e.message}")
        }
    }

    private fun resize(input: Bitmap, maxSide: Int): Bitmap {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(input.width, input.height))
        if (scale >= 1f) return input.copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(input, (input.width * scale).toInt().coerceAtLeast(1),
            (input.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun bitmapBytes(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        return output.toByteArray()
    }

    private fun convertImage() {
        val input = source ?: return
        val base = serverField.text.toString().trim().trimEnd('/')
        if (!base.startsWith("https://")) {
            showError("Enter a valid HTTPS AI server address.")
            return
        }
        val styles = styleSpinner.tag as List<Pair<String, String>>
        val selectedStyle = styles[styleSpinner.selectedItemPosition].second
        val originalBytes = bitmapBytes(input)
        val referenceBytes = reference?.let { bitmapBytes(it) }
        setBusy(true, "Uploading photo and generating real AI anime artwork… This can take a little time.")
        executor.execute {
            try {
                val outputBytes = postMultipart("$base/v1/transform", originalBytes, referenceBytes, selectedStyle)
                val output = BitmapFactory.decodeByteArray(outputBytes, 0, outputBytes.size)
                    ?: error("AI server returned an invalid image.")
                runOnUiThread {
                    imageResult?.recycle()
                    imageResult = output
                    showOriginal = false
                    preview.setImageBitmap(output)
                    setBusy(false, "AI artwork generated. Save it or share it.")
                }
            } catch (e: Exception) {
                runOnUiThread { setBusy(false, "AI generation failed: ${e.message}") }
            }
        }
    }

    private fun postMultipart(endpoint: String, photo: ByteArray, referenceBytes: ByteArray?, style: String): ByteArray {
        val boundary = "----AnimeStudio${UUID.randomUUID()}"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 300000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "image/*")
        }
        try {
            connection.outputStream.use { out ->
                fun field(name: String, value: String) {
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    out.write(value.toByteArray())
                    out.write("\r\n".toByteArray())
                }
                fun file(name: String, bytes: ByteArray) {
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"$name\"; filename=\"upload.jpg\"\r\n".toByteArray())
                    out.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
                    out.write(bytes)
                    out.write("\r\n".toByteArray())
                }
                field("style", style)
                file("photo", photo)
                if (referenceBytes != null) file("reference", referenceBytes)
                out.write("--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                throw IllegalStateException(errorText.take(240))
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun saveImageResult() {
        val bitmap = imageResult ?: run { toast("Generate AI artwork first."); return }
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
        val bitmap = imageResult ?: run { toast("Generate AI artwork first."); return }
        try {
            val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "Anime Image Studio", null)
            if (path == null) error("Could not prepare image")
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share AI anime artwork"))
        } catch (e: Exception) { showError("Share failed: ${e.message}") }
    }

    private fun setBusy(value: Boolean, message: String) {
        busy = value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        createButton.isEnabled = !value && source != null
        status.text = message
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun showError(message: String) { status.text = message; toast(message) }

    override fun onDestroy() {
        executor.shutdown()
        source?.recycle()
        reference?.recycle()
        imageResult?.recycle()
        super.onDestroy()
    }
}
