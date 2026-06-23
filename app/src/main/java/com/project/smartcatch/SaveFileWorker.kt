package com.project.smartcatch

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SaveFileWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val fileName = inputData.getString("FILE_NAME") ?: return Result.failure()
        val folderName = inputData.getString("FOLDER_NAME") ?: return Result.failure()
        val fileUriString = inputData.getString("FILE_URI") ?: return Result.failure()

        val resolver = applicationContext.contentResolver
        val inputUri = Uri.parse(fileUriString)

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folderName)
        }

        return try {
            val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (outputUri != null) {
                resolver.openInputStream(inputUri)?.use { inputStream ->
                    resolver.openOutputStream(outputUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Hiển thị Toast an toàn trên luồng giao diện chính (Main Thread)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "Đã xếp ảnh vào: $folderName",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}