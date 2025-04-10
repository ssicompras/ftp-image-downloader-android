
package com.example.ftpdownloader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var ftpUrlEditText: EditText
    private lateinit var selectFolderButton: Button
    private lateinit var downloadButton: Button
    private var pickedFolderUri: Uri? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pickedFolderUri = result.data?.data
            pickedFolderUri?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ftpUrlEditText = findViewById(R.id.ftpUrlEditText)
        selectFolderButton = findViewById(R.id.selectFolderButton)
        downloadButton = findViewById(R.id.downloadButton)

        selectFolderButton.setOnClickListener { openFolderPicker() }
        downloadButton.setOnClickListener { startFtpDownload() }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        folderPicker.launch(intent)
    }

    private fun startFtpDownload() {
        val ftpUrl = ftpUrlEditText.text.toString()
        val folderUri = pickedFolderUri ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val ftpClient = FTPClient()
            try {
                val server = ftpUrl.substringAfter("ftp://").substringBefore("/")
                ftpClient.connect(server)
                ftpClient.login("anonymous", "")
                ftpClient.enterLocalPassiveMode()

                val files = ftpClient.listFiles()
                for (file in files) {
                    if (!file.isFile || !file.name.endsWith(".jpg", true)) continue

                    val inputStream: InputStream = ftpClient.retrieveFileStream(file.name)
                    val fileUri = DocumentsContract.createDocument(
                        contentResolver, folderUri, "image/jpeg", file.name
                    ) ?: continue

                    val outputStream: OutputStream = contentResolver.openOutputStream(fileUri) ?: continue
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    ftpClient.completePendingCommand()
                }
                ftpClient.logout()
                ftpClient.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
