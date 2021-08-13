# xuetaotao.github.io

# ImagePicker图片操作库

## 调用方法：

### 1.在app moudle的gradle配置依赖库

        allprojects {
            repositories {
                ...
                maven { url 'https://jitpack.io' }
            }
        }
        
        dependencies {
            implementation 'com.github.xuetaotao:imagepicker:1.0.2'
            
            //RxJava2
            implementation 'io.reactivex.rxjava2:rxjava:2.2.21'
            implementation 'io.reactivex.rxjava2:rxandroid:2.1.1'
              
            //RxPermissions  https://github.com/tbruyelle/RxPermissions
            implementation 'com.github.tbruyelle:rxpermissions:0.10.2'
                    
            //图片压缩(按需添加，如果使用LuBan压缩的话) https://github.com/Curzibn/Luban
            implementation 'top.zibin:Luban:1.1.8'
        }

### 2.具体使用

      ImagePicker.with(this)
              .imagePickerListener(object : ImagePicker.ImagePickerListener {
                  override fun onSuccess(imagePath: String) {
                      Log.e("TAG", "复制到APP外部私有目录地址：$imagePath")
                      Glide.with(this@MediaUtilsActivity).load(imagePath).into(imageView)
                  }

                  override fun onFailed(msg: String, code: String) {
                      Log.e("TAG", msg)
                      showToast(msg)
                  }
              })
              .compress(false)
              .crop(true)
              .isCamera(false)
              .startPick()

### 3.参数说明

    名称                  | 是否必选              | 描述
    isCamera                否                   默认为相机拍照，选择false即为从相册选择
    crop                    否                   是否对图片进行裁剪
    compress                否                   是否对图片进行压缩
    imagePickerListener     否                   加载回调，返回APP外部私有目录图片路径

## 4.版本升级计划

    *1.解决图片缓存过多，占用空间过大的问题
    *2.完成原生尺寸压缩quality的取值处理问题（刚开始递减的快一点，后面接近ignoreSize的时候递减慢一点）
