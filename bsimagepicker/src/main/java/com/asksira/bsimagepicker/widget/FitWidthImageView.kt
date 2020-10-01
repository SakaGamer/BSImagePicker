package com.asksira.bsimagepicker.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.asksira.bsimagepicker.R
import kotlin.math.roundToInt

/**
 * An ImageView that measures itself according to specified aspect ratio.
 * This does not work if height is match_parent.
 */
class FitWidthImageView : AppCompatImageView {
    /**
     * Aspect ratio is calculated by width / height.
     */
    private var aspectRatio = 0f

    constructor(context: Context?) : super(context!!)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.AspectRatioView, 0, 0)
        aspectRatio = try {
            a.getFloat(R.styleable.AspectRatioView_view_aspectRatio, 0f)
        } finally {
            a.recycle()
        }
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (aspectRatio > 0) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = (MeasureSpec.getSize(widthMeasureSpec) / aspectRatio).roundToInt()
            val finalWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            val finalHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            super.onMeasure(finalWidthMeasureSpec, finalHeightMeasureSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}