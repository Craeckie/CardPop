/*
 * Copyright (C) 2026 FloFla Dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.cardpop.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * On-the-fly text-to-speech for flashcard fronts (Chinese terms).
 *
 * A single process-wide [TextToSpeech] engine is kept warm across overlay
 * recreations so the first tap doesn't pay the bind/init latency. The engine
 * is created lazily on first use and never shut down — it is released when the
 * process dies. Nothing is stored on disk; audio is synthesized live.
 *
 * Language is Mandarin (zh-CN). If the device has no Chinese voice data the
 * engine reports it via [languageSupported]; callers should surface that to the
 * user rather than failing silently.
 */
object FlashcardTts {

    private const val TAG = "FlashcardTts"

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile var languageSupported: Boolean = true
        private set

    /** Text to speak once the engine finishes its asynchronous init. */
    @Volatile private var pending: String? = null

    /**
     * Eagerly create the engine so the first [speak] is instant. Safe to call
     * repeatedly — a no-op once an engine exists. Uses the application context
     * so the engine isn't tied to a short-lived overlay/service.
     */
    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                languageSupported = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                ready = true
                pending?.let { speakNow(it) }
                pending = null
            } else {
                Log.w(TAG, "TextToSpeech init failed: status=$status")
            }
        }
    }

    /**
     * Speak [text] in Mandarin, interrupting anything currently playing. If the
     * engine is still initializing the request is queued and spoken on ready.
     */
    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        if (tts == null) {
            pending = text
            init(context)
            return
        }
        if (ready) speakNow(text) else pending = text
    }

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "flashcard-tts")
    }
}
