package com.asksira.bsimagepicker

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.IntRange
import androidx.annotation.Px
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.asksira.bsimagepicker.ImageTileAdapter.OnOverSelectListener
import com.asksira.bsimagepicker.ImageTileAdapter.OnSelectedCountChangeListener
import com.asksira.bsimagepicker.databinding.LayoutImagePickerSheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * This is the core class of this library, which extends BottomSheetDialogFragment
 * from the design support library, in order to provide the basic architecture of a bottom sheet.
 *
 *
 * It is also responsible for:
 * - Handling permission
 * - Communicate with caller activity / fragment
 * - As a view controller
 */
class ImagePicker : BottomSheetDialogFragment(), LoaderManager.LoaderCallbacks<Cursor?> {

    // Views
    private var bottomBarView: View? = null
    private var tvDone: TextView? = null
    private var tvMultiSelectMessage: TextView? = null

    private var _binding: LayoutImagePickerSheetBinding? = null
    private val binding get() = _binding!!

    private var bottomSheetBehavior: BottomSheetBehavior<*>? = null
    private var imageListener: ImageListener? = null

    // Component
    private var adapter: ImageTileAdapter? = null

    // Callback
    interface ImageListener : Serializable {
        fun onSingleSelect(uri: Uri?) {}
        fun onMultipleSelect(uriList: List<Uri>?) {}
        fun onCancelled(isMultiSelecting: Boolean) {}
    }

    // State
    private var isMultiSelection = false
    private var dismissOnSelect = true
    private var currentPhotoUri: Uri? = null

    // Configuration
    private var maxDisplayingImage = Int.MAX_VALUE
    private var maxMultiSelectCount = Int.MAX_VALUE
    private var providerAuthority: String? = null
    private var showCameraTile = true
    private var showGalleryTile = true
    private var showOverSelectMessage = true
    private var minMultiSelectCount = 1
    private var spanCount = 3
    private var peekHeight = 360.toPx()
    private var gridSpacing = 2.toPx()

    @ColorRes
    private var multiSelectBarBgColor = android.R.color.white

    @ColorRes
    private var multiSelectTextColor = R.color.primary_text

    @ColorRes
    private var multiSelectDoneTextColor = R.color.multi_select_done

    @ColorRes
    private var overSelectTextColor = R.color.error_text

