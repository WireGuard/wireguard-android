/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
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
import com.wireguard.android.widget.EdgeToEdge.setUpFAB
import com.wireguard.android.widget.EdgeToEdge.setUpRoot
import com.wireguard.android.widget.EdgeToEdge.setUpScrollingContent
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
    private var logLines = arrayListOf<LogLine>()
    private var rawLogLines = StringBuffer()
    private var recyclerView: RecyclerView? = null
    private var saveButton: MenuItem? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val year by lazy {
        val yearFormatter: DateFormat = SimpleDateFormat("yyyy", Locale.US)
        yearFormatter.format(Date())
    }

    @Suppress("Deprecation")
    private val defaultColor by lazy { resources.getColor(R.color.primary_text_color) }

    @Suppress("Deprecation")
    private val debugColor by lazy { resources.getColor(R.color.debug_tag_color) }

    @Suppress("Deprecation")
    private val errorColor by lazy { resources.getColor(R.color.error_tag_color) }

    @Suppress("Deprecation")
    private val infoColor by lazy { resources.getColor(R.color.info_tag_color) }

    @Suppress("Deprecation")
    private val warningColor by lazy { resources.getColor(R.color.warning_tag_color) }

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
        setUpFAB(binding.shareFab)
        setUpRoot(binding.root)
        setUpScrollingContent(binding.recyclerView, binding.shareFab)
        logAdapter = LogEntryAdapter()
        binding.recyclerView.apply {
            recyclerView = this
            layoutManager = LinearLayoutManager(context)
            adapter = logAdapter
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
        }

        coroutineScope.launch { streamingLog() }

        binding.shareFab.setOnClickListener {
            revokeLastUri()
            val key = KeyPair().privateKey.toHex()
            LOGS[key] = rawLogLines.toString().toByteArray(Charsets.UTF_8)
            lastUri = Uri.parse("content://${BuildConfig.APPLICATION_ID}.exported-log/$key")
            val shareIntent = ShareCompat.IntentBuilder.from(this)
                    .setType("text/plain")
                    .setSubject(getString(R.string.log_export_subject))
                    .setStream(lastUri)
                    .setChooserTitle(R.string.log_export_title)
                    .createChooserIntent()
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            grantUriPermission("android", lastUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(shareIntent, SHARE_ACTIVITY_REQUEST)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SHARE_ACTIVITY_REQUEST) {
            revokeLastUri()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.log_viewer, menu)
        saveButton = menu?.findItem(R.id.save_log)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.save_log -> {
                coroutineScope.launch { saveLog() }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    private suspend fun saveLog() {
        val context = this
        withContext(Dispatchers.Main) {
            saveButton?.isEnabled = false
            withContext(Dispatchers.IO) {
                var exception: Throwable? = null
                var outputFile: DownloadsFileSaver.DownloadsFile? = null
                try {
                    outputFile = DownloadsFileSaver.save(context, "wireguard-log.txt", "text/plain", true)
                    outputFile.outputStream.use {
                        it.write(rawLogLines.toString().toByteArray(Charsets.UTF_8))
                    }
                } catch (e: Throwable) {
                    outputFile?.delete()
                    exception = e
                }
                withContext(Dispatchers.Main) {
                    saveButton?.isEnabled = true
                    Snackbar.make(findViewById(android.R.id.content),
                            if (exception == null) getString(R.string.log_export_success, outputFile?.fileName)
                            else getString(R.string.log_export_error, ErrorMessages[exception]),
                            if (exception == null) Snackbar.LENGTH_SHORT else Snackbar.LENGTH_LONG)
                            .setAnchorView(binding.shareFab)
                            .show()
                }
            }
        }
    }

    private suspend fun streamingLog() = withContext(Dispatchers.IO) {
        val builder = ProcessBuilder().command("logcat", "-b", "all", "-v", "threadtime", "*:V")
        builder.environment()["LC_ALL"] = "C"
        val process = try {
            builder.start()
        } catch (e: IOException) {
            e.printStackTrace()
            return@withContext
        }
        val stdout = BufferedReader(InputStreamReader(process!!.inputStream, StandardCharsets.UTF_8))
        var haveScrolled = false
        val start = System.nanoTime()
        var startPeriod = start
        while (true) {
            val line = stdout.readLine() ?: break
            rawLogLines.append(line)
            rawLogLines.append('\n')
            val logLine = parseLine(line)
            withContext(Dispatchers.Main) {
                if (logLine != null) {
                    recyclerView?.let {
                        val shouldScroll = haveScrolled && !it.canScrollVertically(1)
                        logLines.add(logLine)
                        if (haveScrolled) logAdapter.notifyDataSetChanged()
                        if (shouldScroll)
                            it.scrollToPosition(logLines.size - 1)
                    }
                } else {
                    /* I'd prefer for the next line to be:
                     *    logLines.lastOrNull()?.msg += "\n$line"
                     * However, as of writing, that causes the kotlin compiler to freak out and crash, spewing bytecode.
                     */
                    logLines.lastOrNull()?.apply { msg += "\n$line" }
                    if (haveScrolled) logAdapter.notifyDataSetChanged()
                }
                if (!haveScrolled) {
                    val end = System.nanoTime()
                    val scroll = (end - start) > 1000000000L * 2.5 || !stdout.ready()
                    if (logLines.isNotEmpty() && (scroll || (end - startPeriod) > 1000000000L / 4)) {
                        logAdapter.notifyDataSetChanged()
                        recyclerView?.scrollToPosition(logLines.size - 1)
                        startPeriod = end
                    }
                    if (scroll) haveScrolled = true
                }
            }
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
        private val THREADTIME_LINE: Pattern = Pattern.compile("^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})(?:\\s+[0-9A-Za-z]+)?\\s+(\\d+)\\s+(\\d+)\\s+([A-Z])\\s+(.+?)\\s*: (.*)$")
        private val LOGS: MutableMap<String, ByteArray> = ConcurrentHashMap()
        private const val SHARE_ACTIVITY_REQUEST = 49133
    }

    private inner class LogEntryAdapter : RecyclerView.Adapter<LogEntryAdapter.ViewHolder>() {

        private inner class ViewHolder(val layout: View, var isSingleLine: Boolean = true) : RecyclerView.ViewHolder(layout)

        private fun levelToColor(level: String): Int {
            return when (level) {
                "D" -> debugColor
                "E" -> errorColor
                "I" -> infoColor
                "W" -> warningColor
                else -> defaultColor
            }
        }

        override fun getItemCount() = logLines.size

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
                    setSpan(ForegroundColorSpan(levelToColor(line.level)),
                            0, "${line.tag}:".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
                    m.addRow(arrayOf("wireguard-log.txt", it.size.toLong()))
                    m
                }

        override fun onCreate(): Boolean = true

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun getType(uri: Uri): String? = logForUri(uri)?.let { "text/plain" }

        override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? = getType(uri)?.let { if (compareMimeTypes(it, mimeTypeFilter)) arrayOf(it) else null }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
            if (mode != "r") return null
            val log = logForUri(uri) ?: return null
            return openPipeHelper(uri, "text/plain", null, log) { output, _, _, _, l ->
                try {
                    FileOutputStream(output.fileDescriptor).write(l!!)
                } catch (_: Exception) {
                }
            }
        }
    }
}
