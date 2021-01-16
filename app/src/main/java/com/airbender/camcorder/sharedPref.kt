package com.airbender.camcorder

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences

class sharedPref :Activity(){
    private var sharedPreferences:SharedPreferences = getSharedPreferences("Settings",Context.MODE_PRIVATE)
    fun getPref(key:String): Boolean {
        return sharedPreferences.getBoolean(key,false)
    }
    fun putPref(key: String,value : Boolean=false){
       with(sharedPreferences.edit()){
           putBoolean(key, value).apply()
        }
    }
}