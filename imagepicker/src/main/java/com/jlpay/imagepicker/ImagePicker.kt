package com.jlpay.imagepicker

import android.app.Activity
import android.net.Uri
import android.text.TextUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.io.File
import java.io.InputStream

class ImagePicker private constructor(builder: Builder) {

    val TAG: String = ImagePicker::class.java.simpleName

    val imgDirName: String = builder.imgDirName
    val isCamera: Boolean = builder.isCamera
    val compress: Boolean = builder.compress
    val compressType: ImageCompress.ImageCompressType = builder.compressType
    val compressIgnoreSize: Int = builder.compressIgnoreSize
    val reqSize: Int = builder.reqSize
    val crop: Boolean = builder.crop
    val authority: String = builder.authority
    var picToAppPicPath: String? = null//TODO 可以考虑进一步的优化
    val listener: ImagePickerListener? = builder.listener
    val fragmentActivity: FragmentActivity = builder.fragmentActivity

    var mImagePickerFragment: Lazy<ImagePickerFragment>
    var mediaUtils: MediaUtils

    init {
        mImagePickerFragment = getLazySingleton(fragmentActivity.supportFragmentManager)
        mediaUtils = getLazyMediaUtils(imgDirName)
    }

    companion object {
        @JvmStatic
        fun with(fragmentActivity: FragmentActivity): Builder {
            return Builder(fragmentActivity)
        }
    }

    private fun getLazyMediaUtils(imgDirName: String): MediaUtils {
        if (this.mediaUtils == null) {
            synchronized(ImagePicker::class.java) {
                if (this.mediaUtils == null) {
                    this.mediaUtils = MediaUtils(imgDirName)
                }
            }
        }
        return mediaUtils
    }

    private fun getLazySingleton(fragmentManager: FragmentManager): Lazy<ImagePickerFragment> {
        return object : Lazy<ImagePickerFragment> {

            private var imagePickerFragment: ImagePickerFragment? = null

            @Synchronized
            override fun get(): ImagePickerFragment {
                if (this.imagePickerFragment == null) {
                    this.imagePickerFragment = getImagePickerFragment(fragmentManager)
                }
                return this.imagePickerFragment!!
            }
        }
    }

    private fun getImagePickerFragment(fragmentManager: FragmentManager): ImagePickerFragment? {
        var imagePickerFragment = findImagePickerFragment(fragmentManager)
        val isNewInstance: Boolean = imagePickerFragment == null
        if (isNewInstance) {
            imagePickerFragment = ImagePickerFragment()
            fragmentManager.beginTransaction().add(imagePickerFragment, TAG).commitNow()
        }
        return imagePickerFragment
    }

    private fun findImagePickerFragment(fragmentManager: FragmentManager): ImagePickerFragment? {
        val fragment: Fragment? = fragmentManager.findFragmentByTag(TAG)
        return if (fragment != null) {
            fragment as ImagePickerFragment
        } else {
            null
        }
    }

    private fun requestImplementation(
        imageOperationKind: String,
        uri: Uri?,
        cropOutputUri: Uri?,
    ): Observable<ImagePickerResult> {
        val list: ArrayList<Observable<ImagePickerResult>> = ArrayList()
        var subject: PublishSubject<ImagePickerResult>? =
            this.mImagePickerFragment.get().getSubjectByOperationKind(imageOperationKind)
        if (subject == null) {
            subject = PublishSubject.create()
            this.mImagePickerFragment.get().setSubjectForOperationKind(imageOperationKind, subject)
        }
        list.add(subject)

        when (imageOperationKind) {
            ImageOperationKind.TAKE_PHOTO -> {
                takePhotoRequestFromFragment(imageOperationKind, uri!!)
            }
            ImageOperationKind.CHOOSE_PIC -> {
                choosePicRequestFromFragment(imageOperationKind)
            }
            ImageOperationKind.IMAGE_CROP -> {
                imageCropRequestFromFragment(imageOperationKind, uri!!, cropOutputUri!!)
            }
        }

        return Observable.concat(Observable.fromIterable(list))
    }

    private fun setLogging(logging: Boolean) {
        this.mImagePickerFragment.get().setLogging(logging)
    }

    private fun takePhotoRequestFromFragment(
        imageOperationKind: String,
        uri: Uri,
    ) {
        this.mImagePickerFragment.get()
            .log("takePhotoRequestFromFragment:\t" + "imageOperationKind:$imageOperationKind, uri:${uri.toString()}")
        this.mImagePickerFragment.get().takePhoto(imageOperationKind, uri)
    }

    private fun choosePicRequestFromFragment(imageOperationKind: String) {
        this.mImagePickerFragment.get()
            .log("choosePicRequestFromFragment:\timageOperationKind:$imageOperationKind")
        this.mImagePickerFragment.get().choosePic(imageOperationKind)
    }

