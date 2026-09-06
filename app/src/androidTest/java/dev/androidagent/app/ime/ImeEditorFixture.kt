package dev.androidagent.app.ime

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText

/** Only in the test APK: a real editor in a separate app process. */
class ImeEditorFixture : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editor = EditText(this).apply {
            id = android.R.id.edit
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "IME integration fixture"
        }
        setContentView(editor)
        editor.requestFocus()
    }
}
