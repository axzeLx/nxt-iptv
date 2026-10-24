package com.nxt.iptv.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.widget.Toast
import com.nxt.iptv.R
import java.io.File

/**
 * Minimal in-app file browser for TVs that ship without a system document picker.
 * Finds internal storage and every mounted USB/SD volume (TV vendors mount them in odd places),
 * showing folders and playlist files only.
 */
class FileBrowser(private val activity: Activity, private val onPicked: (File) -> Unit) {

    private class Root(val dir: File, val label: String)

    fun show() {
        val roots = findRoots(activity)
        if (roots.isEmpty()) {
            Toast.makeText(activity, R.string.no_storage, Toast.LENGTH_SHORT).show()
            return
        }
        showRoots(roots)
    }

    private fun showRoots(roots: List<Root>) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.choose_storage)
            .setItems(roots.map { "💾  ${it.label}\n      ${it.dir.path}" }.toTypedArray()) { _, which ->
                open(roots[which].dir, roots)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun open(dir: File, roots: List<Root>) {
        val listed = dir.listFiles()
        val children = listed
            ?.filter { !it.name.startsWith(".") && (it.isDirectory || isPlaylist(it)) }
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .orEmpty()
        val atRoot = roots.any { it.dir == dir }
        val labels = ArrayList<String>()
        labels.add("⬆  ..")
        children.forEach { labels.add(if (it.isDirectory) "📁  ${it.name}" else "📄  ${it.name}") }
        if (children.isEmpty()) {
            val msg = if (listed == null) R.string.cannot_read_folder else R.string.no_files
            labels.add("   (${activity.getString(msg)})")
        }

        AlertDialog.Builder(activity)
            .setTitle(dir.path)
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which == 0 -> if (atRoot) showRoots(roots) else dir.parentFile?.let { open(it, roots) }
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

    companion object {
        private val FS_TYPES = setOf(
            "vfat", "exfat", "ntfs", "ntfs3", "fuseblk", "sdfat", "texfat", "tntfs", "ufsd", "fuse", "msdos",
            "ext4", "hfsplus",
        )
        private val SYSTEM_MOUNTS = listOf(
            "/storage/emulated", "/storage/self", "/mnt/runtime", "/mnt/user", "/mnt/pass_through",
            "/mnt/installer", "/mnt/androidwritable", "/mnt/vendor", "/mnt/product", "/mnt/secure",
            "/mnt/asec", "/mnt/obb", "/mnt/appfuse", "/mnt/expand", "/mnt/vendor_de", "/mnt/scratch",
        )

        private fun findRoots(context: Context): List<Root> {
            val found = LinkedHashMap<String, Root>()
            fun add(dir: File?, label: String?) {
                if (dir == null || !dir.isDirectory) return
                val key = runCatching { dir.canonicalPath }.getOrDefault(dir.path)
                if (key !in found) found[key] = Root(dir, label?.takeIf { it.isNotBlank() } ?: dir.name)
            }

            @Suppress("DEPRECATION")
            add(Environment.getExternalStorageDirectory(), context.getString(R.string.internal_storage))

            // 1. Volumes Android knows about, with their user-facing names ("USB drive", "Yerel_Disk"…).
            if (Build.VERSION.SDK_INT >= 24) {
                val sm = context.getSystemService(StorageManager::class.java)
                sm?.storageVolumes?.forEach { v ->
                    val dir = if (Build.VERSION.SDK_INT >= 30) v.directory else runCatching {
                        v.javaClass.getMethod("getPathFile").invoke(v) as File
                    }.getOrNull()
                    add(dir, v.getDescription(context))
                }
            }

            // 2. Whatever the kernel has mounted from removable disks, wherever the vendor put it.
            runCatching {
                File("/proc/mounts").forEachLine { line ->
                    val parts = line.split(' ')
                    if (parts.size < 3) return@forEachLine
                    val mount = parts[1].replace("\\040", " ")
                    val type = parts[2].lowercase()
                    val removable = parts[0].startsWith("/dev/block/vold") || parts[0].startsWith("/dev/sd") ||
                        parts[0].startsWith("/dev/block/sd")
                    if ((type in FS_TYPES || removable) &&
                        (mount.startsWith("/storage/") || mount.startsWith("/mnt/") || mount.startsWith("/media")) &&
                        SYSTEM_MOUNTS.none { mount.startsWith(it) }
                    ) {
                        add(File(mount), File(mount).name)
                    }
                }
            }

            // 3. Common vendor mount points.
            val candidates = ArrayList<File>()
            File("/storage").listFiles()?.filter { it.name != "self" && it.name != "emulated" }?.let(candidates::addAll)
            listOf("/mnt", "/mnt/usb", "/mnt/media_rw", "/media", "/mnt/sdcard").forEach { base ->
                File(base).listFiles()?.filter {
                    val n = it.name.lowercase()
                    base != "/mnt" || n.startsWith("usb") || n.startsWith("udisk") || n.startsWith("sd") ||
                        n.startsWith("extsd") || n.startsWith("external")
                }?.let(candidates::addAll)
            }
            candidates.forEach { add(it, it.name) }

            return found.values.toList()
        }
    }
}
