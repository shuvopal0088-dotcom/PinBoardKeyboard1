package com.pinboard.keyboard.service

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pinboard.keyboard.R
import com.pinboard.keyboard.data.AppDatabase
import com.pinboard.keyboard.data.PinnedMessage
import com.pinboard.keyboard.data.SettingsRepository
import com.pinboard.keyboard.ui.EmojiAdapter
import com.pinboard.keyboard.ui.PinnedAdapter
import com.pinboard.keyboard.util.RandomGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PinBoardIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var keyboardView: KeyboardView
    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard

    private lateinit var panelContainer: FrameLayout
    private lateinit var btnPinned: TextView
    private lateinit var btnEmoji: TextView
    private lateinit var btnGenerator: TextView

    private var capsOn = false
    private var currentPanel: Panel = Panel.NONE

    private lateinit var settings: SettingsRepository
    private lateinit var dao: com.pinboard.keyboard.data.PinnedMessageDao

    private var editingMessage: PinnedMessage? = null

    private enum class Panel { NONE, PINNED, EMOJI }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        dao = AppDatabase.getInstance(this).pinnedMessageDao()
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_container, null) as LinearLayout

        keyboardView = root.findViewById(R.id.keyboardView)
        panelContainer = root.findViewById(R.id.panelContainer)
        btnPinned = root.findViewById(R.id.btnPinned)
        btnEmoji = root.findViewById(R.id.btnEmoji)
        btnGenerator = root.findViewById(R.id.btnGenerator)

        qwertyKeyboard = Keyboard(this, R.xml.qwerty)
        symbolsKeyboard = Keyboard(this, R.xml.symbols)

        keyboardView.keyboard = qwertyKeyboard
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false

        btnPinned.setOnClickListener { togglePanel(Panel.PINNED) }
        btnPinned.setOnLongClickListener {
            togglePanel(Panel.PINNED, forceOpen = true)
            showQuickAdd(null)
            true
        }
        btnEmoji.setOnClickListener { togglePanel(Panel.EMOJI) }
        btnGenerator.setOnClickListener { insertGeneratedText() }

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        capsOn = false
        keyboardView.keyboard = qwertyKeyboard
        closePanel()
    }

    // ---------- Toolbar / panel logic ----------

    private fun togglePanel(target: Panel, forceOpen: Boolean = false) {
        if (!forceOpen && currentPanel == target) {
            closePanel()
            return
        }
        currentPanel = target
        panelContainer.visibility = View.VISIBLE
        panelContainer.removeAllViews()

        when (target) {
            Panel.PINNED -> inflatePinnedPanel()
            Panel.EMOJI -> inflateEmojiPanel()
            Panel.NONE -> {}
        }
    }

    private fun closePanel() {
        currentPanel = Panel.NONE
        panelContainer.visibility = View.GONE
        panelContainer.removeAllViews()
    }

    private fun inflatePinnedPanel() {
        val view = layoutInflater.inflate(R.layout.panel_pinned, panelContainer, true)
        val recycler = view.findViewById<RecyclerView>(R.id.pinnedRecycler)
        val emptyState = view.findViewById<TextView>(R.id.emptyState)
        val searchBox = view.findViewById<EditText>(R.id.searchBox)
        val btnAdd = view.findViewById<TextView>(R.id.btnAddPinned)
        val quickAddRow = view.findViewById<LinearLayout>(R.id.quickAddRow)
        val quickAddText = view.findViewById<EditText>(R.id.quickAddText)
        val btnQuickSave = view.findViewById<TextView>(R.id.btnQuickSave)

        val adapter = PinnedAdapter(
            onInsert = { msg -> currentInputConnection?.commitText(msg.text, 1) },
            onEdit = { msg -> showQuickAdd(msg, quickAddRow, quickAddText) },
            onDelete = { msg ->
                scope.launch {
                    withContext(Dispatchers.IO) { dao.delete(msg) }
                    refreshPinnedList(recycler, emptyState, searchBox.text?.toString())
                }
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnAdd.setOnClickListener { showQuickAdd(null, quickAddRow, quickAddText) }
        btnQuickSave.setOnClickListener {
            val text = quickAddText.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val existing = editingMessage
                        if (existing != null) {
                            dao.update(existing.copy(text = text))
                        } else {
                            dao.insert(PinnedMessage(text = text))
                        }
                    }
                    quickAddText.setText("")
                    quickAddRow.visibility = View.GONE
                    editingMessage = null
                    refreshPinnedList(recycler, emptyState, searchBox.text?.toString())
                }
            }
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                refreshPinnedList(recycler, emptyState, s?.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        refreshPinnedList(recycler, emptyState, null)

        // Stash refs for the toolbar long-press "quick add" entry point
        pendingQuickAddRow = quickAddRow
        pendingQuickAddText = quickAddText
    }

    private var pendingQuickAddRow: LinearLayout? = null
    private var pendingQuickAddText: EditText? = null

    private fun showQuickAdd(
        message: PinnedMessage?,
        row: LinearLayout? = pendingQuickAddRow,
        editText: EditText? = pendingQuickAddText
    ) {
        editingMessage = message
        row?.visibility = View.VISIBLE
        editText?.setText(message?.text.orEmpty())
    }

    private fun refreshPinnedList(recycler: RecyclerView, emptyState: TextView, query: String?) {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                val all = dao.getAllOnce()
                if (query.isNullOrBlank()) all else all.filter { it.text.contains(query, ignoreCase = true) }
            }
            (recycler.adapter as? PinnedAdapter)?.submitList(list)
            emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun inflateEmojiPanel() {
        val view = layoutInflater.inflate(R.layout.panel_emoji, panelContainer, true)
        val recycler = view.findViewById<RecyclerView>(R.id.emojiRecycler)
        recycler.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 8)
        recycler.adapter = EmojiAdapter { emoji ->
            currentInputConnection?.commitText(emoji, 1)
        }
    }

    private fun insertGeneratedText() {
        val text = RandomGenerator.generate(settings.generatorLength)
        currentInputConnection?.commitText(text, 1)
        keyboardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // ---------- KeyboardView.OnKeyboardActionListener ----------

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        keyboardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_SHIFT -> {
                capsOn = !capsOn
                qwertyKeyboard.isShifted = capsOn
                keyboardView.invalidateAllKeys()
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                keyboardView.keyboard = if (keyboardView.keyboard == qwertyKeyboard) symbolsKeyboard else qwertyKeyboard
            }
            Keyboard.KEYCODE_DONE -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
            }
            32 -> ic.commitText(" ", 1)
            else -> {
                var code = primaryCode.toChar()
                if (capsOn) code = code.uppercaseChar()
                ic.commitText(code.toString(), 1)
                if (capsOn) {
                    // one-shot shift like most simple keyboards
                    capsOn = false
                    qwertyKeyboard.isShifted = false
                    keyboardView.invalidateAllKeys()
                }
            }
        }
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {
        currentInputConnection?.commitText(text, 1)
    }
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
