package com.taknikiniga.documentscanner

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.GridLayoutManager
import com.blankj.utilcode.util.FileUtils.getFileMD5ToString
import com.taknikiniga.documentscanner.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.experimental.and
import kotlin.experimental.or


class MainActivity : AppCompatActivity() {

    val imageUriList = mutableListOf<ImageListModel>()

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Checking Permission
        Permission.checkPermission(this)
        loadImages()
    }

    private fun loadImages() = CoroutineScope(Dispatchers.IO).launch {
        val uri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val filesList = mutableListOf<File>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, sortOrder)

        cursor?.use {
            val idColumns = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumns = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
//            val dataColumns = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumns)
                val name = it.getString(nameColumns)
//                val data = it.getString(dataColumns)

                val contentUri: Uri =
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
//                Log.e("ImageUri", "$contentUri")
                imageUriList.add(
                    ImageListModel(
                        imageUri = contentUri,
                        imageName = name,
                        imageId = id
                    )
                )

                filesList.add(File(contentUri.toString()))

            }
            bindRecyclerView()


            imageUriList.forEachIndexed { index, it ->
//
//                if (File(it.imageUri.toString()).length() /1024==File(it.imageUri.toString()).length() /1024 && imageUriList){
//                    Log.e("FileSize", "loadImages:${it.imageName} ", )
//                }
            }


//            filterDuplicateImages(imageUriList, contentResolver).forEach {
//                Log.e("DublicateImageLength", "loadImages: ${File(it.toString()).name} ")
//            }


        }
    }

    private fun bindRecyclerView() {
        binding.recyclerView.adapter = ImageAdapter().also { imageAdapter ->
            imageAdapter.images = imageUriList
        }
        val layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.layoutManager = layoutManager


    }

}

data class ImageListModel(val imageName: String, val imageUri: Uri, val imageId: Long)

suspend fun filterDuplicateImages(
    imageList: List<ImageListModel>,
    contentResolver: ContentResolver
): List<Uri> {

    return withContext(Dispatchers.IO) {
        val uniqueImages: MutableSet<String> = HashSet()
        val filteredList: MutableList<Uri> = ArrayList()

        for (imageUri in imageList) {
            val imageHash = getImageHash(imageUri.imageUri, contentResolver)
            if (!uniqueImages.contains(imageHash)) {
                uniqueImages.add(imageHash)
                filteredList.add(imageUri.imageUri)
            }
        }
        filteredList
    }

}

suspend fun getImageHash(uri: Uri, contentResolver: ContentResolver): String {
    val bitmap: Bitmap? = decodeUri(uri, contentResolver)
    val resizedBitmap: Bitmap? = resizeBitmap(bitmap)
    val hashValue: String? = calculateDHash(resizedBitmap)

    return hashValue ?: ""
}

suspend fun decodeUri(uri: Uri, contentResolver: ContentResolver): Bitmap? {

    return withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        val inputStream = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()
        bitmap
    }


}

fun resizeBitmap(bitmap: Bitmap?): Bitmap? {
    if (bitmap == null) {
        return null
    }

    val targetSize = 9 // Adjust the size as per your requirements
    return Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
}

fun calculateDHash(bitmap: Bitmap?): String? {
    if (bitmap == null) {
        return null
    }

    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val grayscalePixels = convertToGrayscale(pixels, width, height)
    val binaryPixels = convertToBinary(grayscalePixels, width, height)
    val hashValue = convertToHex(binaryPixels)

    return hashValue
}

fun convertToHex(binaryPixels: BooleanArray): String {
    val sb = StringBuilder()

    for (i in binaryPixels.indices step 8) {
        val endIndex = i + 8
        if (endIndex <= binaryPixels.size) {
            val byte = booleanArrayToByte(binaryPixels.copyOfRange(i, endIndex))
            sb.append(String.format("%02x", byte))
        }
    }

    return sb.toString()
}


fun booleanArrayToByte(booleanArray: BooleanArray): Byte {
    require(booleanArray.size <= 8) { "Boolean array size must not exceed 8" }

    var result: Byte = 0
    for (i in booleanArray.indices) {
        if (booleanArray[i]) {
            result = result or (1 shl (7 - i)).toByte()
        }
    }
    return result
}

fun convertToGrayscale(pixels: IntArray, width: Int, height: Int): ByteArray {
    val grayPixels = ByteArray(width * height)

    for (i in pixels.indices) {
        val pixel = pixels[i]
        val red = (pixel shr 16 and 0xFF).toFloat()
        val green = (pixel shr 8 and 0xFF).toFloat()
        val blue = (pixel and 0xFF).toFloat()
        val gray = (red * 0.2989f + green * 0.587f + blue * 0.114f).toInt()
        grayPixels[i] = gray.toByte()
    }

    return grayPixels
}

fun convertToBinary(pixels: ByteArray, width: Int, height: Int): BooleanArray {
    val binaryPixels = BooleanArray(width * height)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixelIndex = y * width + x
            val pixel = pixels[pixelIndex].toInt()
            val nextPixel = if (x < width - 1) pixels[pixelIndex + 1].toInt() else pixel

            binaryPixels[pixelIndex] = nextPixel > pixel
        }
    }

    return binaryPixels
}