    private fun imageCropRequestFromFragment(
        imageOperationKind: String,
        uri: Uri,
        cropOutputUri: Uri,
    ) {
        this.mImagePickerFragment.get()
            .log("imageCropRequestFromFragment:\timageOperationKind:$imageOperationKind")
        this.mImagePickerFragment.get().imageCrop(imageOperationKind, uri, cropOutputUri)
    }

    fun startPick() {
        val subscribe = Observable.just(0)
            //请求权限
            .flatMap {
//                Log.e(TAG, "请求权限的线程：" + Thread.currentThread().name)//main
                if (isCamera) {
                    RxPermissions(fragmentActivity).request(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                } else {
                    RxPermissions(fragmentActivity)
                        .request(
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                }
            }
            //调起拍照/选择相册
            .flatMap(object : Function<Boolean, ObservableSource<Uri>> {
                override fun apply(granted: Boolean): ObservableSource<Uri> {
//                    Log.e(TAG, "调起拍照/选择相册的线程：" + Thread.currentThread().name)//main
                    if (!granted) {
                        return Observable.error(Exception(ErrorCodeBean.Message.PERMISSION_GRANT_FAIL_MSG))
                    }
                    if (isCamera) {
                        val createImgContentPicUri: Uri =
                            mediaUtils.createImgContentPicUri(fragmentActivity)
                                ?: return Observable.error(Exception(ErrorCodeBean.Message.PUBPIC_DIR_CREATE_FAIL_MSG))
                        return requestImplementation(
                            ImageOperationKind.TAKE_PHOTO,
                            createImgContentPicUri,
                            null
                        )
                            .flatMap(object : Function<ImagePickerResult, ObservableSource<Uri>> {
                                override fun apply(t: ImagePickerResult): ObservableSource<Uri> {
                                    if (t.resultCode == Activity.RESULT_CANCELED) {
                                        return Observable.error(Exception(ErrorCodeBean.Message.USER_CANCELED + ErrorCodeBean.Code.CANCEL_CODE))
                                    } else if (t.resultCode != Activity.RESULT_OK) {
                                        return Observable.error(Exception(ErrorCodeBean.Message.PHOTO_RESULT_FAIL_MSG + t.resultCode))
                                    }
                                    return Observable.just(createImgContentPicUri)
                                }
                            })

                    } else {
                        return requestImplementation(ImageOperationKind.CHOOSE_PIC, null, null)
                            .flatMap(object : Function<ImagePickerResult, ObservableSource<Uri>> {
                                override fun apply(t: ImagePickerResult): ObservableSource<Uri> {
                                    if (t.resultCode == Activity.RESULT_CANCELED) {
                                        return Observable.error(Exception(ErrorCodeBean.Message.USER_CANCELED + ErrorCodeBean.Code.CANCEL_CODE))
                                    } else if (t.resultCode != Activity.RESULT_OK) {
                                        return Observable.error(Exception(ErrorCodeBean.Message.CHOOSE_PIC_RESULT_FAIL_MSG + t.resultCode))
                                    }
                                    if (t.uri == null) {
                                        return Observable.error(Exception(ErrorCodeBean.Message.RESULT_URI_NULL_MSG + t.resultCode))
                                    }
                                    return Observable.just(t.uri)
                                }
                            })
                    }
                }
            })
            //复制图片到APP外部私有目录，不执行这一步的话，调用ACTION_GET_CONTENT选择最近照片时，返回uri再去裁剪会出问题
            .observeOn(Schedulers.io())
            .flatMap(object : Function<Uri, ObservableSource<Uri>> {
                override fun apply(t: Uri): ObservableSource<Uri> {
                    if (isCamera) {
                        return Observable.just(t)
                    }
//                    Log.e(TAG,
//                        "复制图片到APP外部私有目录的线程：" + Thread.currentThread().name)//RxCachedThreadScheduler-1
                    val inputStream: InputStream =
                        MediaUtils.Images.getImageFromPic(fragmentActivity, t)
                            ?: return Observable.error(Exception(ErrorCodeBean.Message.PIC_CHOOSE_NOT_EXIST))
                    val copyImgFromPicToAppPic: String? =
                        mediaUtils.copyImgFromPicToAppPic(fragmentActivity, t)
                    if (copyImgFromPicToAppPic == null || TextUtils.isEmpty(copyImgFromPicToAppPic)) {
                        return Observable.error(Exception(ErrorCodeBean.Message.PIC_COPY_TOAPPPIC_FAIL_MSG))
                    }
                    picToAppPicPath =
                        copyImgFromPicToAppPic//全局变量保存当前复制到APP外部私有目录的路径，以便跳过压缩前复制那一步
                    val imageContentUri: Uri = mediaUtils.getImageContentUri(
                        fragmentActivity,
                        copyImgFromPicToAppPic,
                        authority
                    )
                        ?: return Observable.error(Exception(ErrorCodeBean.Message.APPPIC_URI_NULL_MSG))
                    return Observable.just(imageContentUri)
                }
            })
            //图片裁剪
            .compose(object : ObservableTransformer<Uri, Uri> {
                override fun apply(upstream: Observable<Uri>): ObservableSource<Uri> {
//                    Log.e(TAG, "图片裁剪compose的线程：" + Thread.currentThread().name)//main 为什么???
                    if (!crop) {
                        return upstream
                    }
                    val cropOutputUri: Uri =
                        mediaUtils.createImgContentPicUri(fragmentActivity)//这里只能是外部共享目录，否则会报保存错误
                            ?: return Observable.error(Exception(ErrorCodeBean.Message.CROP_PUBPIC_URI_FAIL_MSG))
                    return upstream.flatMap { t ->
//                        Log.e(TAG,
//                            "图片裁剪的线程：" + Thread.currentThread().name)//RxCachedThreadScheduler-1
                        requestImplementation(ImageOperationKind.IMAGE_CROP, t, cropOutputUri)
                            .flatMap(object :
                                Function<ImagePickerResult, ObservableSource<Uri>> {
                                override fun apply(t: ImagePickerResult): ObservableSource<Uri> {
                                    if (t.resultCode == Activity.RESULT_CANCELED) {
                                        return Observable.error(Exception(ErrorCodeBean.Message.USER_CANCELED + ErrorCodeBean.Code.CANCEL_CODE))
                                    } else if (t.resultCode != Activity.RESULT_OK) {
                                        return Observable.error(Exception(ErrorCodeBean.Message.CROP_PIC_RESULT_FAIL_MSG + t.resultCode))
                                    }
                                    return Observable.just(cropOutputUri)
                                }
                            })
                    }
                }
            })
            .observeOn(Schedulers.io())
            .flatMap(object : Function<Uri, ObservableSource<String>> {
                override fun apply(t: Uri): ObservableSource<String> {
//                    Log.e(TAG,
//                        "复制图片到APP外部私有目录的线程2：" + Thread.currentThread().name)//RxCachedThreadScheduler-1
                    //从相册选择图片，前一步已经做过复制操作，这里跳过
                    if (!isCamera && !TextUtils.isEmpty(picToAppPicPath)) {
                        if (!crop) {
                            return Observable.just(picToAppPicPath)
                        } else {
                            val delete = File(picToAppPicPath).delete()
                        }
                    }
                    val inputStream: InputStream =
                        MediaUtils.Images.getImageFromPic(fragmentActivity, t)
                            ?: return Observable.error(Exception(ErrorCodeBean.Message.PIC_CHOOSE_NOT_EXIST))
                    val copyImgFromPicToAppPic: String? =
                        mediaUtils.copyImgFromPicToAppPic(fragmentActivity, t)
                    if (copyImgFromPicToAppPic == null || TextUtils.isEmpty(copyImgFromPicToAppPic)) {
                        return Observable.error(Exception(ErrorCodeBean.Message.PIC_COPY_TOAPPPIC_FAIL_MSG))
                    }
                    return Observable.just(copyImgFromPicToAppPic)
                }
            })
            //图片压缩
            .compose(object : ObservableTransformer<String, String> {
                override fun apply(upstream: Observable<String>): ObservableSource<String> {
//                    Log.e(TAG, "图片压缩compose的线程：" + Thread.currentThread().name)//main
                    return upstream.flatMap(object : Function<String, ObservableSource<String>> {
                        override fun apply(t: String): ObservableSource<String> {
//                            Log.e(TAG,
//                                "图片压缩的线程22：" + Thread.currentThread().name)//RxCachedThreadScheduler-1
                            if (!compress) {
                                return Observable.just(t)
                            }

                            return Observable.create { emitter ->
//                                Log.e(TAG,
//                                    "图片压缩的线程33：" + Thread.currentThread().name)//RxCachedThreadScheduler-1
                                ImageCompress(imgDirName).compress(fragmentActivity,
                                    t,
                                    compressType,
                                    compressIgnoreSize,
                                    reqSize,
                                    object : ImageCompress.ImageCompressListener {
                                        override fun onSuccess(imageCompressPath: String) {
//                                            Log.e(TAG, "压缩后图片路径：$imageCompressPath")
                                            if (!TextUtils.equals(imageCompressPath, t)) {
                                                val delete = File(t).delete()
//                                                Log.e(TAG, "复制到APP私有目录下的原图删除结果：$delete")
                                            }
                                            emitter.onNext(imageCompressPath)
//                                            Log.e(TAG,
//                                                "压缩后图片大小：" + File(imageCompressPath).length())
                                        }

                                        override fun onFailed(msg: String, code: String) {
                                            emitter.onError(Exception(msg))
                                        }
                                    })
                            }
                        }
                    })
                }
            })
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ t -> listener?.onSuccess(t!!) }
            ) { t ->
                listener?.onFailed(
                    t?.message ?: ErrorCodeBean.Message.UNKNOWN_ERROR_MSG,
                    ErrorCodeBean.Code.IMAGE_PICKER_CODE
                )
            }
    }


