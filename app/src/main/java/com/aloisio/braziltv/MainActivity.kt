package com.aloisio.braziltv

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.URL
import java.util.concurrent.Executors

private const val PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/br.m3u"
private const val PREFS = "brazil_tv"
private const val LAST_URL = "last_url"
private const val LAST_NAME = "last_name"
private const val FAVORITES = "favorites"

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String?
)

class MainActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var overlay: View
    private lateinit var recycler: RecyclerView
    private lateinit var search: EditText
    private lateinit var status: TextView
    private lateinit var categories: LinearLayout
    private lateinit var favoritesTab: TextView
    private lateinit var clock: TextView
    private lateinit var player: ExoPlayer

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val allChannels = mutableListOf<Channel>()
    private val filtered = mutableListOf<Channel>()
    private val favorites = mutableSetOf<String>()
    private var activeCategory = "ALL"
    private var favoritesOnly = false

    private val clockTask = object : Runnable {
        override fun run() {
            clock.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.player)
        overlay = findViewById(R.id.overlay)
        recycler = findViewById(R.id.channels)
        search = findViewById(R.id.search)
        status = findViewById(R.id.status)
        categories = findViewById(R.id.categories)
        favoritesTab = findViewById(R.id.favoritesTab)
        clock = findViewById(R.id.clock)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        favorites.addAll(getPreferences().getStringSet(FAVORITES, emptySet()) ?: emptySet())
        recycler.layoutManager = LinearLayoutManager(this)
        search.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                recycler.requestFocus()
                true
            } else false
        }
        search.addTextChangedListener(SimpleTextWatcher { refreshList() })
        favoritesTab.setOnClickListener {
            favoritesOnly = !favoritesOnly
            favoritesTab.text = if (favoritesOnly) "★ ALL CHANNELS" else "★ FAVORITES"
            refreshList()
        }

        handler.post(clockTask)
        loadPlaylist()
    }

    private fun loadPlaylist() {
        status.text = "Updating channel guide..."
        executor.execute {
            try {
                val text = URL(PLAYLIST_URL).openStream().bufferedReader().use { it.readText() }
                val parsed = parseM3u(text)
                runOnUiThread {
                    allChannels.clear()
                    allChannels.addAll(parsed)
                    buildCategories()
                    refreshList()
                    autoPlayLast()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Could not load playlist. Check Internet connection."
                }
            }
        }
    }

    private fun buildCategories() {
        categories.removeAllViews()
        val groups = allChannels.mapNotNull { it.group?.takeIf(String::isNotBlank) }
            .flatMap { it.split(";").map(String::trim) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        addCategory("ALL")
        groups.take(18).forEach(::addCategory)
    }

    private fun addCategory(label: String) {
        val v = TextView(this)
        v.text = label.uppercase()
        v.textSize = 14f
        v.setTextColor(Color.WHITE)
        v.gravity = android.view.Gravity.CENTER
        v.isFocusable = true
        v.isClickable = true
        v.setPadding(28, 0, 28, 0)
        v.background = resources.getDrawable(R.drawable.tab_bg, theme)
        v.setOnClickListener {
            activeCategory = label
            refreshList()
            recycler.requestFocus()
        }
        categories.addView(v, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT
        ).apply { setMargins(0, 0, 10, 0) })
    }

    private fun refreshList() {
        val q = search.text?.toString()?.trim()?.lowercase() ?: ""
        filtered.clear()
        filtered.addAll(allChannels.filter { c ->
            val inSearch = q.isEmpty() || c.name.lowercase().contains(q) ||
                (c.group ?: "").lowercase().contains(q)
            val inCategory = activeCategory == "ALL" ||
                (c.group ?: "").split(";").any { it.trim().equals(activeCategory, true) }
            val inFav = !favoritesOnly || favorites.contains(c.url)
            inSearch && inCategory && inFav
        })
        status.text = "${filtered.size} channels"
        recycler.adapter = ChannelAdapter(filtered, favorites) { c ->
            play(c)
        }
    }

    private fun play(c: Channel) {
        player.setMediaItem(MediaItem.fromUri(c.url))
        player.prepare()
        player.play()
        getPreferences().edit().putString(LAST_URL, c.url).putString(LAST_NAME, c.name).apply()
        status.text = "▶  ${c.name}"
        overlay.visibility = View.GONE
    }

    private fun autoPlayLast() {
        val url = getPreferences().getString(LAST_URL, null) ?: return
        val name = getPreferences().getString(LAST_NAME, "Last channel") ?: "Last channel"
        val c = allChannels.firstOrNull { it.url == url } ?: return
        // Do not surprise the user with autoplay on cold start; just expose it in status.
        status.text = "Last: $name  •  ${allChannels.size} channels"
    }

    internal fun toggleFavorite(c: Channel) {
        if (!favorites.add(c.url)) favorites.remove(c.url)
        getPreferences().edit().putStringSet(FAVORITES, favorites).apply()
        refreshList()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (overlay.visibility != View.VISIBLE) {
                overlay.visibility = View.VISIBLE
                recycler.requestFocus()
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_DPAD_CENTER &&
            event.isLongPress) {
            overlay.visibility = if (overlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockTask)
        player.release()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun parseM3u(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var name = ""
        var logo: String? = null
        var group: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF:", true) -> {
                    val comma = line.indexOf(',')
                    val attrs = if (comma >= 0) line.substring(0, comma) else line
                    name = if (comma >= 0) line.substring(comma + 1).trim() else "Unknown"
                    logo = attr(attrs, "tvg-logo")
                    group = attr(attrs, "group-title")
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    result += Channel(name.ifBlank { "Channel" }, line, logo, group)
                    name = ""; logo = null; group = null
                }
            }
        }
        return result.distinctBy { it.url }
    }

    private fun attr(s: String, key: String): String? =
        Regex("""$key="([^"]*)"""", RegexOption.IGNORE_CASE).find(s)?.groupValues?.getOrNull(1)

    private fun getPreferences() = getSharedPreferences(PREFS, MODE_PRIVATE)
}

class ChannelAdapter(
    private val items: List<Channel>,
    private val favorites: Set<String>,
    private val onPlay: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val number: TextView = v.findViewById(R.id.number)
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
        val group: TextView = v.findViewById(R.id.group)
        val favorite: TextView = v.findViewById(R.id.favorite)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int): VH =
        VH(android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false))

    override fun onBindViewHolder(h: VH, p: Int) {
        val c = items[p]
        h.number.text = String.format("%03d", p + 1)
        h.name.text = c.name
        h.group.text = c.group ?: "Brazil"
        h.favorite.text = if (favorites.contains(c.url)) "★" else "☆"

        // Logos are fetched only when the image URL is present.
        if (c.logo.isNullOrBlank()) {
            h.logo.setImageResource(android.R.drawable.ic_menu_gallery)
        } else {
            // Keep the project dependency-free for images. The channel name remains usable
            // even when a logo host blocks Android TV requests.
            h.logo.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        h.itemView.setOnClickListener { onPlay(c) }
        h.itemView.setOnLongClickListener {
            h.favorite.performClick()
            true
        }
        h.favorite.setOnClickListener {
            val activity = h.itemView.context as MainActivity
            activity.toggleFavorite(c)
        }
    }

    override fun getItemCount() = items.size
}

class SimpleTextWatcher(private val changed: () -> Unit) :
    android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = changed()
    override fun afterTextChanged(e: android.text.Editable?) {}
}
