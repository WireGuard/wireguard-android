/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity

import android.content.ClipDescription.compareMimeTypes
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Typeface.BOLD
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.CircularArray
import androidx.core.app.ShareCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.wireguard.android.BuildConfig
import com.wireguard.android.R
import com.wireguard.android.databinding.LogViewerActivityBinding
import com.wireguard.android.util.DownloadsFileSaver
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.util.resolveAttribute
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

class LogViewerActivity : AppCompatActivity() {
    private lateinit var binding: LogViewerActivityBinding
    private lateinit var logAdapter: LogEntryAdapter
    private var logLines = CircularArray<LogLine>()
    private var rawLogLines = CircularArray<String>()
    private var recyclerView: RecyclerView? = null
    private var saveButton: MenuItem? = null
    private val year by lazy {
        val yearFormatter: DateFormat = SimpleDateFormat("yyyy", Locale.US)
        yearFormatter.format(Date())
    }

    private val defaultColor by lazy { resolveAttribute(com.google.android.material.R.attr.colorOnSurface) }

    private val debugColor by lazy { ResourcesCompat.getColor(resources, R.color.debug_tag_color, theme) }

    private val errorColor by lazy { ResourcesCompat.getColor(resources, R.color.error_tag_color, theme) }

    private val infoColor by lazy { ResourcesCompat.getColor(resources, R.color.info_tag_color, theme) }

    private val warningColor by lazy { ResourcesCompat.getColor(resources, R.color.warning_tag_color, theme) }

    private var lastUri: Uri? = null

