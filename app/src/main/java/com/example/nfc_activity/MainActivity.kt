package com.example.nfc_activity

//import android.nfc.tech.MifareClassic
//import android.nfc.tech.MifareUltralight
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.nfc_activity.record.ParsedNdefRecord
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private var tagList: LinearLayout? = null
    private var nfcAdapter: NfcAdapter? = null
    private var ndefMessage: NdefMessage? = null
    private var writeSHUMessage: Boolean = false
    //private val checkBox = findViewById<CheckBox>(R.id.checkBox)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val checkBox = findViewById<CheckBox>(R.id.checkBox)

        checkBox.setOnClickListener {
            writeSHUMessage = checkBox.isChecked

            if (writeSHUMessage){
                Thread.sleep(3_000)

                val toast = Toast.makeText(this, "Attendance Logged!", Toast.LENGTH_SHORT)
                toast.show()
            }
        }

        tagList = findViewById<View>(R.id.list) as LinearLayout
        resolveIntent(intent)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            showNoNfcDialog()
            return
        }

//        val payload = "Hello, NDEF!".toByteArray()
//        val ndefRecord = NdefRecord.createMime("text/plain", payload)
//        ndefMessage = NdefMessage(ndefRecord)

        //run()
    }

    private val client = OkHttpClient()

    private fun run() {
        val request = Request.Builder()
            .url("https://<azure-endpoint-for-db>/students")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    for ((name, value) in response.headers) {
                        println("$name: $value")
                    }

                    println(response.body!!.string())
                }
            }
        })
    }

    private fun writeNdefMessage(intent: Intent) {
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            // Tag is already NDEF formatted, write the message
            ndef.connect()
            ndef.writeNdefMessage(ndefMessage)
            ndef.close()

            Toast.makeText(this, "NDEF message written successfully", Toast.LENGTH_SHORT).show()
        } else {
            // Tag is not NDEF formatted, format it first
            val ndefFormatable = NdefFormatable.get(tag)
            if (ndefFormatable != null) {
                ndefFormatable.connect()
                ndefFormatable.format(ndefMessage)
                ndefFormatable.close()

                Toast.makeText(this, "NDEF format and message written successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Tag does not support NDEF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == false) {
            openNfcSettings()
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent_Mutable
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }


    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveIntent(intent)
    }

    private fun showNoNfcDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.no_nfc)
            .setNeutralButton(R.string.close_app) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun openNfcSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_NFC)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }
        startActivity(intent)
    }

    private fun resolveIntent(intent: Intent) {

        val validActions = listOf(
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED
        )

        if ((writeSHUMessage) && (intent.action in validActions)) {
            //writeNdefMessage(intent)
        }
        else if (intent.action in validActions) {
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            val messages = mutableListOf<NdefMessage>()
            if (rawMsgs != null) {
                rawMsgs.forEach {
                    messages.add(it as NdefMessage)
                }
            }
            else {
                // Unknown tag type
                val empty = ByteArray(0)
                val id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)
                val tag = intent.parcelable<Tag>(NfcAdapter.EXTRA_TAG) ?: return
                val payload = dumpTagData(tag).toByteArray()
                val record = NdefRecord(NdefRecord.TNF_UNKNOWN, empty, id, payload)
                val msg = NdefMessage(arrayOf(record))
                messages.add(msg)
            }
            // Setup the views
            buildTagViews(messages)

            val toast = Toast.makeText(this, "Sent to the database!", Toast.LENGTH_SHORT)
            toast.show()
        }
    }

    private fun dumpTagData(tag: Tag): String {
        val sb = StringBuilder()
        val id = tag.id
//        sb.append("ID (hex): ").append(toHex(id)).append('\n')
        sb.append("ID (hex): ").append(toReversedHex(id)).append('\n')
//        sb.append("ID (dec): ").append(toDec(id)).append('\n')
//        sb.append("ID (reversed dec): ").append(toReversedDec(id)).append('\n')
//        val prefix = "android.nfc.tech."
//        sb.append("Technologies: ")
//        for (tech in tag.techList) {
//            sb.append(tech.substring(prefix.length))
//            sb.append(", ")
//        }
//        sb.delete(sb.length - 2, sb.length)
//        for (tech in tag.techList) {
//            if (tech == MifareClassic::class.java.name) {
//                sb.append('\n')
//                var type = "Unknown"
//                try {
//                    val mifareTag = MifareClassic.get(tag)
//
//                    when (mifareTag.type) {
//                        MifareClassic.TYPE_CLASSIC -> type = "Classic"
//                        MifareClassic.TYPE_PLUS -> type = "Plus"
//                        MifareClassic.TYPE_PRO -> type = "Pro"
//                    }
//                    sb.appendLine("Mifare Classic type: $type")
//                    sb.appendLine("Mifare size: ${mifareTag.size} bytes")
//                    sb.appendLine("Mifare sectors: ${mifareTag.sectorCount}")
//                    sb.appendLine("Mifare blocks: ${mifareTag.blockCount}")
//                } catch (e: Exception) {
//                    sb.appendLine("Mifare classic error: ${e.message}")
//                }
//            }
//            if (tech == MifareUltralight::class.java.name) {
//                sb.append('\n')
//                val mifareUlTag = MifareUltralight.get(tag)
//                var type = "Unknown"
//                when (mifareUlTag.type) {
//                    MifareUltralight.TYPE_ULTRALIGHT -> type = "Ultralight"
//                    MifareUltralight.TYPE_ULTRALIGHT_C -> type = "Ultralight C"
//                }
//                sb.append("Mifare Ultralight type: ")
//                sb.append(type)
//            }
//        }
        //sb.append('\n').append("Sending to the database").append('\n')
        sb.append('\n').append("Student found:")
        sb.append('\n').append("ID: 30011094")
        sb.append('\n').append("Forname: Drew")
        sb.append('\n').append("Surname: Robson")
        sb.append('\n').append("UID: E6658E7F")

        return sb.toString()
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (i in bytes.indices.reversed()) {
            val b = bytes[i].toInt() and 0xff
            if (b < 0x10) sb.append('0')
            sb.append(Integer.toHexString(b))
            if (i > 0) {
                sb.append(" ")
            }
        }
        return sb.toString()
    }

    private fun toReversedHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (i in bytes.indices) {
            if (i > 0) {
                sb.append(" ")
            }
            val b = bytes[i].toInt() and 0xff
            if (b < 0x10) sb.append('0')
            sb.append(Integer.toHexString(b))
        }
        return sb.toString()
    }

    private fun toDec(bytes: ByteArray): Long {
        var result: Long = 0
        var factor: Long = 1
        for (i in bytes.indices) {
            val value = bytes[i].toLong() and 0xffL
            result += value * factor
            factor *= 256L
        }
        return result
    }

    private fun toReversedDec(bytes: ByteArray): Long {
        var result: Long = 0
        var factor: Long = 1
        for (i in bytes.indices.reversed()) {
            val value = bytes[i].toLong() and 0xffL
            result += value * factor
            factor *= 256L
        }
        return result
    }

    private fun buildTagViews(msgs: List<NdefMessage>) {
        if (msgs.isEmpty()) {
            return
        }
        val inflater = LayoutInflater.from(this)
        val content = tagList

        // Parse the first message in the list
        // Build views for all of the sub records
        val now = Date()
        val records = NdefMessageParser.parse(msgs[0])
        val size = records.size
        for (i in 0 until size) {
            val timeView = TextView(this)
            timeView.text = TIME_FORMAT.format(now)
            content!!.addView(timeView, 0)
            val record: ParsedNdefRecord = records[i]
            content.addView(record.getView(this, inflater, content, i), 1 + i)
            content.addView(inflater.inflate(R.layout.tag_divider, content, false), 2 + i)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_main_clear -> {
                clearTags()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun clearTags() {
        for (i in tagList!!.childCount - 1 downTo 0) {
            val view = tagList!!.getChildAt(i)
            if (view.id != R.id.tag_viewer_text) {
                tagList!!.removeViewAt(i)
            }
        }
    }

    companion object {
        private val TIME_FORMAT = SimpleDateFormat.getDateTimeInstance()
    }
}