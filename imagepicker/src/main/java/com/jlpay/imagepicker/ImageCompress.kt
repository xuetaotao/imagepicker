package com.jlpay.imagepicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.text.TextUtils
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import top.zibin.luban.Luban
import top.zibin.luban.OnCompressListener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class ImageCompress {

    private val TAG: String = "ImagePicker"
    private var imgDirName: String

    constructor() : this("ImagePicker")

    constructor(imgDirName: String) : super() {
        this.imgDirName = imgDirName
    }

    /**
     * 注：这里的imagePath必须为APP外部私有目录下路径
     */
    fun compress(
        context: Context,
        imagePath: String,
        type: ImageCompressType,
        ignoreSize: Int,
        reqSize: Int,
        listener: ImageCompressListener,
    ) {
        when (type) {
            ImageCompressType.LuBan -> {
                luBanCompress(context, imagePath, ignoreSize, listener)
            }
            ImageCompressType.OriginCompress -> {
//                originCompress(context, imagePath, ignoreSize, listener)
                originCompress(context, imagePath, reqSize, reqSize, ignoreSize, listener)
            }
        }
    }

    fun luBanCompress(
        context: Context,
        imagePath: String,
        ignoreSize: Int,
        listener: ImageCompressListener,
    ) {
        val createAppPicDir = MediaUtils.Images.createAppPicDir(context, imgDirName)
        if (TextUtils.isEmpty(createAppPicDir)) {
            listener.onFailed(
                ErrorCodeBean.Message.APPPIC_DIR_CREATE_FAIL_MSG,
                ErrorCodeBean.Code.IMAGE_COMPRESS_CODE
            )
            return
        }
        Luban.with(context)
            .load(imagePath)
            .ignoreBy(ignoreSize)
            .setTargetDir(createAppPicDir)
            .setCompressListener(object : OnCompressListener {
                override fun onStart() {

                }

                override fun onSuccess(file: File?) {
                    if (file == null) {
                        listener.onFailed(
                            ErrorCodeBean.Message.LUBAN_FILE_NULL_MSG,
                            ErrorCodeBean.Code.IMAGE_COMPRESS_CODE
                        )
                    } else {
                        listener.onSuccess(file.absolutePath)
                    }
                }

                override fun onError(e: Throwable?) {
                    listener.onFailed(
                        e?.message ?: ErrorCodeBean.Message.LUBAN_UNKNOWN_MSG,
                        ErrorCodeBean.Code.IMAGE_COMPRESS_CODE
                    )
                }
            })
            .launch()
    }

    /**
     * 仅质量压缩
     * @param ignoreSize 低于这个大小不压缩，单位：KB
     */
    fun originCompress(
        context: Context,
        imagePath: String,
        ignoreSize: Int,
        listener: ImageCompressListener,
    ) {
        originCompress(context, imagePath, ignoreSize, listener, 60)
    }

    private fun originCompress(
        context: Context,
        imagePath: String,
        ignoreSize: Int,
        listener: ImageCompressListener,
        quality: Int,
    ) {
        if (!needCompress(imagePath, ignoreSize)) {
//            Log.e("TAG", "不需要压缩：" + imagePath)
            listener.onSuccess(imagePath)
            return
        }
        var bitmap: Bitmap? = null
        try {
            bitmap =
                rotatePicByDegree(BitmapFactory.decodeFile(imagePath), getPictureDegree(imagePath))
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            listener.onFailed(
                ErrorCodeBean.Message.BITMAP_OUTOFMEMORY_MSG + e.message,
                ErrorCodeBean.Code.IMAGE_COMPRESS_CODE
            )
            return
        }
        val byteArrayOutputStream: ByteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        var qualityCurrent: Int = 100
        var compressTime: Int = 0
        while (byteArrayOutputStream.toByteArray().size / 1024 > ignoreSize && qualityCurrent >= quality) {
            byteArrayOutputStream.reset()

            if (compressTime < 2) {
                qualityCurrent -= 10
            } else {
                qualityCurrent -= 5
            }
            compressTime++

            bitmap.compress(Bitmap.CompressFormat.JPEG, qualityCurrent, byteArrayOutputStream)
            Log.d(
                TAG,
                (byteArrayOutputStream.toByteArray().size / 1024).toString() + "KB" + "\t" + "quality = " + quality
            )
        }
        val createAppPicDir = MediaUtils.Images.createAppPicDir(context, imgDirName)
        if (TextUtils.isEmpty(createAppPicDir)) {
            listener.onFailed(
                ErrorCodeBean.Message.APPPIC_DIR_CREATE_FAIL_MSG,
                ErrorCodeBean.Code.IMAGE_COMPRESS_CODE
            )
            return
        }
        val file = File(createAppPicDir, "IMG" + System.currentTimeMillis() + ".jpg")
        try {
            val fileOutputStream: FileOutputStream = FileOutputStream(file)
            byteArrayOutputStream.writeTo(fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
            byteArrayOutputStream.close()
            listener.onSuccess(file.absolutePath)

        } catch (e: Exception) {
            e.printStackTrace()
            listener.onFailed(
                ErrorCodeBean.Message.STREAM_FAIL_MSG,
                ErrorCodeBean.Code.IMAGE_COMPRESS_CODE
            )
        } finally {
            bitmapRecycle(bitmap)
        }
    }

    /**
     * 判断是否需要压缩处理
     */
    private fun needCompress(imagePath: String, ignoreSize: Int): Boolean {
        if (ignoreSize > 0) {
            val file: File = File(imagePath)
//            Log.e("TAG",
//                "File大小：" + file.length() + "\t" + "ignoreSize大小：" + ignoreSize + "\t" + "移位运算：" + (ignoreSize shl 10))
            return file.exists() && (file.length() > (ignoreSize shl 10))//左移10(<<10)相当于乘以1024，单位变为 字节(Byte)
        }
        return true
    }

    /**
     * Android原生质量和尺寸压缩
     */
    fun originCompress(
        context: Context,
        imagePath: String,
        reqWidth: Int,
        reqHeight: Int,
        ignoreSize: Int,
        listener: ImageCompressListener,
    ) {
        originCompress(context, imagePath, reqWidth, reqHeight, ignoreSize, listener, 60)
    }

    private fun originCompress(
        context: Context,
        imagePath: String,
        reqWidth: Int,
        reqHeight: Int,
        ignoreSize: Int,
        listener: ImageCompressListener,
        quality: Int,
    ) {
        //如果inJustDecoedBounds设置为true的话，解码bitmap时可以只返回其高、宽和Mime类型，而不必为其申请内存，从而节省了内存空间；即只读取图片，不加载到内存中
        val options: BitmapFactory.Options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        var bitmap: Bitmap = BitmapFactory.decodeFile(imagePath)
        val width: Int = bitmap.width
        val height: Int = bitmap.height
        options.inJustDecodeBounds = false

        if (!needCompress(imagePath, ignoreSize) && width <= reqWidth && height <= reqHeight) {
//            Log.e("TAG", "不需要压缩：" + imagePath)
            listener.onSuccess(imagePath)
            return
        }

        //保证绝对压缩到 reqWidth*reqHeight
//        var sampleSize: Int = 1
//        while ((width / sampleSize > reqWidth) || (height / sampleSize > reqHeight)) {
//            sampleSize *= 2
//        }

        //不保证绝对压缩到需求的尺寸，接近略大于
        val sampleSize = max((width / reqWidth), (height / reqHeight))
        options.inSampleSize = sampleSize
        Log.d(TAG, "options.inSampleSize：" + options.inSampleSize)
        try {
            bitmap = BitmapFactory.decodeFile(imagePath, options)
        } catch (e: OutOfMemoryError) {
            listener.onFailed(
                ErrorCodeBean.Message.BITMAP_OUTOFMEMORY_MSG + e.message,
                ErrorCodeBean.Code.IMAGE_COMPRESS_CODE
            )
            return
        }
        bitmap = rotatePicByDegree(bitmap, getPictureDegree(imagePath))
        val byteArrayOutputStream: ByteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        var qualityCurrent = 100
        var compressTime: Int = 0

        Log.d(
            TAG,
            (byteArrayOutputStream.toByteArray().size / 1024).toString() + "KB" + "\t" + "qualityCurrent = " + qualityCurrent
                    + "\t" + "compressTime = " + compressTime
        )

        while (byteArrayOutputStream.toByteArray().size / 1024 > ignoreSize && qualityCurrent >= quality) {
            byteArrayOutputStream.reset()

            if (compressTime < 2) {
                qualityCurrent -= 10
            } else {
                qualityCurrent -= 5
            }
            compressTime++

            bitmap.compress(Bitmap.CompressFormat.JPEG, qualityCurrent, byteArrayOutputStream)

            Log.d(
                TAG,
                (byteArrayOutputStream.toByteArray().size / 1024).toString() + "KB" + "\t" + "qualityCurrent = " + qualityCurrent
                        + "\t" + "compressTime = " + compressTime
            )
        }

        val createAppPicDir = MediaUtils.Images.createAppPicDir(context, imgDirName)
        if (TextUtils.isEmpty(createAppPicDir)) {
            listener.onFailed(
                ErrorCodeBean.Message.APPPIC_DIR_CREATE_FAIL_MSG,
                ErrorCodeBean.Code.IMAGE_COMPRESS_CODE
            )
            return
        }
        val file = File(createAppPicDir, "IMG" + System.currentTimeMillis() + ".jpg")
        try {
            val fileOutputStream: FileOutputStream = FileOutputStream(file)
            byteArrayOutputStream.writeTo(fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
            byteArrayOutputStream.close()
            listener.onSuccess(file.absolutePath)

        } catch (e: Exception) {
            e.printStackTrace()
            listener.onFailed(
                ErrorCodeBean.Message.STREAM_FAIL_MSG,
                ErrorCodeBean.Code.IMAGE_COMPRESS_CODE
            )
        } finally {
            bitmapRecycle(bitmap)
        }
    }

    /**
     * 压缩值quality处理算法
     * TODO 参考LuBan完善
     */
    private fun qualityProcessAlgorithm(quality: Int): Int {
        return quality - 5
    }

    /**
     * 获取图片的旋转角度
     * 手机拍照的图片，本地查看正常的照片，传到服务器发现照片旋转了90°或者270°，这是因为有些手机摄像头的参数原因，拍出来的照片是自带旋转角度的
     * @param imagePath 图片路径
     * @return 图片旋转角度
     */
    private fun getPictureDegree(imagePath: String): Int {
        var degree: Int = 0
        try {
            val exifInterface: ExifInterface = ExifInterface(imagePath)
            val orientation: Int = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> degree = 90
                ExifInterface.ORIENTATION_ROTATE_180 -> degree = 180
                ExifInterface.ORIENTATION_ROTATE_270 -> degree = 270
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return degree
    }

    /**
     * 将图片按照某个角度进行旋转
     * @param bitmap 待旋转图片
     * @param degree 旋转角度
     * @return 旋转后的图片
     */
    private fun rotatePicByDegree(bitmap: Bitmap, degree: Int): Bitmap {
        var rotateBitmap: Bitmap? = null
        val matrix: Matrix = Matrix()
        matrix.postRotate(degree.toFloat())//根据旋转角度，生成旋转矩阵
        try {
            rotateBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        }
        if (rotateBitmap == null) {
            rotateBitmap = bitmap
        }
        if (bitmap != rotateBitmap) {
            bitmap.recycle()
        }
        return rotateBitmap
    }

    fun bitmapRecycle(vararg bitmaps: Bitmap?) {
        for (bitmap in bitmaps) {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    enum class ImageCompressType {
        LuBan, OriginCompress
    }

    interface ImageCompressListener {

        fun onSuccess(imageCompressPath: String)

        fun onFailed(msg: String, code: String)
    }
}