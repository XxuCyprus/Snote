package com.snote.app.ui.screen.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.snote.app.R
import com.snote.app.data.model.Notebook

class NotebookAdapter(
    private val primaryColor: Int,
    private val onClick: (Notebook) -> Unit,
    private val onEdit: (Notebook) -> Unit,
    private val onDelete: (Notebook) -> Unit
) : RecyclerView.Adapter<NotebookAdapter.VH>() {

    private var items: List<Notebook> = emptyList()

    fun submitList(newItems: List<Notebook>) {
        items = newItems
        notifyDataSetChanged()
    }

    // 浅色背景 + 对应深色图标：8种配色
    private val bgColors = intArrayOf(
        0xFFEDE7F6.toInt(), // 浅紫
        0xFFE3F2FD.toInt(), // 浅蓝
        0xFFE0F2F1.toInt(), // 浅青
        0xFFFFF3E0.toInt(), // 浅橙
        0xFFFCE4EC.toInt(), // 浅粉
        0xFFF3E5F5.toInt(), // 浅紫红
        0xFFE8F5E9.toInt(), // 浅绿
        0xFFE8EAF6.toInt(), // 浅靛
    )

    private val iconColors = intArrayOf(
        0xFF7965AF.toInt(), // 紫
        0xFF3586D7.toInt(), // 蓝
        0xFF1F978B.toInt(), // 青
        0xFFE9661F.toInt(), // 橙
        0xFFCD4242.toInt(), // 红
        0xFF8B3AAD.toInt(), // 深紫
        0xFF478D4B.toInt(), // 绿
        0xFF515FB5.toInt(), // 靛蓝
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notebook_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val gradientBar: View = itemView.findViewById(R.id.gradientBar)
        private val iconBox: FrameLayout = itemView.findViewById(R.id.iconBox)
        private val imgIcon: ImageView = itemView.findViewById(R.id.imgNotebookIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvNotebookTitle)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvNotebookDesc)
        private val tvChapterCount: TextView = itemView.findViewById(R.id.tvChapterCount)
        private val btnEdit: FrameLayout = itemView.findViewById(R.id.btnEditNotebook)
        private val btnDelete: FrameLayout = itemView.findViewById(R.id.btnDeleteNotebook)

        fun bind(notebook: Notebook) {
            gradientBar.setBackgroundColor(primaryColor)
            tvTitle.text = notebook.title

            if (notebook.description.isNotEmpty()) {
                tvDesc.visibility = View.VISIBLE
                tvDesc.text = notebook.description
            } else {
                tvDesc.visibility = View.GONE
            }

            tvChapterCount.text = "${notebook.chapters.size} 个章节"

            // 基于笔记本ID确定性分配颜色
            val idx = Math.abs(notebook.id.hashCode()) % bgColors.size
            val bgColor = bgColors[idx]
            val iconColor = iconColors[idx]

            // 设置圆角矩形背景
            val drawable = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 14f * itemView.context.resources.displayMetrics.density
            }
            iconBox.background = drawable
            imgIcon.setColorFilter(iconColor)

            itemView.setOnClickListener { onClick(notebook) }
            btnEdit.setOnClickListener { onEdit(notebook) }
            btnDelete.setOnClickListener { onDelete(notebook) }
        }
    }
}
