package com.asksira.bsimagepicker

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.asksira.bsimagepicker.BSImagePicker.ImageLoaderDelegate
import com.asksira.bsimagepicker.ImageTileAdapter.BaseViewHolder

/**
 * The RecyclerView's adapter of the selectable ivImage tiles.
 */
class ImageTileAdapter(
    private var context: Context,
    private val imageLoaderDelegate: ImageLoaderDelegate,
    private var isMultiSelect: Boolean,
    private var showCameraTile: Boolean = true,
    private val showGalleryTile: Boolean = true
) : RecyclerView.Adapter<BaseViewHolder>() {

    private var imageList: List<Uri> = emptyList()
    private var selectedFiles: ArrayList<Uri> = arrayListOf()

    private var maximumSelectionCount = Int.MAX_VALUE
    private var nonListItemCount = 0

    private var cameraTileClickListener: View.OnClickListener? = null
    private var galleryTileClickListener: View.OnClickListener? = null
    private var imageTileClickListener: View.OnClickListener? = null
    private var onSelectedCountChangeListener: OnSelectedCountChangeListener? = null
    private var onOverSelectListener: OnOverSelectListener? = null

    interface OnSelectedCountChangeListener {
        fun onSelectedCountChange(currentCount: Int)
    }

    interface OnOverSelectListener {
        fun onOverSelect()
    }

    fun getSelectedFiles() = selectedFiles

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_CAMERA -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_picker_camera_tile, parent, false)
                CameraTileViewHolder(view)
            }
            VIEW_TYPE_GALLERY -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_picker_gallery_tile, parent, false)
                GalleryTileViewHolder(view)
            }
            VIEW_TYPE_DUMMY -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_picker_dummy_tile, parent, false)
                DummyViewHolder(view)
            }
            VIEW_TYPE_BOTTOM_SPACE -> {
                val view = View(context)
                val lp = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Utils.dp2px(48))
                view.layoutParams = lp
                DummyViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_picker_image_tile, parent, false)
                ImageTileViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
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
        this.selectedFiles = selectedFiles
        notifyDataSetChanged()
        onSelectedCountChangeListener?.onSelectedCountChange(selectedFiles.size)
    }

    fun setImageList(imageList: List<Uri>) {
        this.imageList = imageList
        notifyDataSetChanged()
    }

    fun setCameraTileOnClickListener(cameraTileOnClickListener: View.OnClickListener?) {
        this.cameraTileClickListener = cameraTileOnClickListener
    }

    fun setGalleryTileOnClickListener(galleryTileOnClickListener: View.OnClickListener?) {
        this.galleryTileClickListener = galleryTileOnClickListener
    }

    fun setImageTileOnClickListener(imageTileOnClickListener: View.OnClickListener?) {
        this.imageTileClickListener = imageTileOnClickListener
    }

    fun setOnSelectedCountChangeListener(onSelectedCountChangeListener: OnSelectedCountChangeListener?) {
        this.onSelectedCountChangeListener = onSelectedCountChangeListener
    }

    fun setMaximumSelectionCount(maximumSelectionCount: Int) {
        this.maximumSelectionCount = maximumSelectionCount
    }

    fun setOnOverSelectListener(onOverSelectListener: OnOverSelectListener?) {
        this.onOverSelectListener = onOverSelectListener
    }

    abstract class BaseViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {
        abstract fun bind(position: Int)
    }

    inner class CameraTileViewHolder(itemView: View) : BaseViewHolder(itemView) {
        override fun bind(position: Int) {
        }

        init {
            itemView.setOnClickListener(cameraTileClickListener)
        }
    }

    inner class GalleryTileViewHolder(itemView: View) : BaseViewHolder(itemView) {
        override fun bind(position: Int) {
        }

        init {
            itemView.setOnClickListener(galleryTileClickListener)
        }
    }

    inner class ImageTileViewHolder(itemView: View) : BaseViewHolder(itemView) {

        private var darken: View = itemView.findViewById(R.id.imageTile_selected_darken)
        private var ivImage: ImageView = itemView.findViewById(R.id.item_imageTile)
        private var ivTick: ImageView = itemView.findViewById(R.id.imageTile_selected)

        override fun bind(position: Int) {
            val imageFile = imageList[position - nonListItemCount]
            itemView.tag = imageFile
            imageLoaderDelegate.loadImage(imageFile, ivImage)
            darken.visibility = if (selectedFiles.contains(imageFile)) View.VISIBLE else View.INVISIBLE
            ivTick.visibility = if (selectedFiles.contains(imageFile)) View.VISIBLE else View.INVISIBLE
        }

        init {
            if (!isMultiSelect) {
                itemView.setOnClickListener(imageTileClickListener)
            } else {
                itemView.setOnClickListener(View.OnClickListener {
                    val thisFile = imageList[adapterPosition]
                    if (selectedFiles.contains(thisFile)) {
                        selectedFiles.remove(thisFile)
                        notifyItemChanged(adapterPosition)
                    } else {
                        if (selectedFiles.size == maximumSelectionCount) {
                            if (onOverSelectListener != null) onOverSelectListener!!.onOverSelect()
                            return@OnClickListener
                        } else {
                            selectedFiles.add(thisFile)
                            notifyItemChanged(adapterPosition)
                        }
                    }
                    if (onSelectedCountChangeListener != null) {
                        onSelectedCountChangeListener!!.onSelectedCountChange(selectedFiles.size)
                    }
                })
            }
        }
    }

    inner class DummyViewHolder(itemView: View?) : BaseViewHolder(itemView) {
        override fun bind(position: Int) {
        }
    }

    companion object {
        private const val VIEW_TYPE_CAMERA = 101
        private const val VIEW_TYPE_GALLERY = 102
        private const val VIEW_TYPE_IMAGE = 103
        private const val VIEW_TYPE_DUMMY = 104
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