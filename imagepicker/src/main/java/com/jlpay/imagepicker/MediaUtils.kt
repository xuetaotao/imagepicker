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
             * ???????????????????????????Pic(??????????????????DCIM)?????????
             * @return ???????????????URL??????(eg: content://media/external/images/media/6612)???null
             */
            fun createImgPicUri(context: Context, imgDirName: String): Uri? {
                if (!checkPermission(context)) {
                    return null
                }

                //1.??????ContentValues
                val imgFileName = "IMG" + System.currentTimeMillis() + ".jpg"
                val contentValues = ContentValues()
                contentValues.put(MediaStore.Images.ImageColumns.DISPLAY_NAME,
                    imgFileName)//???????????????????????????????????????IMG1024.JPG
                contentValues.put(MediaStore.Images.ImageColumns.MIME_TYPE,
                    getImgMimeType(imgFileName)
                )//??????????????????????????????image/jpeg
                contentValues.put(MediaStore.Images.ImageColumns.DATE_ADDED,
                    System.currentTimeMillis())//???????????????????????????
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    //AndroidQ ???????????????DATA???????????????RELATIVE_PATH?????????RELATIVE_PATH????????????????????????????????????DCIM??????????????????
                    contentValues.put(MediaStore.Images.ImageColumns.RELATIVE_PATH,
                        getPicPath(imgDirName)
                    )//???????????????????????????
                } else {
                    //AndroidQ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????API>=29??????10.0????????????????????????insert?????????Uri?????????
                    val imgFileDir = EXTERN_STORAGE_PATH + getPicPath(imgDirName)
                    if (createDirs(context, imgFileDir)) {
                        val imgFilePath = imgFileDir + imgFileName
                        contentValues.put(MediaStore.Images.ImageColumns.DATA,
                            imgFilePath)//???????????????????????????
                    }
                }

                //2.??????insert?????????????????????????????????????????????????????????uri???????????????????????????????????????????????????????????????uri????????????
                // ???????????????Picture???????????????imgDirName?????????(?????????????????????)
                val contentResolver = context.contentResolver
                val pendingUri: Uri? =
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues)
                return pendingUri
            }


            /**
             * ?????????????????????????????????Pic???
             * @return ??????????????????????????????Pic??????Uri??????null
             */
            fun insertImageToPic(
                context: Context,
                inputStream: InputStream,
                imgDirName: String,
            ): Uri? {
                val contentResolver = context.contentResolver
                val pendingUri: Uri? = createImgPicUri(context, imgDirName)
                //3.??????????????????????????????
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
             * ?????????????????????????????????Pic???
             * @return ??????????????????????????????Pic??????Uri??????null
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
             * ?????????????????????????????????
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
             * ????????????????????????Uri
             * ???????????????????????????????????????????????????????????????
             * ??????APP????????????????????????????????????(??????????????????????????????????????????)??????????????????????????????Uri????????????????????????Uri
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
                    //?????????????????????????????????????????????????????????????????????Uri
                    val id =
                        cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID))
                    if (-1 != id) {
                        uri =
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id.toLong())
                    }
                } else {
                    //????????????????????????????????????????????????????????????????????????????????????
//                    val inputStream = FileInputStream(imagePath)
//                    uri = insertImageToPic(context,
//                        inputStream,
//                        DEFAULT_EXTERN_DIR_NAME)//???????????????????????????????????????????????????????????????
                    //???????????????????????????Uri??????
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
             * ????????????Uri
             * @param isPubPicUri ??????????????????????????????????????????Uri
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
                    }//????????????MediaStore API???????????????????????????FileProvider????????????(???10.0?????????)
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
             * ??????????????????????????????????????????APP????????????Image???
             * @return ??????APP????????????Image??????????????????
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
             * ????????????APP?????????????????????????????????????????????
             * @return ?????????????????????Img???Uri??????
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
             * ??????APP?????????????????????????????????
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
        //Android10??????????????????
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