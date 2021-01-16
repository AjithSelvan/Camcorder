package com.airbender.camcorder

import android.app.Activity
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.Image
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.ArCoreApk
import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.AugmentedFaceNode
import kotlinx.android.synthetic.main.activity_scene_form.*
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

/*
var faceRegion: ModelRenderable? = null
lateinit var sceneViewFragment:ArFragment
lateinit var sceneView:ArSceneView
lateinit var scene:Scene
var texture: Texture?=null
private val faceNodeMap: HashMap<AugmentedFace, AugmentedFaceNode> = HashMap()  */

class SceneForm : AppCompatActivity() {
    /*
    private var isImageSaved:Boolean=true
    @RequiresApi(VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scene_form)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            return
        }
        //loadModel1(R.raw.newmask2,null)
        sceneViewFragment=supportFragmentManager.findFragmentById(R.id.sceneviewer) as ArFragment
        sceneView= sceneViewFragment.arSceneView
        sceneView.cameraStreamRenderPriority=Renderable.RENDER_PRIORITY_FIRST
        sceneView.isLightEstimationEnabled=true
        scene=sceneView.scene
        scene.addOnUpdateListener{
            updateFace()
        }
        captureAr.setOnClickListener {
            if(isImageSaved){
                sceneView.arFrame?.acquireCameraImage()?.let { it1 -> savingImg(it1) }
            }
        }
        item1.setOnClickListener {
            loadModel1(R.raw.pixglass,null)
            updateFace()
        }
        item2.setOnClickListener {
            loadModel1(R.raw.untitled,R.drawable.fox_face_mesh_texture)
            updateFace()
        }
    }
*/
    private fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        if(ArCoreApk.getInstance().checkAvailability(this)==ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE){
            Toast.makeText(this,"Not supported for your good old Device :(",Toast.LENGTH_SHORT).show()
            finish()
            return false
        }
        val openGlVersionString =
            (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion
        if (openGlVersionString.toDouble() < 3.0) {
            Log.e("SceneForm", "SceneForm requires OpenGL ES 3.0 later")
            Toast.makeText(activity, "SceneForm requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                .show()
            activity.finish()
            return false
        }
        return true
    }
  /*  @RequiresApi(VERSION_CODES.N)
    fun loadModel2() {
        val GlassM: ModelRenderable.Builder = ModelRenderable.builder().setSource(this, R.raw.fox_face)
        GlassM.build().thenAccept { t: ModelRenderable? ->
            if (t != null) {
                faceRegion = t
            }
            t?.isShadowCaster = false
            t?.isShadowReceiver = false
        }.exceptionally {
            Log.d("LOOOADDDINNGG","ERRORRuu")
            null
        }
        Texture.builder().setSource(this,R.drawable.fox_face_mesh_texture).build().thenAccept {
            t: Texture? ->
            if (t != null) {
                texture=t
            }
        }
    }   */  /*
    @RequiresApi(VERSION_CODES.N)
    fun loadModel1(m: Int?, te:Int?) {
        if (m!=null){
        val object3d: ModelRenderable.Builder? =
            ModelRenderable.builder().setSource(this, m)
        if (object3d!=null){
        object3d.build().thenAccept { t: ModelRenderable? ->
            if (t != null) {
                faceRegion = t
                Log.d("HEEREREREE","11111111")
            }
            t?.isShadowCaster = true
            t?.isShadowReceiver = true
        }?.exceptionally {
            Log.d("LOOOADDDINNGG","ERRORRuu")
            null
        }}}
        else{
            faceRegion=null}
        if (te != null) {
            Texture.builder().setSource(this,te).build().thenAccept { t: Texture? ->
                if (t != null) {
                    Log.d("HEEREREREE","2222222222")
                    texture=t
                }
            }
        }else{
            texture=null
        }
    }
    private fun updateFace() {
        val faceList = sceneView.session
            ?.getAllTrackables(AugmentedFace::class.java)
        // Make new AugmentedFaceNodes for any new faces.
        if (faceList != null) {
            for (face in faceList) {
                if (!faceNodeMap.containsKey(face)) {
                    val faceNode = CustomNodeAr(face,this)//AugmentedFaceNode(face)
                    faceNode.setParent(scene)
                    faceNode.faceRegionsRenderable = faceRegion
                   // faceNode.faceMeshTexture= texture
                    faceNodeMap[face] = faceNode
                    Log.d("if ulll","TRUEEEE")
                }
               /* else{
                        faceNodeMap[face]?.faceRegionsRenderable = faceRegion
                        faceNodeMap[face]?.faceMeshTexture = texture
                        Log.d("else ulll","TRUEEEE")
                }  */
                /*else{
                    val faceNode = AugmentedFaceNode(face)
                    faceNode.setParent(scene)
                    faceNode.faceRegionsRenderable = faceRegion
                    faceNode.faceMeshTexture= texture
                    faceNodeMap[face]= faceNode
                    Log.d("else ulll","TRUEEEE")
                } */
            }
        }
        // Remove any AugmentedFaceNodes associated with an
        // AugmentedFace that stopped tracking.
        val iter: MutableIterator<Map.Entry<AugmentedFace, AugmentedFaceNode>> =
            faceNodeMap.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val face = entry.key
            if (face.trackingState == TrackingState.STOPPED) {
                val faceNode = entry.value
                faceNode.setParent(null)
                iter.remove()
            }
        }
    }   */

    lateinit var arFragment: ArFragment
    private var faceMeshTexture: Texture? = null
    private var glasses: ArrayList<ModelRenderable> = ArrayList()
    private var faceRegionsRenderable: ModelRenderable? = null

    var faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()

    private var index: Int = 0
    private var changeModel: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scene_form)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            return
        }

        /*setContentView(R.layout.activity_glasses)
        button_next.setOnClickListener {
            changeModel = !changeModel
            index++
            if (index > glasses.size - 1) {
                index = 0
            }
            faceRegionsRenderable = glasses.get(index)
        }
*/
       /* arFragment = face_fragment as FaceArFragment
        Texture.builder()
            .setSource(this, R.drawable.makeup)
            .build()
            .thenAccept { texture -> faceMeshTexture = texture }
*/
        ModelRenderable.builder()
            .setSource(this, R.raw.sunglasses2)
            .build()
            .thenAccept { modelRenderable ->
                glasses.add(modelRenderable)
                faceRegionsRenderable = modelRenderable
                modelRenderable.isShadowCaster = false
                modelRenderable.isShadowReceiver = false
            }

       /*   ModelRenderable.builder()
            .setSource(this, Uri.parse("sunglasses.sfb"))
            .build()
            .thenAccept { modelRenderable ->
                glasses.add(modelRenderable)
                modelRenderable.isShadowCaster = false
                modelRenderable.isShadowReceiver = false
            }   */

        arFragment=supportFragmentManager.findFragmentById(R.id.sceneviewer) as ArFragment
        val sceneView = arFragment.arSceneView
        sceneView.cameraStreamRenderPriority = Renderable.RENDER_PRIORITY_FIRST
        sceneView.isLightEstimationEnabled=true
        val scene = sceneView.scene

        scene.addOnUpdateListener {
            if (faceRegionsRenderable != null) {
                sceneView.session
                    ?.getAllTrackables(AugmentedFace::class.java)?.let {
                        for (f in it) {
                            if (!faceNodeMap.containsKey(f)) {
                                val faceNode = AugmentedFaceNode(f)
                                faceNode.setParent(scene)
                                faceNode.faceRegionsRenderable = faceRegionsRenderable
                                faceNodeMap[f] = faceNode
                            } else if (changeModel) {
                                faceNodeMap.getValue(f).faceRegionsRenderable = faceRegionsRenderable
                            }
                        }
                        changeModel = false
                        // Remove any AugmentedFaceNodes associated with an AugmentedFace that stopped tracking.
                        val iter = faceNodeMap.entries.iterator()
                        while (iter.hasNext()) {
                            val entry = iter.next()
                            val face = entry.key
                            if (face.trackingState == TrackingState.STOPPED) {
                                val faceNode = entry.value
                                faceNode.setParent(null)
                                iter.remove()
                            }
                        }
                    }
            }
        }
    }
}



