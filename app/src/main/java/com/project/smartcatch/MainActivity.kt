package com.project.smartcatch

import android.Manifest
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            var showBottomSheet by remember { mutableStateOf(false) }

            val realImageUri = remember {
                if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
                    val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    uri?.toString() ?: ""
                } else ""
            }

            LaunchedEffect(Unit) {
                if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
                    showBottomSheet = true
                }
            }

            // Quản lý cấp quyền đọc bộ nhớ (Đã lược bỏ quyền thông báo đẩy)
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

            if (showBottomSheet) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCatchBottomSheet(
    onDismiss: () -> Unit,
    onSave: (fileName: String, folderName: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    var fileName by remember { mutableStateOf("Rename") }
    var folderName by remember { mutableStateOf("Pictures/SmartCatch") }
    var expanded by remember { mutableStateOf(false) }

    var folderOptions by remember { mutableStateOf(listOf("Pictures/SmartCatch", "Đang tải danh sách...")) }

    LaunchedEffect(Unit) {
        folderOptions = getLocalImageFolders(context)
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
                                folderName = selectionOption
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