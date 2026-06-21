package com.project.smartcatch

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore


class SaveFileWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val fileName = inputData.getString("FILE_NAME") ?: return Result.failure()
        val folderName = inputData.getString("FOLDER_NAME") ?: return Result.failure()
        val fileUriString = inputData.getString("FILE_URI") ?: return Result.failure()

        Log.d("SmartCatch", "Bắt đầu lưu: $fileName vào $folderName")

        val resolver = applicationContext.contentResolver
        val inputUri = Uri.parse(fileUriString)

        // 1. Khai báo thông tin file mới sẽ được tạo trong hệ thống
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.jpg") // Tạm định dạng jpg
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            // RELATIVE_PATH chính là nơi quyết định thư mục lưu trữ (VD: Pictures/SmartCatch)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folderName)
        }

        return try {
            // 2. Yêu cầu hệ thống tạo một "vỏ" file trống tại đích đến
            val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (outputUri != null) {
                // 3. Đọc luồng dữ liệu từ ảnh được Share (Input) và đổ vào vỏ file trống (Output)
                resolver.openInputStream(inputUri)?.use { inputStream ->
                    resolver.openOutputStream(outputUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d("SmartCatch", "Lưu thành công!")
                Result.success()
            } else {
                Log.e("SmartCatch", "Không thể tạo file đầu ra")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e("SmartCatch", "Lỗi sao chép file", e)
            Result.failure()
        }
    }
}