    class Builder(internal var fragmentActivity: FragmentActivity) {

        internal val TAG: String = "ImagePicker"
        internal var imgDirName: String = "ImagePicker"
        internal var isCamera: Boolean = true//默认为相机拍照
        internal var compress: Boolean = false//压缩
        internal var compressType: ImageCompress.ImageCompressType =
            ImageCompress.ImageCompressType.OriginCompress//默认使用原生压缩
        internal var compressIgnoreSize: Int = 1024//默认压缩阈值:单位KB
        internal var reqSize: Int = 1500//默认的压缩尺寸：1500*1500
        internal var crop: Boolean = false//裁剪
        internal var authority: String =
            "com.jlpay.imagepick.fileprovider." + fragmentActivity.application.packageName//Provider授权标志，裁剪图片Crop功能用到
        internal var listener: ImagePickerListener? = null

        fun imgDirName(imgDirName: String) = apply {
            this.imgDirName = imgDirName
        }

        fun isCamera(isCamera: Boolean) = apply {
            this.isCamera = isCamera
        }

        fun compress(compress: Boolean) = apply {
            this.compress = compress
        }

        fun compressType(compressType: ImageCompress.ImageCompressType) = apply {
            this.compressType = compressType
        }

        fun compressIgnoreSize(compressIgnoreSize: Int) = apply {
            this.compressIgnoreSize = compressIgnoreSize
        }

