package dev.androidagent.overlay

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.androidagent.core.ControlOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Native floating controls used while an agent run has visible device control.
 *
 * The full-screen glow is a separate non-touchable window. The small card is
 * the only touchable area, so other apps keep receiving their own input.
 * There is no AccessibilityService dependency here; device actions stay in
 * the core ADB gateway.
 */
class FloatingControlOverlay(
    context: Context,
    private val onStop: () -> Unit,
    private val onSend: (String) -> Unit,
    private val onOpenApp: () -> Unit,
) : ControlOverlay {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var controlRoot: FrameLayout? = null
    private var glowRoot: EdgeGlowView? = null
    private var statusView: TextView? = null
    private var inputView: EditText? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var glowParams: WindowManager.LayoutParams? = null
    private var configCallbacks: ComponentCallbacks? = null
    private var attachListener: View.OnAttachStateChangeListener? = null
    private var showing = false
    private var inputFocusEnabled = false
    private var captureHidden = false

    override suspend fun show(status: String) {
        requireOverlayPermission()
        withContext(Dispatchers.Main.immediate) {
            // Check again on the UI thread immediately before addView. This
            // keeps permission denial ahead of any visible/device action.
            requireOverlayPermission()
            val existing = controlRoot
            if (showing && existing != null) {
                statusView?.text = status.ifBlank { "Ready" }
                glowRoot?.invalidate()
                return@withContext
            }

            removeViews()
            buildViews(status)
            val glow = glowRoot ?: error("Overlay glow was not created")
            val control = controlRoot ?: error("Overlay controls were not created")
            val glowLayout = glowParams ?: error("Overlay glow parameters were not created")
            val controlLayout = controlParams ?: error("Overlay control parameters were not created")
            try {
                // Add the non-touchable layer first so the tint never sits over
                // the card's touchable window.
                windowManager.addView(glow, glowLayout)
                windowManager.addView(control, controlLayout)
                showing = true
                registerConfigurationCallbacks()
                waitForAttach(glow)
                waitForAttach(control)
            } catch (error: Throwable) {
                removeViews()
                throw error
            }
        }
    }

    override fun update(status: String) {
        runOnMain {
            statusView?.text = status.ifBlank { "Ready" }
            glowRoot?.invalidate()
        }
    }

    override fun hide() {
        runOnMain { removeViews() }
    }

    /**
     * Temporarily removes both overlay layers from the captured view without
     * clearing the edit text. Core can restore them after a screenshot/read.
     */
    override suspend fun setCaptureHidden(hidden: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            val glow = glowRoot ?: return@withContext
            val control = controlRoot ?: return@withContext
            if (!glow.isAttachedToWindow || !control.isAttachedToWindow) {
                waitForAttach(glow)
                waitForAttach(control)
            }
            if (captureHidden == hidden) return@withContext
            captureHidden = hidden
            val visibility = if (hidden) View.INVISIBLE else View.VISIBLE
            // Visibility preserves EditText contents and the current focus
            // state while making both windows absent from a screenshot.
            glow.visibility = visibility
            control.visibility = visibility
            if (!hidden) clampPosition()
        }
    }

    /** Move the small card away when a planned device tap would hit it. */
    override fun avoidTouch(x: Int, y: Int) {
        runOnMain {
            val control = controlRoot ?: return@runOnMain
            val lp = controlParams ?: return@runOnMain
            if (!control.isAttachedToWindow || captureHidden) return@runOnMain

            val width = control.width.takeIf { it > 0 } ?: dp(300)
            val height = control.height.takeIf { it > 0 } ?: dp(150)
            val bounds = screenBounds()
            if (!pointInside(lp.x, lp.y, width, height, x, y)) return@runOnMain

            val margin = dp(12)
            val maxX = (bounds.width() - width - margin).coerceAtLeast(margin)
            val maxY = (bounds.height() - height - margin).coerceAtLeast(margin)
            val currentX = lp.x
            val currentY = lp.y
            val candidates = listOf(
                currentX.coerceIn(margin, maxX) to
                    (if (currentY > bounds.height() / 2) margin else maxY),
                (if (currentX > bounds.width() / 2) margin else maxX) to
                    currentY.coerceIn(margin, maxY),
                margin to margin,
                maxX to maxY,
            )
            val destination = candidates.firstOrNull { (candidateX, candidateY) ->
                !pointInside(candidateX, candidateY, width, height, x, y)
            } ?: return@runOnMain
            lp.x = destination.first
            lp.y = destination.second
            updateControlLayout(control, lp)
        }
    }

    // ---------- view construction; main thread only ----------

    private fun buildViews(status: String) {
        val dark = isDark()
        val accent = Color.parseColor(if (dark) "#AEBBFF" else "#4169E1")
        val cardColor = Color.parseColor(if (dark) "#F21B1E2A" else "#F7FFFFFF")
        val onCard = Color.parseColor(if (dark) "#EEF0F8" else "#1B1C22")
        val secondary = Color.parseColor(if (dark) "#BEC3D0" else "#505362")
        val hint = Color.parseColor(if (dark) "#A2A8BA" else "#676A78")

        val glow = EdgeGlowView(appContext, dark).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        glowRoot = glow

        val root = FrameLayout(appContext).apply {
            setPadding(dp(2), dp(2), dp(2), dp(2))
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        val cardBackground = roundedBackground(
            fill = cardColor,
            stroke = if (dark) Color.parseColor("#42FFFFFF") else Color.parseColor("#30000000"),
            radius = 18f,
        )
        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isClickable = true
            isFocusable = false
        }

        val header = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = false
            contentDescription = "Drag agent controls"
        }
        val dot = View(appContext).apply {
            background = dotDrawable(accent)
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
            isClickable = false
            isFocusable = false
        }
        val title = TextView(appContext).apply {
            text = "Android Agent"
            setTextColor(onCard)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(8), 0, dp(6), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val openButton = actionButton("Open", "Open Android Agent", onCard, accent) {
            onOpenApp()
        }
        header.addView(dot)
        header.addView(title)
        header.addView(openButton)

        val statusLabel = TextView(appContext).apply {
            text = status.ifBlank { "Ready" }
            setTextColor(secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, dp(5))
            isClickable = false
            isFocusable = false
        }
        statusView = statusLabel

        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = EditText(appContext).apply {
            this.hint = "Steer or reply"
            setHintTextColor(hint)
            setTextColor(onCard)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            maxLines = 4
            minLines = 1
            isSingleLine = false
            isFocusable = true
            isFocusableInTouchMode = true
            background = roundedBackground(
                fill = if (dark) Color.parseColor("#241F2331") else Color.parseColor("#0C000000"),
                stroke = if (dark) Color.parseColor("#3EFFFFFF") else Color.parseColor("#28000000"),
                radius = 12f,
            )
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) enableInputFocus()
                false
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendFromInput()
                    true
                } else {
                    false
                }
            }
        }
        inputView = input

        val sendButton = actionButton("Send", "Send message", onCard, accent) {
            sendFromInput()
        }
        val stopButton = actionButton("Stop", "Stop run", onCard, Color.parseColor("#D14D61")) {
            // Release any IME focus before the immediate local stop callback.
            disableInputFocus()
            hideKeyboard()
            onStop()
        }
        row.addView(input)
        row.addView(sendButton)
        row.addView(stopButton)

        card.addView(header)
        card.addView(statusLabel)
        card.addView(row)
        root.addView(
            card,
            FrameLayout.LayoutParams(dp(300), FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(2), dp(2), dp(2), dp(2) + maxOf(ime.bottom, bars.bottom) / 4)
            insets
        }

        attachDrag(header)
        controlRoot = root
        inputFocusEnabled = false
        captureHidden = false

        controlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(160)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            token = null
            this.title = "AndroidAgentControl"
        }
        // Android 12 touch obscuring rules do not apply to this layer as a
        // touch target: it is explicitly non-touchable and kept below 0.8f.
        glowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 0.10f
            token = null
            this.title = "AndroidAgentGlow"
        }
    }

    private fun actionButton(
        text: String,
        description: String,
        textColor: Int,
        accent: Int,
        action: () -> Unit,
    ): Button = Button(appContext).apply {
        this.text = text
        this.contentDescription = description
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setAllCaps(false)
        minWidth = 0
        minimumWidth = 0
        minHeight = dp(42)
        minimumHeight = dp(42)
        setPadding(dp(7), 0, dp(7), 0)
        background = roundedBackground(
            fill = Color.argb(35, Color.red(accent), Color.green(accent), Color.blue(accent)),
            stroke = Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent)),
            radius = 12f,
        )
        isFocusable = false
        isFocusableInTouchMode = false
        setOnClickListener { action() }
    }

    private fun attachDrag(header: View) {
        val slop = ViewConfiguration.get(appContext).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        header.setOnTouchListener { _, event ->
            val lp = controlParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (!dragging && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) {
                        dragging = true
                    }
                    if (dragging) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        clampPosition(lp)
                        updateControlLayout(controlRoot, lp)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val moved = dragging
                    dragging = false
                    moved
                }
                else -> false
            }
        }
    }

    private fun sendFromInput() {
        val input = inputView ?: return
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        input.setText("")
        hideKeyboard()
        disableInputFocus()
        onSend(text)
    }

    private fun enableInputFocus() {
        runOnMain {
            val root = controlRoot ?: return@runOnMain
            val input = inputView ?: return@runOnMain
            val lp = controlParams ?: return@runOnMain
            if (!showing || captureHidden) return@runOnMain
            if (!inputFocusEnabled) {
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                updateControlLayout(root, lp)
                inputFocusEnabled = true
            }
            input.requestFocus()
            input.post {
                val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun disableInputFocus() {
        val root = controlRoot ?: return
        val input = inputView
        input?.clearFocus()
        val lp = controlParams ?: return
        if (inputFocusEnabled || lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            inputFocusEnabled = false
            updateControlLayout(root, lp)
        }
    }

    // ---------- window lifecycle ----------

    private fun requireOverlayPermission() {
        if (!Settings.canDrawOverlays(appContext)) {
            throw SecurityException("Overlay permission missing: enable Display over other apps before starting device control.")
        }
    }

    private suspend fun waitForAttach(view: View) {
        if (view.isAttachedToWindow) return
        withTimeout(2_000L) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val listener = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(attached: View) {
                        attached.removeOnAttachStateChangeListener(this)
                        if (attachListener === this) attachListener = null
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onViewDetachedFromWindow(detached: View) = Unit
                }
                attachListener = listener
                view.addOnAttachStateChangeListener(listener)
                if (view.isAttachedToWindow) {
                    view.removeOnAttachStateChangeListener(listener)
                    if (attachListener === listener) attachListener = null
                    if (continuation.isActive) continuation.resume(Unit)
                }
                continuation.invokeOnCancellation {
                    view.removeOnAttachStateChangeListener(listener)
                    if (attachListener === listener) attachListener = null
                }
            }
        }
    }

    private fun registerConfigurationCallbacks() {
        if (configCallbacks != null) return
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                runOnMain { clampPosition() }
            }

            override fun onLowMemory() = Unit
        }
        configCallbacks = callbacks
        appContext.registerComponentCallbacks(callbacks)
    }

    private fun removeViews() {
        hideKeyboard()
        disableInputFocus()
        configCallbacks?.let {
            try {
                appContext.unregisterComponentCallbacks(it)
            } catch (_: Exception) {
            }
        }
        configCallbacks = null
        attachListener?.let { listener ->
            controlRoot?.removeOnAttachStateChangeListener(listener)
            glowRoot?.removeOnAttachStateChangeListener(listener)
        }
        attachListener = null
        removeWindow(controlRoot)
        removeWindow(glowRoot)
        controlRoot = null
        glowRoot = null
        statusView = null
        inputView = null
        controlParams = null
        glowParams = null
        showing = false
        captureHidden = false
    }

    private fun removeWindow(view: View?) {
        if (view == null) return
        try {
            if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
        } catch (_: IllegalArgumentException) {
        } catch (_: Exception) {
        }
    }

    private fun hideKeyboard() {
        val input = inputView ?: return
        try {
            val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(input.windowToken, 0)
        } catch (_: Exception) {
        }
    }

    private fun updateControlLayout(
        root: View?,
        lp: WindowManager.LayoutParams,
    ) {
        if (!showing || root == null || !root.isAttachedToWindow) return
        try {
            windowManager.updateViewLayout(root, lp)
        } catch (_: IllegalArgumentException) {
        } catch (_: Exception) {
        }
    }

    private fun clampPosition() {
        val lp = controlParams ?: return
        clampPosition(lp)
        updateControlLayout(controlRoot, lp)
    }

    private fun clampPosition(lp: WindowManager.LayoutParams) {
        val root = controlRoot
        val width = root?.width?.takeIf { it > 0 } ?: dp(300)
        val height = root?.height?.takeIf { it > 0 } ?: dp(150)
        val bounds = screenBounds()
        val margin = dp(8)
        lp.x = lp.x.coerceIn(margin, (bounds.width() - width - margin).coerceAtLeast(margin))
        lp.y = lp.y.coerceIn(margin, (bounds.height() - height - margin).coerceAtLeast(margin))
    }

    private fun screenBounds() = windowManager.currentWindowMetrics.bounds

    private fun pointInside(left: Int, top: Int, width: Int, height: Int, x: Int, y: Int): Boolean =
        x >= left && x <= left + width && y >= top && y <= top + height

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    // ---------- small visual helpers ----------

    private fun isDark(): Boolean =
        appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun roundedBackground(fill: Int, stroke: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(radius)
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun dotDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        appContext.resources.displayMetrics,
    ).toInt()

    private fun dpF(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        appContext.resources.displayMetrics,
    )

    private class EdgeGlowView(context: Context, dark: Boolean) : View(context) {
        private val accent = if (dark) Color.rgb(106, 128, 255) else Color.rgb(65, 105, 225)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val edge = dp(84).toFloat()
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            paint.shader = LinearGradient(
                0f,
                0f,
                edge,
                0f,
                Color.argb(130, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, edge, h, paint)
            paint.shader = LinearGradient(
                w,
                0f,
                w - edge,
                0f,
                Color.argb(130, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(w - edge, 0f, w, h, paint)
            paint.shader = LinearGradient(
                0f,
                0f,
                0f,
                edge,
                Color.argb(88, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w, edge, paint)
            paint.shader = LinearGradient(
                0f,
                h,
                0f,
                h - edge,
                Color.argb(88, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, h - edge, w, h, paint)
            paint.shader = null
        }

        private fun dp(value: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }
}
