/*
 * Copyright © 2018 The Android Open Source Project
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.widget

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.FloatProperty
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi

class SlashDrawable(private val mDrawable: Drawable) : Drawable() {
    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mPath = Path()
    private val mSlashRect = RectF()
    private var mAnimationEnabled = true

    // Animate this value on change
    private var mCurrentSlashLength = 0f
    private var mRotation = 0f
    private var mSlashed = false

    override fun draw(canvas: Canvas) {
        canvas.save()
        val m = Matrix()
        val width = bounds.width()
        val height = bounds.height()
        val radiusX = scale(CORNER_RADIUS, width)
        val radiusY = scale(CORNER_RADIUS, height)
        updateRect(
            scale(LEFT, width),
            scale(TOP, height),
            scale(RIGHT, width),
            scale(TOP + mCurrentSlashLength, height)
        )
        mPath.reset()
        // Draw the slash vertically
        mPath.addRoundRect(mSlashRect, radiusX, radiusY, Path.Direction.CW)
        // Rotate -45 + desired rotation
        m.setRotate(mRotation + DEFAULT_ROTATION, width / 2f, height / 2f)
        mPath.transform(m)
        canvas.drawPath(mPath, mPaint)

        // Rotate back to vertical
        m.setRotate(-mRotation - DEFAULT_ROTATION, width / 2f, height / 2f)
        mPath.transform(m)

        // Draw another rect right next to the first, for clipping
        m.setTranslate(mSlashRect.width(), 0f)
        mPath.transform(m)
        mPath.addRoundRect(mSlashRect, 1f * width, 1f * height, Path.Direction.CW)
        m.setRotate(mRotation + DEFAULT_ROTATION, width / 2f, height / 2f)
        mPath.transform(m)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            canvas.clipPath(mPath, Region.Op.DIFFERENCE) else canvas.clipOutPath(mPath)
        mDrawable.draw(canvas)
        canvas.restore()
    }

    override fun getIntrinsicHeight() = mDrawable.intrinsicHeight

    override fun getIntrinsicWidth() = mDrawable.intrinsicWidth

    @Deprecated("Deprecated in API level 29")
    override fun getOpacity() = PixelFormat.OPAQUE

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        mDrawable.bounds = bounds
    }

    private fun scale(frac: Float, width: Int) = frac * width

    override fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int) {
        mDrawable.alpha = alpha
        mPaint.alpha = alpha
    }

    fun setAnimationEnabled(enabled: Boolean) {
        mAnimationEnabled = enabled
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mDrawable.colorFilter = colorFilter
        mPaint.colorFilter = colorFilter
    }

    private fun setDrawableTintList(tint: ColorStateList?) {
        mDrawable.setTintList(tint)
    }

    fun setRotation(rotation: Float) {
        if (mRotation == rotation) return
        mRotation = rotation
        invalidateSelf()
    }

    fun setSlashed(slashed: Boolean) {
        if (mSlashed == slashed) return
        mSlashed = slashed
        val end = if (mSlashed) SLASH_HEIGHT / SCALE else 0f
        val start = if (mSlashed) 0f else SLASH_HEIGHT / SCALE
        if (mAnimationEnabled) {
            val anim = ObjectAnimator.ofFloat(this, mSlashLengthProp, start, end)
            anim.addUpdateListener { _ -> invalidateSelf() }
            anim.duration = QS_ANIM_LENGTH
            anim.start()
        } else {
            mCurrentSlashLength = end
            invalidateSelf()
        }
    }

    override fun setTint(@ColorInt tintColor: Int) {
        super.setTint(tintColor)
        mDrawable.setTint(tintColor)
        mPaint.color = tintColor
    }

    override fun setTintList(tint: ColorStateList?) {
        super.setTintList(tint)
        setDrawableTintList(tint)
        mPaint.color = tint?.defaultColor ?: 0
        invalidateSelf()
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        super.setTintMode(tintMode)
        mDrawable.setTintMode(tintMode)
    }

    private fun updateRect(left: Float, top: Float, right: Float, bottom: Float) {
        mSlashRect.left = left
        mSlashRect.top = top
        mSlashRect.right = right
        mSlashRect.bottom = bottom
    }

    companion object {
        private const val CENTER_X = 10.65f
        private const val CENTER_Y = 11.869239f
        private val CORNER_RADIUS = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) 0f else 1f

        // Draw the slash washington-monument style; rotate to no-u-turn style
        private const val DEFAULT_ROTATION = -45f
        private const val QS_ANIM_LENGTH: Long = 350
        private const val SCALE = 24f
        private const val SLASH_HEIGHT = 28f

        // These values are derived in un-rotated (vertical) orientation
        private const val SLASH_WIDTH = 1.8384776f

        // Bottom is derived during animation
        private const val LEFT = (CENTER_X - SLASH_WIDTH / 2) / SCALE
        private const val RIGHT = (CENTER_X + SLASH_WIDTH / 2) / SCALE
        private const val TOP = (CENTER_Y - SLASH_HEIGHT / 2) / SCALE
        private val mSlashLengthProp: FloatProperty<SlashDrawable> = object : FloatProperty<SlashDrawable>("slashLength") {
            override fun get(obj: SlashDrawable): Float {
                return obj.mCurrentSlashLength
            }

            override fun setValue(obj: SlashDrawable, value: Float) {
                obj.mCurrentSlashLength = value
            }
        }
    }

}
