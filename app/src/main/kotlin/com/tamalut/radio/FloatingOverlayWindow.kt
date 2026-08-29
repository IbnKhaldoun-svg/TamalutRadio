package com.tamalut.radio

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.tamalut.radio.core.preferences.OverlayEdge
import kotlin.math.abs

internal data class FloatingOverlayViewState(
    val edge: OverlayEdge,
    val verticalFraction: Float,
    val expanded: Boolean,
    val playbackControls: OverlayPlaybackControlsModel?,
)

private data class OverlayWindowHost(
    val context: Context,
    val manager: WindowManager,
    val density: Float,
    val touchSlop: Int,
    val collapsedWidth: Int,
    val expandedWidth: Int,
    val closeWidth: Int,
    val appEntryWidth: Int,
    val dividerWidth: Int,
    val transportButtonWidth: Int,
    val windowHeight: Int,
    val root: LinearLayout,
    val params: WindowManager.LayoutParams,
)

internal class FloatingOverlayWindow(
    context: Context,
    private val onDismiss: () -> Unit,
    private val onExpandedChanged: (Boolean) -> Unit,
    private val onPositionChanged: (OverlayEdge, Float) -> Unit,
    private val onPlaybackAction: (OverlayPlaybackAction) -> Unit,
    private val onOpenApp: () -> Unit,
    private val onUserInteraction: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val hostSlot = LazyOverlayHostSlot(::createWindowHost)

    private var currentState: FloatingOverlayViewState? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    fun show(state: FloatingOverlayViewState): Boolean {
        if (!Settings.canDrawOverlays(appContext)) return false
        val host = hostSlot.getOrCreate() ?: return false
        currentState = state
        render(host, state)
        if (host.root.isAttachedToWindow) return true

        return try {
            host.manager.addView(host.root, host.params)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: WindowManager.BadTokenException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun update(state: FloatingOverlayViewState) {
        val host = hostSlot.existing() ?: return
        currentState = state
        render(host, state)
    }

    fun hide() {
        val host = hostSlot.existing() ?: return
        if (!host.root.isAttachedToWindow) return
        try {
            host.manager.removeView(host.root)
        } catch (_: IllegalArgumentException) {
            // Android may already have removed the overlay after permission revocation.
        }
    }

    private fun createWindowHost(): OverlayWindowHost? {
        return try {
            val overlayContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val displayManager = appContext.getSystemService(DisplayManager::class.java) ?: return null
                val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    appContext.createWindowContext(
                        display,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        null,
                    )
                } else {
                    appContext
                        .createDisplayContext(display)
                        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                }
            } else {
                appContext
            }

            val manager = overlayContext.getSystemService(WindowManager::class.java) ?: return null
            val density = overlayContext.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()

            val collapsedWidth = dp(36)
            val closeWidth = dp(36)
            val appEntryWidth = dp(52)
            val dividerWidth = dp(1).coerceAtLeast(1)
            val transportButtonWidth = dp(48)
            val expandedWidth = collapsedWidth + closeWidth + appEntryWidth + dividerWidth + (transportButtonWidth * 3)
            val windowHeight = dp(48)
            val root = LinearLayout(overlayContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                elevation = dp(10).toFloat()
                contentDescription = "Player flottante TamalutRadio"
            }
            val params = WindowManager.LayoutParams(
                collapsedWidth,
                windowHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            OverlayWindowHost(
                context = overlayContext,
                manager = manager,
                density = density,
                touchSlop = ViewConfiguration.get(overlayContext).scaledTouchSlop,
                collapsedWidth = collapsedWidth,
                expandedWidth = expandedWidth,
                closeWidth = closeWidth,
                appEntryWidth = appEntryWidth,
                dividerWidth = dividerWidth,
                transportButtonWidth = transportButtonWidth,
                windowHeight = windowHeight,
                root = root,
                params = params,
            )
        } catch (_: UnsupportedOperationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun render(host: OverlayWindowHost, state: FloatingOverlayViewState) {
        val width = if (state.expanded) host.expandedWidth else host.collapsedWidth
        val (screenWidth, screenHeight) = displaySize(host)
        host.params.width = width
        host.params.height = host.windowHeight
        host.params.x = OverlayGeometry.xForEdge(state.edge, screenWidth, width)
        host.params.y = OverlayGeometry.yFromNormalizedFraction(
            fraction = state.verticalFraction,
            screenHeight = screenHeight,
            windowHeight = host.windowHeight,
        )

        host.root.removeAllViews()
        host.root.background = backgroundFor(host, state.edge)

        val tab = edgeTab(host, state)
        val close = if (state.expanded) closeButton(host) else null
        val appEntry = if (state.expanded) appEntryButton(host) else null
        val divider = if (state.expanded) appEntryDivider(host) else null
        val transport = if (state.expanded) transportControls(host, state.playbackControls) else null
        if (state.edge == OverlayEdge.LEFT) {
            host.root.addView(tab, LinearLayout.LayoutParams(host.collapsedWidth, host.windowHeight))
            appEntry?.let {
                host.root.addView(it, LinearLayout.LayoutParams(host.appEntryWidth, host.windowHeight))
            }
            divider?.let {
                host.root.addView(it, LinearLayout.LayoutParams(host.dividerWidth, dp(host, 24)))
            }
            transport?.let {
                host.root.addView(
                    it,
                    LinearLayout.LayoutParams(host.transportButtonWidth * 3, host.windowHeight),
                )
            }
            close?.let {
                host.root.addView(it, LinearLayout.LayoutParams(host.closeWidth, host.windowHeight))
            }
        } else {
            close?.let {
                host.root.addView(it, LinearLayout.LayoutParams(host.closeWidth, host.windowHeight))
            }
            transport?.let {
                host.root.addView(
                    it,
                    LinearLayout.LayoutParams(host.transportButtonWidth * 3, host.windowHeight),
                )
            }
            divider?.let {
                host.root.addView(it, LinearLayout.LayoutParams(host.dividerWidth, dp(host, 24)))
            }
            appEntry?.let {
                host.root.addView(it, LinearLayout.LayoutParams(host.appEntryWidth, host.windowHeight))
            }
            host.root.addView(tab, LinearLayout.LayoutParams(host.collapsedWidth, host.windowHeight))
        }

        if (host.root.isAttachedToWindow) {
            runCatching { host.manager.updateViewLayout(host.root, host.params) }
        }
    }

    private fun edgeTab(host: OverlayWindowHost, state: FloatingOverlayViewState): TextView =
        TextView(host.context).apply {
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
            setOnClickListener {
                onUserInteraction()
                onExpandedChanged(!state.expanded)
            }
            setOnTouchListener(::handleDragTouch)
        }

    private fun appEntryButton(host: OverlayWindowHost): ImageButton = ImageButton(host.context).apply {
        setImageResource(R.mipmap.ic_launcher)
        contentDescription = "Torna a TamalutRadio"
        setBackgroundColor(Color.TRANSPARENT)
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(host, 8), dp(host, 8), dp(host, 8), dp(host, 8))
        setOnClickListener {
            onUserInteraction()
            onOpenApp()
        }
    }

    private fun appEntryDivider(host: OverlayWindowHost): View = View(host.context).apply {
        setBackgroundColor(Color.rgb(79, 138, 115))
        alpha = 0.45f
        contentDescription = null
    }

    private fun transportControls(
        host: OverlayWindowHost,
        model: OverlayPlaybackControlsModel?,
    ): LinearLayout = LinearLayout(host.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(
            transportButton(
                host = host,
                iconRes = android.R.drawable.ic_media_previous,
                description = "Precedente",
                enabled = model?.previousEnabled == true,
                action = OverlayPlaybackAction.PREVIOUS,
            ),
            LinearLayout.LayoutParams(host.transportButtonWidth, host.windowHeight),
        )
        addView(
            transportButton(
                host = host,
                iconRes = if (model?.playPauseIcon == OverlayPlayPauseIcon.PAUSE) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                },
                description = if (model?.playPauseIcon == OverlayPlayPauseIcon.PAUSE) "Pausa" else "Riproduci",
                enabled = model != null,
                action = OverlayPlaybackAction.TOGGLE_PLAY_PAUSE,
            ),
            LinearLayout.LayoutParams(host.transportButtonWidth, host.windowHeight),
        )
        addView(
            transportButton(
                host = host,
                iconRes = android.R.drawable.ic_media_next,
                description = "Successivo",
                enabled = model?.nextEnabled == true,
                action = OverlayPlaybackAction.NEXT,
            ),
            LinearLayout.LayoutParams(host.transportButtonWidth, host.windowHeight),
        )
    }

    private fun transportButton(
        host: OverlayWindowHost,
        iconRes: Int,
        description: String,
        enabled: Boolean,
        action: OverlayPlaybackAction,
    ): ImageButton = ImageButton(host.context).apply {
        setImageResource(iconRes)
        setColorFilter(Color.rgb(216, 179, 106))
        contentDescription = description
        setBackgroundColor(Color.TRANSPARENT)
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.35f
        setPadding(dp(host, 12), dp(host, 12), dp(host, 12), dp(host, 12))
        setOnClickListener {
            onUserInteraction()
            if (isEnabled) onPlaybackAction(action)
        }
    }

    private fun closeButton(host: OverlayWindowHost): TextView = TextView(host.context).apply {
        gravity = Gravity.CENTER
        text = "×"
        textSize = 25f
        setTextColor(Color.rgb(198, 106, 70))
        contentDescription = "Nascondi player flottante per questa sessione"
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener {
            onUserInteraction()
            onDismiss()
        }
    }

    private fun handleDragTouch(view: View, event: MotionEvent): Boolean {
        val host = hostSlot.existing() ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (currentState?.expanded == true) onUserInteraction()
                downRawX = event.rawX
                downRawY = event.rawY
                startX = host.params.x
                startY = host.params.y
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - downRawX
                val deltaY = event.rawY - downRawY
                if (!dragging && (abs(deltaX) > host.touchSlop || abs(deltaY) > host.touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    val (screenWidth, screenHeight) = displaySize(host)
                    host.params.x = (startX + deltaX.toInt()).coerceIn(
                        0,
                        (screenWidth - host.params.width).coerceAtLeast(0),
                    )
                    host.params.y = OverlayGeometry.clampY(
                        y = startY + deltaY.toInt(),
                        screenHeight = screenHeight,
                        windowHeight = host.windowHeight,
                    )
                    if (host.root.isAttachedToWindow) {
                        runCatching { host.manager.updateViewLayout(host.root, host.params) }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val (screenWidth, screenHeight) = displaySize(host)
                    val edge = OverlayGeometry.snapEdge(
                        x = host.params.x,
                        screenWidth = screenWidth,
                        windowWidth = host.params.width,
                    )
                    val fraction = OverlayGeometry.normalizedVerticalFraction(
                        y = host.params.y,
                        screenHeight = screenHeight,
                        windowHeight = host.windowHeight,
                    )
                    val updated = currentState?.copy(edge = edge, verticalFraction = fraction)
                    if (updated != null) {
                        currentState = updated
                        view.post { render(host, updated) }
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

    private fun backgroundFor(host: OverlayWindowHost, edge: OverlayEdge): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            val radius = dp(host, 18).toFloat()
            cornerRadii = if (edge == OverlayEdge.LEFT) {
                floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
            } else {
                floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
            }
            setColor(Color.rgb(22, 27, 34))
            setStroke(dp(host, 1), Color.rgb(79, 138, 115))
        }

    private fun displaySize(host: OverlayWindowHost): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            host.manager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            @Suppress("DEPRECATION")
            host.context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }

    private fun dp(host: OverlayWindowHost, value: Int): Int = (value * host.density).toInt()
}
