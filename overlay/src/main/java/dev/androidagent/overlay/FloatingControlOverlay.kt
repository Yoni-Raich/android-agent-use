package dev.androidagent.overlay

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
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
import dev.androidagent.core.OverlayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Native floating controls used for the full lifetime of an agent run.
 *
 * The small glass pill is the only touchable window, so other apps keep
 * receiving their own input outside its bounds.
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
    private var statusView: TextView? = null
    private var inputView: EditText? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var statusDot: View? = null
    private var configCallbacks: ComponentCallbacks? = null
    private var attachListener: View.OnAttachStateChangeListener? = null
    private var showing = false
    private var inputFocusEnabled = false
    private var captureHidden = false
    private var finishRunnable: Runnable? = null
    private var appForeground = false
    private var runActive = false
    private var currentStatus = "Ready"

    override suspend fun show(status: String) {
        requireOverlayPermission()
        withContext(Dispatchers.Main.immediate) {
            // Check again on the UI thread immediately before addView. This
            // keeps permission denial ahead of any visible/device action.
            requireOverlayPermission()
            currentStatus = status.ifBlank { "Ready" }
            runActive = true
            if (appForeground) {
                // The app owns the foreground surface, so keep the run state
                // without placing a window over the app. It will be rebuilt
                // when the user leaves the app.
                removeViews()
                return@withContext
            }

            finishRunnable?.let(mainHandler::removeCallbacks)
            finishRunnable = null
            addViewsIfNeeded()
            val control = controlRoot ?: error("Overlay controls were not created")
            try {
                waitForAttach(control)
            } catch (error: Throwable) {
                removeViews()
                throw error
            }
        }
    }

    override fun update(status: String) {
        runOnMain {
            currentStatus = status.ifBlank { "Ready" }
            applyStatus(currentStatus)
        }
    }

    override fun finish(state: OverlayState) {
        runOnMain {
            finishRunnable?.let(mainHandler::removeCallbacks)
            finishRunnable = null
            currentStatus = state.label
            runActive = false
            if (appForeground) {
                removeViews()
                return@runOnMain
            }
            applyStatus(currentStatus)
            if (!showing) {
                removeViews()
                openAppAfterFinish()
                return@runOnMain
            }
            val callback = Runnable {
                finishRunnable = null
                removeViews()
                openAppAfterFinish()
            }
            finishRunnable = callback
            mainHandler.postDelayed(callback, FINISH_DISPLAY_MS)
        }
    }

    override fun hide() {
        runOnMain {
            runActive = false
            removeViews()
        }
    }

    /**
     * Keeps the run alive while the app owns the foreground window. The
     * contract has a default implementation in core so other overlays can
     * ignore this lifecycle hint.
     */
    fun setAppForeground(foreground: Boolean) {
        runOnMain {
            if (appForeground == foreground) return@runOnMain
            appForeground = foreground
            if (foreground) {
                // Removing the window makes the foreground app completely
                // unobstructed and also removes it from capture surfaces.
                removeViews()
            } else if (runActive) {
                // A run may have started from the app while this flag was
                // true. Restore the latest status as soon as another app is
                // visible. Lifecycle callbacks must not crash the process if
                // permission was revoked while the app was away.
                runCatching {
                    requireOverlayPermission()
                    addViewsIfNeeded()
                }.onFailure { removeViews() }
            }
        }
    }

    /**
     * Temporarily removes the overlay from the captured view without
     * clearing the edit text. Core can restore them after a screenshot/read.
     */
    override suspend fun setCaptureHidden(hidden: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            val control = controlRoot ?: return@withContext
            if (!control.isAttachedToWindow) waitForAttach(control)
            if (captureHidden == hidden) return@withContext
            if (hidden) {
                // Device actions must not leave the overlay IME focused while
                // the card is hidden from the captured surface.
                disableInputFocus()
                hideKeyboard()
            }
            captureHidden = hidden
            val visibility = if (hidden) View.INVISIBLE else View.VISIBLE
            // Visibility preserves EditText contents and the current focus
            // state while making the window absent from a screenshot.
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

            val width = control.width.takeIf { it > 0 } ?: panelWidthPx()
            val height = control.height.takeIf { it > 0 } ?: dp(72)
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

    private fun addViewsIfNeeded() {
        if (showing && controlRoot != null) {
            applyStatus(currentStatus)
            return
        }
        if (controlRoot != null || controlParams != null) removeViews()
        buildViews(currentStatus)
        val control = controlRoot ?: error("Overlay controls were not created")
        val controlLayout = controlParams ?: error("Overlay control parameters were not created")
        try {
            windowManager.addView(control, controlLayout)
            showing = true
            registerConfigurationCallbacks()
            applyStatus(currentStatus)
        } catch (error: Throwable) {
            removeViews()
            throw error
        }
    }

    private fun buildViews(status: String) {
        val dark = isDark()
        val accent = Color.parseColor(if (dark) "#B9C2FF" else "#4169E1")
        val onCard = Color.parseColor(if (dark) "#F3F4FB" else "#1B1C22")
        val hintColor = Color.parseColor(if (dark) "#989EAF" else "#676A78")

        val root = FrameLayout(appContext).apply {
            setPadding(dp(2), dp(2), dp(2), dp(2))
            clipChildren = false
            clipToPadding = false
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        val cardBackground = glassBackground(dark)
        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground
            setPadding(dp(8), dp(5), dp(8), dp(5))
            elevation = dpF(8f)
            isClickable = true
            isFocusable = false
        }

        val handle = DragHandleView(appContext, dark).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(46))
            contentDescription = "Drag agent controls"
            isClickable = true
            isFocusable = false
        }
        val dot = View(appContext).apply {
            background = dotDrawable(accent)
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                setMargins(dp(2), 0, dp(7), 0)
            }
            isClickable = false
            isFocusable = false
        }
        statusDot = dot
        val center = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            minimumWidth = dp(112)
            isClickable = false
            isFocusable = false
        }
        val statusLabel = TextView(appContext).apply {
            text = status.ifBlank { "Ready" }
            setTextColor(onCard)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            isClickable = false
            isFocusable = false
        }
        this.statusView = statusLabel
        val input = EditText(appContext).apply {
            this.hint = "Steer or reply"
            setHintTextColor(hintColor)
            setTextColor(onCard)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            maxLines = 1
            isSingleLine = true
            isFocusable = true
            isFocusableInTouchMode = true
            background = roundedBackground(
                fill = if (dark) Color.argb(35, 255, 255, 255) else Color.argb(18, 0, 0, 0),
                stroke = if (dark) Color.argb(45, 255, 255, 255) else Color.argb(35, 0, 0, 0),
                radius = 10f,
            )
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(28),
            ).apply { topMargin = dp(3) }
            contentDescription = "Steer or reply"
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
        center.addView(statusLabel)
        center.addView(input)

        val sendButton = actionButton("↑", "Send message", onCard, accent) {
            sendFromInput()
        }
        val stopButton = actionButton("■", "Stop run", onCard, Color.parseColor("#D14D61")) {
            // Release any IME focus before the immediate local stop callback.
            disableInputFocus()
            hideKeyboard()
            onStop()
        }
        val openButton = actionButton("↗", "Open Android Agent", onCard, accent) {
            // Release focus before handing control back to the app window.
            disableInputFocus()
            hideKeyboard()
            onOpenApp()
        }

        card.addView(handle)
        card.addView(dot)
        card.addView(center)
        card.addView(sendButton)
        card.addView(stopButton)
        card.addView(openButton)
        root.addView(
            card,
            FrameLayout.LayoutParams(panelWidthPx(), FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(2), dp(2), dp(2), dp(2) + maxOf(ime.bottom, bars.bottom) / 4)
            insets
        }

        attachDrag(handle)
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
            y = dp(112)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            token = null
            this.title = "AndroidAgentControl"
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
        setAllCaps(false)
        minWidth = dp(34)
        minimumWidth = dp(34)
        minHeight = dp(38)
        minimumHeight = dp(38)
        setPadding(dp(2), 0, dp(2), 0)
        background = roundedBackground(
            fill = Color.argb(35, Color.red(accent), Color.green(accent), Color.blue(accent)),
            stroke = Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent)),
            radius = 11f,
        )
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
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
        finishRunnable?.let(mainHandler::removeCallbacks)
        finishRunnable = null
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
        }
        attachListener = null
        removeWindow(controlRoot)
        controlRoot = null
        statusView = null
        statusDot = null
        inputView = null
        controlParams = null
        showing = false
        captureHidden = false
    }

    private fun openAppAfterFinish() {
        if (!appForeground) onOpenApp()
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
        val width = root?.width?.takeIf { it > 0 } ?: panelWidthPx()
        val height = root?.height?.takeIf { it > 0 } ?: dp(72)
        val bounds = screenBounds()
        val margin = dp(8)
        lp.x = lp.x.coerceIn(margin, (bounds.width() - width - margin).coerceAtLeast(margin))
        lp.y = lp.y.coerceIn(margin, (bounds.height() - height - margin).coerceAtLeast(margin))
    }

    private fun screenBounds() = windowManager.currentWindowMetrics.bounds

    private fun panelWidthPx(): Int = minOf(
        dp(PANEL_WIDTH_DP),
        (screenBounds().width() - dp(16)).coerceAtLeast(dp(280)),
    )

    private fun pointInside(left: Int, top: Int, width: Int, height: Int, x: Int, y: Int): Boolean =
        x >= left && x <= left + width && y >= top && y <= top + height

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun applyStatus(status: String) {
        val value = status.ifBlank { "Ready" }
        statusView?.text = value
        statusDot?.background = dotDrawable(statusColor(value))
    }

    private fun statusColor(status: String): Int {
        return when (overlayTone(status)) {
            OverlayTone.STOPPING -> Color.parseColor("#F2A65A")
            OverlayTone.DONE -> Color.parseColor("#6EDC9A")
            OverlayTone.ERROR -> Color.parseColor("#FF7188")
            OverlayTone.CONTROLLING -> Color.parseColor("#B8C3FF")
            OverlayTone.ACTIVE -> Color.parseColor("#9AA9FF")
        }
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

    /** Translucent gradient fallback that reads like glass on API 30+. */
    private fun glassBackground(dark: Boolean): GradientDrawable {
        val colors = if (dark) {
            intArrayOf(
                Color.argb(232, 43, 47, 66),
                Color.argb(208, 22, 25, 37),
            )
        } else {
            intArrayOf(
                Color.argb(244, 250, 251, 255),
                Color.argb(226, 227, 231, 242),
            )
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = dpF(24f)
            setStroke(
                dp(1),
                if (dark) Color.argb(78, 255, 255, 255) else Color.argb(70, 25, 28, 40),
            )
        }
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

    private companion object {
        const val PANEL_WIDTH_DP = 348
        const val FINISH_DISPLAY_MS = 350L
    }

    private class DragHandleView(context: Context, dark: Boolean) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) Color.argb(190, 224, 228, 244) else Color.argb(170, 75, 80, 95)
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            val spacing = dp(6).toFloat()
            val radius = dp(1.5f)
            for (row in -1..1) {
                val y = centerY + row * spacing
                canvas.drawCircle(centerX - spacing / 2f, y, radius, paint)
                canvas.drawCircle(centerX + spacing / 2f, y, radius, paint)
            }
        }

        private fun dp(value: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()

        private fun dp(value: Float): Float = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics,
        )
    }
}
