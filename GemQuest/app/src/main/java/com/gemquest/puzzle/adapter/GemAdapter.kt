package com.gemquest.puzzle.adapter

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gemquest.puzzle.databinding.ItemGemBinding
import com.gemquest.puzzle.model.Gem
import com.gemquest.puzzle.model.Position

class GemAdapter(
    private val onGemClick: (Position) -> Unit
) : ListAdapter<Gem?, GemAdapter.GemViewHolder>(GemDiffCallback()) {

    private var selectedPosition: Position? = null

    inner class GemViewHolder(
        private val binding: ItemGemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(gem: Gem?) {
            if (gem == null) {
                binding.gemContainer.visibility = View.INVISIBLE
                return
            }

            binding.gemContainer.visibility = View.VISIBLE
            binding.textGem.text = gem.type.emoji

            // Set background color
            val color = ContextCompat.getColor(binding.root.context, gem.type.colorRes)
            binding.gemContainer.setCardBackgroundColor(color)

            // Handle selection
            val isSelected = selectedPosition?.let {
                it.row == gem.row && it.col == gem.col
            } ?: false

            binding.gemContainer.elevation = if (isSelected) 16f else 8f
            binding.gemContainer.scaleX = if (isSelected) 1.1f else 1f
            binding.gemContainer.scaleY = if (isSelected) 1.1f else 1f

            // Click listener
            binding.root.setOnClickListener {
                onGemClick(Position(gem.row, gem.col))
            }

            // Entrance animation
            if (gem.row < 2) {
                binding.gemContainer.alpha = 0f
                binding.gemContainer.translationY = -100f
                binding.gemContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .start()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GemViewHolder {
        val binding = ItemGemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setSelectedPosition(position: Position) {
        val oldPosition = selectedPosition
        selectedPosition = position
        
        oldPosition?.let {
            notifyItemChanged(it.row * 8 + it.col)
        }
        notifyItemChanged(position.row * 8 + position.col)
    }

    fun clearSelection() {
        selectedPosition?.let {
            val pos = it
            selectedPosition = null
            notifyItemChanged(pos.row * 8 + pos.col)
        }
    }

    class GemDiffCallback : DiffUtil.ItemCallback<Gem?>() {
        override fun areItemsTheSame(oldItem: Gem?, newItem: Gem?): Boolean {
            if (oldItem == null || newItem == null) return oldItem == newItem
            return oldItem.row == newItem.row && oldItem.col == newItem.col
        }

        override fun areContentsTheSame(oldItem: Gem?, newItem: Gem?): Boolean {
            return oldItem == newItem
        }
    }
}
