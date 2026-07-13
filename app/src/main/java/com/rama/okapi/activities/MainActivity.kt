package com.rama.okapi.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import androidx.activity.OnBackPressedCallback
import com.rama.okapi.CsActivity
import com.rama.okapi.DatabaseHelper
import com.rama.okapi.Message
import com.rama.okapi.R
import com.rama.okapi.managers.PrefsManager
import com.rama.bohio.R as BohioR
import android.view.GestureDetector
import android.view.MotionEvent
import com.rama.bohio.managers.ThemeManager
import com.rama.bohio.objects.PrefKeys
import com.rama.bohio.util.Dimens.spToPx

private enum class Mode { LIST, EDIT }

class MainActivity : CsActivity() {

    private lateinit var db: DatabaseHelper

    private lateinit var listView: android.widget.LinearLayout
    private lateinit var editView: EditText
    private lateinit var messagesList: ListView
    private lateinit var emptyLabel: View
    private lateinit var themeIcon: ImageView
    private lateinit var listBtn: FrameLayout
    private lateinit var deleteBtn: FrameLayout
    private lateinit var saveBtn: FrameLayout
    private lateinit var editBtn: FrameLayout
    private lateinit var previewBtn: FrameLayout

    private var mode = Mode.LIST
    private var editingId: Long? = null
    private var sortMode = false

    private val minTextSizeSp = 50f
    private val maxTextSizeSp = 300f
    private val resizeHandler = Handler(Looper.getMainLooper())
    private var pendingResize: Runnable? = null
    private val resizeDebounceMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_main)
        db = DatabaseHelper(this)

        val root = findViewById<View>(R.id.root)
        applyEdgeToEdgePadding(root)
        applyCurrentTheme(root)

        listView = findViewById(R.id.list_view)
        editView = findViewById(R.id.edit_view)
        messagesList = findViewById(R.id.messages_list)
        emptyLabel = findViewById(R.id.empty_label)
        themeIcon = findViewById(R.id.theme_icon)
        listBtn = findViewById(R.id.list_btn)
        deleteBtn = findViewById(R.id.delete_btn)
        saveBtn = findViewById(R.id.save_btn)
        editBtn = findViewById(R.id.edit_btn)
        previewBtn = findViewById(R.id.preview_btn)

        updateThemeIcon()

        findViewById<FrameLayout>(R.id.open_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.theme_btn).setOnClickListener {
            prefs.toggleThemeMode()
            updateThemeIcon()
            applyCurrentTheme(root)
        }

        editBtn.setOnClickListener {
            sortMode = !sortMode
            refreshList()
        }

        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (prefs.getBoolean(PrefsManager.FileKeys.PREF_QUICK_ERASE, true)) {
                        deleteCurrent()
                    }
                    return true
                }
            }
        )

        editView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        editView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) {
                    pendingResize?.let { resizeHandler.removeCallbacks(it) }
                    editView.setTextSize(TypedValue.COMPLEX_UNIT_SP, minTextSizeSp)
                } else {
                    scheduleResize()
                }
            }
        })

        listBtn.setOnClickListener { showList() }

        deleteBtn.setOnClickListener { deleteCurrent() }

        saveBtn.setOnClickListener { saveCurrent() }

        findViewById<android.widget.Button>(R.id.add_btn).setOnClickListener { openEdit(null) }

        messagesList.setOnItemClickListener { _, _, position, _ ->
            (messagesList.adapter as? MessageAdapter)?.getItem(position)?.let { openEdit(it) }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mode == Mode.EDIT) {
                    showList()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        showList()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean(
                PrefKeys.SYSTEM_PREVENT_SLEEP,
                false
            )
        ) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        updateThemeIcon()
    }

    private fun showList() {
        pendingResize?.let { resizeHandler.removeCallbacks(it) }
        mode = Mode.LIST
        editingId = null
        listView.visibility = View.VISIBLE
        editView.visibility = View.GONE
        listBtn.visibility = View.GONE
        deleteBtn.visibility = View.GONE
        saveBtn.visibility = View.GONE
        editBtn.visibility = View.VISIBLE
        previewBtn.visibility = View.GONE

        hideKeyboard()
        refreshList()
    }

    private fun openEdit(message: Message?) {
        mode = Mode.EDIT
        editingId = message?.id
        editView.setText(message?.text.orEmpty())
        editView.setSelection(editView.text.length)
        listView.visibility = View.GONE
        editView.visibility = View.VISIBLE
        listBtn.visibility = View.VISIBLE
        deleteBtn.visibility = View.VISIBLE
        saveBtn.visibility = View.VISIBLE
        editBtn.visibility = View.GONE
        previewBtn.visibility = View.VISIBLE

        editView.requestFocus()
        showKeyboard()
        editView.post { resizeTextToFit() }
    }

    // List

    private fun refreshList() {
        val messages = db.getAll()
        messagesList.adapter = MessageAdapter(
            this,
            messages,
            sortMode,
            onMoveUp = { id -> db.moveUp(id); refreshList() },
            onMoveDown = { id -> db.moveDown(id); refreshList() },
        )
        emptyLabel.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
        messagesList.visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun saveCurrent() {
        val text = editView.text.toString()
        if (text.isBlank()) {
            editingId?.let { db.delete(it) }
        } else {
            db.save(editingId, text)
        }
        showList()
    }

    private fun deleteCurrent() {
        editingId?.let { db.delete(it) }
        editingId = null
        editView.setText("")
        resizeTextToFit()
    }

    // Text sizing - fills the available width/height without going below minTextSizeSp.
    private fun scheduleResize() {
        pendingResize?.let { resizeHandler.removeCallbacks(it) }
        val runnable = Runnable { resizeTextToFit() }
        pendingResize = runnable
        resizeHandler.postDelayed(runnable, resizeDebounceMs)
    }

    private fun resizeTextToFit() {
        val text = editView.text?.toString().orEmpty()
        val availableWidth = editView.width - editView.paddingLeft - editView.paddingRight
        val availableHeight = editView.height - editView.paddingTop - editView.paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return

        if (text.isEmpty()) {
            editView.setTextSize(TypedValue.COMPLEX_UNIT_SP, minTextSizeSp)
            return
        }

        val paint = TextPaint(editView.paint)

        fun hasBrokenWord(layout: StaticLayout, text: String): Boolean {
            for (i in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(i)

                // Ignore explicit newlines
                if (end >= text.length) continue
                if (text[end - 1] == '\n') continue

                val prev = text[end - 1]
                val next = text[end]

                if (!prev.isWhitespace() && !next.isWhitespace()) {
                    return true
                }
            }
            return false
        }

        @Suppress("DEPRECATION")
        fun fits(sizeSp: Float): Boolean {
            paint.textSize = spToPx(this, sizeSp).toFloat()

            val layout = StaticLayout(
                text,
                paint,
                availableWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1f,
                0f,
                false
            )

            return layout.height <= availableHeight &&
                    !hasBrokenWord(layout, text)
        }

        if (!fits(minTextSizeSp)) {
            editView.setTextSize(TypedValue.COMPLEX_UNIT_SP, minTextSizeSp)
            return
        }

        var lo = minTextSizeSp
        var hi = maxTextSizeSp
        var best = lo
        while (hi - lo > 1f) {
            val mid = (lo + hi) / 2f
            if (fits(mid)) {
                best = mid
                lo = mid
            } else {
                hi = mid
            }
        }
        editView.setTextSize(TypedValue.COMPLEX_UNIT_SP, best)
    }

    // Theme

    private fun updateThemeIcon() {
        themeIcon.setImageResource(
            if (prefs.isLightMode()) BohioR.drawable.px_moon else BohioR.drawable.px_sun
        )
    }

    // Keyboard

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editView.windowToken, 0)
    }
}

