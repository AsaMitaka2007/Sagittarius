package com.dsha.sagittarius.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.FoodEntity
import com.google.android.material.card.MaterialCardView
import java.util.Locale

/** 食材胶囊：点击详情、长按拖动、原地松手显示删除。 */
class FoodCapsuleAdapter(
    private val onClick: (FoodEntity, View) -> Unit,
    private val onQuickAdd: (FoodEntity) -> Unit,
    private val onDelete: (FoodEntity) -> Unit
) : ListAdapter<FoodEntity, FoodCapsuleAdapter.Holder>(Diff) {

    private var pendingDeleteId: Long? = null

    fun ids(): List<Long> = currentList.map { it.id }

    fun move(from: Int, to: Int): Boolean {
        if (from !in currentList.indices || to !in currentList.indices) return false
        val list = currentList.toMutableList()
        list.add(to, list.removeAt(from))
        submitList(list)
        return true
    }

    fun startDrag(position: Int) {
        pendingDeleteId = null
        notifyItemChanged(position)
    }

    fun showDelete(position: Int) {
        val food = getItemOrNull(position) ?: return
        pendingDeleteId = food.id
        notifyItemChanged(position)
    }

    fun clearDelete(foodId: Long) {
        if (pendingDeleteId == foodId) pendingDeleteId = null
    }

    private fun getItemOrNull(position: Int): FoodEntity? =
        currentList.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_food_capsule, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.card)
        private val image: ImageView = view.findViewById(R.id.ivFood)
        private val name: TextView = view.findViewById(R.id.tvFoodName)
        private val kcal: TextView = view.findViewById(R.id.tvFoodKcal)
        private val quickAdd: TextView = view.findViewById(R.id.btnQuickAdd)
        private val delete: TextView = view.findViewById(R.id.btnDelete)
        private val density = view.resources.displayMetrics.density

        fun bind(food: FoodEntity) {
            card.radius = 26f * density
            card.scaleX = 1f
            card.scaleY = 1f
            name.alpha = 1f
            kcal.alpha = 1f
            quickAdd.visibility = View.VISIBLE
            delete.visibility = if (pendingDeleteId == food.id) View.VISIBLE else View.GONE
            val bitmap = FoodImageLoader.load(itemView.context, food.imageUri)
            if (bitmap != null) image.setImageBitmap(bitmap)
            else image.setImageResource(R.drawable.ic_food_placeholder)
            name.text = food.name
            kcal.text = String.format(Locale.CHINA, "%.0f kcal / 100g", food.caloriesPer100g)
            card.setOnClickListener {
                card.animate().scaleX(1.06f).scaleY(1.06f).setDuration(80L).withEndAction {
                    card.scaleX = 1f
                    card.scaleY = 1f
                    onClick(food, card)
                }.start()
            }
            quickAdd.setOnClickListener {
                delete.visibility = View.GONE
                pendingDeleteId = null
                onQuickAdd(food)
            }
            delete.setOnClickListener {
                pendingDeleteId = null
                delete.visibility = View.GONE
                onDelete(food)
            }
        }

        fun enhanceDrag() {
            card.animate()
                .scaleX(0.9f)
                .scaleY(0.86f)
                .setDuration(120L)
                .start()
            card.radius = itemView.height / 2f
            name.alpha = 0.25f
            kcal.alpha = 0.25f
            quickAdd.visibility = View.GONE
            delete.visibility = View.GONE
        }

        fun resetDrag() {
            card.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
            card.radius = 26f * density
            name.alpha = 1f
            kcal.alpha = 1f
            quickAdd.visibility = View.VISIBLE
        }
    }

    private object Diff : DiffUtil.ItemCallback<FoodEntity>() {
        override fun areItemsTheSame(oldItem: FoodEntity, newItem: FoodEntity) = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FoodEntity, newItem: FoodEntity) = oldItem == newItem
    }
}
