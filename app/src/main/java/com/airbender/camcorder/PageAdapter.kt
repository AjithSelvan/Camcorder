package com.airbender.camcorder

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

open class PageAdapter(context: Context, arrayList: ArrayList<Uri>) : RecyclerView.Adapter<PageAdapter.MyViewHolder>() {
    private var mContext: Context? =null
    var galleryUri= arrayListOf<Uri>()
    private var layoutInflater: LayoutInflater? =null
    init {
        this.mContext=context
        this.galleryUri= arrayList
        this.layoutInflater=context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }
    private fun onBindViewHolderNew(holder: MyViewHolder, position: Int) {
        holder.iView.setImageURI(galleryUri[position])
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view=LayoutInflater.from(mContext).inflate(R.layout.image_container,parent,false)
        return MyViewHolder(view)
    }
    override fun getItemCount(): Int {
        return galleryUri.size
    }
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iView: ImageView
        init {
            super.itemView
            iView=itemView.findViewById(R.id.imageContainer)
        }
    }
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        onBindViewHolderNew(holder, position)
    }
}