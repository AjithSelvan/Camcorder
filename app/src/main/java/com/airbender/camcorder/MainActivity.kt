package com.airbender.camcorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
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
import androidx.camera.core.Camera
import androidx.camera.extensions.BokehImageCaptureExtender
import androidx.camera.extensions.HdrImageCaptureExtender
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.api.clear
import coil.api.load
import coil.transform.CircleCropTransformation
import com.airbender.camcorder.databinding.ActivityMainBinding
import com.airbender.camcorder.databinding.SettingScrollViewBinding
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
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

class MainActivity : AppCompatActivity() {
    //val sharedPref:SharedPreferences=getSharedPreferences("Settings",Context.MODE_PRIVATE)
    private lateinit var sharedPrefs: sharedPref //by lazy{getSharedPreferences("Settings",Context.MODE_PRIVATE)}
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
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
    private var disId=-1

    //video
    private var videocapConfig: VideoCapture? = null
    private var CameraMode: Char = 'P'
    private var isRecording = false

    //uri array in directory
    private var lastImageUri: Uri? = null
    private val galleryUri = arrayListOf<Uri>()

    //Binding views
    private lateinit var bindingMainActivity:ActivityMainBinding
    private lateinit var bindingSettingsScroll: SettingScrollViewBinding
    private lateinit var cameraProvider: ProcessCameraProvider

/*
    private lateinit var doubleTapZoomListener:GestureDetector
    private val zoom=object : GestureDetector.SimpleOnGestureListener(){
        override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
            var delta=0
            when(MotionEvent.ACTION_MOVE){
                MotionEvent.ACTION_UP->{
                    delta += 1
                    Log.d("ZOOOM","$delta")
                }
                MotionEvent.ACTION_DOWN->{
                    delta-=1
                    Log.d("ZOOOM","$delta")
                }
            }
            //return super.onDoubleTapEvent(e)
            val currentZoomRatio: Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 0F
            //val delta= (e?.y)?.div(100F)
            camera?.cameraControl?.setZoomRatio(currentZoomRatio+delta)
            Log.d("ZOOOMmuu","$delta")
            return true
        }
    }   */
    private lateinit var singleTapUpListener:GestureDetector
    private  val focus=object :GestureDetector.SimpleOnGestureListener(){
        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            val factory = bindingMainActivity.viewFinder.meteringPointFactory
            //CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build())
            val point = e?.x?.let { factory.createPoint(it, e.y) }
            val action = point?.let { FocusMeteringAction.Builder(it).build() }
            val a= action?.let { camera?.cameraControl?.startFocusAndMetering(it) }
            /*
            if (a != null) {
                while (!a.isDone){
                    bindingMainActivity.focusView.post {
                        Runnable {
                            Log.d("FFOUUCSSS","COOMIngg")
                            bindingMainActivity.focusView.drawer(e)
                            bindingMainActivity.focusView.draw(Canvas().apply { drawCircle(e.x,e.y,10F,Paint().apply {
                                isAntiAlias=true
                                color= Color.MAGENTA
                                style=Paint.Style.FILL_AND_STROKE
                            }
                            )})
                        }
                    }
                }
            } */
            return true
        }
    }
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private val listenerZoom = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector?): Boolean {
            val currentZoomRatio: Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 0F
            val delta = detector?.scaleFactor
            camera?.cameraControl?.setZoomRatio(currentZoomRatio * delta!!)
            Log.d("zzzommmuuuu","$currentZoomRatio")
            return true
        }
    }
    private val displayListener:DisplayManager.DisplayListener= object : DisplayManager.DisplayListener{
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            val view=bindingMainActivity.root
            if (displayId==this@MainActivity.disId){
                imageCapture?.targetRotation=view.display.rotation
            }
        }
    }
    override fun onRestart() {
        scanPic()
        if (PageAdapter(this, galleryUri).galleryUri.size == 0) {
            lastImageUri = null
            bindingMainActivity.gallery.clear()
        }
        loadThumbnailOnLaunch()
        lastImageUri?.let { setThumbnail(it) }
        tryStartCamera()
        super.onRestart()
    }

    private fun settingInflater(){
        bindingSettingsScroll= SettingScrollViewBinding.inflate(layoutInflater)
        bindingMainActivity.scView.addView(bindingSettingsScroll.root)
    }
    private fun settingLoader(){
        ASPECTRATIO =if(!sharedPrefs.getPref("AspectRatio")) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
        flashMode =if (!sharedPrefs.getPref("FlashMode")) ImageCapture.FLASH_MODE_OFF else ImageCapture.FLASH_MODE_ON
        LATENCY = if(!sharedPrefs.getPref("Latency")) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        lensFacing =if (!sharedPrefs.getPref("LensFacing")) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
    }
    private fun setupViewModel(){
        val vm=ViewModelProvider(this).get(ViewModelCamera::class.java)
        vm.processCameraProvider.observe(
            this, { cameraProvider=it ?: return@observe
                tryStartCamera()
            }
        )
    }

    private fun tryStartCamera() {
        if(::cameraProvider.isInitialized && isPermissionGranted()){
            startCamera(cameraProvider)
        }
    }
