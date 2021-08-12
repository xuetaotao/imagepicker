package com.jlpay.imagepicker

import android.annotation.TargetApi
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import io.reactivex.subjects.PublishSubject

class ImagePickerFragment : Fragment() {

    private val TAG: String = "ImagePicker"
    private val TAKE_PHOTO_REQUEST_CODE: Int = 0x1001
    private val CHOOSE_PIC: Int = 0x1002
    private val IMAGE_CROP: Int = 0x1003

    private var mLogging: Boolean = false
    private var mSubjects: HashMap<String, PublishSubject<ImagePickerResult>> = HashMap()
    private var imageOperationKind: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    fun takePhoto(imageOperationKind: String, uri: Uri) {
        this.imageOperationKind = imageOperationKind
        val intent: Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        startActivityForResult(intent, TAKE_PHOTO_REQUEST_CODE)
    }

    fun choosePic(imageOperationKind: String) {
        this.imageOperationKind = imageOperationKind
//        val intent: Intent = Intent(Intent.ACTION_PICK)//小米机型Android11版本返回resultCode!=RESULT_OK
        val intent: Intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityForResult(intent, CHOOSE_PIC)
    }

    fun imageCrop(imageOperationKind: String, uri: Uri, cropOutputUri: Uri) {
        this.imageOperationKind = imageOperationKind
        val intent: Intent = Intent("com.android.camera.action.CROP")
        intent.setDataAndType(uri, "image/*")
        intent.putExtra("crop", "true")//设置为裁切
        //设置裁切的宽高比例
        if (Build.MANUFACTURER == "HUAWEI") {//解决华为手机，裁剪后圆头像问题
            intent.putExtra("aspectX", 9998)//裁切的宽比例
            intent.putExtra("aspectY", 9999)//裁切的高比例
        } else {
            intent.putExtra("aspectX", 1)//裁切的宽比例
            intent.putExtra("aspectY", 1)//裁切的高比例
        }
        //设置裁切的宽度和高度
        intent.putExtra("outputX", 500)//裁切的宽度
        intent.putExtra("outputY", 500)//裁切的高度
        intent.putExtra("scale", true)//支持缩放
        intent.putExtra("return-data", false)//剪切的图片不直接返回，返回uri,否则6.0可能会返回null
        intent.putExtra(MediaStore.EXTRA_OUTPUT,
            cropOutputUri)//这里是剪切后图片保存的文件地址，加了这行，可能有些手机返回的intent是空的，所以最好直接使用这个uri获取剪切后的图片
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())//裁切成的图片的格式
        intent.putExtra("noFaceDetection", true)//no face detection，没有人脸检测
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        startActivityForResult(intent, IMAGE_CROP)
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //后续可以加上请求权限
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            TAKE_PHOTO_REQUEST_CODE -> {
                onActivityResult(resultCode, data)
            }
            CHOOSE_PIC -> {
                onActivityResult(resultCode, data)
            }
            IMAGE_CROP -> {
                onActivityResult(resultCode, data)
            }
        }
    }

    fun onActivityResult(resultCode: Int, data: Intent?) {
        val subject: PublishSubject<ImagePickerResult>? = this.mSubjects[imageOperationKind]
        this.mSubjects.remove(imageOperationKind)
        subject.run {
            if (this == null) {
                Log.e(TAG,
                    "ImagePicker.onActivityResult invoked but didn't find the corresponding imageOperationKind request.")
                return
            }
            mSubjects.remove(imageOperationKind)
            onNext(ImagePickerResult(resultCode, data?.data))
            onComplete()
        }
    }

    fun getSubjectByOperationKind(imageOperationKind: String): PublishSubject<ImagePickerResult>? {
        return if (this.mSubjects[imageOperationKind] != null) {
            this.mSubjects[imageOperationKind] as PublishSubject<ImagePickerResult>
        } else {
            null
        }
    }

    fun setSubjectForOperationKind(
        imageOperationKind: String,
        subject: PublishSubject<ImagePickerResult>
    ) {
        this.mSubjects[imageOperationKind] = subject
    }

    fun containsByOperationKind(imageOperationKind: String): Boolean {
        return this.mSubjects.containsKey(imageOperationKind)
    }

    fun setLogging(logging: Boolean) {
        this.mLogging = logging
    }

    fun log(message: String) {
        if (this.mLogging) {
            Log.d(TAG, message)
        }
    }
}