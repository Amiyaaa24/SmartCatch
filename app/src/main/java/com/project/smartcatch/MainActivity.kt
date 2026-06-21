package com.project.smartcatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hàm xử lý Intent được gọi để bóc tách dữ liệu và ghi Log
        handleSharedIntent(intent)

        setContent {
            // Biến kiểm soát trạng thái hiển thị của Bottom Sheet
            var showBottomSheet by remember { mutableStateOf(false) }

            val realImageUri = remember {
                if (intent?.action == Intent.ACTION_SEND) {
                    val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    uri?.toString() ?: ""
                } else ""
            }

            LaunchedEffect(Unit) {
                if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
                    showBottomSheet = true
                }
            }

            // Giao diện chính của màn hình
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(text = "SmartCatch đã sẵn sàng xử lý dữ liệu!")
                }
            }

            // Gọi giao diện Bottom Sheet (Bước 1 được nhúng vào đây)
            if (showBottomSheet) {
                SmartCatchBottomSheet(
                    onDismiss = {
                        showBottomSheet = false
                        finish()
                    },
                    onSave = { name, folder ->
                        val inputData = Data.Builder()
                            .putString("FILE_NAME", name)
                            .putString("FOLDER_NAME", folder)
                            .putString("FILE_URI", realImageUri)
                            .build()

                        val saveRequest = OneTimeWorkRequestBuilder<SaveFileWorker>()
                            .setInputData(inputData)
                            .build()

                        // 3. Giao việc cho WorkManager
                        WorkManager.getInstance(applicationContext).enqueue(saveRequest)

                        Log.d("SmartCatch", "Đã ném tác vụ vào hàng đợi nền")

                        showBottomSheet = false
                        finish() // Đóng pop-up và trả người dùng về app cũ ngay lập tức
                    }
                )
            }
        }
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val type = intent.type

        when {
            // Trường hợp 1: Nhận 1 mục (Xử lý cả ảnh hoặc link đơn lẻ)
            Intent.ACTION_SEND == action && type != null -> {
                if (type.startsWith("image/")) {
                    val imageUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    if (imageUri != null) {
                        Log.d("SmartCatch", "Đường dẫn ảnh đơn: $imageUri")
                    }
                } else if ("text/plain" == type) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (sharedText != null) {
                        Log.d("SmartCatch", "Nội dung link nhận được: $sharedText")
                    }
                }
            }

            // Trường hợp 2: Nhận nhiều ảnh cùng lúc
            Intent.ACTION_SEND_MULTIPLE == action && type != null -> {
                if (type.startsWith("image/")) {
                    val imageUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    if (imageUris != null) {
                        Log.d("SmartCatch", "Số lượng ảnh nhận được: ${imageUris.size}")
                        for (uri in imageUris) {
                            Log.d("SmartCatch", "URI ảnh trong danh sách: $uri")
                        }
                    }
                }
            }
        }
    }
}

// BƯỚC 1: Hàm cấu trúc giao diện Bottom Sheet Pop-up
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCatchBottomSheet(
    onDismiss: () -> Unit,
    onSave: (fileName: String, folderName: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    var fileName by remember { mutableStateOf("Rename") }
    var folderName by remember { mutableStateOf("Pictures/SmartCatch") }

    // THÊM MỚI: Các biến dùng cho Dropdown Menu
    var expanded by remember { mutableStateOf(false) }
    val folderOptions = listOf("Pictures/SmartCatch", "DCIM/Camera", "Download", "Tạo thư mục mới...")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "SmartCatch",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 1. Ô nhập tên ảnh
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("Đổi tên ảnh") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Ô chọn Album (Đã nâng cấp thành Dropdown Menu)
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {},
                    readOnly = true, // Chỉ cho phép chọn, không cho phép gõ phím
                    label = { Text("Chọn Album") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor() // Bắt buộc phải có để menu trượt xuống đúng vị trí
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    folderOptions.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                folderName = selectionOption
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Nút Lưu
            Button(
                onClick = { onSave(fileName, folderName) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("LƯU")
            }
        }
    }
}