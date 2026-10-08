package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _finalText = MutableStateFlow("")
    val finalText: StateFlow<String> = _finalText.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isOnDevice = MutableStateFlow(false)
    val isOnDevice: StateFlow<Boolean> = _isOnDevice.asStateFlow()

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening() {
        stopListening()
        _errorMessage.value = null
        _partialText.value = ""
        _finalText.value = ""

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                _isOnDevice.value = true
            } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                _isOnDevice.value = false
            } else {
                _errorMessage.value = "Voice transcription isn't available right now."
                return
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {
                    _isListening.value = true
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Deliberately no waveform/equalizer visualization as per Home AI UX design
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                        SpeechRecognizer.ERROR_NETWORK -> "Network required for online speech recognition"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech was detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy, please try again"
                        SpeechRecognizer.ERROR_SERVER -> "Speech server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input heard"
                        else -> "Speech recognition encountered an issue"
                    }
                    // Only show error if no text was captured
                    if (_finalText.value.isBlank() && _partialText.value.isBlank()) {
                        _errorMessage.value = message
                    }
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val resultText = matches?.firstOrNull()?.trim()
                    if (!resultText.isNullOrEmpty()) {
                        val current = _finalText.value
                        _finalText.value = if (current.isBlank()) resultText else "$current $resultText"
                        _partialText.value = ""
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim()
                    if (!partial.isNullOrEmpty()) {
                        _partialText.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true

        } catch (e: Exception) {
            _isListening.value = false
            _errorMessage.value = "Voice transcription isn't available right now."
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        _isListening.value = false
    }

    fun reset() {
        stopListening()
        _partialText.value = ""
        _finalText.value = ""
        _errorMessage.value = null
    }
}