    /**
     * Here we check if the caller Activity has registered callback and reference it.
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ImageListener) {
            imageListener = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadConfigFromBuilder()
        if (isReadStorageGranted()) {
            LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this@ImagePicker)
        } else {
            checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, PERMISSION_READ_STORAGE)
        }
        if (savedInstanceState != null) {
            currentPhotoUri = savedInstanceState.getParcelable("currentPhotoUri")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = LayoutImagePickerSheetBinding.inflate(inflater, container, false)

        /**
         * Here we check if the parent fragment has registered callback and reference it.
         */
        if (parentFragment != null && parentFragment is ImageListener) {
            imageListener = parentFragment as ImageListener?
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Here we make the bottom bar fade out when the Dialog is being slided down.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { dialogInterface -> // Get the BottomSheetBehavior
            val dialog = dialogInterface as BottomSheetDialog
            val bottomSheet = dialog.findViewById<FrameLayout>(R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
                (bottomSheetBehavior)?.peekHeight = peekHeight
                (bottomSheetBehavior)?.addBottomSheetCallback(object : BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        when (newState) {
                            BottomSheetBehavior.STATE_HIDDEN -> dismiss()
                            BottomSheetBehavior.STATE_COLLAPSED -> {
                            }
                            BottomSheetBehavior.STATE_DRAGGING -> {
                            }
                            BottomSheetBehavior.STATE_EXPANDED -> {
                            }
                            BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                            }
                            BottomSheetBehavior.STATE_SETTLING -> {
                            }
                        }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        bottomBarView?.alpha = if (slideOffset < 0) 1f + slideOffset else 1f
                    }
                })
            }
        }
        return bottomSheetDialog
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        imageListener?.onCancelled(isMultiSelection)
    }

    /**
     * Here we create and setup the bottom bar if in multi-selection mode.
     */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (isMultiSelection) {
            setupBottomBar(view)
        }
        if (savedInstanceState != null && adapter != null) {
            val savedUriList: ArrayList<Uri>? = savedInstanceState.getParcelableArrayList("selectedImages")
            if (savedUriList != null) {
                adapter?.setSelectedFiles(savedUriList)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (context == null) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }
        when (requestCode) {
            PERMISSION_READ_STORAGE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this)
            } else {
                dismiss()
            }
            PERMISSION_CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isWriteStorageGranted()) {
                        launchCamera()
                    } else {
                        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_WRITE_STORAGE)
                    }
                }
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isCameraGranted()) {
                        launchCamera()
                    } else {
                        checkPermission(Manifest.permission.CAMERA, PERMISSION_CAMERA)
                    }
                }
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
            PERMISSION_WRITE_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isCameraGranted()) {
                        launchCamera()
                    } else {
                        checkPermission(Manifest.permission.CAMERA, PERMISSION_CAMERA)
                    }
                }
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_TAKE_PHOTO -> if (resultCode == Activity.RESULT_OK) {
                notifyGallery()
                imageListener?.onSingleSelect(currentPhotoUri)
                if (dismissOnSelect) dismiss()
            } else {
                tryCatch {
                    val file = File(URI.create(currentPhotoUri.toString()))
                    file.delete()
                }
            }
            REQUEST_SELECT_FROM_GALLERY -> if (resultCode == Activity.RESULT_OK) {
                imageListener?.onSingleSelect(data!!.data)
                if (dismissOnSelect) dismiss()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList("selectedImages", adapter?.getSelectedFiles())
        outState.putParcelable("currentPhotoUri", currentPhotoUri)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor?> {
        return if (id == LOADER_ID && context != null) {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC"
            CursorLoader(requireContext(), uri, projection, null, null, sortOrder)
        } else {
            CursorLoader(requireContext(), Uri.EMPTY, null, null, null, null)
        }
    }

    override fun onLoadFinished(loader: Loader<Cursor?>, cursor: Cursor?) {
        if (cursor == null) return

        var index = 0
        val uriList: MutableList<Uri> = ArrayList()
        while (cursor.moveToNext() && index < maxDisplayingImage) {
            val id = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media._ID))
            val baseUri = Uri.parse("content://media/external/images/media")
            uriList.add(Uri.withAppendedPath(baseUri, "" + id))
            index++
        }
        cursor.moveToPosition(-1) //Restore cursor back to the beginning
        adapter?.setImageList(uriList)
        // We are not closing the cursor here because Android Doc says Loader will manage them.

        if (uriList.size < 1 && !showCameraTile && !showGalleryTile) {
            binding.textEmpty.visibility = View.VISIBLE
            bottomBarView?.visibility = View.GONE
        } else {
            binding.textEmpty.visibility = View.INVISIBLE
            bottomBarView?.visibility = View.VISIBLE
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor?>) {
        adapter?.setImageList(emptyList())
    }

    private fun loadConfigFromBuilder() {
        val bundle = arguments ?: return
        tryCatch {
            providerAuthority = bundle.getString("providerAuthority")
            isMultiSelection = bundle.getBoolean("isMultiSelect")
            dismissOnSelect = bundle.getBoolean("dismissOnSelect")
            maxDisplayingImage = bundle.getInt("maximumDisplayingImages")
            minMultiSelectCount = bundle.getInt("minimumMultiSelectCount")
            maxMultiSelectCount = bundle.getInt("maximumMultiSelectCount")
            if (isMultiSelection) {
                showCameraTile = false
                showGalleryTile = false
            } else {
                showCameraTile = bundle.getBoolean("showCameraTile")
                showGalleryTile = bundle.getBoolean("showGalleryTile")
            }
            spanCount = bundle.getInt("spanCount")
            peekHeight = bundle.getInt("peekHeight")
            gridSpacing = bundle.getInt("gridSpacing")
            multiSelectBarBgColor = bundle.getInt("multiSelectBarBgColor")
            multiSelectTextColor = bundle.getInt("multiSelectTextColor")
            multiSelectDoneTextColor = bundle.getInt("multiSelectDoneTextColor")
            showOverSelectMessage = bundle.getBoolean("showOverSelectMessage")
            overSelectTextColor = bundle.getInt("overSelectTextColor")
            if (imageListener == null) {
                imageListener = bundle.getSerializable(ARG_IMAGE_SELECT_LISTENER) as ImageListener
            }
        }

        require(imageListener != null) {
            "Your caller activity or parent fragment must implements ImageListener"
        }
    }

    private fun setupRecyclerView() {
        /**
         * We are disabling item change animation because the default animation is fade out fade in, which will
         * appear a little bit strange due to the fact that we are darkening the cell at the same time.
         */
        (binding.recyclerView.itemAnimator as SimpleItemAnimator?)?.supportsChangeAnimations = false
        binding.recyclerView.addItemDecoration(GridItemSpacingDecoration(spanCount, gridSpacing, false))
        if (adapter == null) {
            adapter = ImageTileAdapter(
                isMultiSelection,
                showCameraTile,
                showGalleryTile
            )
            adapter?.setMaximumSelectionCount(maxMultiSelectCount)
            adapter?.setCameraTileOnClickListener {
                if (isCameraGranted() && isWriteStorageGranted()) {
                    launchCamera()
                } else {
                    if (isCameraGranted()) {
                        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_WRITE_STORAGE)
                    } else {
                        checkPermission(Manifest.permission.CAMERA, PERMISSION_CAMERA)
                    }
                }
            }
            adapter?.setGalleryTileOnClickListener {
                if (!isMultiSelection) {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(intent, REQUEST_SELECT_FROM_GALLERY)
                }
            }
            adapter?.setImageTileOnClickListener { v ->
                if (v.tag != null && v.tag is Uri) {
                    imageListener?.onSingleSelect(v.tag as Uri)
                    if (dismissOnSelect) dismiss()
                }
            }
            if (isMultiSelection) {
                adapter?.setOnSelectedCountChangeListener(object : OnSelectedCountChangeListener {
                    override fun onSelectedCountChange(currentCount: Int) {
                        updateSelectCount(currentCount)
                    }
                })
                adapter?.setOnOverSelectListener(object : OnOverSelectListener {
                    override fun onOverSelect() {
                        if (showOverSelectMessage) showOverSelectMessage()
                    }
                })
            }
        }
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = layoutManager
    }

    private fun setupBottomBar(rootView: View?) {
        val parentView = rootView?.parent?.parent as CoordinatorLayout?
        bottomBarView = LayoutInflater.from(context).inflate(R.layout.item_picker_multi_selection_bar, parentView, false)
        ViewCompat.setTranslationZ(bottomBarView!!, ViewCompat.getZ((rootView?.parent as View)))
        parentView?.addView(bottomBarView, -2)
        bottomBarView?.findViewById<View>(R.id.multi_select_bar_bg)?.setBackgroundColor(ContextCompat.getColor(context!!, multiSelectBarBgColor))
        tvMultiSelectMessage = bottomBarView?.findViewById(R.id.tv_multi_select_message)
        tvMultiSelectMessage?.setTextColor(ContextCompat.getColor(context!!, multiSelectTextColor))
        tvMultiSelectMessage?.text = if (minMultiSelectCount == 1) {
            getString(R.string.title_multi_select_not_enough_singular)
        } else {
            getString(R.string.title_multi_select_not_enough_plural, minMultiSelectCount)
        }
        tvDone = bottomBarView?.findViewById(R.id.tv_multi_select_done)
        tvDone?.setTextColor(ContextCompat.getColor(context!!, multiSelectDoneTextColor))
        tvDone?.setOnClickListener {
            imageListener?.onMultipleSelect(adapter?.getSelectedFiles())
            dismiss()
        }
        tvDone?.alpha = 0.4f
        tvDone?.isEnabled = false
    }

    private fun launchCamera() {
        if (context == null) return
        val takePhotoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val packageManager = requireContext().packageManager
        if (takePhotoIntent.resolveActivity(packageManager) != null) {
            var photoFile: File? = null
            tryCatch { photoFile = createImageFile() }
            if (photoFile != null) {
                val photoURI = FileProvider.getUriForFile(
                    context!!,
                    providerAuthority!!,
                    photoFile!!
                )
                takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                val resolvedIntentActivities =
                    packageManager.queryIntentActivities(takePhotoIntent, PackageManager.MATCH_DEFAULT_ONLY)
                for (resolvedIntentInfo in resolvedIntentActivities) {
                    context?.grantUriPermission(
                        resolvedIntentInfo.activityInfo.packageName,
                        photoURI,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                startActivityForResult(takePhotoIntent, REQUEST_TAKE_PHOTO)
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val currentTime = Calendar.getInstance().time
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(currentTime)
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = context?.getExternalFilesDir(Environment.DIRECTORY_DCIM)

        //        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        val image = File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",  /* suffix */
            storageDir /* directory */
        )

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoUri = Uri.fromFile(image)
        return image
    }

    private fun notifyGallery() {
        if (context == null) return
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = currentPhotoUri
        context?.sendBroadcast(mediaScanIntent)
    }

    private fun updateSelectCount(newCount: Int) {
        if (context == null) return
        if (tvMultiSelectMessage != null) {
            tvMultiSelectMessage?.setTextColor(ContextCompat.getColor(context!!, multiSelectTextColor))
            if (newCount < minMultiSelectCount) {
                tvMultiSelectMessage?.text = if (minMultiSelectCount - newCount == 1) {
                    getString(R.string.title_multi_select_not_enough_singular)
                } else {
                    getString(R.string.title_multi_select_not_enough_plural, minMultiSelectCount - newCount)
                }
                tvDone?.alpha = 0.4f
                tvDone?.isEnabled = false
            } else {
                tvMultiSelectMessage?.text = if (newCount == 1) {
                    getString(R.string.title_multi_select_enough_singular)
                } else {
                    getString(R.string.title_multi_select_enough_plural, newCount)
                }
                tvDone?.alpha = 1f
                tvDone?.isEnabled = true
            }
        }
    }

    private fun showOverSelectMessage() {
        if (context != null) {
            tvMultiSelectMessage?.setTextColor(ContextCompat.getColor(context!!, overSelectTextColor))
            tvMultiSelectMessage?.text = getString(R.string.title_multi_select_over_select, maxMultiSelectCount)
        }
    }

    /**
     * Builder of the BSImagePicker.
     * Caller should always create the dialog using this builder.
     */
    class Builder(
        private val providerAuthority: String
    ) {

        private var _tag: String? = null
        private var _isMultiSelect = false
        private var _dismissOnSelect = true
        private var _maxDisplayingImage = Int.MAX_VALUE
        private var _minMultiSelectCount = 1
        private var _maxMultiSelectCount = Int.MAX_VALUE
        private var _showCameraTile = true
        private var _showGalleryTile = true
        private var _peekHeight = 360.toPx()
        private var _gridSpacing = 2.toPx()
        private var _spanCount = 3
        private var isShowOverSelectMessage = true
        private var imageSelectListener: ImageListener? = null

        @ColorRes
        private var multiSelectBarBgColor = android.R.color.white

        @ColorRes
        private var multiSelectTextColor = R.color.primary_text

        @ColorRes
        private var multiSelectDoneTextColor = R.color.multi_select_done

        @ColorRes
        private var overSelectTextColor = R.color.error_text

        fun isMultiSelect(): Builder {
            _isMultiSelect = true
            return this
        }

        fun setListener(listener: ImageListener): Builder {
            imageSelectListener = listener
            return this
        }

        fun dismissOnSelect(dismiss: Boolean): Builder {
            _dismissOnSelect = dismiss
            return this
        }

        fun setMaximumDisplayingImages(@IntRange(from = 1, to = Int.MAX_VALUE.toLong()) maximumDisplayingImages: Int): Builder {
            _maxDisplayingImage = maximumDisplayingImages
            return this
        }

        fun setMinimumMultiSelectCount(@IntRange(from = 1, to = Int.MAX_VALUE.toLong()) minimumMultiSelectCount: Int): Builder {
            _minMultiSelectCount = minimumMultiSelectCount
            return this
        }

        fun setMaximumMultiSelectCount(@IntRange(from = 1, to = Int.MAX_VALUE.toLong()) maximumMultiSelectCount: Int): Builder {
            _maxMultiSelectCount = maximumMultiSelectCount
            return this
        }

        fun setGridSpacing(@Px spacing: Int): Builder {
            require(_gridSpacing >= 0) { "Grid spacing must be >= 0" }
            _gridSpacing = spacing
            return this
        }

        fun setMultiSelectBarBgColor(@ColorRes multiSelectColor: Int): Builder {
            multiSelectBarBgColor = multiSelectColor
            return this
        }

        fun setTag(tag: String?): Builder {
            this._tag = tag
            return this
        }

        fun setMultiSelectDoneTextColor(@ColorRes color: Int): Builder {
            multiSelectDoneTextColor = color
            return this
        }

        fun setMultiSelectTextColor(@ColorRes color: Int): Builder {
            multiSelectTextColor = color
            return this
        }

        fun setOverSelectTextColor(@ColorRes color: Int): Builder {
            overSelectTextColor = color
            return this
        }

        fun setPeekHeight(@Px height: Int): Builder {
            require(height >= 0) { "Peek Height must be >= 0" }
            _peekHeight = height
            return this
        }

        fun hideCameraTile(): Builder {
            _showCameraTile = false
            return this
        }

        fun hideGalleryTile(): Builder {
            _showGalleryTile = false
            return this
        }

        fun disableOverSelectionMessage(): Builder {
            isShowOverSelectMessage = false
            return this
        }

        fun setSpanCount(@IntRange(from = 1, to = 10) spanCount: Int): Builder {
            require(spanCount >= 0) { "Span Count must be > 0" }
            _spanCount = spanCount
            return this
        }

        fun build(): ImagePicker {
            val args = Bundle()
            args.putString("providerAuthority", providerAuthority)
            args.putString("tag", _tag)
            args.putBoolean("isMultiSelect", _isMultiSelect)
            args.putBoolean("dismissOnSelect", _dismissOnSelect)
            args.putInt("maximumDisplayingImages", _maxDisplayingImage)
            args.putInt("minimumMultiSelectCount", _minMultiSelectCount)
            args.putInt("maximumMultiSelectCount", _maxMultiSelectCount)
            args.putBoolean("showCameraTile", _showCameraTile)
            args.putBoolean("showGalleryTile", _showGalleryTile)
            args.putInt("peekHeight", _peekHeight)
            args.putInt("spanCount", _spanCount)
            args.putInt("gridSpacing", _gridSpacing)
            args.putInt("multiSelectBarBgColor", multiSelectBarBgColor)
            args.putInt("multiSelectTextColor", multiSelectTextColor)
            args.putInt("multiSelectDoneTextColor", multiSelectDoneTextColor)
            args.putBoolean("showOverSelectMessage", isShowOverSelectMessage)
            args.putInt("overSelectTextColor", overSelectTextColor)
            args.putSerializable(ARG_IMAGE_SELECT_LISTENER, imageSelectListener)
            val fragment = ImagePicker()
            fragment.arguments = args
            return fragment
        }
    }

    companion object {
        private const val LOADER_ID = 1000
        private const val PERMISSION_READ_STORAGE = 2001
        private const val PERMISSION_CAMERA = 2002
        private const val PERMISSION_WRITE_STORAGE = 2003
        private const val REQUEST_TAKE_PHOTO = 3001
        private const val REQUEST_SELECT_FROM_GALLERY = 3002

        // constant
        private const val ARG_IMAGE_SELECT_LISTENER = "image_select_listener"
        private const val ARG_OPEN_FRONT_CAMERA = "open_front_camera"
        private const val ARG_OVER_SELECT_TEXT_COLOR = "over_select_text_color"
        private const val ARG_OVER_SELECT_MESSAGE = "over_select_message"
        private const val ARG_MULTI_SELECT_DONE_TEXT_COLOR = "multi_select_done_text_color"
        private const val ARG_MULTI_SELECT_TEXT_COLOR = "multi_select_text_color"
    }

}