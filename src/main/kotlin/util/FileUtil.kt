package util

import java.io.File
import java.io.FileInputStream

object FileUtil {

    fun readFromFile(filepath: String): IntArray {
        val file = File(filepath)
        val bytes = ByteArray(file.length().toInt())
        val fileInputStream = FileInputStream(file)
        try {
            fileInputStream.read(bytes)
        } catch (e: Exception) {
            println("File reading failed: ${e.printStackTrace()}")
        }
        val unsignedByteValues = IntArray(bytes.size)
        for (i: Int in bytes.indices) {
            unsignedByteValues[i] = (bytes[i].toInt() and 0xFF)
        }
        return unsignedByteValues
    }
}