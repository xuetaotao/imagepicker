package com.jlpay.imagepicker

object ErrorCodeBean {

    object Message {
        const val RESULT_FAIL_MSG = "resultCode!=RESULT_OK，回调失败"
        const val COPY_TOAPPPIC_FAIL_MSG = "选择的图片复制到APP外部私有目录失败"
        const val RESULT_URI_NULL_MSG = "ImagePickerResult返回Uri为空"
        const val RESULT_NULL_MSG = "ImagePickerResult返回为空"
        const val CHOOSE_PIC_RESULT_FAIL_MSG = "resultCode!=RESULT_OK，相册选择照片回调失败"
        const val CHOOSE_PIC_RESULT_URI_NULL_MSG = "选择照片返回的ImagePickerResult.Uri为空"
        const val CROP_AUTHORITY_NULL_MSG = "裁剪图片时传入的authority为空"
        const val CROP_APPPIC_URI_NULL_MSG = "裁剪图片时复制原图到APP私有目录下并获取图片Uri出错"

        const val PHOTO_COPY_TOAPPPIC_FAIL_MSG = "拍照照片复制到APP外部私有目录失败"
        const val PHOTO_RESULT_FAIL_MSG = "resultCode!=RESULT_OK，拍照回调失败;"

        const val APPPIC_DIR_CREATE_FAIL_MSG = "APP外部私有目录下压缩图片保存目录创建失败"
        const val STREAM_FAIL_MSG = "压缩图片流操作失败"
        const val LUBAN_FILE_NULL_MSG = "LuBan压缩成功，但是返回file为空"
        const val LUBAN_UNKNOWN_MSG = "LuBan压缩未知错误"
        const val BITMAP_OUTOFMEMORY_MSG = "Bitmap内存溢出"

        const val LEAK_LIBRARY_RXJAVA_MSG = "缺少RxJava依赖库"
        const val LEAK_LIBRARY_RXPERMISSIONS_MSG = "缺少RxPermissions依赖库"
        const val LEAK_LIBRARY_LUBAN_MSG = "缺少Luban依赖库"

        const val PUBPIC_DIR_CREATE_FAIL_MSG = "外部共享目录Uri创建失败"
        const val PERMISSION_GRANT_FAIL_MSG = "权限获取失败"
        const val CROP_PUBPIC_URI_FAIL_MSG = "裁剪图片的外部共享目录Uri创建失败"
        const val CROP_PIC_RESULT_FAIL_MSG = "resultCode!=RESULT_OK，裁剪图片回调失败"

        const val PIC_COPY_TOAPPPIC_FAIL_MSG = "图片复制到APP外部私有目录失败"
        const val APPPIC_URI_NULL_MSG = "APP私有目录下图片获取Uri出错"
        const val APPPIC_TO_PATH_FAIL_MSG = "图片Uri转换图片路径Path失败"

        const val USER_CANCELED = "用户取消"
        const val UNKNOWN_ERROR_MSG = "未知错误"
    }

    object Code {
        const val TAKE_PHOTO_CODE = "01"

        const val CHOOSE_PIC_CODE = "02"

        const val IMAGE_COMPRESS_CODE = "03"

        const val LEAK_LIBRARY_CODE = "04"

        const val IMAGE_PICKER_CODE = "05"

        const val CANCEL_CODE = "06"
    }

}