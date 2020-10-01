package com.asksira.bsimagepicker

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.Px
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.asksira.bsimagepicker.ImageTileAdapter.OnOverSelectListener
import com.asksira.bsimagepicker.ImageTileAdapter.OnSelectedCountChangeListener
import com.asksira.bsimagepicker.Utils.checkPermission
import com.asksira.bsimagepicker.Utils.dp2px
import com.asksira.bsimagepicker.Utils.isCameraGranted
import com.asksira.bsimagepicker.Utils.isReadStorageGranted
import com.asksira.bsimagepicker.Utils.isWriteStorageGranted
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import java.io.IOException
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
class BSImagePicker : BottomSheetDialogFragment(), LoaderManager.LoaderCallbacks<Cursor?> {

    //Views
    private var recyclerView: RecyclerView? = null
    private var bottomBarView: View? = null
    private var tvDone: TextView? = null
    private var tvMultiSelectMessage: TextView? = null

    /**
     * Returns the TextView that appears when there is no item,
     * So that user can customize its styles, etc.
     */
    var emptyTextView: TextView? = null
        private set

    private var bottomSheetBehavior: BottomSheetBehavior<*>? = null
    private var onMultiImageSelectedListener: OnMultiImageSelectedListener? = null
    private var onSingleImageSelectedListener: OnSingleImageSelectedListener? = null
    private var onSelectImageCancelledListener: OnSelectImageCancelledListener? = null
    private var imageLoaderDelegate: ImageLoaderDelegate? = null

    //Components
    private var adapter: ImageTileAdapter? = null

    //Callbacks
    interface OnSingleImageSelectedListener {
        fun onSingleImageSelected(uri: Uri?, tag: String?)
    }

    interface OnMultiImageSelectedListener {
        fun onMultiImageSelected(uriList: List<Uri?>?, tag: String?)
    }

    interface ImageLoaderDelegate {
        fun loadImage(imageUri: Uri?, ivImage: ImageView?)
    }

    interface OnSelectImageCancelledListener {
        fun onCancelled(isMultiSelecting: Boolean, tag: String?)
    }


    //States
    private var isMultiSelection = false
    private var dismissOnSelect = true
    private var currentPhotoUri: Uri? = null

    //Configurations
    private var maximumDisplayingImages = Int.MAX_VALUE
    private var peekHeight = dp2px(360)
    private var minimumMultiSelectCount = 1
    private var maximumMultiSelectCount = Int.MAX_VALUE
    private var providerAuthority: String? = null
    private var showCameraTile = true
    private var showGalleryTile = true
    private var spanCount = 3
    private var gridSpacing = dp2px(2)
    private var multiSelectBarBgColor = android.R.color.white
    private var multiSelectTextColor = R.color.primary_text
    private var multiSelectDoneTextColor = R.color.multiselect_done
    private var showOverSelectMessage = true
    private var overSelectTextColor = R.color.error_text
    private var isOpenFrontCamera = false

