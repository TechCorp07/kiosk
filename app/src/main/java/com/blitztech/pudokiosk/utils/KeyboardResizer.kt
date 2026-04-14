package com.blitztech.pudokiosk.utils

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout

/**
 * Standard workaround for Android issue 5497 where adjustResize fails
 * to work when an activity is running in fullscreen mode.
 * 
 * It listens to the layout tree and explicitly resizes the parent
 * container when the soft keyboard appears.
 */
class KeyboardResizer private constructor(activity: Activity) {

    private val mChildOfContent: View
    private var usableHeightPrevious = 0
    private val frameLayoutParams: FrameLayout.LayoutParams

    init {
        val content = activity.findViewById<FrameLayout>(android.R.id.content)
        mChildOfContent = content.getChildAt(0)
        mChildOfContent.viewTreeObserver.addOnGlobalLayoutListener { possiblyResizeChildOfContent() }
        frameLayoutParams = mChildOfContent.layoutParams as FrameLayout.LayoutParams
    }

    private fun possiblyResizeChildOfContent() {
        val usableHeightNow = computeUsableHeight()
        if (usableHeightNow != usableHeightPrevious) {
            val usableHeightSansKeyboard = mChildOfContent.rootView.height
            val heightDifference = usableHeightSansKeyboard - usableHeightNow
            if (heightDifference > usableHeightSansKeyboard / 4) {
                // keyboard probably just became visible
                frameLayoutParams.height = usableHeightSansKeyboard - heightDifference
            } else {
                // keyboard probably just became hidden
                frameLayoutParams.height = usableHeightSansKeyboard
            }
            mChildOfContent.requestLayout()
            usableHeightPrevious = usableHeightNow
        }
    }

    private fun computeUsableHeight(): Int {
        val r = Rect()
        mChildOfContent.getWindowVisibleDisplayFrame(r)
        return r.bottom - r.top
    }

    companion object {
        fun assistActivity(activity: Activity) {
            KeyboardResizer(activity)
        }
    }
}
