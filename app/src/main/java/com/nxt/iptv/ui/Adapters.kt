package com.nxt.iptv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nxt.iptv.R
import com.nxt.iptv.data.Channel

/*
 * Selection is owned by MainActivity rather than Android focus: remotes on cheap TV boxes
 * repeat keys faster than RecyclerView focus search can keep up, so we just paint the row.
 */

private fun paintRow(row: View, selected: Boolean, active: Boolean, idleBackground: Int, vararg texts: TextView) {
    val ctx = row.context
    row.setBackgroundResource(
        when {
            selected && active -> R.drawable.bg_selected
            selected -> R.drawable.bg_selected_inactive
            else -> idleBackground
        }
    )
    val on = selected && active
    texts.forEachIndexed { i, t ->
        t.setTextColor(
            ContextCompat.getColor(
                ctx,
                when {
                    on -> R.color.on_accent
                    i == 0 -> R.color.text
                    else -> R.color.text_dim
                }
            )
        )
    }
}

class CatItem(val type: Int, val key: String, val label: String, var count: Int) {
    companion object {
        const val FAV = 0
        const val HEADER = 1
        const val CATEGORY = 2
    }
}

class CategoryAdapter(
    private val items: List<CatItem>,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var selected = 0
    var active = true

    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = items[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == CatItem.HEADER) R.layout.item_header else R.layout.item_category
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return object : RecyclerView.ViewHolder(v) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val v = holder.itemView
        val label = v.findViewById<TextView>(R.id.label)
        if (item.type == CatItem.HEADER) {
            label.text = item.label
            return
        }
        val count = v.findViewById<TextView>(R.id.count)
        label.text = if (item.type == CatItem.FAV) "★  ${item.label}" else item.label
        count.text = item.count.toString()
        val idle = if (item.type == CatItem.FAV) R.drawable.bg_box else android.R.color.transparent
        paintRow(v, position == selected, active, idle, label, count)
        v.setOnClickListener { onClick(holder.bindingAdapterPosition) }
    }
}

class ChannelAdapter(
    private val logoSizePx: Int,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var channels: List<Channel> = emptyList()
    var favorites: Set<String> = emptySet()
    var playingUrl: String? = null
    /** When set (recently watched list), shown in place of the channel number, e.g. "21:45". */
    var rightLabel: ((Channel) -> String)? = null
    var selected = 0
    var active = false

    override fun getItemCount() = channels.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return object : RecyclerView.ViewHolder(v) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val ch = channels[position]
        val v = holder.itemView
        val name = v.findViewById<TextView>(R.id.name)
        val number = v.findViewById<TextView>(R.id.number)
        val playing = v.findViewById<TextView>(R.id.playing)
        val fav = v.findViewById<TextView>(R.id.fav)
        name.text = ch.name
        number.text = rightLabel?.invoke(ch) ?: ch.number.toString()
        ImageLoader.load(ch.logo, v.findViewById<ImageView>(R.id.logo), logoSizePx)
        val isSelected = position == selected
        paintRow(v, isSelected, active, android.R.color.transparent, name, number)
        val on = isSelected && active
        val markColor = ContextCompat.getColor(v.context, if (on) R.color.on_accent else R.color.accent)
        playing.visibility = if (ch.url == playingUrl) View.VISIBLE else View.GONE
        playing.setTextColor(markColor)
        fav.visibility = if (ch.url in favorites) View.VISIBLE else View.GONE
        fav.setTextColor(markColor)
        v.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        v.setOnLongClickListener { onLongClick(holder.bindingAdapterPosition); true }
    }
}