suspend fun bitmapToByteArray(bitmap: Bitmap?): ByteArray? {

    return withContext(Dispatchers.IO) {
        if (bitmap == null) {
            return@withContext null
        }

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        stream.close()

        byteArray
    }

}

suspend fun getDuplicateFiles(files: List<File>): List<Duplicate> =
    withContext(Dispatchers.Default) {
        val hashmap: HashMap<String, Duplicate> = HashMap()
        val duplicateHashSet: HashMap<String, Duplicate> = HashMap()

        for (file in files) {
            val md5 = getFileMD5ToString(file)
            if (hashmap.containsKey(md5)) {
                val original = hashmap[md5]
                if (duplicateHashSet.containsKey(md5)) {
                    val fileList: MutableList<DuplicateFile> =
                        original?.duplicateFiles?.toMutableList()
                            ?: mutableListOf()
                    fileList.add(
                        DuplicateFile(
                            original?.duplicateFiles?.get(0)?.file ?: file,
                            false
                        )
                    )
                } else {
                    val fileList: MutableList<DuplicateFile> = mutableListOf()
                    original?.duplicateFiles?.get(0)?.file?.let { firstDuplicateFile ->
                        fileList.add(DuplicateFile(firstDuplicateFile, false))
                    }
                    fileList.add(DuplicateFile(file, false))
                    duplicateHashSet[md5] = Duplicate(fileList)
                }
            } else {
                val fileList: MutableList<DuplicateFile> = mutableListOf()
                fileList.add(DuplicateFile(file, false))
                hashmap[md5] = Duplicate(fileList)
            }
        }

        duplicateHashSet.values.toList()
    }

//fun getDuplicateFiles(files: List<File>): List<Duplicate>? {
//    val hashmap = HashMap<String, Duplicate>()
//    val duplicateHashSet = HashMap<String, Duplicate>()
//    for (file in files) {
//        val md5: String = getFileMD5ToString(file)
//        if (hashmap.containsKey(md5)) {
//            val original: Duplicate? = hashmap[md5]
//            if (duplicateHashSet.containsKey(md5)) {
//                var fileList = mutableListOf<DuplicateFile>()
//                if (original != null) {
//                    fileList = original.getDuplicateFiles()
//                    fileList.add(
//                        DuplicateFile(
//                            original.getDuplicateFiles()[0].file,
//                            false
//                        )
//                    )
//                } else {
//
//                    fileList.add(DuplicateFile(file!!, false))
//                }
//            } else {
//                val fileList = mutableListOf<DuplicateFile>()
//                if (original!!.getDuplicateFiles() == null) {
//                    original.setDuplicateFiles(fileList)
//                }
//                fileList.add(DuplicateFile(original.getDuplicateFiles().get(0).file, false))
//                fileList.add(DuplicateFile(file!!, false))
//                duplicateHashSet[md5] = Duplicate(fileList)
//            }
//        } else {
//            val fileList: MutableList<DuplicateFile> = ArrayList<DuplicateFile>()
//            fileList.add(DuplicateFile(file!!, false))
//            hashmap[md5] = Duplicate(fileList)
//        }
//    }
//    var list: List<Duplicate>? = null
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//        list = ArrayList(duplicateHashSet.values)
//    }
//    Log.e("Do", "getDuplicateFiles: ")
//    return list
//}

private fun getFileMD5ToStrings(file: File): String {
    val messageDigest = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(8192)
    val inputStream = file.inputStream()
    var bytesRead: Int

    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        messageDigest.update(buffer, 0, bytesRead)
    }

    inputStream.close()

    val md5Bytes = messageDigest.digest()
    val sb = StringBuilder()
    for (i in md5Bytes.indices) {
        sb.append(Integer.toString((md5Bytes[i] and 0xff.toByte()) + 0x100, 16).substring(1))
    }
    return sb.toString()
}

data class DuplicateFile(val file: File, val isOriginal: Boolean)

data class Duplicate(val duplicateFiles: List<DuplicateFile>)


//class Duplicate(duplicateFiles: MutableList<DuplicateFile>) : Serializable {
//    var duplicateFiles: MutableList<DuplicateFile>
//
//    init {
//        this.duplicateFiles = duplicateFiles
//    }
//
//    fun getDuplicateFiles(): MutableList<DuplicateFile> {
//        return duplicateFiles
//    }
//
//    fun setDuplicateFiles(duplicateFiles: MutableList<DuplicateFile>) {
//        this.duplicateFiles = duplicateFiles
//    }
//
//    override fun toString(): String {
//        return """
//            {duplicateFiles=$duplicateFiles}
//
//            """.trimIndent()
//    }
//}
//
//class DuplicateFile(var file: File, var isSelect: Boolean) {
//
//    override fun toString(): String {
//        return "{" +
//                "file=" + file +
//                ", isSelect=" + isSelect +
//                '}'
//    }
//}