private class MessageAdapter(
    context: android.content.Context,
    messages: List<Message>,
    private val sortMode: Boolean,
    private val onMoveUp: (Long) -> Unit,
    private val onMoveDown: (Long) -> Unit,
) : ArrayAdapter<Message>(context, 0, messages) {

    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
        val view = convertView
            ?: android.view.LayoutInflater.from(context)
                .inflate(R.layout.row_message, parent, false)
        val message = getItem(position) ?: return view

        view.findViewById<android.widget.TextView>(R.id.row_text).text = message.text

        val ascendBtn = view.findViewById<View>(R.id.ascend_button)
        val descendBtn = view.findViewById<View>(R.id.descend_button)

        ascendBtn.visibility = if (sortMode) View.VISIBLE else View.GONE
        descendBtn.visibility = if (sortMode) View.VISIBLE else View.GONE
        ascendBtn.isEnabled = sortMode && position > 0
        descendBtn.isEnabled = sortMode && position < count - 1
        ascendBtn.alpha = if (ascendBtn.isEnabled) 1f else 0.3f
        descendBtn.alpha = if (descendBtn.isEnabled) 1f else 0.3f

        ascendBtn.setOnClickListener { if (ascendBtn.isEnabled) onMoveUp(message.id) }
        descendBtn.setOnClickListener { if (descendBtn.isEnabled) onMoveDown(message.id) }

        ThemeManager.applyTheme(context, view)
        return view
    }
}