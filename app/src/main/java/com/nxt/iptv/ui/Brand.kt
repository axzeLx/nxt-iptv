package com.nxt.iptv.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.nxt.iptv.R

/** "NXT IPTV" with the NXT part in the accent color. */
fun brandTitle(context: Context): CharSequence =
    SpannableString("NXT IPTV").apply {
        setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.accent)), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
