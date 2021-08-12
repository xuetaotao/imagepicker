package com.jlpay.imagepicker

import androidx.core.content.FileProvider

class ImagePickerFileProvider : FileProvider() {

    override fun onCreate(): Boolean {
        return true
    }
}