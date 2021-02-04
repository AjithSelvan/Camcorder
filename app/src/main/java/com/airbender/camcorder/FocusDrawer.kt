package com.airbender.camcorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

class FocusDrawer constructor(context: Context?,attributeSet: AttributeSet?):View(context,attributeSet) {
    private var xpoint=0F
    private var ypoint=0F
    val paint= Paint().apply {
        isAntiAlias=true
        color= Color.MAGENTA
        style=Paint.Style.FILL_AND_STROKE
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.drawCircle(xpoint,ypoint,10F,paint)
        Log.d("FFOUUCSSS","COOMIngg othuuuuu")
        super.onDraw(canvas)
    }
    fun drawer(e:MotionEvent){
        this.xpoint=e.x
        this.ypoint=e.y
    }


}