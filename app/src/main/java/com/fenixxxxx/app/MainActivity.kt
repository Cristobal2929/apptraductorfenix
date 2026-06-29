package com.fenixxxxx.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.fenixxxxx.app.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var tts: TextToSpeech
    private var isListening = false
    private var lastTranslation: String? = null
    private var lastOriginal: String? = null
    private val client = OkHttpClient()
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init Clipboard
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // Init TTS
        tts = TextToSpeech(this, this)

        // UI setup
        updateToggleButton()

        binding.btnToggle.setOnClickListener {
            isListening = !isListening
            if (isListening) {
                startListening()
            } else {
                stopListening()
            }
            updateToggleButton()
        }

        binding.btnRepeat.setOnClickListener {
            lastTranslation?.let {
                speak(it)
            } ?: run {
                showMessage(getString(R.string.no_translation_yet))
            }
        }
    }

    private fun updateToggleButton() {
        if (isListening) {
            binding.btnToggle.text = getString(R.string.pause)
            binding.btnToggle.setBackgroundColor(Color.parseColor("#4CAF50")) // verde
        } else {
            binding.btnToggle.text = getString(R.string.activate)
            binding.btnToggle.setBackgroundColor(Color.parseColor("#F44336")) // rojo
        }
    }

    @SuppressLint("NewApi")
    private fun startListening() {
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            val clip: ClipData? = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this).toString()
                if (text.isNotBlank() && text != lastOriginal) {
                    handleNewClipboardText(text)
                }
            }
        }
        clipboardManager.addPrimaryClipChangedListener(clipboardListener!!)
    }

    private fun stopListening() {
        clipboardListener?.let {
            clipboardManager.removePrimaryClipChangedListener(it)
        }
        clipboardListener = null
    }

    private fun handleNewClipboardText(text: String) {
        if (text.length > 500) {
            showMessage(getString(R.string.text_too_long))
            return
        }
        // Show original text immediately
        binding.tvOriginal.text = text
        // Translate
        coroutineScope.launch {
            val translation = translateText(text)
            if (translation != null) {
                lastTranslation = translation
                lastOriginal = text
                binding.tvTranslation.text = translation
                speak(translation)
            } else {
                showMessage(getString(R.string.translation_failed))
            }
        }
    }

    private suspend fun translateText(text: String): String? {
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url =
                "https://api.mymemory.translated.net/get?q=$encodedText&langpair=fr|es"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                null
            } else {
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val responseData = json.getJSONObject("responseData")
                    responseData.getString("translatedText")
                } else {
                    null
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun speak(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translation")
        } else {
            @Suppress("DEPRECATION")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // TextToSpeech initialization
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("es"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                showMessage(getString(R.string.tts_not_supported))
            }
        } else {
            showMessage(getString(R.string.tts_init_failed))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        tts.shutdown()
        coroutineScope.cancel()
    }
}