private fun isPermissionGranted():Boolean{
    return REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this,it)==PackageManager.PERMISSION_GRANTED
    }
}
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupViewModel()
        bindingMainActivity = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bindingMainActivity.root)
        val dManager=getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dManager.registerDisplayListener(displayListener,null)
        sharedPrefs=sharedPref(applicationContext)
        if (isPermissionGranted()) {
           try {
                tryStartCamera()
                bindingMainActivity.viewFinder.scaleType = if(!sharedPrefs.getPref("AspectRatio")) PreviewView.ScaleType.FILL_CENTER else PreviewView.ScaleType.FIT_CENTER
             } catch (e: Exception) {
                Toast.makeText(this, R.string.NotSupportMessage, Toast.LENGTH_SHORT).show()
                finishAffinity()
            }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        //.inflate(LayoutInflater.from(this),R.layout.activity_main,null ,true)//setContentView(this,R.layout.activity_main)1w2e
        //doubleTapZoomListener= GestureDetector(this , zoom)
        scaleGestureDetector = ScaleGestureDetector(this, listenerZoom)
        singleTapUpListener= GestureDetector(this,focus)
        //always rotate
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        //fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        //preview_view implementaion mode
        bindingMainActivity.viewFinder.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        bindingMainActivity.viewFinder.post {
            disId=bindingMainActivity.viewFinder.display.displayId
        }
        //.getSharedPreference()//getSharedPreferences(SHAREDPREFERENCE_NAME,Context.MODE_PRIVATE)
        settingLoader()
        settingInflater()

        //getting current rotation to rotate bitmap accordingly
        val display = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        rot = display.rotation

        //creating single thread camera executor
        cameraExecutor  = Executors.newSingleThreadExecutor()
        // Request camera permissions
        loadThumbnailOnLaunch()
        lastImageUri?.let {
            lifecycleScope.launch(Dispatchers.IO){
            setThumbnail(it)
            scanPic()}.start()
        }
        // Setup the listener for take photo button
        bindingMainActivity.cameraCaptureButton.setOnClickListener {
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
        bindingMainActivity.ar.setOnClickListener {
            val intent = Intent(this, SceneForm::class.java)
            startActivity(intent)
        }
        //setting view opener
        fun settingArrow() {
            if (bindingMainActivity.sView.visibility == View.GONE) {
                if (rot == 0 || rot == 2) {
                    if (bindingMainActivity.sView.visibility == View.GONE) {
                        bindingMainActivity.settingsArrow.visibility = View.INVISIBLE
                        bindingMainActivity.sView.visibility = View.VISIBLE
                        val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_out)
                        bindingMainActivity.sView.startAnimation(ani)
                    } else {
                        val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_in)
                        bindingMainActivity.sView.startAnimation(ani)
                        bindingMainActivity.sView.visibility = View.GONE
                        bindingMainActivity.settingsArrow.visibility = View.VISIBLE
                    }
                } else {
                    if (bindingMainActivity.sView.visibility == View.GONE) {
                        bindingMainActivity.settingsArrow.visibility = View.INVISIBLE
                        bindingMainActivity.sView.visibility = View.VISIBLE
                        val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_out_land)
                        bindingMainActivity.sView.startAnimation(ani)
                    } else {
                        val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_in_land)
                        bindingMainActivity.sView.startAnimation(ani)
                        bindingMainActivity.sView.visibility = View.GONE
                        bindingMainActivity.settingsArrow.visibility = View.VISIBLE
                    }
                }
            }
        }
        bindingMainActivity.settingsArrow.setOnClickListener {
            settingArrow()
        }
        //setting view closer
        fun backButton() {
            if (bindingMainActivity.sView.visibility == View.VISIBLE) {
                if (rot == 0 || rot == 2) {
                    val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_in)
                    bindingMainActivity.sView.also {
                        it.startAnimation(ani)
                        it.visibility=View.GONE
                    }
                    val arrAni = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
                    bindingMainActivity.settingsArrow.also{
                        it.startAnimation(arrAni)
                        it.visibility=View.VISIBLE
                    }
                } else {
                    val ani = AnimationUtils.loadAnimation(this, R.anim.inflate_in_land)
                    bindingMainActivity.sView.also {
                        it.startAnimation(ani)
                        it.visibility=View.GONE
                    }
                    val arrAni = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
                    bindingMainActivity.settingsArrow.also{
                        it.startAnimation(arrAni)
                        it.visibility=View.VISIBLE
                    }
                }
            }
        }
        bindingMainActivity.gallery.setOnClickListener {
            if (lastImageUri != null) {
                GlobalScope.launch {
                    scanPic()
                    val intent = Intent(this@MainActivity, GalleryActivity::class.java)
                    intent.putExtra("Gallery_Uri", galleryUri)
                    startActivity(intent)
                }.start()
            }
        }
        //flip camera method
        fun flip() {
           lensFacing= if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
               sharedPrefs.putPref("LensFacing",false)
               CameraSelector.LENS_FACING_BACK
            } else {
               sharedPrefs.putPref("LensFacing",true)
               CameraSelector.LENS_FACING_FRONT
            }
            tryStartCamera()
        }
        bindingMainActivity.buttonback.setOnClickListener {
            backButton()
        }
        //toggle camera
        bindingMainActivity.cameraFlip.setOnClickListener {
            flip()
        }
        //button.setOnClickListener { val intgal=Intent(Intent.ACTION_VIEW, Uri.parse("content://media/internal/images/media"))
        //startActivity(intgal)}
        bindingSettingsScroll.buttonfon.setOnClickListener {
            //camera?.cameraControl?.enableTorch(true)
            if (flashMode!=ImageCapture.FLASH_MODE_ON){
                flashMode=ImageCapture.FLASH_MODE_ON
                sharedPrefs.putPref("FlashMode",true)
                bindingSettingsScroll.buttonfoff.setImageResource(R.drawable.flash_off_off)
                bindingSettingsScroll.buttonfon.setImageResource(R.drawable.flash_on_on)
                tryStartCamera()
            }
        }
        bindingSettingsScroll.buttonfoff.setOnClickListener {
            //camera?.cameraControl?.enableTorch(false)
            if (flashMode!=ImageCapture.FLASH_MODE_OFF ){
                flashMode=ImageCapture.FLASH_MODE_OFF
                sharedPrefs.putPref("FlashMode",false)
                bindingSettingsScroll.buttonfon.setImageResource(R.drawable.flash_on_off)
                bindingSettingsScroll.buttonfoff.setImageResource(R.drawable.flash_off_on)
                tryStartCamera()
            }
        }

        bindingSettingsScroll.button43.setOnClickListener {
            if (ASPECTRATIO!=AspectRatio.RATIO_4_3) {
                bindingMainActivity.viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER
                ASPECTRATIO = AspectRatio.RATIO_4_3
                sharedPrefs.putPref("AspectRatio", true)
                bindingSettingsScroll.button169.setImageResource(R.drawable.r16_9_on_off)
                bindingSettingsScroll.button43.setImageResource(R.drawable.r4_3_on_on)
                tryStartCamera()
            }
        }
        bindingSettingsScroll.button169.setOnClickListener {
            if(ASPECTRATIO!=AspectRatio.RATIO_16_9){
                bindingMainActivity.viewFinder.scaleType = PreviewView.ScaleType.FILL_CENTER
                ASPECTRATIO = AspectRatio.RATIO_16_9
                sharedPrefs.putPref("AspectRatio", false)
                bindingSettingsScroll.button43.setImageResource(R.drawable.r4_3_on_off)
                bindingSettingsScroll.button169.setImageResource(R.drawable.r16_9_on_on)
                tryStartCamera()
            }
        }
        bindingSettingsScroll.min.setOnClickListener {
            LATENCY= ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            tryStartCamera()
        }
        bindingSettingsScroll.max.setOnClickListener {
            LATENCY= ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            tryStartCamera()
        }
        //swipe gesture variables
        val swipegestureup = SwipeGestureClass(rot).apply {
            setSwipeCallBack(up = { backButton() })
        }
        val swipegesturedown = SwipeGestureClass(rot).apply {
            setSwipeCallBack(down = { settingArrow() })
        }
        val swipegestureDoubleTap = SwipeGestureClass(rot).apply {
            setSwipeCallBack(onLongPress = { flip() })
        }
        val swipegestureTap = SwipeGestureClass(rot).apply {
            setSwipeCallBack(tapFocus = { backButton() })
        }
        val gestureCompactup = GestureDetector(this, swipegestureup)
        val gestureCompactdown = GestureDetector(this, swipegesturedown)
        val gestureCompactDoubleTap = GestureDetector(this, swipegestureDoubleTap)
        val gesturecompatTap = GestureDetector(this, swipegestureTap)
        //focus,swipe up and down gesture listener
        bindingMainActivity.viewFinder.setOnTouchListener { _, event ->
            when (event.pointerCount) {
                1 -> {
                    if (bindingMainActivity.sView.visibility == View.VISIBLE) {
                        gesturecompatTap.onTouchEvent(event)
                    }
                    //tap focus
                    //onTouch(event.x, event.y)
                    singleTapUpListener.onTouchEvent(event)
                    //double tap to  flip
                    gestureCompactDoubleTap.onTouchEvent(event)
                    //hide setting view
                    gestureCompactup.onTouchEvent(event)
                    //to open setting view
                    gestureCompactdown.onTouchEvent(event)
                    //double tap and event to zoom
                    //doubleTapZoomListener.onTouchEvent(event)
                }
                2 -> {
                    //double finger zoom gesture
                    scaleGestureDetector.onTouchEvent(event)
                }
                else -> return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }
    }

    private fun loadThumbnailOnLaunch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf( MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.DATE_ADDED)
            val selection = "$RELATIVE_PATH LIKE ?"
            val selectionArg = arrayOf("%DCIM/Camcorder%")
            val sortOrder = "$DATE_ADDED DESC"
            application.contentResolver.query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArg, sortOrder).use {
                if (it != null) {
                    if (it.count != 0) {
                        it.moveToFirst()
                        val id = it.getLong(it.getColumnIndex(BaseColumns._ID))
                        lastImageUri = id.let { it1 ->
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it1)
                        }
                    }
                }
                it?.close()
            }
        } else {
            val path = Environment.getExternalStorageDirectory().toString() + Environment.DIRECTORY_DCIM+"/Camcorder"
            val dir = File(path)
            if (dir.exists()) {
                val files = dir.listFiles()
                if (files != null)
                    for (element in files) {
                        lastImageUri = Uri.fromFile(element)
                    }
            }
        }
    }
    private fun flashOnCapture(){
        bindingMainActivity.viewFinder.postDelayed({
            bindingMainActivity.viewFinder.foreground = ColorDrawable(Color.BLACK)
            bindingMainActivity.viewFinder.postDelayed({ bindingMainActivity.viewFinder.foreground = null }, 50L)
        }, 100L)
    }
    private fun scanPic() {
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
                        galleryUri.add(
                            i,
                            ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                        )
                        i++
                        it.moveToNext()
                    }
                    it.close()
                }
            } else {
                val path = Environment.getExternalStorageDirectory()
                    .toString() + Environment.DIRECTORY_DCIM + "/Camcorder"
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
                    lifecycleScope.launch(Dispatchers.Default){
                    takePhoto()
                    }.start()
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
                stopVideoRecorder()
                false
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finishAffinity()
        }
        return true
    }
