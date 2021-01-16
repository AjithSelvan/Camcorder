package com.airbender.camcorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns.DATE_ADDED
import android.provider.MediaStore.MediaColumns.RELATIVE_PATH
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.extensions.BokehImageCaptureExtender
import androidx.camera.extensions.HdrImageCaptureExtender
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import coil.api.clear
import coil.api.load
import coil.transform.CircleCropTransformation
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.setting_scroll_view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.properties.Delegates

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    //val sharedPref:SharedPreferences=getSharedPreferences("Settings",Context.MODE_PRIVATE)
    private lateinit var sharedPrefs: SharedPreferences //by lazy{getSharedPreferences("Settings",Context.MODE_PRIVATE)}
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var builder = ImageCapture.Builder()
    private val bokeh = BokehImageCaptureExtender.create(builder)
    private val hdr = HdrImageCaptureExtender.create(builder)



    //camera settings
    private var lensFacing by Delegates.notNull<Int>()
    private var ASPECTRATIO by Delegates.notNull<Int>()
    private var LATENCY by Delegates.notNull<Int>() //= if(!getPref("Latency")) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
    private var flashMode by Delegates.notNull<Int>() //=if (!getPref("FlashMode")) ImageCapture.FLASH_MODE_OFF else ImageCapture.FLASH_MODE_AUTO
    private var isImageSaved: Boolean = true

    //bit process
    private var rot: Int = 0

    //video
    private var videocapConfig: VideoCapture? = null
    private var CameraMode: Char = 'P'
    private var isRecording = false

    //uri array in directory
    private var lastImageUri: Uri? = null
    private val galleryUri = arrayListOf<Uri>()


    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private val listenerZoom = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector?): Boolean {
            val currentZoomRatio: Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 0F
            val delta = detector?.scaleFactor
            camera?.cameraControl?.setZoomRatio(currentZoomRatio * delta!!)
            return true
        }
    }
    override fun onRestart() {
        scanPic()
        if (PageAdapter(this, galleryUri).galleryUri.size == 0) {
            lastImageUri = null
            gallery.clear()
        }
        loadThumbnailOnLaunch()
        lastImageUri?.let { setThumbnail(it) }
        super.onRestart()
    }

    private fun getPref(key:String): Boolean {
        return sharedPrefs.getBoolean(key,false)
    }
    private fun putPref(key: String, value : Boolean){
        with(sharedPrefs.edit()){
            putBoolean(key, value).apply()
        }
    }

    private fun settingLoader(){
        ASPECTRATIO =if(!getPref("AspectRatio")) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
        flashMode =if (!getPref("FlashMode")) ImageCapture.FLASH_MODE_OFF else ImageCapture.FLASH_MODE_AUTO
        LATENCY = if(!getPref("Latency")) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        lensFacing =if (!getPref("LensFacing")) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
    }
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scaleGestureDetector = ScaleGestureDetector(this, listenerZoom)
        //always rotate
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        //fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        sharedPrefs=getSharedPreferences(SHAREDPREFERENCE_NAME,Context.MODE_PRIVATE)
        settingLoader()
        //preview_view implementaion mode
        viewFinder.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        //getting current rotation to rotate bitmap accordingly
        val display = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        rot = display.rotation


        //creating single thread camera executor
        cameraExecutor  = Executors.newSingleThreadExecutor()
        // Request camera permissions
        if (allPermissionsGranted()) {
            try {
                startCamera()
                //make viewfinder according to ratio 16_9
                viewFinder.scaleType = PreviewView.ScaleType.FILL_CENTER
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "CameraX has no support for your good old device",
                    Toast.LENGTH_SHORT
                ).show()
                finishAffinity()
            }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        loadThumbnailOnLaunch()
        lastImageUri?.let {
            setThumbnail(it)
            scanPic()
        }
        val li = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        //inflating setting view
        val view = li.inflate(R.layout.setting_scroll_view, null)
        sc_view.addView(view)
        // Setup the listener for take photo button
        camera_capture_button.setOnClickListener {
            if (isImageSaved) {
                flashOnCapture()
                isImageSaved = false
                takePhoto()
            } else {
                Toast.makeText(
                    this,
                    "I wont even try to do anything if you tap me like this :(",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        ar.setOnClickListener {
            val intent = Intent(this, SceneForm::class.java)
            startActivity(intent)
        }
        //setting view opener
        fun setting_arrow() {
            if (s_view.visibility == View.GONE) {
                if (rot == 0 || rot == 2) {
                    if (s_view.visibility == View.GONE) {
                        settings_arrow.visibility = View.INVISIBLE
                        s_view.visibility = View.VISIBLE
                        val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_out)
                        s_view.startAnimation(ani)
                    } else {
                        val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_in)
                        s_view.startAnimation(ani)
                        s_view.visibility = View.GONE
                        settings_arrow.visibility = View.VISIBLE
                    }
                } else {
                    if (s_view.visibility == View.GONE) {
                        settings_arrow.visibility = View.INVISIBLE
                        s_view.visibility = View.VISIBLE
                        val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_out_land)
                        s_view.startAnimation(ani)
                    } else {
                        val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_in_land)
                        s_view.startAnimation(ani)
                        s_view.visibility = View.GONE
                        settings_arrow.visibility = View.VISIBLE
                    }
                }
            }
        }
        settings_arrow.setOnClickListener {
            setting_arrow()
        }
        //setting view closer
        fun back_button() {
            if (s_view.visibility == View.VISIBLE) {
                if (rot == 0 || rot == 2) {
                    val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_in)
                    s_view.startAnimation(ani)
                    s_view.visibility = View.GONE
                    val arr_ani = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
                    settings_arrow.startAnimation(arr_ani)
                    settings_arrow.visibility = View.VISIBLE
                } else {
                    val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_in_land)
                    s_view.startAnimation(ani)
                    s_view.visibility = View.GONE
                    val arr_ani = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
                    settings_arrow.startAnimation(arr_ani)
                    settings_arrow.visibility = View.VISIBLE
                }
            }
        }
        gallery.setOnClickListener {
            if (lastImageUri != null) {
                GlobalScope.launch {
                    scanPic()
                    val intent = Intent(this@MainActivity, GalleryActivity::class.java)
                    intent.putExtra("Gallery_Uri", galleryUri)
                    startActivity(intent)
                }.start()
            }
        }
        //flip method
        fun flip() {
           lensFacing= if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
               putPref("LensFacing",true)
               CameraSelector.LENS_FACING_BACK
            } else {
               putPref("LensFacing",false)
               CameraSelector.LENS_FACING_FRONT
            }
            startCamera()
        }
        buttonback.setOnClickListener {
            back_button()
        }
        //toggle camera
        camera_flip.setOnClickListener {
            flip()
        }
        //button.setOnClickListener { val intgal=Intent(Intent.ACTION_VIEW, Uri.parse("content://media/internal/images/media"))
        //startActivity(intgal)}
        buttonfon.setOnClickListener {
            //camera?.cameraControl?.enableTorch(true)
            if (flashMode!=ImageCapture.FLASH_MODE_AUTO){
            flashMode=ImageCapture.FLASH_MODE_AUTO
                putPref("FlashMode",true)
            buttonfoff.setImageResource(R.drawable.flash_off_off)
            buttonfon.setImageResource(R.drawable.flash_on_on)
            startCamera()}
        }
        buttonfoff.setOnClickListener {
            //camera?.cameraControl?.enableTorch(false)
            if (flashMode!=ImageCapture.FLASH_MODE_OFF){
                flashMode=ImageCapture.FLASH_MODE_OFF
                putPref("FlashMode",false)
                buttonfon.setImageResource(R.drawable.flash_on_off)
                buttonfoff.setImageResource(R.drawable.flash_off_on)
                startCamera()
            }
        }
        button43.setOnClickListener {
            if (ASPECTRATIO == AspectRatio.RATIO_16_9) {
                viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER
                ASPECTRATIO = AspectRatio.RATIO_4_3
                putPref("AspectRatio",true)
                startCamera()
            }
            button169.setImageResource(R.drawable.r16_9_on_off)
            button43.setImageResource(R.drawable.r4_3_on_on)
        }
        button169.setOnClickListener {
            if (ASPECTRATIO == AspectRatio.RATIO_4_3) {
                viewFinder.scaleType = PreviewView.ScaleType.FILL_CENTER
                ASPECTRATIO = AspectRatio.RATIO_16_9
                putPref("AspectRatio",false)
                startCamera()
            }
            button43.setImageResource(R.drawable.r4_3_on_off)
            button169.setImageResource(R.drawable.r16_9_on_on)
        }
        min.setOnClickListener {
            LATENCY= ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            startCamera()
        }
        max.setOnClickListener {
            LATENCY= ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            startCamera()
        }
        //swipe gesture variables
        val swipegestureup = SwipeGestureClass(rot).apply {
            setSwipeCallBack(up = { back_button() })
        }
        val swipegesturedown = SwipeGestureClass(rot).apply {
            setSwipeCallBack(down = { setting_arrow() })
        }
        val swipegestureLongPress = SwipeGestureClass(rot).apply {
            setSwipeCallBack(onLongPress = { flip() })
        }
        val swipegestureTap = SwipeGestureClass(rot).apply {
            setSwipeCallBack(tapFocus = { back_button() })
        }
        val gestureCompactup = GestureDetector(this, swipegestureup)
        val gestureCompactdown = GestureDetector(this, swipegesturedown)
        val gestureCompactLongpress = GestureDetector(this, swipegestureLongPress)
        val gesturecompatTap = GestureDetector(this, swipegestureTap)
        //focus,swipe up and down gesture listener
        viewFinder.setOnTouchListener { _, event ->
            when (event.pointerCount) {
                1 -> {
                    if (s_view.visibility == View.VISIBLE) {
                        gesturecompatTap.onTouchEvent(event)
                    }
                    //tap focus
                    ontouch(event.x, event.y)
                    //long press flip
                    gestureCompactLongpress.onTouchEvent(event)
                    //hide setting view
                    gestureCompactup.onTouchEvent(event)
                    //to open setting view
                    gestureCompactdown.onTouchEvent(event)
                }
                2 -> {
                    //zoom gesture
                    scaleGestureDetector.onTouchEvent(event)
                }
                else -> return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }
    }


    private fun loadThumbnailOnLaunch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_ADDED
            )
            val selection = "$RELATIVE_PATH LIKE ?"
            val selectionarg = arrayOf("%DCIM/Camcorder%")
            val sortorder = "$DATE_ADDED DESC"
            application.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionarg,
                sortorder
            ).use {
                if (it != null) {
                    if (it.count != 0) {
                        it.moveToFirst()
                        val id = it.getLong(it.getColumnIndex(BaseColumns._ID))
                        lastImageUri = id.let { it1 ->
                            ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                it1
                            )
                        }
                    }
                }
                it?.close()
            }
        } else {
            val path = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camcorder"
            val dir = File(path)
            if (dir.exists()) {
                val files = dir.listFiles()
                if (files != null)
                    for (element in files) {
                        lastImageUri = Uri.fromFile(element)
                        Log.d("SUUSSIDDSNOI", "$lastImageUri")
                    }
            }
        }
    }
    private fun flashOnCapture(){
        viewFinder.postDelayed({
            viewFinder.foreground = ColorDrawable(Color.BLACK)
            viewFinder.postDelayed({ viewFinder.foreground = null }, 50L)
        }, 100L)
    }
    private fun scanPic() {
        Log.d("SCANNER UKLAA", "$galleryUri[i]")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_ADDED
            )
            val selection = "$RELATIVE_PATH LIKE ?"
            val selectionarg = arrayOf("%DCIM/Camcorder%")
            val sortorder = "$DATE_ADDED DESC"
            var i = 0
            galleryUri.clear()
            Log.d("SCANNER UKLAA", "$galleryUri[i]")
            application.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionarg,
                sortorder
            )?.use {
                it.moveToFirst()
                while (!it.isAfterLast) {
                    val id = it.getLong(it.getColumnIndex(BaseColumns._ID))
                    Log.d("ullaaa", "vanthufhu $i")
                    galleryUri.add(
                        i,
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    )
                    i++
                    it.moveToNext()
                }
                it.close()
            }
        } else {
            val path = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camcorder"
            val dir = File(path)
            if (dir.exists()) {
                galleryUri.clear()
                val files = dir.listFiles()
                files!!.reverse()
                for (i in 0 until (files.size)) {
                    galleryUri.add(i, Uri.fromFile(files[i]))
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (CameraMode == 'P') {
                if (isImageSaved) {
                    flashOnCapture()
                    isImageSaved  = false
                    takePhoto()
                } else {
                    Toast.makeText(
                        this,
                        "I wont even try to do anything if you tap me like this :(",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            isRecording = if (!isRecording) {
                //startvideorecording()
                true
            } else {
                stopvideorecorder()
                false
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finishAffinity()
        }
        return true
    }

    @SuppressLint("RestrictedApi")
    private fun startVideoCamera() {
        CameraMode = 'V'
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            videocapConfig = VideoCapture.Builder()
                .setCameraSelector(cameraSelector)
                //.setTargetRotation(ASPECTRATIO)
                .setVideoFrameRate(240)
                .setTargetRotation(viewFinder.display.rotation)
                .build()
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videocapConfig
                )
                preview?.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createFileVideo(): File? {
        var v_file: Boolean? = null
        var urii: File? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val valu = ContentValues()
            val filename =
                SimpleDateFormat(
                    FILENAME_FORMAT,
                    Locale.US
                ).format(System.currentTimeMillis()) + ".mp4"
            valu.put(MediaStore.Video.Media.TITLE, filename)
            valu.put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            // valu.put(MediaStore.ACTION_VIDEO_CAPTURE,1)
            // valu.put(MediaStore.Video.Media.IS_PENDING,1)
            valu.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            valu.put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_DCIM + "/" + "Camcorder"
            )
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, valu)!!
            //val failu=File(uri)
            val uritopath = uri.path
            val finfile = File(uritopath!!)
            //val ios = contentResolver.openOutputStream(uri)!!
            urii = finfile
            //val savedfile= contentResolver.PaFileDescriptor(uri,"w")?.
            //ios.close()
            //v_file= uri.toFile()
            //urii=uri.toFile()
        } else {
            val parent =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/Camcoder")
            if (!parent.exists()) {
                parent.mkdir()
            }
            val child = File(
                parent.path,
                SimpleDateFormat(
                    FILENAME_FORMAT,
                    Locale.US
                ).format(System.currentTimeMillis()) + ".mp4"
            )
            urii = child
            child.createNewFile()
        }
        return urii
    }

    @SuppressLint("RestrictedApi")
    /* private fun startvideorecording(){
        //val vidfile=createFileVideo()
        //Log.d("VIDEOO","$vidfile")
        if(videocapConfig!=null){
            createFileVideo()?.let {
                videocapConfig!!.startRecording(it,Executors.newSingleThreadExecutor(),object:VideoCapture.OnVideoSavedCallback{
                    override fun onVideoSaved(file: File) {
                        Toast.makeText(this@MainActivity,"VIDEO SAVED $file",Toast.LENGTH_SHORT).show()
                    }
                    override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                        Log.d("VIDEO ERRORU","$videoCaptureError , $message")
                    }
                }
                )
            }
        }
    } */
    private fun stopvideorecorder() {
        videocapConfig?.stopRecording()
    }

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.isDone
        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Preview
            preview = Preview.Builder()
                .build()
            imageCapture = ImageCapture.Builder().setFlashMode(flashMode)
                .setTargetAspectRatio(ASPECTRATIO)
                .setCaptureMode(LATENCY)
                .build()
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
                preview?.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setThumbnail(uri: Uri) {
        gallery.post {
            gallery.setPadding(4)
            gallery.load(uri) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
        }
    }

    fun ontouch(x: Float, y: Float) {
        val facto = viewFinder.meteringPointFactory
        //CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build())
        val point = facto.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    private fun enablebokeh(cameraSelector: CameraSelector) {
        if (bokeh.isExtensionAvailable(cameraSelector)) {
            bokeh.enableExtension(cameraSelector)
            Toast.makeText(this, "Bokeh ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enableHdr(cameraSelector: CameraSelector) {
        if (hdr.isExtensionAvailable(cameraSelector)) {
            Toast.makeText(this, "HDR ", Toast.LENGTH_SHORT).show()
            hdr.enableExtension(cameraSelector)
        }
    }

    fun savingImg(img: Image) {
        val ios: OutputStream?
        val bit: Bitmap?
        var rotBit: Bitmap?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val value = ContentValues()
            val filename =
                SimpleDateFormat(
                    FILENAME_FORMAT,
                    Locale.US
                ).format(System.currentTimeMillis()) + ".jpg"
            value.put(MediaStore.Images.Media.TITLE, filename)
            value.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            value.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            value.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_DCIM + "/" + "Camcorder"
            )
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, value)!!
            ios =applicationContext.contentResolver.openOutputStream(uri)!!
            bit = img.toBitmap()
            setThumbnail(uri)
            if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                if (rot == 0) {
                    rotBit = rotateBitmap(bit, 90)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 2) {
                    rotBit = rotateBitmap(bit, 270)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 3) {
                    rotBit = rotateBitmap(bit, 180)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 1) {
                    bit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
            }
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                if (rot == 0) {
                    rotBit = rotateBitmap(bit, 270)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 2) {
                    rotBit = rotateBitmap(bit, 90)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 3) {
                    rotBit = rotateBitmap(bit, 180)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 1) {
                    bit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
            }
        } else {
            val parent =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/Camcorder")
            if (!parent.exists()) {
                parent.mkdir()
            }
            val child = File(
                parent.path,
                SimpleDateFormat(
                    FILENAME_FORMAT,
                    Locale.US
                ).format(System.currentTimeMillis()) + ".jpg"
            )
            setThumbnail(Uri.fromFile(child))
            ios = FileOutputStream(child)
            bit = img.toBitmap()
            if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                if (rot == 0) {
                    rotBit = rotateBitmap(bit, 90)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 1) {
                    bit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 2) {
                    rotBit = rotateBitmap(bit, 270)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 3) {
                    rotBit = rotateBitmap(bit, 180)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
            }
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                if (rot == 0) {
                    rotBit = rotateBitmap(bit, 270)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 2) {
                    rotBit = rotateBitmap(bit, 90)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 3) {
                    rotBit = rotateBitmap(bit, 180)
                    rotBit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
                if (rot == 1) {
                    bit.compress(Bitmap.CompressFormat.JPEG, 100, ios)
                }
            }

        }
        ios.close()
        img.close()
        isImageSaved = true
    }
    private fun Image.toBitmap():Bitmap{
        val yBuffer=planes[0].buffer
        val ySize=ByteArray(yBuffer.remaining())
        yBuffer.get(ySize)
        return BitmapFactory.decodeByteArray(ySize,0,ySize.size,null)
    }

    private fun rotateBitmap(Bit: Bitmap, angle: Int): Bitmap {
        val newMat = Matrix()
        //selfiecam mirror
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            newMat.preScale(1F, -1F)
        }
        newMat.postRotate(angle.toFloat())
        return Bitmap.createBitmap(Bit, 0, 0, Bit.width, Bit.height, newMat, true)

    }

    //private fun enablefocus(cameraSelector: CameraSelector) {
    @SuppressLint("RestrictedApi")
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return
        // Create timestamped output file to hold the image
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this)
            , object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exc.message}", exc)
                }

                @RequiresApi(Build.VERSION_CODES.Q)
                @SuppressLint("UnsafeExperimentalUsageError")
                override fun onCaptureSuccess(image: ImageProxy) {
                    image.image?.let { savingImg(it) }
                    image.close()
                    loadThumbnailOnLaunch()
                }
            })
    }

private fun allPermissionsGranted() = false
override fun onRequestPermissionsResult(
requestCode: Int, permissions: Array<String>, grantResults:
IntArray) {
if (requestCode == REQUEST_CODE_PERMISSIONS) {
    if (ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED
        &&ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        && ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
        startCamera()
    } else {
       // startCamera()
        Toast.makeText(this,
            "Permissions not granted by the user.",
            Toast.LENGTH_SHORT).show()
        finish()
    }
}
}

companion object {
    const val SHAREDPREFERENCE_NAME="Settings"
    const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    private const val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.RECORD_AUDIO)
    }
}



