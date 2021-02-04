package com.airbender.camcorder

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity

class sharedPref(applicationContext: Context) :AppCompatActivity() {
    companion object {
        const val SHAREDPREFERENCE_NAME = "Settings"
    }

    private val sharedPreferences: SharedPreferences = applicationContext.getSharedPreferences(SHAREDPREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getSharedPreference() = sharedPreferences
    fun getPref(key: String): Boolean {
        return sharedPreferences.getBoolean(key, false)
    }

    fun putPref(key: String, value: Boolean) {
        with(sharedPreferences.edit()) {
            putBoolean(key, value).apply()
        }
    }

}