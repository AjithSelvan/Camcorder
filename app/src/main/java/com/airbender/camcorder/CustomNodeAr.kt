package com.airbender.camcorder

import android.content.Context
import android.util.Log
import com.google.ar.core.AugmentedFace
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.AugmentedFaceNode

class CustomNodeAr(augmentedFace: AugmentedFace,val context: Context) :AugmentedFaceNode(augmentedFace){
    private var centerNode:Node?=null

    companion object{
        enum class FaceRegion{
            CENTRE
        }
    }

    override fun onActivate() {
        super.onActivate()
        centerNode=Node()
        centerNode?.setParent(this)

        ModelRenderable.builder()
            .setSource(context,R.raw.transformedglass )
            .build()
            .thenAccept { modelRenderable ->
                faceRegionsRenderable = modelRenderable
                modelRenderable.isShadowCaster = false
                modelRenderable.isShadowReceiver = false
            }.exceptionally {
                null
            }

    }
    private fun getRegion(region: FaceRegion) :Vector3?{
        val buffer=augmentedFace?.meshVertices
        if (buffer!=null){
            Log.d("Ar","PSSSIIOOTIOn")
            return when (region){ FaceRegion.CENTRE->{
                Vector3(buffer.get(11*3),buffer.get(11*3+1),buffer.get(11*3+2))}
            }
        }
        return null
    }

    override fun onUpdate(frameTime: FrameTime?) {
        super.onUpdate(frameTime)
        augmentedFace?.let {
            getRegion(FaceRegion.CENTRE)?.let {
                centerNode?.localPosition=Vector3(it.x,it.y+.5f,it.z+.9f)
            }
        }
    }

}