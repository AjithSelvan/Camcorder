package com.airbender.camcorder

import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent

class SwipeGestureClass(Rot:Int):GestureDetector.SimpleOnGestureListener() {
    private var rot:Int =0
    init {
        rot=Rot
    }
    private var swipeCallback: SwipeCallback? =null
    override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
        if (e1==null || e2==null) return super.onFling(e1, e2, velocityX, velocityY)
        val deltaY=e1.y - e2.y
        val deltaYAbs= kotlin.math.abs(deltaY)
        val deltaX=e1.x - e2.x
        val deltaXAbs= kotlin.math.abs(deltaX)
        if (rot==0|| rot==2){
        if (deltaYAbs >= 100) {
            if(deltaY>0){
                swipeCallback?.onUpSwipe()
            }
            else{swipeCallback?.onDownSwipe()
            }
        }}
        else{
            if (deltaXAbs >= 100) {
                if(deltaX>0){
                    swipeCallback?.onUpSwipe()
                }
                else{swipeCallback?.onDownSwipe()
                }
            }
        }
        return true
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        if (e==null)  return super.onSingleTapUp(e)
        swipeCallback?.onTapUp()
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent?): Boolean {

        if(e==null) return super.onDoubleTapEvent(e)
        if (e.action==MotionEvent.ACTION_DOWN){
        swipeCallback?.onLongPress()}
        return true
    }

    interface  SwipeCallback{
        fun onUpSwipe()
        fun onDownSwipe()
        fun onTapUp()
        fun onLongPress()
    }
    fun setSwipeCallBack(up:()-> Unit={}, down:()-> Unit={}, tapFocus:()-> Unit={}, onLongPress:()-> Unit={}){
        swipeCallback=object :SwipeCallback{
            override fun onUpSwipe() {
                up()
            }
            override fun onDownSwipe() {
                down()
            }
            override fun onTapUp() {
                tapFocus()
            }
            override fun onLongPress() {
                onLongPress()
            }
        }
    }
}