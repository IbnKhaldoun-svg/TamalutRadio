package com.tamalut.radio

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.tamalut.radio.core.preferences.OverlayEdge
import kotlin.math.abs

internal data class FloatingOverlayViewState(
    val edge: OverlayEdge,
    val verticalFraction: Float,
    val expanded: Boolean,
)

internal class FloatingOverlayWindow(
    context: Context,
    private val onDismiss: () -> Unit,
    private val onExpandedChanged: (Boolean) -> Unit,
    private val onPositionChanged: (OverlayEdge, Float) -> Unit,
) {
    private val appContext = context.applicationContext
    private val overlayContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        appContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    } else {
        appContext
    }
    private val manager = overlayContext.getSystemService(WindowManager::class.java)
    private val density = overlayContext.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(overlayContext).scaledTouchSlop
    private val collapsedWidth = dp(36)
    private val expandedWidth = dp(92)
    private val windowHeight = dp(48)

    private val root = LinearLayout(overlayContext).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        elevation = dp(10).toFloat()
        contentDescription = "Player flottante TamalutRadio"
    }

    private val params = WindowManager.LayoutParams(
        collapsedWidth,
        windowHeight,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private var currentState: FloatingOverlayViewState? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    fun show(state: FloatingOverlayViewState): Boolean {
        if (!Settings.canDrawOverlays(appContext)) return false
        currentState = state
        render(state)
        if (root.isAttachedToWindow) return true

        return try {
            manager.addView(root, params)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: WindowManager.BadTokenException) {
            false
        }
    }

    fun update(state: FloatingOverlayViewState) {
        currentState = state
        render(state)
    }

    fun hide() {
        if (!root.isAttachedToWindow) return
        try {
            manager.removeView(root)
        } catch (_: IllegalArgumentException) {
            // Android may already have removed the overlay after permission revocation.
        }
    }

    private fun render(state: FloatingOverlayViewState) {
        val width = if (state.expanded) expandedWidth else collapsedWidth
        val (screenWidth, screenHeight) = displaySize()
        params.width = width
        params.height = windowHeight
        params.x = OverlayGeometry.xForEdge(state.edge, screenWidth, width)
        params.y = OverlayGeometry.yFromNormalizedFraction(
            fraction = state.verticalFraction,
            screenHeight = screenHeight,
            windowHeight = windowHeight,
        )

        root.removeAllViews()
        root.background = backgroundFor(state.edge)

        val tab = edgeTab(state)
        val close = if (state.expanded) closeButton() else null
        if (state.edge == OverlayEdge.LEFT) {
            root.addView(tab, LinearLayout.LayoutParams(collapsedWidth, windowHeight))
            close?.let { root.addView(it, LinearLayout.LayoutParams(expandedWidth - collapsedWidth, windowHeight)) }
        } else {
            close?.let { root.addView(it, LinearLayout.LayoutParams(expandedWidth - collapsedWidth, windowHeight)) }
            root.addView(tab, LinearLayout.LayoutParams(collapsedWidth, windowHeight))
        }

        if (root.isAttachedToWindow) {
            runCatching { manager.updateViewLayout(root, params) }
        }
    }

    private fun edgeTab(state: FloatingOverlayViewState): TextView = TextView(overlayContext).apply {
        gravity = Gravity.CENTER
        textSize = 24f
        setTextColor(Color.rgb(216, 179, 106))
        text = when {
            !state.expanded && state.edge == OverlayEdge.LEFT -> "›"
            !state.expanded && state.edge == OverlayEdge.RIGHT -> "‹"
            state.expanded && state.edge == OverlayEdge.LEFT -> "‹"
            else -> "›"
        }
        contentDescription = if (state.expanded) {
            "Richiudi player flottante"
        } else {
            "Espandi player flottante"
        }
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { onExpandedChanged(!state.expanded) }
        setOnTouchListener(::handleDragTouch)
    }

    private fun closeButton(): TextView = TextView(overlayContext).apply {
        gravity = Gravity.CENTER
        text = "×"
        textSize = 25f
        setTextColor(Color.rgb(198, 106, 70))
        contentDescription = "Nascondi player flottante per questa sessione"
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { onDismiss() }
    }

    private fun handleDragTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - downRawX
                val deltaY = event.rawY - downRawY
                if (!dragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    val (screenWidth, screenHeight) = displaySize()
                    params.x = (startX + deltaX.toInt()).coerceIn(
                        0,
                        (screenWidth - params.width).coerceAtLeast(0),
                    )
                    params.y = OverlayGeometry.clampY(
                        y = startY + deltaY.toInt(),
                        screenHeight = screenHeight,
                        windowHeight = windowHeight,
                    )
                    if (root.isAttachedToWindow) {
                        runCatching { manager.updateViewLayout(root, params) }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val (screenWidth, screenHeight) = displaySize()
                    val edge = OverlayGeometry.snapEdge(
                        x = params.x,
                        screenWidth = screenWidth,
                        windowWidth = params.width,
                    )
                    val fraction = OverlayGeometry.normalizedVerticalFraction(
                        y = params.y,
                        screenHeight = screenHeight,
                        windowHeight = windowHeight,
                    )
                    val updated = currentState?.copy(edge = edge, verticalFraction = fraction)
                    if (updated != null) {
                        currentState = updated
                        view.post { render(updated) }
                    }
                    onPositionChanged(edge, fraction)
                } else {
                    view.performClick()
                }
                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return false
    }

    private fun backgroundFor(edge: OverlayEdge): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        val radius = dp(18).toFloat()
        cornerRadii = if (edge == OverlayEdge.LEFT) {
            floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
        } else {
            floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
        }
        setColor(Color.rgb(22, 27, 34))
        setStroke(dp(1), Color.rgb(79, 138, 115))
    }

    private fun displaySize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        manager.currentWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        @Suppress("DEPRECATION")
        overlayContext.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
