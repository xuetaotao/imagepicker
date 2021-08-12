package com.jlpay.imagepicker

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.*
import java.util.*

class MediaUtils : IAndroid11Upgrade {

    private lateinit var imgDirName: String

    constructor() : this("ImagePicker")

    constructor(imgDirName: String) {
        this.imgDirName = imgDirName
    }

    override fun getImgFromPubPic(context: Context, uri: Uri): InputStream? {
        return Images.getImageFromPic(context, uri)
    }

    override fun saveImgToPubPic(context: Context, inputStream: InputStream): Uri? {
        return Images.insertImageToPic(context, inputStream, imgDirName)
    }

    override fun saveImgToPubPic(context: Context, bitmap: Bitmap): Uri? {
        return Images.insertImageToPic(context, bitmap, imgDirName)
    }

    override fun copyImgFromPicToAppPic(context: Context, uri: Uri): String? {
        return Images.copyImgFromPicToAppPic(context, uri, imgDirName)
    }

    override fun copyImgFromAppPicToPic(context: Context, imgPath: String): Uri? {
        return Images.copyImgFromAppPicToPic(context, imgPath, imgDirName)
    }

    override fun createImgContentPicUri(context: Context): Uri? {
        return Images.createImageContentUri(context, true, imgDirName, null)
    }

    override fun createImgContentAppPicUri(context: Context, authority: String?): Uri? {
        return Images.createImageContentUri(context, false, imgDirName, authority)
    }

    override fun getImageContentUri(context: Context, imagePath: String, authority: String): Uri? {
        return Images.getImageContentUri(context, imagePath, authority)
    }

    class Images {

        companion object {

            private fun getPicPath(imgDirName: String): String {
                return Environment.DIRECTORY_PICTURES + File.separator + imgDirName + File.separator
            }

            private fun getDcimPath(imgDirName: String): String {
                return Environment.DIRECTORY_DCIM + File.separator + imgDirName + File.separator
            }

            /**
             * 保存图片到外部共享Pic(暂时没有优先DCIM)目录下
             * @return 创建图片的URL地址(eg: content://media/external/images/media/6612)或null
             */
            fun createImgPicUri(context: Context, imgDirName: String): Uri? {
                if (!checkPermission(context)) {
                    return null
                }

                //1.创建ContentValues
                val imgFileName = "IMG" + System.currentTimeMillis() + ".jpg"
                val contentValues = ContentValues()
                contentValues.put(MediaStore.Images.ImageColumns.DISPLAY_NAME,
                    imgFileName)//设置带扩展名的文件名，如：IMG1024.JPG
                contentValues.put(MediaStore.Images.ImageColumns.MIME_TYPE,
                    getImgMimeType(imgFileName)
                )//设置文件类型，类似于image/jpeg
                contentValues.put(MediaStore.Images.ImageColumns.DATE_ADDED,
                    System.currentTimeMillis())//第一次被添加的时间
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    //AndroidQ 中不再使用DATA字段，而用RELATIVE_PATH代替，RELATIVE_PATH是相对路径不是绝对路径，DCIM是系统文件夹
                    contentValues.put(MediaStore.Images.ImageColumns.RELATIVE_PATH,
                        getPicPath(imgDirName)
                    )//文件存储的相对路径
                } else {
                    //AndroidQ以下版本，直接使用外部公共存储目录下的图片绝对路径，不能去掉，因为上面的要求API>=29，在10.0以下设备中下一步insert产生的Uri会为空
                    val imgFileDir = EXTERN_STORAGE_PATH + getPicPath(imgDirName)
                    if (createDirs(context, imgFileDir)) {
                        val imgFilePath = imgFileDir + imgFileName
                        contentValues.put(MediaStore.Images.ImageColumns.DATA,
                            imgFilePath)//文件存储的绝对路径
                    }
                }

                //2.执行insert操作，向系统文件夹中添加文件；若生成了uri，则表示该文件添加成功，使用流将内容写入该uri中即可。
                // 这一步会在Picture目录下创建imgDirName文件夹(没有该文件夹时)
                val contentResolver = context.contentResolver
                val pendingUri: Uri? =
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues)
                return pendingUri
            }


