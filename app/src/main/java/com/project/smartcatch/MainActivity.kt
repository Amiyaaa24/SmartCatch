package com.project.smartcatch // Đảm bảo đúng package name của bạn

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Kiểm tra xem app được mở bằng cách bình thường hay được Share tới
        handleSharedIntent(intent)

        setContent {
            // Tạm thời hiển thị text cơ bản để test
            Text(text = "SmartCatch đang chạy!")
        }
    }

    // Hàm xử lý dữ liệu được share tới
    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val type = intent.type

        when {
            // Trường hợp 1: Nhận 1 ảnh hoặc 1 đoạn text (link)
            Intent.ACTION_SEND == action && type != null -> {
                if (type.startsWith("image/")) {
                    Log.d("SmartCatch", "Đã nhận 1 bức ảnh!")
                    // TODO: Xử lý hiển thị URI ảnh lên Bottom Sheet
                } else if ("text/plain" == type) {
                    Log.d("SmartCatch", "Đã nhận 1 đoạn text/link!")
                    // TODO: Xử lý bóc tách link
                }
            }

            // Trường hợp 2: Nhận nhiều ảnh cùng lúc
            Intent.ACTION_SEND_MULTIPLE == action && type != null -> {
                if (type.startsWith("image/")) {
                    Log.d("SmartCatch", "Đã nhận nhiều bức ảnh!")
                    // TODO: Xử lý danh sách các URI ảnh
                }
            }
        }
    }
}