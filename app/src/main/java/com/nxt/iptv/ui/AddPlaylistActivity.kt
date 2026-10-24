package com.nxt.iptv.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.nxt.iptv.R
import com.nxt.iptv.data.Playlist
import com.nxt.iptv.data.PlaylistRepository
import com.nxt.iptv.data.Prefs
import java.io.File

class AddPlaylistActivity : BaseActivity() {
    private lateinit var repo: PlaylistRepository
    private lateinit var name: EditText
    private lateinit var url: EditText
    private lateinit var fileName: TextView
    private lateinit var status: TextView
    private lateinit var btnSave: View
    private var editing: Playlist? = null
    /** Chosen local file (content:// URI or absolute path); takes precedence over the URL field. */
    private var fileSource: String? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_playlist)
        repo = PlaylistRepository(this)
        name = findViewById(R.id.name)
        url = findViewById(R.id.url)
        fileName = findViewById(R.id.fileName)
        status = findViewById(R.id.status)
        btnSave = findViewById(R.id.btnSave)

        editing = intent.getStringExtra(EXTRA_ID)?.let { repo.get(it) }
        editing?.let { p ->
            findViewById<TextView>(R.id.title).setText(R.string.edit_playlist)
            name.setText(p.name)
            if (p.isRemote) url.setText(p.source) else setFile(p.source, displayName(p.source))
        }

        findViewById<View>(R.id.btnFile).setOnClickListener { chooseFile() }
        btnSave.setOnClickListener { save() }
        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        url.setOnEditorActionListener { _, _, _ -> btnSave.requestFocus(); false }
        // Typing a URL means the user no longer wants the previously chosen file.
        url.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus && url.text.isNotBlank()) setFile(null, null) }
        name.requestFocus()
    }

    private fun setFile(source: String?, label: String?) {
        fileSource = source
        fileName.text = label?.let { getString(R.string.file_selected, it) } ?: ""
        if (source != null) url.setText("")
    }

    private fun chooseFile() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        val hasPicker = picker.resolveActivity(packageManager) != null
        if (!hasPicker) {
            browseStorage()
            return
        }
        AlertDialog.Builder(this)
            .setItems(arrayOf(getString(R.string.browse_storage), getString(R.string.system_picker))) { _, which ->
                if (which == 0) browseStorage() else startActivityForResult(picker, REQ_PICK)
            }
            .show()
    }

    private fun browseStorage() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_PERMISSION)
            return
        }
        FileBrowser(this) { f ->
            setFile(f.absolutePath, f.name)
            if (name.text.isBlank()) name.setText(f.nameWithoutExtension)
        }.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) browseStorage()
        else Toast.makeText(this, R.string.storage_permission, Toast.LENGTH_LONG).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || uri == null) return
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        setFile(uri.toString(), displayName(uri.toString()))
        if (name.text.isBlank()) name.setText(displayName(uri.toString()).substringBeforeLast('.'))
    }

    private fun displayName(source: String): String {
        if (source.startsWith("content://")) {
            runCatching {
                contentResolver.query(Uri.parse(source), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) return it.getString(0) }
            }
        }
        return File(Uri.parse(source).path ?: source).name
    }

    private fun save() {
        if (busy) return
        val title = name.text.toString().trim()
        var source = fileSource ?: url.text.toString().trim()
        if (title.isEmpty()) {
            name.error = getString(R.string.error_name)
            name.requestFocus()
            return
        }
        if (source.isEmpty()) {
            status.text = getString(R.string.error_source)
            url.requestFocus()
            return
        }
        if (fileSource == null && !source.contains("://")) source = "http://$source"

        val old = editing
        val playlist = Playlist(
            id = old?.id ?: repo.newId(),
            name = title,
            source = source,
            updated = old?.updated ?: 0,
            count = old?.count ?: 0,
        )
        // Renaming only: no need to download again.
        if (old != null && old.source == source && repo.hasCache(old.id)) {
            repo.save(playlist)
            finish()
            return
        }

        busy = true
        status.text = getString(R.string.downloading)
        Thread {
            val result = runCatching { repo.fetch(playlist) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                result.onSuccess {
                    Prefs(this).lastPlaylist = it.id
                    finish()
                }.onFailure { e ->
                    status.text = if (e is PlaylistRepository.EmptyPlaylistException) getString(R.string.error_empty)
                    else getString(R.string.error_download, e.message ?: e.javaClass.simpleName)
                }
            }
        }.start()
    }

    companion object {
        const val EXTRA_ID = "id"
        private const val REQ_PICK = 1
        private const val REQ_PERMISSION = 2
    }
}
