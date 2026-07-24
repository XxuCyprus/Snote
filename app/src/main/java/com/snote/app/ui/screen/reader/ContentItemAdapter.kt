package com.snote.app.ui.screen.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
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

class ContentItemAdapter(
    private val themeColor: Int,
    private val getAbsolutePath: (String) -> String,
    private val onDelete: (String) -> Unit,
    private val onEdit: (String) -> Unit,
    private val onToggleMark: (String) -> Unit,
    private val onSwapUp: (String, String) -> Unit,
    private val onSwapDown: (String, String) -> Unit,
    private val onImageClick: (String) -> Unit,
    private val onVideoClick: (String) -> Unit,
    private val onImageEdit: (String, String) -> Unit,
    private val onSaveToGallery: (String) -> Unit
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

    // ========== 音频播放管理 ==========

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

    /** 释放音频播放资源 */
    fun release() {
        progressHandler.removeCallbacks(progressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        playingItemId = null
        isPlaying = false
    }

    private fun playAudio(itemId: String, path: String) {
        // 停止当前播放
        progressHandler.removeCallbacks(progressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        playingItemId = null
        isPlaying = false

        try {
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(null, "音频文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    progressHandler.removeCallbacks(progressRunnable)
                    this@ContentItemAdapter.isPlaying = false
                    val id = playingItemId
                    playingItemId = null
                    if (id != null) {
                        val idx = items.indexOfFirst { it.id == id }
                        if (idx >= 0) notifyItemChanged(idx + 1)
                    }
                    it.release()
                    if (this@ContentItemAdapter.mediaPlayer === it) {
                        this@ContentItemAdapter.mediaPlayer = null
                    }
                }
            }
            playingItemId = itemId
            isPlaying = true
            progressHandler.post(progressRunnable)
        } catch (e: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            playingItemId = null
            isPlaying = false
        }
    }

    // ========== RecyclerView 核心 ==========

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
                .inflate(R.layout.item_content_card, parent, false)
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
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val btnMark: FrameLayout = itemView.findViewById(R.id.btnMark)
        private val imgMark: ImageView = itemView.findViewById(R.id.imgMark)
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
            val markColor = Color.parseColor("#FFA000")

            // 只在类型变化时重置可见性，避免同类型滚动时闪烁
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

            // Content by type
            when (item.type) {
                ContentType.TEXT -> {
                    tvText.visibility = View.VISIBLE
                    tvText.text = item.content
                    tvText.isClickable = true
                    tvText.isFocusable = true
                    tvText.setOnClickListener { onEdit(item.id) }
                }
                ContentType.IMAGE -> {
                    imgContent.visibility = View.VISIBLE
                    val path = getAbsolutePath(item.content)
                    try {
                        val file = File(path)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            // Android 9+：ImageDecoder 软件解码（全分辨率，避免 GPU 纹理限制降采样）
                            val source = ImageDecoder.createSource(file)
                            val drawable = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                                decoder.isMutableRequired = false
                            }
                            imgContent.setImageDrawable(drawable)
                        } else {
                            // Android 8 及以下：BitmapFactory
                            val opts = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            val bmp = BitmapFactory.decodeFile(path, opts)
                            if (bmp != null) imgContent.setImageBitmap(bmp)
                        }
                    } catch (e: Exception) {
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
                }
                ContentType.FILE -> {
                    fileArea.visibility = View.VISIBLE
                    tvFileName.text = File(item.content).name
                }
            }

            // Marked state
            val isMarked = item.isMarked
            imgMark.setImageResource(
                if (isMarked) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            imgMark.setColorFilter(
                if (isMarked) markColor else 0x6649474F.toInt()
            )
            btnMark.setOnClickListener { onToggleMark(item.id) }

            // Background color for marked items
            cardRoot.setBackgroundColor(
                if (isMarked) Color.parseColor("#FFFFF8E1") else Color.WHITE
            )

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
                if (item.type == ContentType.IMAGE) {
                    onImageEdit(getAbsolutePath(item.content), item.id)
                } else {
                    onEdit(item.id)
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
                // 当前正在播放或已暂停的音轨 — 显示进度条
                val dur = mp.duration
                val pos = mp.currentPosition

                progressAudio.max = if (dur > 0) dur else 1
                progressAudio.progress = pos
                progressAudio.visibility = View.VISIBLE

                audioTimeRow.visibility = View.VISIBLE
                tvAudioCurrent.text = formatTime(pos)
                tvAudioTotal.text = formatTime(dur)

                imgPlayAudio.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                )
            } else {
                // 非当前音轨 — 隐藏进度条，显示播放按钮
                progressAudio.visibility = View.GONE
                audioTimeRow.visibility = View.GONE
                imgPlayAudio.setImageResource(R.drawable.ic_play_arrow)
            }

            // 播放/暂停按钮点击
            btnPlayAudio.setOnClickListener {
                if (isCurrentTrack && isPlaying) {
                    // 暂停
                    mp?.pause()
                    isPlaying = false
                    progressHandler.removeCallbacks(progressRunnable)
                    val idx = items.indexOfFirst { it.id == item.id }
                    if (idx >= 0) notifyItemChanged(idx + 1)
                } else if (isCurrentTrack && mp != null) {
                    // 恢复播放
                    mp.start()
                    isPlaying = true
                    progressHandler.post(progressRunnable)
                    val idx = items.indexOfFirst { it.id == item.id }
                    if (idx >= 0) notifyItemChanged(idx + 1)
                } else {
                    // 播放新音轨
                    val prevId = playingItemId
                    playAudio(item.id, getAbsolutePath(item.content))
                    val idx = items.indexOfFirst { it.id == item.id }
                    if (idx >= 0) notifyItemChanged(idx + 1)
                    // 刷新上一个播放项
                    if (prevId != null) {
                        val prevIdx = items.indexOfFirst { it.id == prevId }
                        if (prevIdx >= 0) notifyItemChanged(prevIdx + 1)
                    }
                }
            }
        }
    }
}
