package islamic.duas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import islamic.duas.databinding.ItemDuaBinding

class DuaAdapter(
    private val duas: List<Dua>
) : RecyclerView.Adapter<DuaAdapter.DuaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DuaViewHolder {
        val binding = ItemDuaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DuaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DuaViewHolder, position: Int) {
        holder.bind(duas[position])
    }

    override fun getItemCount() = duas.size

    class DuaViewHolder(
        private val binding: ItemDuaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(dua: Dua) {
            binding.categoryText.text = dua.category
            binding.titleText.text = dua.title
            binding.arabicText.text = dua.arabic
            binding.urduText.text = dua.urdu
        }
    }
}
