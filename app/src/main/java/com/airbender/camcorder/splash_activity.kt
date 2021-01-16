package com.airbender.camcorder

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity

lateinit var handler: Handler
class splash_activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splashanime)
        handler= Handler()
        handler.postDelayed(
            {
                val intent=Intent(this,MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.slide_in_left,android.R.anim.slide_out_right)
                finish()
            }
        ,1000)
    }
}