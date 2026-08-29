package com.tamalut.radio

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.tamalut.radio.core.preferences.DataStoreUserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object FloatingOverlayWindow {
    private val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rootView: View? = null
    private var windowManager: WindowManager? = null
    private var preferencesRepository: DataStoreUserPreferencesRepository? = null

    fun show(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) return false
        if (rootView != null) return true

        val manager = appContext.getSystemService(WindowManager::class.java)
        val density = appContext.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(Color.rgb(22, 27, 34))
            setStroke(dp(1), Color.rgb(79, 138, 115))
        }

        val root = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(6), dp(8))
            background = backgroundDrawable
            elevation = dp(10).toFloat()
            contentDescription = "Player flottante TamalutRadio"
        }

        val label = TextView(appContext).apply {
            text = "TamalutRadio"
            textSize = 15f
            setTextColor(Color.rgb(255, 248, 236))
            setPadding(0, 0, dp(8), 0)
        }
        root.addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val close = ImageButton(appContext).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.rgb(216, 179, 106))
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Chiudi player flottante"
            setOnClickListener {
                hide()
                val repository = preferencesRepository
                    ?: DataStoreUserPreferencesRepository(appContext).also {
                        preferencesRepository = it
                    }
                preferenceScope.launch {
                    repository.setOverlayEnabled(false)
                }
            }
        }
        root.addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(96)
        }

        return try {
            manager.addView(root, params)
            windowManager = manager
            rootView = root
            true
        } catch (_: SecurityException) {
            false
        } catch (_: WindowManager.BadTokenException) {
            false
        }
    }

    fun hide() {
        val view = rootView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The system may already have removed the overlay after permission revocation.
        } finally {
            rootView = null
            windowManager = null
        }
    }
}
