package com.coze.kotlin_example.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object ToastUtil {
    private val uiHandler = Handler(Looper.getMainLooper())
    private var toast: Toast? = null
    private var dialog: AlertDialog? = null

    fun showAlert(context: Context, message: String) {
        uiHandler.post {
            if (dialog?.isShowing == true) {
                return@post
            }
            dialog = AlertDialog.Builder(context)
                .setTitle("错误")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    fun showLongToast(context: Context, msg: String) {
        uiHandler.post {
            toast?.cancel()
            toast = Toast.makeText(context, msg, Toast.LENGTH_LONG)
            toast?.show()
        }
    }

    fun showShortToast(context: Context, msg: String) {
        uiHandler.post {
            toast?.cancel()
            toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
            toast?.show()
        }
    }
} 