package com.example.emvatc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var logTextView: TextView
    private lateinit var clearButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // シンプルな画面構成をコードで作成
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        clearButton = Button(this).apply {
            text = "ログをクリア"
            setOnClickListener { logTextView.text = "カードをかざしてください...\n" }
        }
        
        logTextView = TextView(this).apply {
            text = "カードをかざしてください...\n"
            textSize = 14f
        }

        val scrollView = android.widget.ScrollView(this).apply {
            addView(logTextView)
        }

        layout.addView(clearButton)
        layout.addView(scrollView)
        setContentView(layout)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    }

    override fun onResume() {
        super.onResume()
        val options = Bundle()
        // NFC読み取り時の遅延を減らす設定
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            options
        )
    }

    override fun onPause() {
        super.pause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        val isoDep = IsoDep.get(tag) ?: return
        
        runOnUiThread { appendLog("\n--- カードを検出しました ---") }

        try {
            isoDep.connect()
            isoDep.timeout = 5000

            // 1. PPSE SELECT (2PAY.SYS.DDF01)
            val ppseCmd = byteArrayOf(
                0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
                0x0E.toByte(),
                '2'.code.toByte(), 'P'.code.toByte(), 'A'.code.toByte(), 'Y'.code.toByte(),
                '.'.code.toByte(), 'S'.code.toByte(), 'Y'.code.toByte(), 'S'.code.toByte(),
                '.'.code.toByte(), 'D'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte(),
                '0'.code.toByte(), '1'.code.toByte(), 0x00.toByte()
            )
            val ppseRes = isoDep.transceive(ppseCmd)
            runOnUiThread { appendLog("PPSE Select: ${ppseRes.toHex()}") }

            // 2. ATCを取得 (GET DATA: 80 CA 9F 36 00)
            val getAtcCmd = byteArrayOf(0x80.toByte(), 0xCA.toByte(), 0x9F.toByte(), 0x36.toByte(), 0x00.toByte())
            val atcResBefore = isoDep.transceive(getAtcCmd)
            runOnUiThread { appendLog("ATC (Before): ${atcResBefore.toHex()}") }

            // 3. (オプション) GPO等でATC増加のアクションを試行
            // ※ここに特定のAIDのSelectやGPOコマンドを追加可能です

        } catch (e: Exception) {
            runOnUiThread { appendLog("エラー: ${e.message}") }
        } finally {
            try { isoDep.close() } catch (e: IOException) {}
        }
    }

    private fun appendLog(msg: String) {
        logTextView.append("$msg\n")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
