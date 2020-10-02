package com.asksira.bsimagepicker

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.asksira.bsimagepicker.ImageTileAdapter.BaseHolder
import com.asksira.bsimagepicker.databinding.ItemPickerCameraTileBinding
import com.asksira.bsimagepicker.databinding.ItemPickerGalleryTileBinding
import com.asksira.bsimagepicker.databinding.ItemPickerImageTileBinding

/**
 * The RecyclerView's adapter of the selectable ivImage tiles.
 */
class ImageTileAdapter(
    private var isMultiSelect: Boolean,
    private var showCameraTile: Boolean,
    private val showGalleryTile: Boolean
) : RecyclerView.Adapter<BaseHolder>() {

    private var imageList: List<Uri> = emptyList()
    private var selectedList: ArrayList<Uri> = arrayListOf()

    private var maximumSelectionCount = Int.MAX_VALUE
    private var nonListItemCount = 0

    private var cameraListener: View.OnClickListener? = null
    private var galleryListener: View.OnClickListener? = null
    private var imageClickListener: View.OnClickListener? = null
    private var selectChangeListener: OnSelectedCountChangeListener? = null
    private var overSelectListener: OnOverSelectListener? = null

    interface OnSelectedCountChangeListener {
        fun onSelectedCountChange(currentCount: Int)
    }

    interface OnOverSelectListener {
        fun onOverSelect()
    }

    fun getSelectedFiles() = selectedList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseHolder {
        return when (viewType) {
            VIEW_TYPE_CAMERA -> {
                val binding = ItemPickerCameraTileBinding.inflate(parent.inflater(), parent, false)
                CameraTileHolder(binding, cameraListener)
            }
            VIEW_TYPE_GALLERY -> {
                val binding = ItemPickerGalleryTileBinding.inflate(parent.inflater(), parent, false)
                GalleryTileHolder(binding, galleryListener)
            }
            VIEW_TYPE_BOTTOM_SPACE -> {
                val view = Space(parent.context)
                val params = ViewGroup.LayoutParams(-1, 48.toPx())
                view.layoutParams = params
                SpaceHolder(view)
            }
            else -> {
                val binding = ItemPickerImageTileBinding.inflate(parent.inflater(), parent, false)
                ImageTileHolder(binding)
            }
        }
    }

    private fun ViewGroup.inflater(): LayoutInflater {
        return LayoutInflater.from(context)
    }

    override fun onBindViewHolder(holder: BaseHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int {
        return if (!isMultiSelect) {
            nonListItemCount + imageList.size
        } else {
            imageList.size + 1
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (!isMultiSelect) {
            when (position) {
                0 -> when {
                    showCameraTile -> {
                        VIEW_TYPE_CAMERA
                    }
                    showGalleryTile -> {
                        VIEW_TYPE_GALLERY
                    }
                    else -> {
                        VIEW_TYPE_IMAGE
                    }
                }
                1 -> if (showCameraTile && showGalleryTile) VIEW_TYPE_GALLERY else VIEW_TYPE_IMAGE
                else -> VIEW_TYPE_IMAGE
            }
        } else {
            if (position == itemCount - 1) return VIEW_TYPE_BOTTOM_SPACE
            VIEW_TYPE_IMAGE
        }
    }

    fun setSelectedFiles(selectedFiles: ArrayList<Uri>) {
        selectedList = selectedFiles
        notifyDataSetChanged()
        selectChangeListener?.onSelectedCountChange(selectedFiles.size)
    }

    fun setImageList(list: List<Uri>) {
        imageList = list
        notifyDataSetChanged()
    }

    fun setCameraTileOnClickListener(listener: View.OnClickListener?) {
        cameraListener = listener
    }

    fun setGalleryTileOnClickListener(listener: View.OnClickListener?) {
        galleryListener = listener
    }

    fun setImageTileOnClickListener(listener: View.OnClickListener?) {
        imageClickListener = listener
    }

    fun setOnSelectedCountChangeListener(listener: OnSelectedCountChangeListener?) {
        selectChangeListener = listener
    }

    fun setOnOverSelectListener(listener: OnOverSelectListener?) {
        overSelectListener = listener
    }

    fun setMaximumSelectionCount(count: Int) {
        maximumSelectionCount = count
    }

    abstract class BaseHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {
        abstract fun bind(position: Int)
    }

    class CameraTileHolder(
        private val binding: ItemPickerCameraTileBinding,
        private val listener: View.OnClickListener?
    ) : BaseHolder(binding.root) {

        override fun bind(position: Int) {
            binding.root.setOnClickListener(listener)
        }
    }

    class GalleryTileHolder(
        private val binding: ItemPickerGalleryTileBinding,
        private val listener: View.OnClickListener?
    ) : BaseHolder(binding.root) {

        override fun bind(position: Int) {
            binding.root.setOnClickListener(listener)
        }
    }

    inner class ImageTileHolder(
        private val binding: ItemPickerImageTileBinding
    ) : BaseHolder(binding.root) {

        override fun bind(position: Int) {
            val imageUri = imageList[position - nonListItemCount]
            itemView.tag = imageUri
            binding.imageContent.load(imageUri)
            binding.viewDarken.visibility = if (selectedList.contains(imageUri)) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            binding.imageTick.visibility = if (selectedList.contains(imageUri)) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        }

        init {
            if (!isMultiSelect) {
                itemView.setOnClickListener(imageClickListener)
            } else {
                itemView.setOnClickListener(View.OnClickListener {
                    val thisFile = imageList[adapterPosition]
                    if (selectedList.contains(thisFile)) {
                        selectedList.remove(thisFile)
                        notifyItemChanged(adapterPosition)
                    } else {
                        if (selectedList.size == maximumSelectionCount) {
                            overSelectListener?.onOverSelect()
                            return@OnClickListener
                        } else {
                            selectedList.add(thisFile)
                            notifyItemChanged(adapterPosition)
                        }
                    }
                    selectChangeListener?.onSelectedCountChange(selectedList.size)
                })
            }
        }
    }

    class SpaceHolder(itemView: View?) : BaseHolder(itemView) {
        override fun bind(position: Int) {
        }
    }

    companion object {
        private const val VIEW_TYPE_CAMERA = 101
        private const val VIEW_TYPE_GALLERY = 102
        private const val VIEW_TYPE_IMAGE = 103
        private const val VIEW_TYPE_BOTTOM_SPACE = 105
    }

    init {
        nonListItemCount = if (isMultiSelect) {
            0
        } else {
            if (showCameraTile && showGalleryTile) {
                2
            } else if (showCameraTile || showGalleryTile) {
                1
            } else {
                0
            }
        }
    }
}