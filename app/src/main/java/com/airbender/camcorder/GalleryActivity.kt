package com.airbender.camcorder

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.airbender.camcorder.databinding.ActivityGalleryBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

class GalleryActivity : AppCompatActivity() {
    lateinit var bindingGalleryActivity :ActivityGalleryBinding
    private var galleryUri= arrayListOf<Uri>()
    private lateinit var iAdapter:PageAdapter
    @RequiresApi(Build.VERSION_CODES.Q)
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindingGalleryActivity= ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(bindingGalleryActivity.root)
        GlobalScope.launch {
        galleryUri= intent.extras?.get("Gallery_Uri") as ArrayList<Uri>
            iAdapter = PageAdapter(this@GalleryActivity, galleryUri )
            bindingGalleryActivity.viewPager.adapter = iAdapter
            bindingGalleryActivity.viewPager.setPageTransformer(ZoomOutPageTransformer())}.start()
        overridePendingTransition(android.R.anim.slide_in_left,android.R.anim.slide_out_right)
        bindingGalleryActivity.galleryBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.slide_in_left,android.R.anim.slide_out_right)
        }
        bindingGalleryActivity.buttondelete.setOnClickListener {
            deletePic()
            if (galleryUri.size==0){
                finish()
                overridePendingTransition(android.R.anim.slide_in_left,android.R.anim.slide_out_right)
            }
        }
    }
    private fun deletePic(){
        if (Build.VERSION_CODES.Q<=Build.VERSION.SDK_INT)
            contentResolver.delete(galleryUri[bindingGalleryActivity.viewPager.currentItem],null,null)
        else{
            val file=File(galleryUri[bindingGalleryActivity.viewPager.currentItem].path!!)
            file.delete()
        }
        galleryUri.removeAt(bindingGalleryActivity.viewPager.currentItem)
        iAdapter.notifyItemRemoved(bindingGalleryActivity.viewPager.currentItem)
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