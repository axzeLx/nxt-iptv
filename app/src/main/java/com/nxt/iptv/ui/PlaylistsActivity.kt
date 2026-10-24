package com.nxt.iptv.ui

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nxt.iptv.R
import com.nxt.iptv.data.Playlist
import com.nxt.iptv.data.PlaylistRepository
import com.nxt.iptv.data.Prefs
import java.text.DateFormat
import java.util.Date

/** Start screen: pick one of the saved playlists (the last used one is pre-selected) or add a new one. */
class PlaylistsActivity : BaseActivity() {
    private lateinit var repo: PlaylistRepository
    private lateinit var prefs: Prefs
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var btnAdd: View
    private var playlists: List<Playlist> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlists)
        repo = PlaylistRepository(this)
        prefs = Prefs(this)
        repo.seedDefaults()
        ImageLoader.init(this)

        findViewById<TextView>(R.id.logo).text = brandTitle(this)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        btnAdd = findViewById(R.id.btnAdd)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        btnAdd.setOnClickListener { startActivity(Intent(this, AddPlaylistActivity::class.java)) }
        findViewById<View>(R.id.btnSettings).setOnClickListener { SettingsDialog.show(this) }
    }

    override fun onResume() {
        super.onResume()
        playlists = repo.all()
        adapter.notifyDataSetChanged()
        empty.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
        if (playlists.isEmpty()) {
            btnAdd.requestFocus()
            return
        }
        val last = playlists.indexOfFirst { it.id == prefs.lastPlaylist }.coerceAtLeast(0)
        list.scrollToPosition(last)
        list.post { list.findViewHolderForAdapterPosition(last)?.itemView?.findViewById<View>(R.id.main)?.requestFocus() }
    }

    private fun open(p: Playlist) {
        prefs.lastPlaylist = p.id
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PLAYLIST, p.id))
    }

    private fun showOptions(p: Playlist) {
        val items = arrayOf(getString(R.string.refresh), getString(R.string.edit), getString(R.string.delete))
        AlertDialog.Builder(this)
            .setTitle(p.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> refresh(p)
                    1 -> startActivity(
                        Intent(this, AddPlaylistActivity::class.java).putExtra(AddPlaylistActivity.EXTRA_ID, p.id)
                    )
                    2 -> confirmDelete(p)
                }
            }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun refresh(p: Playlist) {
        val progress = ProgressDialog(this).apply {
            setMessage(getString(R.string.downloading))
            setCancelable(false)
            show()
        }
        Thread {
            val result = runCatching { repo.fetch(p) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.dismiss()
                result.onSuccess {
                    Toast.makeText(this, getString(R.string.refreshed, it.count), Toast.LENGTH_SHORT).show()
                    onResume()
                }.onFailure { showError(it) }
            }
        }.start()
    }

    private fun confirmDelete(p: Playlist) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_confirm, p.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                repo.delete(p.id)
                onResume()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showError(e: Throwable) {
        val msg = if (e is PlaylistRepository.EmptyPlaylistException) getString(R.string.error_empty)
        else getString(R.string.error_download, e.message ?: e.javaClass.simpleName)
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun describe(p: Playlist): String {
        val source = if (p.isRemote) Uri.parse(p.source).host ?: p.source else getString(R.string.source_file)
        // Not downloaded yet (e.g. the default playlist): show only where it comes from.
        if (p.updated == 0L) return source
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(p.updated))
        return getString(R.string.playlist_info, p.count, source) + "  ·  " + date
    }

    private val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = playlists.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
            ) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val p = playlists[position]
            val v = holder.itemView
            v.findViewById<TextView>(R.id.name).text = p.name
            v.findViewById<TextView>(R.id.info).text = describe(p)
            v.findViewById<View>(R.id.main).apply {
                setOnClickListener { open(p) }
                setOnLongClickListener { showOptions(p); true }
            }
            v.findViewById<View>(R.id.more).setOnClickListener { showOptions(p) }
        }
    }
}
