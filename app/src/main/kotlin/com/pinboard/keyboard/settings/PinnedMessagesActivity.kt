package com.pinboard.keyboard.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.pinboard.keyboard.R
import com.pinboard.keyboard.data.AppDatabase
import com.pinboard.keyboard.data.PinnedMessage
import com.pinboard.keyboard.ui.PinnedAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PinnedMessagesActivity : AppCompatActivity() {

    private lateinit var dao: com.pinboard.keyboard.data.PinnedMessageDao
    private lateinit var adapter: PinnedAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pinned_messages)
        dao = AppDatabase.getInstance(this).pinnedMessageDao()

        recycler = findViewById(R.id.recycler)
        emptyState = findViewById(R.id.emptyState)
        val searchBox = findViewById<EditText>(R.id.searchBox)
        val fab = findViewById<FloatingActionButton>(R.id.fabAdd)

        adapter = PinnedAdapter(
            onInsert = { /* no-op here; insertion only happens from the keyboard */ },
            onEdit = { msg -> showEditDialog(msg, searchBox) },
            onDelete = { msg ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { dao.delete(msg) }
                    reload(searchBox.text?.toString())
                }
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fab.setOnClickListener { showEditDialog(null, searchBox) }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { reload(s?.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        reload(null)
    }

    private fun reload(query: String?) {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                val all = dao.getAllOnce()
                if (query.isNullOrBlank()) all else all.filter { it.text.contains(query, ignoreCase = true) }
            }
            adapter.submitList(list)
            emptyState.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun showEditDialog(message: PinnedMessage?, searchBox: EditText) {
        val input = EditText(this).apply {
            setText(message?.text.orEmpty())
            setPadding(48, 32, 48, 32)
            hint = getString(R.string.hint_message)
            minLines = 2
            maxLines = 6
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (message == null) R.string.add_pinned_message else R.string.edit_pinned_message)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            if (message != null) dao.update(message.copy(text = text))
                            else dao.insert(PinnedMessage(text = text))
                        }
                        reload(searchBox.text?.toString())
                    }
                }
            }
            .show()
    }
}
