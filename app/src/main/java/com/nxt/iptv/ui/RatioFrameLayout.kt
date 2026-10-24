package com.nxt.iptv.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/** FrameLayout that keeps a 16:9 shape inside whatever space it is given. */
class RatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec)
        val maxH = MeasureSpec.getSize(heightMeasureSpec)
        var w = maxW
        var h = w * 9 / 16
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED && h > maxH) {
            h = maxH
            w = h * 16 / 9
        }
        super.onMeasure(
            View.MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
        )
    }
}
