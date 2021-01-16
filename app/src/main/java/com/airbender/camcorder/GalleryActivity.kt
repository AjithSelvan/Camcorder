package com.airbender.camcorder

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import kotlinx.android.synthetic.main.activity_gallery.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

class GalleryActivity : AppCompatActivity() {
    private var galleryUri= arrayListOf<Uri>()
    private lateinit var iAdapter:PageAdapter
    @RequiresApi(Build.VERSION_CODES.Q)
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        GlobalScope.launch {
        galleryUri= intent.extras?.get("Gallery_Uri") as ArrayList<Uri>
            iAdapter = PageAdapter(this@GalleryActivity, galleryUri )
        viewPager.adapter = iAdapter
        viewPager.setPageTransformer(ZoomOutPageTransformer())}.start()
        overridePendingTransition(android.R.anim.slide_in_left,android.R.anim.slide_out_right)
        galleryBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.slide_in_left,android.R.anim.slide_out_right)
        }
        buttondelete.setOnClickListener {
            deletePic()
            if (galleryUri.size==0){
                finish()
                overridePendingTransition(android.R.anim.slide_in_left,android.R.anim.slide_out_right)
            }
        }
    }
    private fun deletePic(){
        contentResolver.delete(galleryUri[viewPager.currentItem],null,null)
        galleryUri.removeAt(viewPager.currentItem)
        iAdapter.notifyItemRemoved(viewPager.currentItem)
    }
     override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode==KeyEvent.KEYCODE_BACK){
            finish()
            overridePendingTransition(android.R.anim.slide_in_left,android.R.anim.slide_out_right)
        }
         return true
    }
}
class ZoomOutPageTransformer:ViewPager2.PageTransformer{
    override fun transformPage(page: View, position: Float) {
        page.apply {
            val pageW=width
            val pageH=height
            when {
                position < -1 ->{ alpha=0f}
                position <=1 ->{
                    val scaleFactor= 0.85f.coerceAtLeast(1 - abs(position))
                    val VMargin=pageH*(1-scaleFactor)/2
                    val HMargin=pageW*(1-scaleFactor)/2
                    translationX=if (position<0){
                        HMargin-VMargin/2
                    }else{
                        HMargin+VMargin+2
                    }
                    scaleX=scaleFactor
                    scaleY=scaleFactor
                    alpha=(0.5f+(((scaleFactor-0.5f)/(1-0.5f))*(1-0.5f)))
                }
                else->{
                    alpha=0f
                }
            }
        }
    }

}