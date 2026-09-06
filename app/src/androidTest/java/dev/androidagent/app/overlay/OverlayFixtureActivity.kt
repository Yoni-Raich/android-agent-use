package dev.androidagent.app.overlay

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

/** Synthetic, non-private surface used for the overlay visual check. */
class OverlayFixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val surface = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(22, 25, 35))
        }
        val label = TextView(this).apply {
            text = "Synthetic overlay test surface"
            setTextColor(Color.rgb(236, 239, 248))
            textSize = 20f
            gravity = Gravity.CENTER
            contentDescription = "Synthetic overlay test surface"
        }
        surface.addView(label, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        setContentView(surface)
    }
}
