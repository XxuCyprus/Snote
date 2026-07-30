package com.snote.app.ui.screen.todo

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.snote.app.R
import com.snote.app.data.model.ContentItem
import com.snote.app.data.model.ContentType
import java.io.File

class TodoItemAdapter(
    private val themeColor: Int,
    private val getAbsolutePath: (String) -> String,
    private val onDelete: (String) -> Unit,
    private val onToggleComplete: (String) -> Unit,
    private val onEdit: (String) -> Unit,
    private val onSwapUp: (String, String) -> Unit,
    private val onSwapDown: (String, String) -> Unit,
    private val onImageClick: (String) -> Unit,
    private val onVideoClick: (String) -> Unit,
    private val onImageEdit: (String, String) -> Unit,
    private val onSaveToGallery: (String) -> Unit,
    private val onFileClick: (String) -> Unit,
    private val onRenameContent: (String, String) -> Unit,
    private val isCompleted: Boolean = false
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    var headerTitle: String = ""
        set(value) { field = value; notifyItemChanged(0) }

    private var items: List<ContentItem> = emptyList()

    fun submitList(newItems: List<ContentItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItems(): List<ContentItem> = items

    private var mediaPlayer: MediaPlayer? = null
    private var playingItemId: String? = null
    private var isPlaying = false
    private val progressHandler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer ?: return
            val id = playingItemId ?: return
            val idx = items.indexOfFirst { it.id == id }
            if (idx < 0) return
            if (mp.isPlaying) {
                notifyItemChanged(idx + 1)
                progressHandler.postDelayed(this, 250)
            }
        }
    }

    fun release() {
        progressHandler.removeCallbacks(progressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        playingItemId = null
        isPlaying = false
    }

    private fun playAudio(itemId: String, path: String) {
        progressHandler.removeCallbacks(progressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        playingItemId = null
        isPlaying = false

        try {
            val file = File(path)
            if (!file.exists()) return
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    progressHandler.removeCallbacks(progressRunnable)
                    this@TodoItemAdapter.isPlaying = false
                    val id = playingItemId
                    playingItemId = null
                    if (id != null) {
                        val idx = items.indexOfFirst { it.id == id }
                        if (idx >= 0) notifyItemChanged(idx + 1)
                    }
                    it.release()
                    if (this@TodoItemAdapter.mediaPlayer === it) {
                        this@TodoItemAdapter.mediaPlayer = null
                    }
                }
            }
            playingItemId = itemId
            isPlaying = true
            progressHandler.post(progressRunnable)
        } catch (_: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            playingItemId = null
            isPlaying = false
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (position == 0) TYPE_HEADER else TYPE_ITEM

    override fun getItemId(position: Int): Long =
        if (position == 0) -1L else items[position - 1].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            HeaderVH(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_todo_card, parent, false)
            ItemVH(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderVH) {
            holder.bind(headerTitle)
        } else if (holder is ItemVH) {
            holder.bind(items[position - 1], position - 1)
        }
    }

    override fun getItemCount(): Int = items.size + 1

    inner class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(title: String) {
            (itemView as TextView).text = title
            itemView.setPadding(0, 24, 0, 8)
            (itemView as TextView).textSize = 22f
            (itemView as TextView).setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    inner class ItemVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: LinearLayout = itemView.findViewById(R.id.cardRoot)
        private val tvText: TextView = itemView.findViewById(R.id.tvText)
        private val imgContent: ImageView = itemView.findViewById(R.id.imgContent)
        private val videoArea: LinearLayout = itemView.findViewById(R.id.videoArea)
        private val btnPlayVideo: FrameLayout = itemView.findViewById(R.id.btnPlayVideo)
        private val tvVideoName: TextView = itemView.findViewById(R.id.tvVideoName)
        private val audioArea: LinearLayout = itemView.findViewById(R.id.audioArea)
        private val btnPlayAudio: FrameLayout = itemView.findViewById(R.id.btnPlayAudio)
        private val imgPlayAudio: ImageView = itemView.findViewById(R.id.imgPlayAudio)
        private val tvAudioName: TextView = itemView.findViewById(R.id.tvAudioName)
        private val progressAudio: ProgressBar = itemView.findViewById(R.id.progressAudio)
        private val audioTimeRow: LinearLayout = itemView.findViewById(R.id.audioTimeRow)
        private val tvAudioCurrent: TextView = itemView.findViewById(R.id.tvAudioCurrent)
        private val tvAudioTotal: TextView = itemView.findViewById(R.id.tvAudioTotal)
        private val fileArea: LinearLayout = itemView.findViewById(R.id.fileArea)
        private val btnFileIcon: FrameLayout = itemView.findViewById(R.id.btnFileIcon)
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val btnCheck: FrameLayout = itemView.findViewById(R.id.btnCheck)
        private val imgCheck: ImageView = itemView.findViewById(R.id.imgCheck)
        private val btnMoveUp: LinearLayout = itemView.findViewById(R.id.btnMoveUp)
        private val btnMoveDown: LinearLayout = itemView.findViewById(R.id.btnMoveDown)
        private val btnEdit: LinearLayout = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: LinearLayout = itemView.findViewById(R.id.btnDelete)
        private val btnSaveToGallery: LinearLayout = itemView.findViewById(R.id.btnSaveToGallery)
        private var lastContentType: ContentType? = null

        private fun formatTime(ms: Int): String {
            val s = ms / 1000
            return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
        }

        fun bind(item: ContentItem, position: Int) {
            if (item.type != lastContentType) {
                tvText.visibility = View.GONE
                imgContent.visibility = View.GONE
                videoArea.visibility = View.GONE
                audioArea.visibility = View.GONE
                fileArea.visibility = View.GONE
                btnEdit.visibility = View.GONE
                btnSaveToGallery.visibility = View.GONE
                lastContentType = item.type
            }

            when (item.type) {
                ContentType.TEXT -> {
                    tvText.visibility = View.VISIBLE
                    tvText.text = item.content
                    tvText.isClickable = true
                    tvText.isFocusable = true
                    tvText.setOnClickListener { onEdit(item.id) }
                    btnEdit.visibility = View.VISIBLE
                }
                ContentType.IMAGE -> {
                    imgContent.visibility = View.VISIBLE
                    val path = getAbsolutePath(item.content)
                    try {
                        val opts = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                        val bmp = BitmapFactory.decodeFile(path, opts)
                        if (bmp != null) imgContent.setImageBitmap(bmp)
                    } catch (_: Exception) {
                        imgContent.setImageResource(android.R.drawable.ic_menu_report_image)
                    }
                    imgContent.setOnClickListener { onImageClick(path) }
                    btnEdit.visibility = View.VISIBLE
                    btnSaveToGallery.visibility = View.VISIBLE
                }
                ContentType.VIDEO -> {
                    videoArea.visibility = View.VISIBLE
                    tvVideoName.text = File(item.content).name
                    val videoBg = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(themeColor)
                    }
                    btnPlayVideo.background = videoBg
                    videoArea.setOnClickListener { onVideoClick(getAbsolutePath(item.content)) }
                    btnEdit.visibility = View.VISIBLE
                }
                ContentType.AUDIO -> {
                    audioArea.visibility = View.VISIBLE
                    tvAudioName.text = File(item.content).name
                    val audioBg = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(themeColor)
                    }
                    btnPlayAudio.background = audioBg
                    bindAudioPlayback(item)
                    btnEdit.visibility = View.VISIBLE
                }
                ContentType.FILE -> {
                    fileArea.visibility = View.VISIBLE
                    tvFileName.text = File(item.content).name
                    val fileBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(themeColor) }
                    btnFileIcon.background = fileBg
                    fileArea.isClickable = true
                    fileArea.isFocusable = true
                    fileArea.setOnClickListener { onFileClick(getAbsolutePath(item.content)) }
                    btnEdit.visibility = View.VISIBLE
                }
            }

            // Checkbox state
            imgCheck.setImageResource(
                if (isCompleted) R.drawable.ic_checkbox_filled else R.drawable.ic_checkbox_outline
            )
            imgCheck.clearColorFilter()
            btnCheck.setOnClickListener { onToggleComplete(item.id) }

            // Background
            cardRoot.setBackgroundColor(Color.WHITE)

            // Move buttons
            btnMoveUp.visibility = if (position > 0) View.VISIBLE else View.GONE
            btnMoveUp.setOnClickListener {
                onSwapUp(items[position - 1].id, item.id)
            }
            btnMoveDown.visibility = if (position < items.size - 1) View.VISIBLE else View.GONE
            btnMoveDown.setOnClickListener {
                onSwapDown(item.id, items[position + 1].id)
            }

            // Edit / Delete / Save
            btnDelete.setOnClickListener { onDelete(item.id) }
            btnEdit.setOnClickListener {
                when (item.type) {
                    ContentType.IMAGE -> onImageEdit(getAbsolutePath(item.content), item.id)
                    ContentType.TEXT -> onEdit(item.id)
                    else -> onRenameContent(getAbsolutePath(item.content), item.id)
                }
            }
            btnSaveToGallery.setOnClickListener {
                if (item.type == ContentType.IMAGE) {
                    onSaveToGallery(getAbsolutePath(item.content))
                }
            }
        }

        private fun bindAudioPlayback(item: ContentItem) {
            val isCurrentTrack = item.id == playingItemId
            val mp = mediaPlayer

            if (isCurrentTrack && mp != null) {
                val dur = mp.duration
                val pos = mp.currentPosition
                progressAudio.max = if (dur > 0) dur else 1
                progressAudio.progress = pos
                progressAudio.progressTintList = android.content.res.ColorStateList.valueOf(themeColor)
                progressAudio.visibility = View.VISIBLE
                audioTimeRow.visibility = View.VISIBLE
                tvAudioCurrent.text = formatTime(pos)
                tvAudioTotal.text = formatTime(dur)
                imgPlayAudio.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                )
            } else {
                progressAudio.visibility = View.GONE
                audioTimeRow.visibility = View.GONE
                imgPlayAudio.setImageResource(R.drawable.ic_play_arrow)
            }

            btnPlayAudio.setOnClickListener {
                if (isCurrentTrack && isPlaying) {
                    mp?.pause()
                    isPlaying = false
                    progressHandler.removeCallbacks(progressRunnable)
                    val idx = items.indexOfFirst { it.id == item.id }
                    if (idx >= 0) notifyItemChanged(idx + 1)
                } else if (isCurrentTrack && mp != null) {
                    mp.start()
                    isPlaying = true
                    progressHandler.post(progressRunnable)
                    val idx = items.indexOfFirst { it.id == item.id }
                    if (idx >= 0) notifyItemChanged(idx + 1)
                } else {
                    val prevId = playingItemId
                    playAudio(item.id, getAbsolutePath(item.content))
                    val idx = items.indexOfFirst { it.id == item.id }
                    if (idx >= 0) notifyItemChanged(idx + 1)
                    if (prevId != null) {
                        val prevIdx = items.indexOfFirst { it.id == prevId }
                        if (prevIdx >= 0) notifyItemChanged(prevIdx + 1)
                    }
                }
            }
        }
    }
}
