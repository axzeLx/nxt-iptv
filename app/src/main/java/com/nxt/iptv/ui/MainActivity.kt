package com.nxt.iptv.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nxt.iptv.R
import com.nxt.iptv.data.Channel
import com.nxt.iptv.data.PlaylistData
import com.nxt.iptv.data.PlaylistRepository
import com.nxt.iptv.data.Prefs
import com.nxt.iptv.player.PlayerHolder
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : BaseActivity(), PlayerHolder.Listener {

    private enum class Focus { CATEGORIES, CHANNELS, TOP }

    private lateinit var repo: PlaylistRepository
    private lateinit var prefs: Prefs
    private lateinit var player: PlayerHolder
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: FrameLayout
    private lateinit var content: View
    private lateinit var catList: RecyclerView
    private lateinit var chList: RecyclerView
    private lateinit var chEmpty: TextView
    private lateinit var catLeft: View
    private lateinit var catRight: View
    private lateinit var catPos: TextView
    private lateinit var chUp: View
    private lateinit var chDown: View
    private lateinit var previewSlot: View
    private lateinit var playerFrame: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var playerMsg: TextView
    private lateinit var osd: View
    private lateinit var numberView: TextView
    private lateinit var loading: TextView
    private lateinit var clock: TextView
    private lateinit var topButtons: List<TextView>

    private lateinit var catAdapter: CategoryAdapter
    private lateinit var chAdapter: ChannelAdapter

    private var pid = ""
    private var data: PlaylistData? = null
    private var byUrl: Map<String, Channel> = emptyMap()
    private val favorites = LinkedHashSet<String>()

    private val cats = ArrayList<CatItem>()
    private var catSel = 0
    /** Key of the category whose channels are listed; null while showing search results. */
    private var shownKey: String? = null
    private var channels: List<Channel> = emptyList()
    private var chSel = 0
    private var focus = Focus.CATEGORIES
    private var topSel = 0
    private var fullscreen = false

    /** The list zapping walks through: the one the playing channel was started from. */
    private var playingList: List<Channel> = emptyList()
    private var playingIndex = -1
    private val playing: Channel? get() = playingList.getOrNull(playingIndex)
    private var videoInfo = ""
    private var statusInfo = ""

    private var okDown = false
    private var okLongFired = false
    private var backDown = false
    private var backLongFired = false
    private val digits = StringBuilder()
    private var rowHeightPx = 0
    private var chipWidthPx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = PlaylistRepository(this)
        prefs = Prefs(this)
        player = PlayerHolder(this, this)
        ImageLoader.init(this)
        rowHeightPx = (37 * resources.displayMetrics.density).toInt()
        chipWidthPx = (140 * resources.displayMetrics.density).toInt()

        root = findViewById(R.id.root)
        content = findViewById(R.id.content)
        catList = findViewById(R.id.catList)
        chList = findViewById(R.id.chList)
        catLeft = findViewById(R.id.catLeft)
        catRight = findViewById(R.id.catRight)
        catPos = findViewById(R.id.catPos)
        chUp = findViewById(R.id.chUp)
        chDown = findViewById(R.id.chDown)
        chEmpty = findViewById(R.id.chEmpty)
        previewSlot = findViewById(R.id.previewSlot)
        playerFrame = findViewById(R.id.playerFrame)
        playerView = findViewById(R.id.playerView)
        playerMsg = findViewById(R.id.playerMsg)
        osd = findViewById(R.id.osd)
        numberView = findViewById(R.id.numberView)
        loading = findViewById(R.id.loading)
        clock = findViewById(R.id.clock)
        topButtons = listOf(
            findViewById(R.id.btnSearch), findViewById(R.id.btnPlaylists),
            findViewById(R.id.btnSettings), findViewById(R.id.btnExit),
        )
        topButtons.forEachIndexed { i, b ->
            b.setOnClickListener { topAction(i) }
            // Clickable views become focusable on API 26+; selection here is painted by refreshFocus().
            b.isFocusable = false
        }

        catAdapter = CategoryAdapter(cats) { pos ->
            if (pos < 0 || cats[pos].type == CatItem.HEADER) return@CategoryAdapter
            focus = Focus.CATEGORIES
            selectCategory(pos)
            refreshFocus()
        }
        chAdapter = ChannelAdapter(
            logoSizePx = (34 * resources.displayMetrics.density).toInt(),
            onClick = { pos ->
                if (pos < 0) return@ChannelAdapter
                focus = Focus.CHANNELS
                chSel = pos
                activateChannel()
            },
            onLongClick = { pos ->
                if (pos >= 0) toggleFavorite(channels[pos])
            },
        )
        setupList(catList, catAdapter, RecyclerView.HORIZONTAL)
        setupList(chList, chAdapter, RecyclerView.VERTICAL)
        catLeft.setOnClickListener { moveCategory(-1) }
        catRight.setOnClickListener { moveCategory(1) }

        previewSlot.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> playerFrame.post { positionPlayer() } }
        setupPlayerTouch()

        pid = intent.getStringExtra(EXTRA_PLAYLIST) ?: prefs.lastPlaylist ?: run {
            finish()
            return
        }
        loadPlaylist()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val id = intent.getStringExtra(EXTRA_PLAYLIST) ?: return
        if (id != pid) {
            pid = id
            if (fullscreen) exitFullscreen()
            player.stop()
            playingList = emptyList()
            playingIndex = -1
            loadPlaylist()
        }
    }

    override fun onStart() {
        super.onStart()
        player.attach(playerView)
        playing?.let { player.play(it) }
        tickClock()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(clockTick)
        player.release()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // ---------------------------------------------------------------- loading

    private fun loadPlaylist() {
        loading.visibility = View.VISIBLE
        loading.setText(R.string.loading)
        findViewById<TextView>(R.id.playlistName).text = repo.get(pid)?.name.orEmpty()
        val id = pid
        Thread {
            val result = runCatching { repo.load(id) }
            runOnUiThread {
                if (isDestroyed || id != pid) return@runOnUiThread
                result.onSuccess { onLoaded(it) }.onFailure { e ->
                    AlertDialog.Builder(this)
                        .setMessage(
                            if (e is PlaylistRepository.EmptyPlaylistException) getString(R.string.error_empty)
                            else getString(R.string.error_download, e.message ?: e.javaClass.simpleName)
                        )
                        .setPositiveButton(android.R.string.ok, null)
                        .setOnDismissListener { finish() }
                        .show()
                }
            }
        }.start()
    }

    private fun onLoaded(d: PlaylistData) {
        data = d
        byUrl = HashMap<String, Channel>(d.channels.size * 2).apply {
            for (c in d.channels) if (!containsKey(c.url)) put(c.url, c)
        }
        favorites.clear()
        favorites.addAll(prefs.favorites(pid).filter { it in byUrl })
        chAdapter.favorites = favorites
        buildCategories()

        val lastKey = prefs.lastCategory(pid)
        val catIndex = cats.indexOfFirst { it.key == lastKey && it.type != CatItem.HEADER }
            .takeIf { it >= 0 } ?: cats.indexOfFirst { it.key == KEY_ALL }
        selectCategory(catIndex)

        val lastUrl = prefs.lastChannel(pid)
        val inShown = channels.indexOfFirst { it.url == lastUrl }
        when {
            inShown >= 0 -> {
                chSel = inShown
                chAdapter.selected = inShown
                playAt(channels, inShown)
                focus = Focus.CHANNELS
            }
            lastUrl != null && byUrl.containsKey(lastUrl) -> {
                playAt(d.channels, d.channels.indexOf(byUrl[lastUrl]))
                focus = Focus.CATEGORIES
            }
            else -> {
                focus = Focus.CATEGORIES
                showIdleInfo()
            }
        }
        loading.visibility = View.GONE
        refreshFocus()
        scrollTo(catList, catSel)
        scrollTo(chList, chSel)
    }

    private fun buildCategories() {
        val d = data ?: return
        cats.clear()
        cats.add(CatItem(CatItem.FAV, KEY_FAV, getString(R.string.favorites), favorites.size))
        cats.add(CatItem(CatItem.CATEGORY, KEY_ALL, getString(R.string.all_channels), d.channels.size))
        for (g in d.groups) {
            cats.add(CatItem(CatItem.CATEGORY, g, groupLabel(g), d.byGroup[g]?.size ?: 0))
        }
        catAdapter.notifyDataSetChanged()
    }

    private fun groupLabel(group: String) = group.ifEmpty { getString(R.string.uncategorized) }

    private fun channelsFor(key: String): List<Channel> {
        val d = data ?: return emptyList()
        return when (key) {
            KEY_FAV -> favorites.mapNotNull { byUrl[it] }
            KEY_ALL -> d.channels
            else -> d.byGroup[key].orEmpty()
        }
    }

    // ---------------------------------------------------------------- browsing

    private fun selectCategory(index: Int) {
        if (index !in cats.indices) return
        catSel = index
        val item = cats[index]
        shownKey = item.key
        showChannels(channelsFor(item.key))
        prefs.setLastCategory(pid, item.key)
        catAdapter.selected = index
        catAdapter.notifyDataSetChanged()
        catPos.text = "${index + 1} / ${cats.size}"
    }

    private fun showChannels(list: List<Channel>) {
        channels = list
        val playingUrl = playing?.url
        chSel = list.indexOfFirst { it.url == playingUrl }.coerceAtLeast(0)
        chEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        chEmpty.setText(if (shownKey == KEY_FAV) R.string.no_favorites else R.string.no_channels)
        chAdapter.channels = list
        chAdapter.selected = chSel
        chAdapter.notifyDataSetChanged()
        scrollTo(chList, chSel)
    }

    /** Steps through the category bar, wrapping around at both ends. */
    private fun moveCategory(delta: Int) {
        if (cats.isEmpty()) return
        val i = ((catSel + delta) % cats.size + cats.size) % cats.size
        selectCategory(i)
        revealCategory(i, delta)
    }

    /**
     * Lets the highlight travel across the bar and only scrolls once it gets within one chip of
     * the edge, so the movement is visible and the next category in that direction is always shown.
     */
    private fun revealCategory(index: Int, direction: Int) {
        val lm = catList.layoutManager as LinearLayoutManager
        // scrollToPosition() scrolls the minimum needed to bring that chip fully into view.
        val neighbor = (index + if (direction >= 0) 1 else -1).coerceIn(0, cats.size - 1)
        lm.scrollToPosition(neighbor)
        catList.post {
            // After a wrap-around jump the selected chip itself may still be off-screen.
            val first = lm.findFirstCompletelyVisibleItemPosition()
            val last = lm.findLastCompletelyVisibleItemPosition()
            if (first != RecyclerView.NO_POSITION && (index < first || index > last)) lm.scrollToPosition(index)
        }
    }

    /** Full vertical list of categories, for playlists with too many to step through. */
    private fun showCategoryPicker() {
        if (cats.isEmpty()) return
        val labels = cats.map { item ->
            val star = if (item.type == CatItem.FAV) "★  " else ""
            "$star${item.label}   (${item.count})"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.categories)
            .setSingleChoiceItems(labels, catSel) { dialog, which ->
                dialog.dismiss()
                selectCategory(which)
                lm(catList).scrollToPositionWithOffset((which - 1).coerceAtLeast(0), 0)
                setFocus(Focus.CHANNELS)
            }
            .show()
    }

    private fun lm(list: RecyclerView) = list.layoutManager as LinearLayoutManager

    private fun moveChannel(delta: Int) {
        if (channels.isEmpty()) return
        val target = chSel + delta
        if (target < 0) {
            if (chSel == 0) setFocus(Focus.CATEGORIES) else setChannelSelection(0)
            return
        }
        setChannelSelection(target.coerceAtMost(channels.size - 1))
    }

    private fun setChannelSelection(index: Int) {
        val old = chSel
        chSel = index
        chAdapter.selected = index
        chAdapter.notifyItemChanged(old)
        chAdapter.notifyItemChanged(index)
        scrollTo(chList, index)
    }

    private fun setFocus(f: Focus) {
        if (f == Focus.TOP) topSel = 0
        if (f == Focus.CHANNELS && channels.isEmpty()) return
        focus = f
        refreshFocus()
    }

    private var focusBeforeTop = Focus.CATEGORIES

    private fun refreshFocus() {
        if (focus != Focus.TOP) focusBeforeTop = focus
        catAdapter.active = focus == Focus.CATEGORIES
        chAdapter.active = focus == Focus.CHANNELS
        catAdapter.notifyDataSetChanged()
        chAdapter.notifyDataSetChanged()
        topButtons.forEachIndexed { i, b ->
            val on = focus == Focus.TOP && i == topSel
            b.setBackgroundResource(if (on) R.drawable.bg_selected else R.drawable.bg_row)
            b.setTextColor(ContextCompat.getColor(this, if (on) R.color.on_accent else R.color.text))
        }
    }

    /** Scrolls so [position] sits near the middle of the list (vertical or horizontal). */
    private fun scrollTo(list: RecyclerView, position: Int) {
        if (position < 0) return
        val lm = list.layoutManager as LinearLayoutManager
        val offset = if (lm.orientation == RecyclerView.HORIZONTAL) {
            if (list.width > 0) list.width / 2 - chipWidthPx / 2 else 0
        } else {
            if (list.height > 0) list.height / 2 - rowHeightPx / 2 else 0
        }
        lm.scrollToPositionWithOffset(position, offset)
        list.post { updateScrollHints() }
    }

    /**
     * Shows ▲ ▼ only when the channel list can scroll that way. The category ‹ › stay visible
     * since the bar wraps around and they double as a "use Left/Right here" hint.
     */
    private fun updateScrollHints() {
        chUp.visibility = if (chList.canScrollVertically(-1)) View.VISIBLE else View.INVISIBLE
        chDown.visibility = if (chList.canScrollVertically(1)) View.VISIBLE else View.INVISIBLE
    }

    private fun activateChannel() {
        val ch = channels.getOrNull(chSel) ?: return
        if (ch.url == playing?.url && playingList === channels) {
            enterFullscreen()
        } else {
            playAt(channels, chSel)
        }
        refreshFocus()
    }

    private fun topAction(index: Int) {
        when (index) {
            0 -> showSearch()
            1 -> finish()
            2 -> SettingsDialog.show(this)
            3 -> finishAffinity() // explicit Exit button: no confirmation
        }
    }

    /** Holding Back asks first, since it is easy to trigger by accident. */
    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setMessage(R.string.exit_confirm)
            .setPositiveButton(R.string.exit) { _, _ -> finishAffinity() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSearch() {
        val d = data ?: return
        val input = EditText(this).apply {
            setSingleLine()
            hint = getString(R.string.search_hint)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val box = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.search)
            .setView(box)
            .setPositiveButton(R.string.search) { _, _ -> runSearch(d, input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .create()
        input.setOnEditorActionListener { _, _, _ ->
            runSearch(d, input.text.toString())
            dialog.dismiss()
            true
        }
        dialog.show()
        input.requestFocus()
    }

    private fun runSearch(d: PlaylistData, query: String) {
        val q = normalize(query.trim())
        if (q.isEmpty()) return
        val results = d.channels.filter { normalize(it.name).contains(q) }
        shownKey = null
        showChannels(results)
        Toast.makeText(
            this,
            getString(R.string.search_results, query.trim()) + "  ·  " +
                resources.getQuantityString(R.plurals.channels_count, results.size, results.size),
            Toast.LENGTH_SHORT,
        ).show()
        focus = if (results.isEmpty()) Focus.CATEGORIES else Focus.CHANNELS
        chSel = 0
        chAdapter.selected = 0
        refreshFocus()
    }

    /** Case- and dotted-i-insensitive form so "trt" finds "TRT" and "İzmir" finds "izmir". */
    private fun normalize(s: String) =
        s.lowercase(Locale.ROOT).replace("i̇", "i").replace('ı', 'i')

    // ---------------------------------------------------------------- playback

    private fun playAt(list: List<Channel>, index: Int) {
        val ch = list.getOrNull(index) ?: return
        playingList = list
        playingIndex = index
        videoInfo = ""
        statusInfo = ""
        playerMsg.visibility = View.GONE
        player.play(ch)
        prefs.setLastChannel(pid, ch.url)
        chAdapter.playingUrl = ch.url
        chAdapter.notifyDataSetChanged()
        updateInfo()
        if (fullscreen) showOsd()
    }

    private fun zap(delta: Int) {
        if (playingList.isEmpty()) return
        val size = playingList.size
        val index = ((playingIndex + delta) % size + size) % size
        playAt(playingList, index)
        if (playingList === channels) {
            chSel = index
            chAdapter.selected = index
            scrollTo(chList, index)
        }
    }

    private fun enterFullscreen() {
        if (playing == null) return
        fullscreen = true
        content.visibility = View.INVISIBLE
        positionPlayer()
        showOsd()
    }

    private fun exitFullscreen() {
        fullscreen = false
        content.visibility = View.VISIBLE
        osd.visibility = View.GONE
        handler.removeCallbacks(hideOsd)
        positionPlayer()
        if (playingList === channels && playingIndex >= 0) {
            chSel = playingIndex
            chAdapter.selected = chSel
            focus = Focus.CHANNELS
            scrollTo(chList, chSel)
        }
        refreshFocus()
    }

    private fun positionPlayer() {
        val lp = playerFrame.layoutParams as FrameLayout.LayoutParams
        val before = intArrayOf(lp.width, lp.height, lp.leftMargin, lp.topMargin)
        if (fullscreen) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            lp.leftMargin = 0
            lp.topMargin = 0
        } else {
            val slot = IntArray(2)
            val base = IntArray(2)
            previewSlot.getLocationInWindow(slot)
            root.getLocationInWindow(base)
            // Inset by the slot's 1dp border.
            val inset = resources.displayMetrics.density.toInt().coerceAtLeast(1)
            lp.width = (previewSlot.width - inset * 2).coerceAtLeast(1)
            lp.height = (previewSlot.height - inset * 2).coerceAtLeast(1)
            lp.leftMargin = slot[0] - base[0] + inset
            lp.topMargin = slot[1] - base[1] + inset
        }
        if (!before.contentEquals(intArrayOf(lp.width, lp.height, lp.leftMargin, lp.topMargin))) {
            playerFrame.layoutParams = lp
        }
    }

    private fun showOsd() {
        val ch = playing ?: return
        findViewById<TextView>(R.id.osdNumber).text = ch.number.toString()
        findViewById<TextView>(R.id.osdName).text = ch.name
        findViewById<TextView>(R.id.osdMeta).text = metaText(ch)
        findViewById<View>(R.id.osdFav).visibility = if (ch.url in favorites) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.osdClock).text = DateFormat.getTimeFormat(this).format(Date())
        ImageLoader.load(ch.logo, findViewById(R.id.osdLogo), (96 * resources.displayMetrics.density).toInt())
        osd.visibility = View.VISIBLE
        handler.removeCallbacks(hideOsd)
        handler.postDelayed(hideOsd, 4_000)
    }

    private val hideOsd = Runnable { osd.visibility = View.GONE }

    private fun metaText(ch: Channel, withGroup: Boolean = true): String =
        // The playlist's quality tag stands in until the stream reports its real resolution.
        listOf(if (withGroup) groupLabel(ch.group) else "", videoInfo.ifEmpty { ch.quality.orEmpty() }, statusInfo)
            .filter { it.isNotEmpty() }.joinToString("  ·  ")

    private fun updateInfo() {
        val ch = playing ?: return showIdleInfo()
        findViewById<TextView>(R.id.infoNumber).text = ch.number.toString()
        findViewById<TextView>(R.id.infoName).text = ch.name
        // Category sits on the right of the second line, so the left part skips it.
        findViewById<TextView>(R.id.infoMeta).text = metaText(ch, withGroup = false)
        findViewById<TextView>(R.id.infoCategory).text = groupLabel(ch.group)
        findViewById<View>(R.id.infoFav).visibility = if (ch.url in favorites) View.VISIBLE else View.GONE
        ImageLoader.load(ch.logo, findViewById<ImageView>(R.id.infoLogo), (56 * resources.displayMetrics.density).toInt())
        if (fullscreen && osd.visibility == View.VISIBLE) {
            findViewById<TextView>(R.id.osdMeta).text = metaText(ch)
        }
    }

    private fun showIdleInfo() {
        findViewById<TextView>(R.id.infoNumber).text = ""
        findViewById<TextView>(R.id.infoName).setText(R.string.nothing_playing)
        findViewById<TextView>(R.id.infoMeta).text = ""
        findViewById<TextView>(R.id.infoCategory).text = ""
        findViewById<View>(R.id.infoFav).visibility = View.GONE
        findViewById<ImageView>(R.id.infoLogo).setImageDrawable(null)
    }

    override fun onBuffering(buffering: Boolean) {}

    override fun onPlaying() {
        statusInfo = ""
        playerMsg.visibility = View.GONE
        updateInfo()
    }

    override fun onRetrying() {
        statusInfo = getString(R.string.retrying)
        playerMsg.text = statusInfo
        playerMsg.visibility = View.VISIBLE
        updateInfo()
    }

    override fun onFailed(message: String) {
        statusInfo = getString(R.string.error_stream)
        playerMsg.text = statusInfo
        playerMsg.visibility = View.VISIBLE
        updateInfo()
    }

    override fun onVideoSize(width: Int, height: Int) {
        videoInfo = when {
            height >= 2000 -> "4K"
            height >= 1000 -> "FHD"
            height >= 700 -> "HD"
            else -> "SD"
        } + "  ${width}×$height"
        updateInfo()
    }

    // ---------------------------------------------------------------- favorites

    private fun toggleFavorite(ch: Channel) {
        val added = favorites.add(ch.url)
        if (!added) favorites.remove(ch.url)
        prefs.setFavorites(pid, favorites)
        cats.firstOrNull { it.type == CatItem.FAV }?.count = favorites.size
        Toast.makeText(this, if (added) R.string.added_fav else R.string.removed_fav, Toast.LENGTH_SHORT).show()
        if (shownKey == KEY_FAV) {
            val keep = chSel
            showChannels(channelsFor(KEY_FAV))
            if (channels.isNotEmpty()) setChannelSelection(keep.coerceAtMost(channels.size - 1))
            else if (focus == Focus.CHANNELS) focus = Focus.CATEGORIES
        }
        catAdapter.notifyDataSetChanged()
        chAdapter.notifyDataSetChanged()
        updateInfo()
        if (fullscreen) showOsd()
    }

    // ---------------------------------------------------------------- number zapping

    private fun onDigit(d: Int) {
        if (data == null || digits.length >= 5) return
        digits.append(d)
        numberView.text = digits
        numberView.visibility = View.VISIBLE
        handler.removeCallbacks(commitNumber)
        handler.postDelayed(commitNumber, 1_500)
    }

    private val commitNumber = Runnable {
        val n = digits.toString().toIntOrNull() ?: 0
        digits.setLength(0)
        numberView.visibility = View.GONE
        val all = data?.channels ?: return@Runnable
        if (n !in 1..all.size) {
            Toast.makeText(this, getString(R.string.channel_not_found, n), Toast.LENGTH_SHORT).show()
            return@Runnable
        }
        val allIndex = cats.indexOfFirst { it.key == KEY_ALL }
        if (shownKey != KEY_ALL) selectCategory(allIndex)
        playAt(all, n - 1)
        chSel = n - 1
        chAdapter.selected = chSel
        if (!fullscreen) {
            focus = Focus.CHANNELS
            refreshFocus()
            scrollTo(catList, allIndex)
        }
        scrollTo(chList, chSel)
    }

    // ---------------------------------------------------------------- input

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (data == null) {
            return if (event.keyCode == KeyEvent.KEYCODE_BACK) super.dispatchKeyEvent(event) else true
        }
        val code = event.keyCode
        if (isOk(code)) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                    okDown = true
                    okLongFired = false
                } else if (okDown && !okLongFired) {
                    okLongFired = true
                    onOkLong()
                }
                KeyEvent.ACTION_UP -> {
                    if (okDown && !okLongFired) onOk()
                    okDown = false
                }
            }
            return true
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            // Short press acts on release; holding Back offers to close the app.
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                    backDown = true
                    backLongFired = false
                } else if (backDown && !backLongFired) {
                    backLongFired = true
                    confirmExit()
                }
                KeyEvent.ACTION_UP -> {
                    if (backDown && !backLongFired) onBack()
                    backDown = false
                }
            }
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) {
            return if (handles(code)) true else super.dispatchKeyEvent(event)
        }
        val step = when {
            event.repeatCount > 20 -> 5
            event.repeatCount > 8 -> 2
            else -> 1
        }
        if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            onDigit(code - KeyEvent.KEYCODE_0)
            return true
        }
        if (code in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
            onDigit(code - KeyEvent.KEYCODE_NUMPAD_0)
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); return true }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> { SettingsDialog.show(this); return true }
            KeyEvent.KEYCODE_SEARCH -> { showSearch(); return true }
        }
        val handled = if (fullscreen) fullscreenKey(code) else browseKey(code, step)
        return handled || super.dispatchKeyEvent(event)
    }

    private fun handles(code: Int) = code in HANDLED_KEYS

    private fun isOk(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A

    private fun fullscreenKey(code: Int): Boolean {
        val up = if (prefs.invertChannelKeys) 1 else -1
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> zap(up)
            KeyEvent.KEYCODE_DPAD_DOWN -> zap(-up)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> showOsd()
            else -> return false
        }
        return true
    }

    /** Short Back: fullscreen -> lists, lists -> back to the picture. */
    private fun onBack() {
        when {
            fullscreen -> exitFullscreen()
            playing != null -> {
                if (focus == Focus.TOP) focus = focusBeforeTop
                enterFullscreen()
            }
            // Nothing playing yet: step out of the channel list, then back to the playlists screen.
            focus == Focus.CHANNELS -> {
                if (shownKey == null) selectCategory(catSel) // leave search results
                focus = Focus.CATEGORIES
                refreshFocus()
            }
            else -> finish()
        }
    }

    private fun browseKey(code: Int, step: Int): Boolean {
        when (focus) {
            Focus.TOP -> when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> topSel = (topSel - 1).coerceAtLeast(0)
                KeyEvent.KEYCODE_DPAD_RIGHT -> topSel = (topSel + 1).coerceAtMost(topButtons.size - 1)
                KeyEvent.KEYCODE_DPAD_DOWN -> focus = focusBeforeTop
                KeyEvent.KEYCODE_DPAD_UP -> Unit
                else -> return false
            }
            Focus.CATEGORIES -> when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> moveCategory(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> moveCategory(1)
                KeyEvent.KEYCODE_PAGE_UP -> repeat(5) { moveCategory(-1) }
                KeyEvent.KEYCODE_PAGE_DOWN -> repeat(5) { moveCategory(1) }
                KeyEvent.KEYCODE_DPAD_DOWN -> setFocus(Focus.CHANNELS)
                KeyEvent.KEYCODE_DPAD_UP -> setFocus(Focus.TOP)
                else -> return false
            }
            Focus.CHANNELS -> when (code) {
                KeyEvent.KEYCODE_DPAD_UP -> moveChannel(-step)
                KeyEvent.KEYCODE_DPAD_DOWN -> moveChannel(step)
                KeyEvent.KEYCODE_PAGE_UP -> moveChannel(-10)
                KeyEvent.KEYCODE_PAGE_DOWN -> moveChannel(10)
                // Left/Right flip categories without leaving the channel list.
                KeyEvent.KEYCODE_DPAD_LEFT -> moveCategory(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> moveCategory(1)
                else -> return false
            }
        }
        refreshFocus()
        return true
    }

    private fun onOk() {
        if (fullscreen) {
            exitFullscreen()
            return
        }
        when (focus) {
            Focus.TOP -> topAction(topSel)
            Focus.CATEGORIES -> showCategoryPicker()
            Focus.CHANNELS -> activateChannel()
        }
    }

    private fun onOkLong() {
        val ch = when {
            fullscreen -> playing
            focus == Focus.CHANNELS -> channels.getOrNull(chSel)
            else -> null
        } ?: return
        toggleFavorite(ch)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPlayerTouch() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (fullscreen) {
                    if (osd.visibility == View.VISIBLE) osd.visibility = View.GONE else showOsd()
                } else {
                    enterFullscreen()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (fullscreen) exitFullscreen() else enterFullscreen()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (!fullscreen || e1 == null) return false
                val dy = e2.y - e1.y
                if (abs(dy) < abs(e2.x - e1.x) || abs(dy) < 80) return false
                zap(if (dy < 0) 1 else -1) // swipe up = next channel
                return true
            }
        })
        playerFrame.setOnTouchListener { _, ev -> detector.onTouchEvent(ev) }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = DateFormat.getTimeFormat(this@MainActivity).format(Date())
            handler.postDelayed(this, 15_000)
        }
    }

    private fun tickClock() {
        handler.removeCallbacks(clockTick)
        clockTick.run()
    }

    private fun setupList(list: RecyclerView, adapter: RecyclerView.Adapter<*>, orientation: Int) {
        list.layoutManager = LinearLayoutManager(this, orientation, false)
        list.adapter = adapter
        list.itemAnimator = null
        list.setHasFixedSize(true)
        list.isFocusable = false
        list.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = updateScrollHints()
        })
    }

    companion object {
        const val EXTRA_PLAYLIST = "playlist"
        private const val KEY_FAV = "\u0001fav"
        private const val KEY_ALL = "\u0001all"
        private val HANDLED_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO,
        )
    }
}