            /**
             * 保存图片到外部共享目录Pic下
             * @return 保存到的外部共享目录Pic下的Uri或者null
             */
            fun insertImageToPic(
                context: Context,
                inputStream: InputStream,
                imgDirName: String,
            ): Uri? {
                val contentResolver = context.contentResolver
                val pendingUri: Uri? = createImgPicUri(context, imgDirName)
                //3.执行图片数据插入操作
                if (pendingUri != null) {
                    try {
                        val outputStream: OutputStream? =
                            contentResolver.openOutputStream(pendingUri)
                        if (outputStream != null) {
                            copy(inputStream, outputStream)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        contentResolver.delete(pendingUri, null, null)
                        return null
                    }
                }
                return pendingUri
            }

            /**
             * 保存图片到外部共享目录Pic下
             * @return 保存到的外部共享目录Pic下的Uri或者null
             */
            fun insertImageToPic(
                context: Context,
                bitmap: Bitmap,
                imgDirName: String,
            ): Uri? {
                val contentResolver = context.contentResolver
                val pendingUri: Uri? = createImgPicUri(context, imgDirName)
                if (pendingUri != null) {
                    try {
                        val outputStream: OutputStream? =
                            contentResolver.openOutputStream(pendingUri)
                        if (outputStream != null) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        contentResolver.delete(pendingUri, null, null)
                        return null
                    }
                }
                return pendingUri
            }

            /**
             * 从外部共享目录获取图片
             */
            fun getImageFromPic(context: Context, uri: Uri): InputStream? {
                if (!checkPermission(context)) {
                    return null
                }
                return try {
                    context.contentResolver.openInputStream(uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            /**
             * 将图片路径转换为Uri
             * 注：若为外部共享目录下的图片，可以直接转化
             * 若为APP外部私有目录下的图片，则(方式一：先插入外部共享目录下)（方式二：返回图片的Uri地址），然后返回Uri
             * @return Uri
             */
            fun getImageContentUri(
                context: Context,
                imagePath: String,
                authority: String,
            ): Uri? {
                if (!checkPermission(context)) {
                    return null
                }
                val cursor: Cursor? = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.ImageColumns._ID),
                    MediaStore.Images.ImageColumns.DATA + "=? ",
                    arrayOf(imagePath), null
                )
                var uri: Uri? = null
                if (cursor != null && cursor.moveToFirst()) {
                    //若为外部共享目录下的图片，可以直接把路径转化为Uri
                    val id =
                        cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID))
                    if (-1 != id) {
                        uri =
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id.toLong())
                    }
                } else {
                    //操作一：如果图片不在外部共享目录图片数据库，就先把它插入
//                    val inputStream = FileInputStream(imagePath)
//                    uri = insertImageToPic(context,
//                        inputStream,
//                        DEFAULT_EXTERN_DIR_NAME)//这里固定写了，若使用该方式可以考虑改为变量
                    //操作二：返回图片的Uri地址
                    val file = File(imagePath)
                    uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(context,
                            authority,
                            file)// content://com.jlpay.kotlindemo.FileProvider/external_files_path/Image/IMG1625475923370.jpg
                    } else {
                        Uri.fromFile(file)// file://storage/emulated/0/Android/data/com.jlpay.kotlindemo/files/Image/IMG1625477375523.jpg
                    }
                }
                cursor?.close()
                return uri
            }

            /**
             * 根据图片Uri获取图片Path路径，仅建议APP外部私有目录下使用该方法
             * Android10下外部存储不能直接通过文件路径访问文件
             *
             * TODO 有点问题，先别用
             */
            fun getImagePath(context: Context, uri: Uri): String? {
                var imagePath: String? = null
                // 以 file:// 开头的，比如 file://storage/emulated/0/Android/data/com.jlpay.kotlindemo/files/Image/IMG1625477375523.jpg
                if (ContentResolver.SCHEME_FILE == uri.scheme) {
                    imagePath = uri.path
                }
                // 以 content:// 开头的，比如 content://com.jlpay.kotlindemo.FileProvider/external_files_path/Image/IMG1625475923370.jpg
                else if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
                    var cursor: Cursor? = null
                    val projection = arrayOf(MediaStore.Images.ImageColumns.DATA)
                    try {
                        cursor = context.contentResolver.query(uri, projection, null, null, null)
                        if (cursor != null && cursor.moveToFirst()) {
                            val columnIndex: Int = cursor.getColumnIndexOrThrow(projection[0])
                            imagePath = cursor.getString(columnIndex)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return null
                    } finally {
                        cursor?.close()
                    }
                }
                return imagePath
            }

            /**
             * 创建图片Uri
             * @param isPubPicUri 是否为外部共享目录创建的图片Uri
             * @return
             */
            fun createImageContentUri(
                context: Context, isPubPicUri: Boolean, imgDirName: String, authority: String?,
            ): Uri? {
                if (!checkPermission(context)) {
                    return null
                }
                if (isPubPicUri) {
                    return imgDirName?.let {
                        createImgPicUri(context,
                            it)
                    }//直接通过MediaStore API操作即可，也可以和FileProvider一起使用(在10.0版本下)
                } else {
                    var uri: Uri? = null
                    val IMG_APP_EXTERNAL: String? = createAppPicDir(context, imgDirName)
                    if (!TextUtils.isEmpty(IMG_APP_EXTERNAL)) {
                        val file =
                            File(IMG_APP_EXTERNAL, "IMG" + System.currentTimeMillis() + ".jpg")
                        uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            authority?.let {
                                FileProvider.getUriForFile(context,
                                    authority,
                                    file)// content://com.jlpay.kotlindemo.FileProvider/external_files_path/Image/IMG1625475923370.jpg
                            }
                        } else {
                            Uri.fromFile(file)// file://storage/emulated/0/Android/data/com.jlpay.kotlindemo/files/Image/IMG1625477375523.jpg
                        }
                    }
                    return uri
                }
            }


            /**
             * 复制外部共享目录下图片到外部APP私有目录Image下
             * @return 外部APP私有目录Image下的保存路径
             */
            fun copyImgFromPicToAppPic(context: Context, uri: Uri, imgDirName: String): String? {
                if (!checkPermission(context)) {
                    return null
                }
                var imgPath: String? = null
                val IMG_APP_EXTERNAL = createAppPicDir(context, imgDirName)
                if (!TextUtils.isEmpty(IMG_APP_EXTERNAL)) {
                    val imgFileName = "IMG" + System.currentTimeMillis() + ".jpg"
                    val file = File(IMG_APP_EXTERNAL, imgFileName)
                    val inputStream = getImageFromPic(context, uri)
                    try {
                        val fileOutputStream = FileOutputStream(file)
                        if (inputStream != null) {
                            copy(inputStream, fileOutputStream)
                            imgPath = file.absolutePath
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return null
                    }
                }
                return imgPath
            }

            /**
             * 复制外部APP私有目录下图片到外部共享目录下
             * @return 外部共享目录下Img的Uri地址
             */
            fun copyImgFromAppPicToPic(
                context: Context,
                imgPath: String,
                imgDirName: String,
            ): Uri? {
                if (!checkPermission(context)) {
                    return null
                }
                val inputStream = FileInputStream(imgPath)
                return insertImageToPic(context, inputStream, imgDirName)
            }


            /**
             * 创建APP私有目录下图片保存目录
             */
            fun createAppPicDir(context: Context, imgDirName: String): String? {
                val IMG_APP_EXTERNAL: String = context.getExternalFilesDir(null)
                    .toString() + File.separator + "Image" + File.separator + imgDirName + File.separator
                if (createDirs(context, IMG_APP_EXTERNAL)) {
                    return IMG_APP_EXTERNAL
                }
                return null
            }

            private fun getImgMimeType(imgFileName: String): String {
                val toLowerCase = imgFileName.toLowerCase(Locale.ROOT)
                if (toLowerCase.endsWith("jpg") || toLowerCase.endsWith("jpeg")) {
                    return "image/jpeg"
                } else if (toLowerCase.endsWith("png")) {
                    return "image/png"
                } else if (toLowerCase.endsWith("gif")) {
                    return "image/gif"
                }
                return "image/jpeg"
            }
        }
    }


    companion object {
        //Android10以下版本使用
        private var EXTERN_STORAGE_PATH: String =
            Environment.getExternalStorageDirectory().absolutePath + File.separator

        private fun checkPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        fun createDirs(context: Context, dir: String): Boolean {
            if (!checkPermission(context)) {
                return false
            }
            if (TextUtils.isEmpty(dir)) {
                return false
            }
            val fileDir = File(dir)
            if (!fileDir.exists()) {
                return fileDir.mkdirs()
            }
            return true
        }

        private fun copy(inputStream: InputStream, outputStream: OutputStream) {
            val buffer: ByteArray = ByteArray(1024)
            var readLength: Int
            try {
                while (inputStream.read(buffer).also { readLength = it } != -1) {
                    outputStream.write(buffer, 0, readLength)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    inputStream.close()
                    outputStream.flush()
                    outputStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}