/*
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
                .setTargetRotation(bindingMainActivity.viewFinder.display.rotation)
                .build()
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videocapConfig
                )
                preview?.setSurfaceProvider(bindingMainActivity.viewFinder.surfaceProvider)
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
*/
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
    private fun stopVideoRecorder() {
        videocapConfig?.stopRecording()
    }
    @SuppressLint("RestrictedApi")
    private fun startCamera(cameraProvider: ProcessCameraProvider) {
       /* val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.isDone
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get() */
            // Preview
            preview = Preview.Builder()
                .build()
            imageCapture = ImageCapture.Builder()
                .setFlashMode(flashMode)
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
                preview?.setSurfaceProvider(bindingMainActivity.viewFinder.surfaceProvider)
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
    }

    private fun setThumbnail(uri: Uri) {
        bindingMainActivity.gallery.post {
            bindingMainActivity.gallery.setPadding(4)
            bindingMainActivity.gallery.load(uri) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
        }
    }
    private fun onTouch(x: Float, y: Float) {
        val facto = bindingMainActivity.viewFinder.meteringPointFactory
        //CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build())
        val point = facto.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        val a= camera?.cameraControl?.startFocusAndMetering(action)
        if (a != null) {
            while (!a.isDone){
                Canvas().drawCircle(x,y,2F,Paint().apply { isAntiAlias=true
                    color=Color.MAGENTA
                    style=Paint.Style.FILL_AND_STROKE
            })
        }
    }
    }

    /*
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
    */

    fun savingImage(img: Image) {
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
                    image.image?.let {
                        lifecycleScope.launch(Dispatchers.IO){
                            savingImage(it)
                            image.close()
                            }.start()
                        }
                    loadThumbnailOnLaunch()
                }
            })
    }

 /*   override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    if (requestCode == REQUEST_CODE_PERMISSIONS) {
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED
            &&ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted=true
        } else {
            // startCamera()
            Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
} */

companion object {
    const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    private const val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.RECORD_AUDIO)
    }
}



