package com.jlpay.imagepicker


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.InputStream

/**
 * 针对Android11版本新特性的适配
 */
interface IAndroid11Upgrade {

    //1.内部存储目录的读写：/data 目录。一般我们使用 getFilesDir() 或 getCacheDir() 方法获取本应用的内部储存路径，读写该路径下的文件不需要申请储存空间读写权限，
    // 且卸载应用时会自动删除


    /**
     * 外部存储目录的读写:
     * /storage 或 /mnt 目录。一般我们使用 getExternalStorageDirectory() 方法获取的路径来存取文件
     *
     * 按文件位置/访问方法/卸载应用是否移除文件分为三部分：
     * 1.特定目录（App-specific），使用 getExternalFilesDir() 或 getExternalCacheDir() 方法访问。无需权限，且卸载应用时会自动删除(操作不变)
     * 2.照片、视频、音频这类媒体文件(Download、Documents、Pictures 、DCIM、Movies、Music、Ringtones等，详见Environment.STANDARD_DIRECTORIES)。
     * 使用 MediaStore 访问，访问其他应用的媒体文件时需要 READ_EXTERNAL_STORAGE 权限
     * 3.其他目录，使用 存储访问框架SAF （Storage Access Framwork）
     */
    //从外部共享目录下获取图片
    fun getImgFromPubPic(context: Context, uri: Uri): InputStream?

    //将图片保存到外部共享目录Picture下
    fun saveImgToPubPic(context: Context, inputStream: InputStream): Uri?

    //保存图片到外部共享目录Picture下
    fun saveImgToPubPic(context: Context, bitmap: Bitmap): Uri?

    //将外部共享目录图片复制到APP外部私有目录
    fun copyImgFromPicToAppPic(context: Context, uri: Uri): String?

    //将APP外部私有目录复制到外部共享目录
    fun copyImgFromAppPicToPic(context: Context, imgPath: String): Uri?

    //拍照保存到外部共享目录
    fun createImgContentPicUri(context: Context): Uri?

    //拍照保存到APP外部私有目录
    fun createImgContentAppPicUri(context: Context, authority: String?): Uri?

    //String路径与Uri之间的转换
    fun getImageContentUri(context: Context, imagePath: String, authority: String): Uri?
}