        fun reqSize(reqSize: Int) = apply {
            this.reqSize = reqSize
        }

        //解决Android11调用ACTION_GET_CONTENT选择最近照片时，返回uri再去裁剪的如下问题:(故而选择先复制到APP外部私有目录下，再根据其路径获取uri进行操作)
        //UID 10363 does not have permission to content://com.android.providers.media.documents/document/image%3A7402 [user 0]; you could obtain access using ACTION_OPEN_DOCUMENT or related APIs
        fun crop(crop: Boolean) = apply {
            this.crop = crop
        }

        fun imagePickerListener(listener: ImagePickerListener) = apply {
            this.listener = listener
        }

        fun build(): ImagePicker = ImagePicker(this)

        private fun checkRxJavaSdk(): Boolean {
            return try {
                val clazz = Class.forName("io.reactivex.Observable")
                true
            } catch (e: Exception) {
                false
            } catch (e: Error) {
                false
            }
        }

        private fun checkRxPermissionsSdk(): Boolean {
            return try {
                val clazz = Class.forName("com.tbruyelle.rxpermissions2.RxPermissions")
                true
            } catch (e: Exception) {
                false
            } catch (e: Error) {
                false
            }
        }

        private fun checkLuBanSdk(): Boolean {
            return try {
                val clazz = Class.forName("top.zibin.luban.Luban")
                true
            } catch (e: Exception) {
                false
            } catch (e: Error) {
                false
            }
        }

        fun startPick() {
            if (!checkRxJavaSdk()) {
                listener?.onFailed(
                    ErrorCodeBean.Message.LEAK_LIBRARY_RXJAVA_MSG,
                    ErrorCodeBean.Code.LEAK_LIBRARY_CODE
                )
                return
            }
            if (!checkRxPermissionsSdk()) {
                listener?.onFailed(
                    ErrorCodeBean.Message.LEAK_LIBRARY_RXPERMISSIONS_MSG,
                    ErrorCodeBean.Code.LEAK_LIBRARY_CODE
                )
                return
            }
            if (compress && compressType == ImageCompress.ImageCompressType.LuBan && !checkLuBanSdk()) {
                listener?.onFailed(
                    ErrorCodeBean.Message.LEAK_LIBRARY_LUBAN_MSG,
                    ErrorCodeBean.Code.LEAK_LIBRARY_CODE
                )
                return
            }
            build().startPick()
        }
    }


    interface ImagePickerListener {

        fun onSuccess(imagePath: String)

        fun onFailed(msg: String, code: String)
    }

    interface Lazy<V> {
        fun get(): V
    }


}