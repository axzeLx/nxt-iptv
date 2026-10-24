package com.nxt.iptv.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Environment
import android.widget.Toast
import com.nxt.iptv.R
import java.io.File

/**
 * Minimal in-app file browser for TVs that ship without a system document picker.
 * Lists internal storage and mounted USB drives, showing folders and playlist files only.
 */
class FileBrowser(private val activity: Activity, private val onPicked: (File) -> Unit) {

    fun show() {
        val roots = roots()
        if (roots.isEmpty()) {
            Toast.makeText(activity, R.string.no_storage, Toast.LENGTH_SHORT).show()
            return
        }
        if (roots.size == 1) open(roots[0], roots) else showRoots(roots)
    }

    private fun showRoots(roots: List<File>) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.choose_file)
            .setItems(roots.map { it.path }.toTypedArray()) { _, which -> open(roots[which], roots) }
            .show()
    }

    private fun open(dir: File, roots: List<File>) {
        val children = dir.listFiles()
            ?.filter { !it.name.startsWith(".") && (it.isDirectory || isPlaylist(it)) }
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .orEmpty()
        val atRoot = roots.any { it == dir }
        val labels = ArrayList<String>()
        labels.add("⬆  ..")
        children.forEach { labels.add(if (it.isDirectory) "📁  ${it.name}" else "📄  ${it.name}") }
        if (children.isEmpty()) labels.add("   (${activity.getString(R.string.no_files)})")

        AlertDialog.Builder(activity)
            .setTitle(dir.path)
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which == 0 -> if (atRoot) {
                        if (roots.size > 1) showRoots(roots)
                    } else {
                        dir.parentFile?.let { open(it, roots) }
                    }
                    which - 1 < children.size -> {
                        val f = children[which - 1]
                        if (f.isDirectory) open(f, roots) else onPicked(f)
                    }
                    else -> open(dir, roots)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isPlaylist(f: File): Boolean {
        val n = f.name.lowercase()
        return n.endsWith(".m3u") || n.endsWith(".m3u8") || n.endsWith(".txt")
    }

    private fun roots(): List<File> {
        val result = LinkedHashSet<File>()
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory()?.let { if (it.canRead()) result.add(it) }
        // USB sticks and SD cards are usually mounted under /storage/<uuid> or /mnt/usb*.
        File("/storage").listFiles()?.forEach {
            if (it.name != "self" && it.name != "emulated" && it.canRead()) result.add(it)
        }
        File("/mnt").listFiles()?.forEach {
            if (it.name.startsWith("usb", true) && it.canRead()) result.add(it)
        }
        File("/mnt/media_rw").listFiles()?.forEach { if (it.canRead()) result.add(it) }
        return result.toList()
    }
}
