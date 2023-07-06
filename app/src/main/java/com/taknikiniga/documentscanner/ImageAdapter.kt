package com.taknikiniga.documentscanner

import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.taknikiniga.documentscanner.databinding.ItemImageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

class ImageAdapter : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

     var images = mutableListOf<ImageListModel>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {

        return ImageViewHolder(
            ItemImageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position])
    }

    override fun getItemCount() = images.size

    inner class ImageViewHolder(private val binding : ItemImageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item :ImageListModel ){
            binding.bind = item

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val contentResolver = binding.root.context.contentResolver
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver,item.imageUri)
                    Glide.with(binding.root.context).load(bitmap).into(binding.image)

//                binding.image.setImageBitmap(bitmap)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

        }
    }
}

