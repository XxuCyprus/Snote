package com.snote.app.ui.screen.reader

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.snote.app.R

class ChapterAdapter(
    private val onSwitchChapter: (String) -> Unit,
    private val onToggleExpand: (String) -> Unit,
    private val onToggleMark: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onSwapUp: (String, String) -> Unit,
    private val onSwapDown: (String, String) -> Unit,
    private val onAddChild: (String) -> Unit,
    private val onRename: (String) -> Unit
) : ListAdapter<FlatChapterNode, ChapterAdapter.VH>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<FlatChapterNode>() {
            override fun areItemsTheSame(old: FlatChapterNode, new: FlatChapterNode): Boolean {
                return old.chapter.id == new.chapter.id
            }
            override fun areContentsTheSame(old: FlatChapterNode, new: FlatChapterNode): Boolean {
                return old.isExpanded == new.isExpanded &&
                    old.isCurrent == new.isCurrent &&
                    old.canMoveUp == new.canMoveUp &&
                    old.canMoveDown == new.canMoveDown &&
                    old.level == new.level &&
                    old.chapter == new.chapter
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter_tree, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorBar: View = itemView.findViewById(R.id.colorBar)
        private val contentRow: View = itemView.findViewById(R.id.contentRow)
        private val levelDot: View = itemView.findViewById(R.id.levelDot)
        private val btnExpand: FrameLayout = itemView.findViewById(R.id.btnExpand)
        private val imgExpand: ImageView = itemView.findViewById(R.id.imgExpand)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val btnMark: FrameLayout = itemView.findViewById(R.id.btnMark)
        private val imgMark: ImageView = itemView.findViewById(R.id.imgMark)
        private val btnDelete: FrameLayout = itemView.findViewById(R.id.btnDelete)
        private val btnMoveUp: FrameLayout = itemView.findViewById(R.id.btnMoveUp)
        private val btnMoveDown: FrameLayout = itemView.findViewById(R.id.btnMoveDown)
        private val btnAddChild: FrameLayout = itemView.findViewById(R.id.btnAddChild)
        private val btnRename: FrameLayout = itemView.findViewById(R.id.btnRename)

        fun bind(node: FlatChapterNode) {
            val ctx = itemView.context
            val density = ctx.resources.displayMetrics.density
            val ch = node.chapter
            val level = node.level

            // 左侧彩条颜色 — 每级鲜明区分（6级全覆盖）
            val levelColor = when (level) {
                0 -> 0xFF6750A4.toInt()  // 深紫色
                1 -> 0xFF1976D2.toInt()  // 蓝色
                2 -> 0xFF00897B.toInt()  // 青绿色
                3 -> 0xFFE64A19.toInt()  // 深橙色
                4 -> 0xFFC62828.toInt()  // 红色
                else -> 0xFF6A1B9A.toInt()  // 深紫红色
            }
            colorBar.setBackgroundColor(levelColor)

            // 左侧缩进
            val startPadding = (12 + level * 18).dp(density)
            contentRow.setPadding(startPadding, 8.dp(density), 8.dp(density), 8.dp(density))

            // 当前章节高亮
            if (node.isCurrent) {
                contentRow.setBackgroundColor(0x146750A4.toInt())
            } else {
                contentRow.setBackgroundColor(Color.TRANSPARENT)
            }

            // 层级小圆点 — 加大 + 更实心
            levelDot.isVisible = level > 0
            (levelDot.background as? android.graphics.drawable.GradientDrawable)?.let {
                it.setColor(levelColor)
            }

            // 展开/折叠
            if (ch.children.isNotEmpty()) {
                btnExpand.isVisible = true
                imgExpand.setImageResource(
                    if (node.isExpanded) R.drawable.ic_expand_down
                    else R.drawable.ic_expand_right
                )
                btnExpand.setOnClickListener { onToggleExpand(ch.id) }
            } else {
                btnExpand.isVisible = false
            }

            // 标题文字 — 层级越高字号越大
            tvTitle.text = ch.title
            when (level) {
                0 -> { tvTitle.textSize = 15f; tvTitle.setTypeface(tvTitle.typeface, android.graphics.Typeface.BOLD) }
                1 -> { tvTitle.textSize = 14f; tvTitle.setTypeface(tvTitle.typeface, android.graphics.Typeface.BOLD) }
                2 -> { tvTitle.textSize = 13f; tvTitle.setTypeface(tvTitle.typeface, android.graphics.Typeface.NORMAL) }
                else -> { tvTitle.textSize = 12f; tvTitle.setTypeface(tvTitle.typeface, android.graphics.Typeface.NORMAL) }
            }

            // 点击跳转章节
            contentRow.setOnClickListener { onSwitchChapter(ch.id) }

            // 标记按钮
            imgMark.setImageResource(
                if (ch.isMarked) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            if (ch.isMarked) {
                imgMark.setColorFilter(0xFFFFA000.toInt())
            } else {
                imgMark.setColorFilter(0x6649474F.toInt()) // onSurfaceVariant alpha 0.4
            }
            btnMark.setOnClickListener { onToggleMark(ch.id) }

            // 删除按钮
            btnDelete.setOnClickListener { onDelete(ch.id) }

            // 上移
            btnMoveUp.isVisible = node.canMoveUp
            btnMoveUp.setOnClickListener {
                val siblings = node.siblings
                val idx = siblings.indexOfFirst { it.id == ch.id }
                if (idx > 0) onSwapUp(ch.id, siblings[idx - 1].id)
            }

            // 下移
            btnMoveDown.isVisible = node.canMoveDown
            btnMoveDown.setOnClickListener {
                val siblings = node.siblings
                val idx = siblings.indexOfFirst { it.id == ch.id }
                if (idx >= 0 && idx < siblings.size - 1) onSwapDown(ch.id, siblings[idx + 1].id)
            }

            // 添加子章节
            btnAddChild.isVisible = ch.level < 6
            btnAddChild.setOnClickListener { onAddChild(ch.id) }

            // 重命名
            btnRename.setOnClickListener { onRename(ch.id) }
        }

        private fun Int.dp(density: Float): Int = (this * density + 0.5f).toInt()
    }
}
