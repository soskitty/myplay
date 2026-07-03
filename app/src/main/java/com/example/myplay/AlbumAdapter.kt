package com.example.myplay

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlbumAdapter(
    private val albums: MutableList<Album>,
    private val selectedAlbumId: () -> Long?,
    private val onClick: (Album) -> Unit,
    private val onDelete: (Album) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.ViewHolder>() {

    fun submitList(newAlbums: List<Album>) {
        albums.clear()
        albums.addAll(newAlbums)
        notifyDataSetChanged()
    }

    fun getAlbums(): MutableList<Album> = albums

    fun moveItem(from: Int, to: Int) {
        val moved = albums.removeAt(from)
        albums.add(to, moved)
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = albums[position]
        holder.name.text = album.name
        holder.detail.text = "第 ${album.trackIndex + 1} 集 · ${formatTime(album.positionMs)}"
        val isSelected = selectedAlbumId() == album.id
        holder.itemView.alpha = if (isSelected) 1f else 0.82f
        holder.itemView.setBackgroundResource(if (isSelected) R.drawable.panel_bg_selected else R.drawable.panel_bg)
        holder.itemView.setOnClickListener { onClick(album) }
        holder.itemView.setOnLongClickListener { onDelete(album); true }
    }

    override fun getItemCount(): Int = albums.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_name)
        val detail: TextView = view.findViewById(R.id.tv_detail)
    }
}
