package com.jlpay.demo

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jlpay.imagepicker.ImagePicker

class MainActivity : AppCompatActivity() {

    lateinit var iv_image: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        iv_image = findViewById(R.id.iv_image)
    }

    fun imagePickerTest(view: View) {
        ImagePicker.with(this)
            .imagePickerListener(object : ImagePicker.ImagePickerListener {
                override fun onFailed(msg: String, code: String) {
                    Toast.makeText(this@MainActivity, msg + code, Toast.LENGTH_SHORT).show()
                }

                override fun onSuccess(imagePath: String) {
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    iv_image.setImageBitmap(bitmap)
                }
            })
            .compress(true)
            .crop(true)
            .isCamera(false)
            .startPick()
    }

    fun imagePickerCamera(view: View) {
        ImagePicker.with(this)
            .imagePickerListener(object : ImagePicker.ImagePickerListener {
                override fun onFailed(msg: String, code: String) {
                    Toast.makeText(this@MainActivity, msg + code, Toast.LENGTH_SHORT).show()
                }

                override fun onSuccess(imagePath: String) {
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    iv_image.setImageBitmap(bitmap)
                }
            })
            .compress(true)
            .crop(true)
            .isCamera(true)
            .startPick()
    }


}