    private fun revokeLastUri() {
        lastUri?.let {
            LOGS.remove(it.pathSegments.lastOrNull())
            revokeUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            lastUri = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LogViewerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        logAdapter = LogEntryAdapter()
        binding.recyclerView.apply {
            recyclerView = this
            layoutManager = LinearLayoutManager(context)
            adapter = logAdapter
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
        }

        lifecycleScope.launch(Dispatchers.IO) { streamingLog() }

        val revokeLastActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            revokeLastUri()
        }

        binding.shareFab.setOnClickListener {
            lifecycleScope.launch {
                revokeLastUri()
                val key = KeyPair().privateKey.toHex()
                LOGS[key] = rawLogBytes()
                lastUri = Uri.parse("content://${BuildConfig.APPLICATION_ID}.exported-log/$key")
                val shareIntent = ShareCompat.IntentBuilder(this@LogViewerActivity)
                    .setType("text/plain")
                    .setSubject(getString(R.string.log_export_subject))
                    .setStream(lastUri)
                    .setChooserTitle(R.string.log_export_title)
                    .createChooserIntent()
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                grantUriPermission("android", lastUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                revokeLastActivityResultLauncher.launch(shareIntent)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.log_viewer, menu)
        saveButton = menu.findItem(R.id.save_log)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            R.id.save_log -> {
                saveButton?.isEnabled = false
                lifecycleScope.launch { saveLog() }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private val downloadsFileSaver = DownloadsFileSaver(this)

    private suspend fun rawLogBytes(): ByteArray {
        val builder = StringBuilder()
        withContext(Dispatchers.IO) {
            for (i in 0 until rawLogLines.size()) {
                builder.append(rawLogLines[i])
                builder.append('\n')
            }
        }
        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun saveLog() {
        var exception: Throwable? = null
        var outputFile: DownloadsFileSaver.DownloadsFile? = null
        withContext(Dispatchers.IO) {
            try {
                outputFile = downloadsFileSaver.save("wireguard-log.txt", "text/plain", true)
                outputFile?.outputStream?.write(rawLogBytes())
            } catch (e: Throwable) {
                outputFile?.delete()
                exception = e
            }
        }
        saveButton?.isEnabled = true
        if (outputFile == null)
            return
        Snackbar.make(
            findViewById(android.R.id.content),
            if (exception == null) getString(R.string.log_export_success, outputFile.fileName)
            else getString(R.string.log_export_error, ErrorMessages[exception]),
            if (exception == null) Snackbar.LENGTH_SHORT else Snackbar.LENGTH_LONG
        )
            .setAnchorView(binding.shareFab)
            .show()
    }

    private suspend fun streamingLog() = withContext(Dispatchers.IO) {
        val builder = ProcessBuilder().command("logcat", "-b", "all", "-v", "threadtime", "*:V")
        builder.environment()["LC_ALL"] = "C"
        var process: Process? = null
        try {
            process = try {
                builder.start()
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
                return@withContext
            }
            val stdout = BufferedReader(InputStreamReader(process!!.inputStream, StandardCharsets.UTF_8))

            var posStart = 0
            var timeLastNotify = System.nanoTime()
            var priorModified = false
            val bufferedLogLines = arrayListOf<LogLine>()
            var timeout = 1000000000L / 2 // The timeout is initially small so that the view gets populated immediately.
            val MAX_LINES = (1 shl 16) - 1
            val MAX_BUFFERED_LINES = (1 shl 14) - 1

            while (true) {
                val line = stdout.readLine() ?: break
                if (rawLogLines.size() >= MAX_LINES)
                    rawLogLines.popFirst()
                rawLogLines.addLast(line)
                val logLine = parseLine(line)
                if (logLine != null) {
                    bufferedLogLines.add(logLine)
                } else {
                    if (bufferedLogLines.isNotEmpty()) {
                        bufferedLogLines.last().msg += "\n$line"
                    } else if (!logLines.isEmpty()) {
                        logLines[logLines.size() - 1].msg += "\n$line"
                        priorModified = true
                    }
                }
                val timeNow = System.nanoTime()
                if (bufferedLogLines.size < MAX_BUFFERED_LINES && (timeNow - timeLastNotify) < timeout && stdout.ready())
                    continue
                timeout = 1000000000L * 5 / 2 // Increase the timeout after the initial view has something in it.
                timeLastNotify = timeNow

                withContext(Dispatchers.Main.immediate) {
                    val isScrolledToBottomAlready = recyclerView?.canScrollVertically(1) == false
                    if (priorModified) {
                        logAdapter.notifyItemChanged(posStart - 1)
                        priorModified = false
                    }
                    val fullLen = logLines.size() + bufferedLogLines.size
                    if (fullLen >= MAX_LINES) {
                        val numToRemove = fullLen - MAX_LINES + 1
                        logLines.removeFromStart(numToRemove)
                        logAdapter.notifyItemRangeRemoved(0, numToRemove)
                        posStart -= numToRemove

                    }
                    for (bufferedLine in bufferedLogLines) {
                        logLines.addLast(bufferedLine)
                    }
                    bufferedLogLines.clear()
                    logAdapter.notifyItemRangeInserted(posStart, logLines.size() - posStart)
                    posStart = logLines.size()

                    if (isScrolledToBottomAlready) {
                        recyclerView?.scrollToPosition(logLines.size() - 1)
                    }
                }
            }
        } finally {
            process?.destroy()
        }
    }

    private fun parseTime(timeStr: String): Date? {
        val formatter: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return try {
            formatter.parse("$year-$timeStr")
        } catch (e: ParseException) {
            null
        }
    }

    private fun parseLine(line: String): LogLine? {
        val m: Matcher = THREADTIME_LINE.matcher(line)
        return if (m.matches()) {
            LogLine(m.group(2)!!.toInt(), m.group(3)!!.toInt(), parseTime(m.group(1)!!), m.group(4)!!, m.group(5)!!, m.group(6)!!)
        } else {
            null
        }
    }

    private data class LogLine(val pid: Int, val tid: Int, val time: Date?, val level: String, val tag: String, var msg: String)

    companion object {
        /**
         * Match a single line of `logcat -v threadtime`, such as:
         *
         * <pre>05-26 11:02:36.886 5689 5689 D AndroidRuntime: CheckJNI is OFF.</pre>
         */
        private val THREADTIME_LINE: Pattern =
            Pattern.compile("^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})(?:\\s+[0-9A-Za-z]+)?\\s+(\\d+)\\s+(\\d+)\\s+([A-Z])\\s+(.+?)\\s*: (.*)$")
        private val LOGS: MutableMap<String, ByteArray> = ConcurrentHashMap()
        private const val TAG = "WireGuard/LogViewerActivity"
    }

    private inner class LogEntryAdapter : RecyclerView.Adapter<LogEntryAdapter.ViewHolder>() {

        private inner class ViewHolder(val layout: View, var isSingleLine: Boolean = true) : RecyclerView.ViewHolder(layout)

        private fun levelToColor(level: String): Int {
            return when (level) {
                "V", "D" -> debugColor
                "E" -> errorColor
                "I" -> infoColor
                "W" -> warningColor
                else -> defaultColor
            }
        }

        override fun getItemCount() = logLines.size()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.log_viewer_entry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val line = logLines[position]
            val spannable = if (position > 0 && logLines[position - 1].tag == line.tag)
                SpannableString(line.msg)
            else
                SpannableString("${line.tag}: ${line.msg}").apply {
                    setSpan(StyleSpan(BOLD), 0, "${line.tag}:".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(
                        ForegroundColorSpan(levelToColor(line.level)),
                        0, "${line.tag}:".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            holder.layout.apply {
                findViewById<MaterialTextView>(R.id.log_date).text = line.time.toString()
                findViewById<MaterialTextView>(R.id.log_msg).apply {
                    setSingleLine()
                    text = spannable
                    setOnClickListener {
                        isSingleLine = !holder.isSingleLine
                        holder.isSingleLine = !holder.isSingleLine
                    }
                }
            }
        }
    }

    class ExportedLogContentProvider : ContentProvider() {
        private fun logForUri(uri: Uri): ByteArray? = LOGS[uri.pathSegments.lastOrNull()]

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
            logForUri(uri)?.let {
                val m = MatrixCursor(arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), 1)
                m.addRow(arrayOf<Any>("wireguard-log.txt", it.size.toLong()))
                m
            }

        override fun onCreate(): Boolean = true

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun getType(uri: Uri): String? = logForUri(uri)?.let { "text/plain" }

        override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? =
            getType(uri)?.let { if (compareMimeTypes(it, mimeTypeFilter)) arrayOf(it) else null }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
            if (mode != "r") return null
            val log = logForUri(uri) ?: return null
            return openPipeHelper(uri, "text/plain", null, log) { output, _, _, _, l ->
                try {
                    FileOutputStream(output.fileDescriptor).write(l!!)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
