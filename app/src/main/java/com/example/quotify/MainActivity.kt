package com.example.quotify

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.quotify.databinding.ActivityMainBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tts: TextToSpeech
    private var currentQuote: QuoteModel? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)

        setupClickListeners()
        getQuote()
    }


    private fun setupClickListeners() {
        binding.nextBtn.setOnClickListener {
            getQuote()
        }

        binding.audioButton.setOnClickListener {
            speakQuote()
        }

        binding.copyButton.setOnClickListener {
            copyQuoteToClipboard()
            animateCopyButton()
        }

        binding.shareButton.setOnClickListener {
            shareQuote()
        }
    }


    private fun getQuote() {
        setInProgress(true)

        GlobalScope.launch {
            try {
                val response = RetrofitInstance.quoteApi.getRandomQuote()
                runOnUiThread {
                    setInProgress(false)
                    response.body()?.first()?.let {
                        currentQuote = it
                        setUI(it)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setInProgress(false)
                    Toast.makeText(applicationContext, "Something went wrong", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setUI(quote: QuoteModel) {
        binding.quoteTv.text = quote.q
        binding.authorTv.text = quote.a
    }

    private fun setInProgress(inProgress: Boolean) {
        if (inProgress) {
            binding.progressBar.visibility = View.VISIBLE
            binding.nextBtn.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.nextBtn.visibility = View.VISIBLE
        }
    }

    private fun speakQuote() {
        currentQuote?.let { quote ->
            val toSpeak = "${quote.q} by ${quote.a}"

            requestAudioFocus()

            tts.setSpeechRate(0.85f)
            tts.setPitch(1.05f)

            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val comfortableVolume = (maxVolume * 0.8).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, comfortableVolume, 0)

            val params = Bundle()
            params.putInt(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, 0)
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0.0f)

            tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, params, "QuoteUtterance")
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { }
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun copyQuoteToClipboard() {
        currentQuote?.let { quote ->
            val quoteText = "${quote.q} - ${quote.a}"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Quote", quoteText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Quote copied to clipboard", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "No quote available to copy", Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateCopyButton() {
        val colorFrom = Color.TRANSPARENT
        val colorTo = ContextCompat.getColor(this, R.color.highlight_color)
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.duration = 250 // milliseconds

        colorAnimation.addUpdateListener { animator ->
            binding.copyButton.setColorFilter(animator.animatedValue as Int)
        }

        colorAnimation.start()

        // Reset the color after the animation
        binding.copyButton.postDelayed({
            binding.copyButton.clearColorFilter()
        }, 300)
    }

    private fun shareQuote() {
        currentQuote?.let { quote ->
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${quote.q} - ${quote.a}")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Quote"))
        } ?: run {
            Toast.makeText(this, "No quote available to share", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
            } else {
                tts.setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build())
            }
        } else {
            Toast.makeText(this, "Text-to-Speech initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        super.onDestroy()
    }
}