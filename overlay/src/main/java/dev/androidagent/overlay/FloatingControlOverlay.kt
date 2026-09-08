package dev.androidagent.overlay

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
 * The floating card is the only touchable window, so other apps keep
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
    private var collapsed = false
    private var expandedCard: View? = null
    private var bubble: TextView? = null
    private var runActive = false
    private var currentStatus = "Ready"
    private var imeBottomInsetPx = 0

    override suspend fun show(status: String) {
        requireOverlayPermission()
        withContext(Dispatchers.Main.immediate) {
            // Check again on the UI thread immediately before addView. This
            // keeps permission denial ahead of any visible/device action.
            requireOverlayPermission()
            currentStatus = status.ifBlank { "Ready" }
            if (!runActive) collapsed = false
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
            val hadControl = runActive
            runActive = false
            if (!hadControl) { removeViews(); return@runOnMain }
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

            val width = lp.width.takeIf { it > 0 } ?: control.width.takeIf { it > 0 } ?: panelWidthPx()
            val height = control.height.takeIf { it > 0 } ?: dp(140)
            val bounds = screenBounds()
            if (!pointInside(lp.x, lp.y, width, height, x, y)) return@runOnMain

            val margin = dp(12)
            val maxX = (bounds.width() - width - margin).coerceAtLeast(margin)
            val maxY = bottomLimit(bounds, height, margin)
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
        val accent = Color.parseColor(if (dark) "#5ED6C4" else "#0F766E")
        val onCard = Color.parseColor(if (dark) "#F2F8F6" else "#172526")
        val secondary = Color.parseColor(if (dark) "#A9C2BD" else "#4C6461")
        val hintColor = Color.parseColor(if (dark) "#9CB4AF" else "#6A7A77")
        val stopColor = Color.parseColor(if (dark) "#FF887D" else "#A83F37")
        val stopTint = Color.parseColor(if (dark) "#3A1714" else "#FFFFFF")

        val root = FrameLayout(appContext).apply {
            // The window owns the shadow halo. Keep padding stable so IME
            // insets never create an invisible, touch-blocking strip.
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipChildren = false
            clipToPadding = false
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        val cardBackground = glassBackground(dark)
        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = dpF(12f)
            isClickable = true
            isFocusable = false
        }

        val handle = DragHandleView(appContext, dark).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(48))
            contentDescription = "Drag agent controls"
            isClickable = true
            isFocusable = false
        }
        val dot = View(appContext).apply {
            background = dotDrawable(accent)
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { setMargins(0, 0, dp(8), 0) }
            isClickable = false
            isFocusable = false
        }
        statusDot = dot
        val title = TextView(appContext).apply {
            text = "Android Agent"
            setTextColor(onCard)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            isClickable = false
            isFocusable = false
        }
        val statusLabel = TextView(appContext).apply {
            text = status.ifBlank { "Ready" }
            setTextColor(secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            isClickable = false
            isFocusable = false
        }
        this.statusView = statusLabel
        val identity = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, dp(8), 0)
            isClickable = false
            isFocusable = false
        }
        val titleRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = false
            isFocusable = false
        }
        titleRow.addView(dot)
        titleRow.addView(title)
        identity.addView(titleRow)
        identity.addView(
            statusLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(3) },
        )

        val header = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48),
            )
        }

        val openButton = iconActionButton(
            description = "Open Android Agent",
            icon = ActionIcon.OPEN,
            tint = accent,
            fill = if (dark) Color.argb(42, 94, 214, 196) else Color.argb(24, 15, 118, 110),
            stroke = if (dark) Color.argb(120, 94, 214, 196) else Color.argb(90, 15, 118, 110),
        ) {
            // Release focus before handing control back to the app window.
            disableInputFocus()
            hideKeyboard()
            onOpenApp()
        }
        openButton.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            marginStart = dp(4)
        }

        val stopButton = labeledActionButton(
            label = "Stop",
            description = "Stop run",
            icon = ActionIcon.STOP,
            tint = stopTint,
            fill = stopColor,
            stroke = if (dark) Color.argb(170, 255, 174, 164) else Color.argb(120, 125, 34, 29),
        ) {
            // Release any IME focus before the immediate local stop callback.
            disableInputFocus()
            hideKeyboard()
            onStop()
        }
        stopButton.layoutParams = LinearLayout.LayoutParams(dp(78), dp(48)).apply {
            marginStart = dp(8)
        }

        header.addView(handle)
        header.addView(identity)
        header.addView(openButton)
        header.addView(stopButton)

        val input = EditText(appContext).apply {
            this.hint = "Steer or reply"
            setHintTextColor(hintColor)
            setTextColor(onCard)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            maxLines = 1
            isSingleLine = true
            isFocusable = true
            isFocusableInTouchMode = true
            background = roundedBackground(
                fill = if (dark) Color.argb(48, 255, 255, 255) else Color.argb(23, 15, 118, 110),
                stroke = if (dark) Color.argb(90, 145, 211, 200) else Color.argb(58, 15, 118, 110),
                radius = 14f,
            )
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(48),
                1f,
            )
            contentDescription = "Steer or reply"
            setOnClickListener { enableInputFocus() }
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
        val sendButton = iconActionButton(
            description = "Send message",
            icon = ActionIcon.SEND,
            tint = if (dark) Color.parseColor("#0E2825") else Color.WHITE,
            fill = accent,
            stroke = if (dark) Color.argb(180, 130, 238, 220) else Color.argb(125, 8, 88, 81),
        ) {
            sendFromInput()
        }
        sendButton.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            marginStart = dp(8)
        }

        val composer = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48),
            ).apply { topMargin = dp(12) }
        }
        val minimize = TextView(appContext).apply {
            text = "−"; textSize = 26f; gravity = Gravity.CENTER
            setTextColor(onCard); contentDescription = "Minimize agent controls"
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            background = actionRipple(accent)
            setOnClickListener { setCollapsed(true) }
        }
        composer.addView(minimize)
        composer.addView(input)
        composer.addView(sendButton)

        card.addView(header)
        card.addView(composer)
        root.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        expandedCard = card
        val compact = TextView(appContext).apply {
            text = "A"; textSize = 22f; gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(onCard)
            background = roundedBackground(if (dark) Color.parseColor("#172B29") else Color.parseColor("#E1F5EE"), statusColor(status), 32f)
            contentDescription = "Expand agent controls · $status"
            isClickable = true
            setOnClickListener { setCollapsed(false) }
            setOnLongClickListener { onStop(); true }
        }
        bubble = compact
        root.addView(compact, FrameLayout.LayoutParams(dp(56), dp(56)))
        attachDrag(compact)
        card.visibility = if (collapsed) View.GONE else View.VISIBLE
        compact.visibility = if (collapsed) View.VISIBLE else View.GONE
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomInset = maxOf(ime.bottom, bars.bottom)
            // Keep the card's measured size stable and use the inset only as
            // a positioning bound while the keyboard is visible.
            if (imeBottomInsetPx != bottomInset) {
                imeBottomInsetPx = bottomInset
                runOnMain { resizeControlWindow() }
            }
            insets
        }

        attachDrag(handle)
        controlRoot = root
        inputFocusEnabled = false
        captureHidden = false

        controlParams = WindowManager.LayoutParams(
            if (collapsed) dp(64) else panelWidthPx(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // x is an absolute screen coordinate, also on Hebrew/RTL devices.
            gravity = Gravity.TOP or Gravity.LEFT
            x = dp(12)
            y = dp(112)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            token = null
            this.title = "AndroidAgentControl"
        }
    }

    private fun iconActionButton(
        description: String,
        icon: ActionIcon,
        tint: Int,
        fill: Int,
        stroke: Int,
        action: () -> Unit,
    ): FrameLayout = FrameLayout(appContext).apply {
        this.contentDescription = description
        isClickable = true
        isFocusable = true
        background = roundedBackground(
            fill = fill,
            stroke = stroke,
            radius = 14f,
        )
        foreground = actionRipple(tint)
        addView(
            ActionIconView(appContext, icon, tint),
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER),
        )
        setOnClickListener { action() }
    }

    private fun labeledActionButton(
        label: String,
        description: String,
        icon: ActionIcon,
        tint: Int,
        fill: Int,
        stroke: Int,
        action: () -> Unit,
    ): FrameLayout = FrameLayout(appContext).apply {
        contentDescription = description
        isClickable = true
        isFocusable = true
        background = roundedBackground(fill, stroke, 14f)
        foreground = actionRipple(tint)
        val content = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        content.addView(
            ActionIconView(appContext, icon, tint),
            LinearLayout.LayoutParams(dp(18), dp(18)),
        )
        content.addView(
            TextView(appContext).apply {
                text = label
                setTextColor(tint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                includeFontPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(5)
            },
        )
        addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
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
                    if (!moved && event.actionMasked == MotionEvent.ACTION_UP) header.performClick()
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
                runOnMain { resizeControlWindow() }
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
        expandedCard = null
        bubble = null
        statusView = null
        statusDot = null
        inputView = null
        controlParams = null
        imeBottomInsetPx = 0
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
        val width = lp.width.takeIf { it > 0 } ?: root?.width?.takeIf { it > 0 } ?: panelWidthPx()
        val height = root?.height?.takeIf { it > 0 } ?: dp(140)
        val bounds = screenBounds()
        val margin = dp(8)
        lp.x = lp.x.coerceIn(margin, (bounds.width() - width - margin).coerceAtLeast(margin))
        lp.y = lp.y.coerceIn(margin, bottomLimit(bounds, height, margin))
    }

    private fun screenBounds() = windowManager.currentWindowMetrics.bounds

    private fun panelWidthPx(): Int = minOf(
        dp(PANEL_WIDTH_DP),
        (screenBounds().width() - dp(16)).coerceAtLeast(dp(1)),
    )

    private fun bottomLimit(bounds: android.graphics.Rect, height: Int, margin: Int): Int =
        (bounds.height() - imeBottomInsetPx - height - margin).coerceAtLeast(margin)

    private fun setCollapsed(value: Boolean) {
        disableInputFocus()
        hideKeyboard()
        collapsed = value
        expandedCard?.visibility = if (value) View.GONE else View.VISIBLE
        bubble?.visibility = if (value) View.VISIBLE else View.GONE
        resizeControlWindow()
        controlRoot?.post { clampPosition() }
    }

    private fun resizeControlWindow() {
        val lp = controlParams ?: return
        lp.width = if (collapsed) dp(64) else panelWidthPx()
        clampPosition(lp)
        updateControlLayout(controlRoot, lp)
    }

    private fun pointInside(left: Int, top: Int, width: Int, height: Int, x: Int, y: Int): Boolean =
        x >= left && x <= left + width && y >= top && y <= top + height

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun applyStatus(status: String) {
        val value = status.ifBlank { "Ready" }
        statusView?.text = value
        statusDot?.background = dotDrawable(statusColor(value))
        bubble?.apply {
            contentDescription = "Expand agent controls · $value"
            background = roundedBackground(if (isDark()) Color.parseColor("#172B29") else Color.parseColor("#E1F5EE"), statusColor(value), 32f)
        }
    }

    private fun statusColor(status: String): Int {
        return when (overlayTone(status)) {
            OverlayTone.STOPPING -> Color.parseColor(if (isDark()) "#F6B86A" else "#9A5707")
            OverlayTone.DONE -> Color.parseColor(if (isDark()) "#78E2B4" else "#08744D")
            OverlayTone.ERROR -> Color.parseColor(if (isDark()) "#FF9A8F" else "#B3261E")
            OverlayTone.CONTROLLING -> Color.parseColor(if (isDark()) "#66D8C6" else "#0B6B63")
            OverlayTone.ACTIVE -> Color.parseColor(if (isDark()) "#A3E8DC" else "#116A64")
        }
    }

    // ---------- small visual helpers ----------

    private fun actionRipple(tint: Int) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(45, Color.red(tint), Color.green(tint), Color.blue(tint))),
        null,
        roundedBackground(Color.WHITE, Color.TRANSPARENT, 14f),
    )

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
                Color.argb(246, 31, 48, 49),
                Color.argb(242, 16, 27, 29),
            )
        } else {
            intArrayOf(
                Color.argb(248, 248, 252, 251),
                Color.argb(242, 226, 239, 236),
            )
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = dpF(22f)
            setStroke(
                dp(1),
                if (dark) Color.argb(110, 104, 207, 190) else Color.argb(75, 15, 94, 88),
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
        const val PANEL_WIDTH_DP = 360
        const val FINISH_DISPLAY_MS = 350L
    }

    private enum class ActionIcon {
        SEND,
        OPEN,
        STOP,
    }

    private class ActionIconView(
        context: Context,
        private val icon: ActionIcon,
        private val tint: Int,
    ) : View(context) {
        private val iconPath = Path()
        private val iconBounds = RectF()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            style = Paint.Style.FILL
            strokeWidth = dp(1.8f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = dp(2f)
            val right = width.toFloat() - inset
            val bottom = height.toFloat() - inset
            when (icon) {
                ActionIcon.SEND -> {
                    val plane = iconPath.apply {
                        reset()
                        moveTo(inset, height / 2f)
                        lineTo(right, inset)
                        lineTo(right - dp(4f), bottom)
                        lineTo(width / 2f, height / 2f + dp(2f))
                        close()
                    }
                    canvas.drawPath(plane, paint)
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(width / 2f, height / 2f + dp(2f), right, inset, paint)
                    paint.style = Paint.Style.FILL
                }
                ActionIcon.OPEN -> {
                    paint.style = Paint.Style.STROKE
                    val corner = iconPath.apply {
                        reset()
                        moveTo(dp(9f), inset)
                        lineTo(inset, inset)
                        lineTo(inset, bottom)
                        lineTo(right, bottom)
                        lineTo(right, dp(13f))
                    }
                    canvas.drawPath(corner, paint)
                    canvas.drawLine(dp(10f), dp(12f), right, inset, paint)
                    canvas.drawLine(dp(13f), inset, right, inset, paint)
                    canvas.drawLine(right, inset, right, dp(9f), paint)
                    paint.style = Paint.Style.FILL
                }
                ActionIcon.STOP -> {
                    iconBounds.set(dp(4f), dp(4f), width - dp(4f), height - dp(4f))
                    canvas.drawRoundRect(
                        iconBounds,
                        dp(3f),
                        dp(3f),
                        paint,
                    )
                }
            }
        }

        private fun dp(value: Float): Float = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics,
        )
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