    /**
     * Here we check if the caller Activity has registered callback and reference it.
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnSingleImageSelectedListener) {
            onSingleImageSelectedListener = context
        }
        if (context is OnMultiImageSelectedListener) {
            onMultiImageSelectedListener = context
        }
        if (context is ImageLoaderDelegate) {
            imageLoaderDelegate = context
        }
        if (context is OnSelectImageCancelledListener) {
            onSelectImageCancelledListener = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadConfigFromBuilder()
        if (isReadStorageGranted(context)) {
            LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this@BSImagePicker)
        } else {
            checkPermission(this@BSImagePicker, Manifest.permission.READ_EXTERNAL_STORAGE, PERMISSION_READ_STORAGE)
        }
        if (savedInstanceState != null) {
            currentPhotoUri = savedInstanceState.getParcelable("currentPhotoUri")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_imagepicker_sheet, container, false)
        /*
         Here we check if the parent fragment has registered callback and reference it.
         */if (parentFragment != null && parentFragment is OnSingleImageSelectedListener) {
            onSingleImageSelectedListener = parentFragment as OnSingleImageSelectedListener?
        }
        if (parentFragment != null && parentFragment is OnMultiImageSelectedListener) {
            onMultiImageSelectedListener = parentFragment as OnMultiImageSelectedListener?
        }
        if (parentFragment != null && parentFragment is ImageLoaderDelegate) {
            imageLoaderDelegate = parentFragment as ImageLoaderDelegate?
        }
        if (parentFragment != null && parentFragment is OnSelectImageCancelledListener) {
            onSelectImageCancelledListener = parentFragment as OnSelectImageCancelledListener?
        }
        /*
         If no correct callback is registered, throw an exception.
         */
        require(!(isMultiSelection && onMultiImageSelectedListener == null ||
            !isMultiSelection && onSingleImageSelectedListener == null)) { "Your caller activity or parent fragment must implements the correct ImageSelectedListener" }
        requireNotNull(imageLoaderDelegate) { "Your caller activity or parent fragment must implements ImageLoaderDelegate" }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupRecyclerView()
    }

    /**
     * Here we make the bottom bar fade out when the Dialog is being slided down.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialog2 -> //Get the BottomSheetBehavior
            val d = dialog2 as BottomSheetDialog
            val bottomSheet = d.findViewById<FrameLayout>(R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
                (bottomSheetBehavior as BottomSheetBehavior<*>).peekHeight = peekHeight
                (bottomSheetBehavior as BottomSheetBehavior<*>).addBottomSheetCallback(object : BottomSheetCallback() {
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
                        if (bottomBarView != null) {
                            bottomBarView!!.alpha = if (slideOffset < 0) 1f + slideOffset else 1f
                        }
                    }
                })
            }
        }
        return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        if (onSelectImageCancelledListener != null) {
            onSelectImageCancelledListener!!.onCancelled(isMultiSelection, tag)
        }
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
                adapter!!.setSelectedFiles(savedUriList)
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
                loaderManager.initLoader(LOADER_ID, null, this)
            } else {
                dismiss()
            }
            PERMISSION_CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isWriteStorageGranted(context)) {
                        launchCamera()
                    } else {
                        checkPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_WRITE_STORAGE)
                    }
                }
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isCameraGranted(context)) {
                        launchCamera()
                    } else {
                        checkPermission(this, Manifest.permission.CAMERA, PERMISSION_CAMERA)
                    }
                }
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
            PERMISSION_WRITE_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isCameraGranted(context)) {
                        launchCamera()
                    } else {
                        checkPermission(this, Manifest.permission.CAMERA, PERMISSION_CAMERA)
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
                if (onSingleImageSelectedListener != null) {
                    onSingleImageSelectedListener!!.onSingleImageSelected(currentPhotoUri, tag)
                    if (dismissOnSelect) dismiss()
                }
            } else {
                try {
                    val file = File(URI.create(currentPhotoUri.toString()))
                    file.delete()
                } catch (e: Exception) {
//                    if (BuildConfig.DEBUG) Log.d("ImagePicker", "Failed to delete temp file: " + currentPhotoUri.toString())
                }
            }
            REQUEST_SELECT_FROM_GALLERY -> if (resultCode == Activity.RESULT_OK) {
                if (onSingleImageSelectedListener != null) {
                    onSingleImageSelectedListener!!.onSingleImageSelected(data!!.data, tag)
                    if (dismissOnSelect) dismiss()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList("selectedImages", adapter!!.getSelectedFiles())
        outState.putParcelable("currentPhotoUri", currentPhotoUri)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor?> {
        return if (id == LOADER_ID && context != null) {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC"
            CursorLoader(context!!, uri, projection, null, null, sortOrder)
        } else {
            CursorLoader(context!!, Uri.EMPTY, null, null, null, null)
        }
    }

    override fun onLoadFinished(loader: Loader<Cursor?>, cursor: Cursor?) {
        if (cursor != null) {
            val uriList: MutableList<Uri> = ArrayList()
            var index = 0
            while (cursor.moveToNext() && index < maximumDisplayingImages) {
                val id = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media._ID))
                val baseUri = Uri.parse("content://media/external/images/media")
                uriList.add(Uri.withAppendedPath(baseUri, "" + id))
                index++
            }
            cursor.moveToPosition(-1) //Restore cursor back to the beginning
            adapter!!.setImageList(uriList)
            //We are not closing the cursor here because Android Doc says Loader will manage them.
            if (uriList.size < 1 && !showCameraTile && !showGalleryTile) {
                emptyTextView!!.visibility = View.VISIBLE
                if (bottomBarView != null) {
                    bottomBarView!!.visibility = View.GONE
                }
            } else {
                emptyTextView!!.visibility = View.INVISIBLE
                if (bottomBarView != null) {
                    bottomBarView!!.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor?>) {
        adapter!!.setImageList(emptyList())
    }

    private fun loadConfigFromBuilder() {
        try {
            providerAuthority = arguments!!.getString("providerAuthority")
//            tag = arguments!!.getString("tag")
            isMultiSelection = arguments!!.getBoolean("isMultiSelect")
            dismissOnSelect = arguments!!.getBoolean("dismissOnSelect")
            maximumDisplayingImages = arguments!!.getInt("maximumDisplayingImages")
            minimumMultiSelectCount = arguments!!.getInt("minimumMultiSelectCount")
            maximumMultiSelectCount = arguments!!.getInt("maximumMultiSelectCount")
            if (isMultiSelection) {
                showCameraTile = false
                showGalleryTile = false
            } else {
                showCameraTile = arguments!!.getBoolean("showCameraTile")
                showGalleryTile = arguments!!.getBoolean("showGalleryTile")
            }
            spanCount = arguments!!.getInt("spanCount")
            peekHeight = arguments!!.getInt("peekHeight")
            gridSpacing = arguments!!.getInt("gridSpacing")
            multiSelectBarBgColor = arguments!!.getInt("multiSelectBarBgColor")
            multiSelectTextColor = arguments!!.getInt("multiSelectTextColor")
            multiSelectDoneTextColor = arguments!!.getInt("multiSelectDoneTextColor")
            showOverSelectMessage = arguments!!.getBoolean("showOverSelectMessage")
            overSelectTextColor = arguments!!.getInt("overSelectTextColor")
            isOpenFrontCamera = arguments!!.getBoolean("isOpenFrontCamera")
        } catch (e: Exception) {
//            if (BuildConfig.DEBUG) e.printStackTrace()
        }
    }

    private fun bindViews(rootView: View) {
        recyclerView = rootView.findViewById(R.id.picker_recyclerview)
        emptyTextView = rootView.findViewById(R.id.tv_picker_empty_view)
    }

    private fun setupRecyclerView() {
        val gll = GridLayoutManager(context, spanCount)
        recyclerView?.layoutManager = gll
        /* We are disabling item change animation because the default animation is fade out fade in, which will
         * appear a little bit strange due to the fact that we are darkening the cell at the same time. */(recyclerView!!.itemAnimator as SimpleItemAnimator?)!!.supportsChangeAnimations = false
        recyclerView?.addItemDecoration(GridItemSpacingDecoration(spanCount, gridSpacing, false))
        if (adapter == null) {
            adapter = ImageTileAdapter(context!!,
                imageLoaderDelegate!!,
                isMultiSelection,
                showCameraTile,
                showGalleryTile)
            adapter!!.setMaximumSelectionCount(maximumMultiSelectCount)
            adapter!!.setCameraTileOnClickListener {
                if (isCameraGranted(context) && isWriteStorageGranted(context)) {
                    launchCamera()
                } else {
                    if (isCameraGranted(context)) {
                        checkPermission(this@BSImagePicker, Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_WRITE_STORAGE)
                    } else {
                        checkPermission(this@BSImagePicker, Manifest.permission.CAMERA, PERMISSION_CAMERA)
                    }
                }
            }
            adapter!!.setGalleryTileOnClickListener {
                if (!isMultiSelection) {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(intent, REQUEST_SELECT_FROM_GALLERY)
                }
            }
            adapter!!.setImageTileOnClickListener { v ->
                if (v.tag != null && v.tag is Uri && onSingleImageSelectedListener != null) {
                    onSingleImageSelectedListener!!.onSingleImageSelected(v.tag as Uri, tag)
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
        recyclerView!!.adapter = adapter
    }

    private fun setupBottomBar(rootView: View?) {
        val parentView = rootView!!.parent.parent as CoordinatorLayout
        bottomBarView = LayoutInflater.from(context).inflate(R.layout.item_picker_multiselection_bar, parentView, false)
        ViewCompat.setTranslationZ(bottomBarView!!, ViewCompat.getZ((rootView.parent as View)))
        parentView.addView(bottomBarView, -2)
        bottomBarView?.findViewById<View>(R.id.multiselect_bar_bg)?.setBackgroundColor(ContextCompat.getColor(context!!, multiSelectBarBgColor))
        tvMultiSelectMessage = bottomBarView?.findViewById(R.id.tv_multiselect_message)
        tvMultiSelectMessage?.setTextColor(ContextCompat.getColor(context!!, multiSelectTextColor))
        tvMultiSelectMessage?.text = if (minimumMultiSelectCount == 1) {
            getString(R.string.imagepicker_multiselect_not_enough_singular)
        } else {
            getString(R.string.imagepicker_multiselect_not_enough_plural, minimumMultiSelectCount)
        }
        tvDone = bottomBarView?.findViewById(R.id.tv_multiselect_done)
        tvDone?.setTextColor(ContextCompat.getColor(context!!, multiSelectDoneTextColor))
        tvDone?.setOnClickListener {
            onMultiImageSelectedListener?.onMultiImageSelected(adapter!!.getSelectedFiles(), tag)
            dismiss()
        }
        tvDone?.alpha = 0.4f
        tvDone?.isEnabled = false
    }

    private fun launchCamera() {
        if (context == null) return
        val takePhotoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePhotoIntent.resolveActivity(context!!.packageManager) != null) {
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
            } catch (e: IOException) {
//                if (BuildConfig.DEBUG) e.printStackTrace()
            }
            if (photoFile != null) {
                val photoURI = FileProvider.getUriForFile(context!!,
                    providerAuthority!!,
                    photoFile)
                takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                if (isOpenFrontCamera) {
                    //Below does not always work, just a hack.
                    //Reference: https://stackoverflow.com/a/40175503/7870874
                    takePhotoIntent.putExtra("android.intent.extras.CAMERA_FACING", Camera.CameraInfo.CAMERA_FACING_FRONT)
                    takePhotoIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                    takePhotoIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                }
                val resolvedIntentActivities = context!!.packageManager.queryIntentActivities(takePhotoIntent, PackageManager.MATCH_DEFAULT_ONLY)
                for (resolvedIntentInfo in resolvedIntentActivities) {
                    val packageName = resolvedIntentInfo.activityInfo.packageName
                    context!!.grantUriPermission(packageName, photoURI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivityForResult(takePhotoIntent, REQUEST_TAKE_PHOTO)
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Calendar.getInstance().time)
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = context!!.getExternalFilesDir(Environment.DIRECTORY_DCIM)
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
        context!!.sendBroadcast(mediaScanIntent)
    }

    private fun updateSelectCount(newCount: Int) {
        if (context == null) return
        if (tvMultiSelectMessage != null) {
            tvMultiSelectMessage!!.setTextColor(ContextCompat.getColor(context!!, multiSelectTextColor))
            if (newCount < minimumMultiSelectCount) {
                tvMultiSelectMessage!!.text = if (minimumMultiSelectCount - newCount == 1) getString(R.string.imagepicker_multiselect_not_enough_singular) else getString(R.string.imagepicker_multiselect_not_enough_plural, minimumMultiSelectCount - newCount)
                tvDone!!.alpha = 0.4f
                tvDone!!.isEnabled = false
            } else {
                tvMultiSelectMessage!!.text = if (newCount == 1) getString(R.string.imagepicker_multiselect_enough_singular) else getString(R.string.imagepicker_multiselect_enough_plural, newCount)
                tvDone!!.alpha = 1f
                tvDone!!.isEnabled = true
            }
        }
    }

    private fun showOverSelectMessage() {
        if (tvMultiSelectMessage != null && context != null) {
            tvMultiSelectMessage!!.setTextColor(ContextCompat.getColor(context!!, overSelectTextColor))
            tvMultiSelectMessage!!.text = getString(R.string.imagepicker_multiselect_overselect, maximumMultiSelectCount)
        }
    }

    /**
     * Builder of the BSImagePicker.
     * Caller should always create the dialog using this builder.
     */
    class Builder(private val providerAuthority: String) {

        private var tag: String? = null
        private var isMultiSelect = false
        private var dismissOnSelect = true
        private var maxDisplayingImage = Int.MAX_VALUE
        private var minMultiSelectCount = 1
        private var maxMultiSelectCount = Int.MAX_VALUE
        private var showCameraTile = true
        private var showGalleryTile = true
        private var peekHeight = dp2px(360)
        private var spanCount = 3
        private var gridSpacing = dp2px(2)
        private var multiSelectBarBgColor = android.R.color.white
        private var multiSelectTextColor = R.color.primary_text
        private var multiSelectDoneTextColor = R.color.multiselect_done
        private var isShowOverSelectMessage = true
        private var overSelectTextColor = R.color.error_text
        private var isOpenFrontCamera = false

        fun isMultiSelect(): Builder {
            isMultiSelect = true
            return this
        }

        fun dismissOnSelect(dismiss: Boolean): Builder {
            dismissOnSelect = dismiss
            return this
        }

        fun setMaximumDisplayingImages(maximumDisplayingImages: Int): Builder {
            this.maxDisplayingImage = maximumDisplayingImages
            return this
        }

        fun setMinimumMultiSelectCount(minimumMultiSelectCount: Int): Builder {
            require(minimumMultiSelectCount > 0) { "Minimum Multi Select Count must be >= 1" }
            this.minMultiSelectCount = minimumMultiSelectCount
            return this
        }

        fun setMaximumMultiSelectCount(maximumMultiSelectCount: Int): Builder {
            require(maximumMultiSelectCount >= 0) { "Maximum Multi Select Count must be > 0" }
            this.maxMultiSelectCount = maximumMultiSelectCount
            return this
        }

        fun setGridSpacing(@Px gridSpacing: Int): Builder {
            require(gridSpacing >= 0) { "Grid spacing must be >= 0" }
            this.gridSpacing = gridSpacing
            return this
        }

        fun setMultiSelectBarBgColor(@ColorRes multiSelectBarBgColor: Int): Builder {
            this.multiSelectBarBgColor = multiSelectBarBgColor
            return this
        }

        fun setTag(tag: String?): Builder {
            this.tag = tag
            return this
        }

        fun setMultiSelectDoneTextColor(@ColorRes multiSelectDoneTextColor: Int): Builder {
            this.multiSelectDoneTextColor = multiSelectDoneTextColor
            return this
        }

        fun setMultiSelectTextColor(@ColorRes multiSelectTextColor: Int): Builder {
            this.multiSelectTextColor = multiSelectTextColor
            return this
        }

        fun setOverSelectTextColor(@ColorRes overSelectTextColor: Int): Builder {
            this.overSelectTextColor = overSelectTextColor
            return this
        }

        fun setPeekHeight(@Px peekHeight: Int): Builder {
            require(peekHeight >= 0) { "Peek Height must be >= 0" }
            this.peekHeight = peekHeight
            return this
        }

        fun hideCameraTile(): Builder {
            showCameraTile = false
            return this
        }

        fun hideGalleryTile(): Builder {
            showGalleryTile = false
            return this
        }

        fun disableOverSelectionMessage(): Builder {
            isShowOverSelectMessage = false
            return this
        }

        fun setSpanCount(spanCount: Int): Builder {
            require(spanCount >= 0) { "Span Count must be > 0" }
            this.spanCount = spanCount
            return this
        }

        fun useFrontCamera(): Builder {
            isOpenFrontCamera = true
            return this
        }

        fun build(): BSImagePicker {
            val args = Bundle()
            args.putString("providerAuthority", providerAuthority)
            args.putString("tag", tag)
            args.putBoolean("isMultiSelect", isMultiSelect)
            args.putBoolean("dismissOnSelect", dismissOnSelect)
            args.putInt("maximumDisplayingImages", maxDisplayingImage)
            args.putInt("minimumMultiSelectCount", minMultiSelectCount)
            args.putInt("maximumMultiSelectCount", maxMultiSelectCount)
            args.putBoolean("showCameraTile", showCameraTile)
            args.putBoolean("showGalleryTile", showGalleryTile)
            args.putInt("peekHeight", peekHeight)
            args.putInt("spanCount", spanCount)
            args.putInt("gridSpacing", gridSpacing)
            args.putInt("multiSelectBarBgColor", multiSelectBarBgColor)
            args.putInt("multiSelectTextColor", multiSelectTextColor)
            args.putInt("multiSelectDoneTextColor", multiSelectDoneTextColor)
            args.putBoolean("showOverSelectMessage", isShowOverSelectMessage)
            args.putInt("overSelectTextColor", overSelectTextColor)
            args.putBoolean("isOpenFrontCamera", isOpenFrontCamera)
            val fragment = BSImagePicker()
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

        private const val IS_OPEN_FRONT_CAMERA = ""
    }

}