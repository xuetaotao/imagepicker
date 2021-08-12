package com.jlpay.imagepicker

/**
 * 用来定义执行图片操作的类型
 */
object ImageOperationKind {

    const val TAKE_PHOTO: String = "takePhoto"//拍照
    const val CHOOSE_PIC: String = "choosePic"//从相册选择图片
    const val IMAGE_CROP: String = "imageCrop"//图片裁剪
}