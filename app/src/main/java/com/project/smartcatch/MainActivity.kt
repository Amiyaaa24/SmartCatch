package com.project.smartcatch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        setContent {
            val context = LocalContext.current
            var showBottomSheet by remember { mutableStateOf(false) }

            // 1. Nhận diện và trích xuất dữ liệu dựa theo kiểu chia sẻ (Ảnh hoặc Link)
            val realImageUri = remember {
                if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
                    val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    uri?.toString() ?: ""
                } else ""
            }

            // THÊM MỚI: Trích xuất chuỗi văn bản văn bản/đường dẫn (text/plain)
            val sharedLink = remember {
                if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                } else ""
            }

            // Kích hoạt Bottom Sheet nếu có bất kỳ luồng chia sẻ nào truyền tới
            LaunchedEffect(Unit) {
                if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
                    showBottomSheet = true
                }
            }

            // Quản lý cấp quyền hệ thống
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                permissions.entries.forEach {
                    Log.d("SmartCatch", "Quyền ${it.key} được cấp: ${it.value}")
                }
            }

            LaunchedEffect(Unit) {
                val permissionsToRequest = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                val missingPermissions = permissionsToRequest.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }.toTypedArray()

                if (missingPermissions.isNotEmpty()) {
                    permissionLauncher.launch(missingPermissions)
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "SmartCatch đã sẵn sàng xử lý dữ liệu!")
                }
            }

            // 2. Điều hướng hiển thị Bottom Sheet dựa vào loại dữ liệu đầu vào
            if (showBottomSheet) {
                if (sharedLink.isNotEmpty()) {
                    // LUỒNG MỚI: Người dùng chia sẻ đường dẫn (Giai đoạn 2)
                    // Tạm thời hiển thị một thông báo hoặc giao diện chờ bóc tách link
                    ModalBottomSheet(
                        onDismissRequest = {
                            showBottomSheet = false
                            finishAndRemoveTask()
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Link nhận được:", style = MaterialTheme.typography.titleMedium)
                            Text(text = sharedLink, modifier = Modifier.padding(vertical = 8.dp))
                            CircularProgressIndicator() // Hiển thị vòng xoay chờ bóc tách
                            Text(text = "Đang bóc tách liên kết...", modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                } else {
                    // LUỒNG CŨ: Người dùng chia sẻ ảnh trực tiếp (Giai đoạn 1)
                    SmartCatchBottomSheet(
                        onDismiss = {
                            showBottomSheet = false
                            finishAndRemoveTask()
                        },
                        onSave = { name, folder ->
                            if (realImageUri.isNotEmpty()) {
                                val inputData = Data.Builder()
                                    .putString("FILE_NAME", name)
                                    .putString("FOLDER_NAME", folder)
                                    .putString("FILE_URI", realImageUri)
                                    .build()

                                val saveRequest = OneTimeWorkRequestBuilder<SaveFileWorker>()
                                    .setInputData(inputData)
                                    .build()

                                WorkManager.getInstance(applicationContext).enqueue(saveRequest)
                            }
                            showBottomSheet = false
                            finishAndRemoveTask()
                        }
                    )
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "smartcatch_channel",
            "Thông báo SmartCatch",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Kênh hiển thị thông báo khi lưu file thành công"
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCatchBottomSheet(
    onDismiss: () -> Unit, // ĐÃ SỬA TẠI ĐÂY: Khai báo đúng kiểu dữ liệu callback () -> Unit
    onSave: (fileName: String, folderName: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    var fileName by remember { mutableStateOf("Rename") }
    var folderName by remember { mutableStateOf("Pictures/SmartCatch") }
    var expanded by remember { mutableStateOf(false) }

    var folderOptions by remember { mutableStateOf(listOf("Pictures/SmartCatch", "Đang tải danh sách...")) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val dynamicFolders = getLocalImageFolders(context)
        folderOptions = dynamicFolders + "➕ Tạo thư mục mới..."
    }

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

            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("Đổi tên ảnh") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Chọn Album") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
                                if (selectionOption == "➕ Tạo thư mục mới...") {
                                    showCreateFolderDialog = true
                                } else {
                                    folderName = selectionOption
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSave(fileName, folderName) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("LƯU")
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Tạo thư mục mới") },
            text = {
                Column {
                    Text(
                        text = "Thư mục mới sẽ được tạo trong mục Pictures/",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newFolderNameInput,
                        onValueChange = { newFolderNameInput = it },
                        label = { Text("Tên thư mục") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderNameInput.isNotBlank()) {
                            val formattedPath = "Pictures/${newFolderNameInput.trim()}"
                            folderName = formattedPath
                            val currentListWithoutAction = folderOptions.filter { it != "➕ Tạo thư mục mới..." }
                            folderOptions = (currentListWithoutAction + formattedPath).sorted() + "➕ Tạo thư mục mới..."
                            showCreateFolderDialog = false
                            newFolderNameInput = ""
                        }
                    }
                ) {
                    Text("Tạo")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateFolderDialog = false
                        newFolderNameInput = ""
                    }
                ) {
                    Text("Hủy")
                }
            }
        )
    }
}

suspend fun getLocalImageFolders(context: Context): List<String> = withContext(Dispatchers.IO) {
    val folders = mutableSetOf<String>()
    val projection = arrayOf(MediaStore.Images.Media.RELATIVE_PATH)
    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val path = cursor.getString(pathColumn)
                if (path != null) {
                    folders.add(path.trimEnd('/'))
                }
            }
        }
    } catch (e: Exception) {
        Log.e("SmartCatch", "Lỗi khi quét thư mục", e)
    }

    val defaultFolders = listOf("Pictures/SmartCatch", "DCIM/Camera", "Download")
    (defaultFolders + folders.sorted()).distinct()
}