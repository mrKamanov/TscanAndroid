package com.example.myapplication

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.view.animation.AnimationUtils
import android.view.animation.TranslateAnimation

class MenuAdapter(
    private val items: List<MenuItem>
) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: FrameLayout = view.findViewById(R.id.menu_card)
        val title: TextView = view.findViewById(R.id.menu_title)
        val desc: TextView = view.findViewById(R.id.menu_desc)
        val layout: LinearLayout = view.findViewById(R.id.menu_card_layout)
        val topLeftGlare: View = view.findViewById(R.id.top_left_glare)
        val bottomRightGlare: View = view.findViewById(R.id.bottom_right_glare)
        val menuIcon: ImageView = view.findViewById(R.id.menu_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.menu_item, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.desc.text = item.description
        holder.card.setOnClickListener { item.onClick() }
        holder.menuIcon.setImageResource(item.iconRes)

        // Анимация появления и "плавания"
        val fadeInUp = TranslateAnimation(0f, 0f, 60f, 0f)
        fadeInUp.duration = 500
        fadeInUp.startOffset = (position * 100).toLong()
        holder.card.startAnimation(fadeInUp)
        holder.card.alpha = 0f
        holder.card.animate().alpha(1f).setDuration(500).setStartDelay((position * 100).toLong()).start()

        // Анимация блика в уголках
        holder.topLeftGlare.alpha = 0f
        holder.bottomRightGlare.alpha = 0f
        holder.topLeftGlare.animate().alpha(0.18f).setDuration(900).setStartDelay((position * 200 + 400).toLong()).withEndAction {
            holder.topLeftGlare.animate().alpha(0f).setDuration(900).setStartDelay(1200).start()
        }.start()
        holder.bottomRightGlare.animate().alpha(0.13f).setDuration(1200).setStartDelay((position * 200 + 1200).toLong()).withEndAction {
            holder.bottomRightGlare.animate().alpha(0f).setDuration(900).setStartDelay(1200).start()
        }.start()
    }

    override fun getItemCount() = items.size
} 