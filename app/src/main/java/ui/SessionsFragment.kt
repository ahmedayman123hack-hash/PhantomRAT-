package com.phantom.rat.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.phantom.rat.R
import com.phantom.rat.core.C2Client
import com.phantom.rat.core.SessionManager
import org.json.JSONArray
import org.json.JSONObject

class SessionsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SessionAdapter
    private lateinit var sessionManager: SessionManager
    private lateinit var c2Client: C2Client
    private lateinit var emptyView: View
    private lateinit var refreshBtn: MaterialButton
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    private val sessionsList = mutableListOf<SessionItem>()

    data class SessionItem(
        val id: String,
        val alias: String,
        val model: String,
        val ip: String,
        val lastSeen: Long,
        val isOnline: Boolean,
        val battery: Int,
        val isAdmin: Boolean,
        val commandsSent: Int,
        val commandsReceived: Int,
        val osVersion: String,
        val country: String
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_sessions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sessionManager = SessionManager(requireContext())
        c2Client = C2Client(requireContext())
        
        recyclerView = view.findViewById(R.id.recyclerSessions)
        emptyView = view.findViewById(R.id.emptyView)
        refreshBtn = view.findViewById(R.id.btnRefresh)
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = SessionAdapter(sessionsList) { session ->
            showSessionDialog(session)
        }
        recyclerView.adapter = adapter
        
        refreshBtn.setOnClickListener { refreshSessions() }
        
        // Auto-refresh every 5 seconds
        refreshRunnable = Runnable {
            refreshSessions()
            handler.postDelayed(refreshRunnable!!, 5000)
        }
        handler.post(refreshRunnable!!)
        
        refreshSessions()
    }

    private fun refreshSessions() {
        // Load from session manager
        val savedSessions = sessionManager.getConnectedSessions()
        sessionsList.clear()
        
        for (i in 0 until savedSessions.length()) {
            try {
                val session = savedSessions.getJSONObject(i)
                val data = JSONObject(session.optString("data", "{}"))
                
                sessionsList.add(SessionItem(
                    id = session.optString("id", ""),
                    alias = "Device ${i + 1}",
                    model = data.optString("model", android.os.Build.MODEL),
                    ip = data.optString("ip", "192.168.1.1"),
                    lastSeen = session.optLong("connected_at", System.currentTimeMillis()),
                    isOnline = true,
                    battery = data.optInt("battery", 100),
                    isAdmin = data.optBoolean("is_admin", false),
                    commandsSent = data.optInt("commands_sent", 0),
                    commandsReceived = data.optInt("commands_received", 0),
                    osVersion = data.optString("version", android.os.Build.VERSION.RELEASE),
                    country = data.optString("country", "EG")
                ))
            } catch (_: Exception) {}
        }
        
        // If no saved sessions, add current device as a session
        if (sessionsList.isEmpty()) {
            sessionsList.add(SessionItem(
                id = sessionManager.getDeviceId(),
                alias = "الجهاز الحالي",
                model = android.os.Build.MODEL,
                ip = "localhost",
                lastSeen = System.currentTimeMillis(),
                isOnline = true,
                battery = 85,
                isAdmin = sessionManager.isAdmin(),
                commandsSent = 0,
                commandsReceived = 0,
                osVersion = android.os.Build.VERSION.RELEASE,
                country = "EG"
            ))
        }
        
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (sessionsList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSessionDialog(session: SessionItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_session_details, null)
        
        dialogView.findViewById<TextView>(R.id.tvDeviceId).text = "ID: ${session.id}"
        dialogView.findViewById<TextView>(R.id.tvModel).text = "الموديل: ${session.model}"
        dialogView.findViewById<TextView>(R.id.tvOsVersion).text = "الإصدار: ${session.osVersion}"
        dialogView.findViewById<TextView>(R.id.tvStatus).text = 
            if (session.isOnline) "🟢 متصل" else "🔴 غير متصل"
        dialogView.findViewById<TextView>(R.id.tvBattery).text = "🔋 ${session.battery}%"
        dialogView.findViewById<TextView>(R.id.tvLastSeen).text = 
            "آخر ظهور: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date(session.lastSeen))}"
        dialogView.findViewById<TextView>(R.id.tvCommands).text = 
            "أوامر مرسلة: ${session.commandsSent} | مستقبلة: ${session.commandsReceived}"
        dialogView.findViewById<TextView>(R.id.tvAdmin).text = 
            if (session.isAdmin) "👑 مسؤول الجهاز" else "❌ ليس مسؤولاً"
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📱 ${session.alias}")
            .setView(dialogView)
            .setPositiveButton("🎯 تحكم") { _, _ ->
                // Navigate to controls with this session
                Snackbar.make(requireView(), "تم تحديد الجلسة: ${session.id.take(16)}...", 
                    Snackbar.LENGTH_SHORT).show()
            }
            .setNeutralButton("🗑️ حذف") { _, _ ->
                removeSession(session.id)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun removeSession(sessionId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف الجلسة")
            .setMessage("هل أنت متأكد من حذف هذه الجلسة؟")
            .setPositiveButton("حذف") { _, _ ->
                sessionsList.removeAll { it.id == sessionId }
                adapter.notifyDataSetChanged()
                Snackbar.make(requireView(), "✅ تم حذف الجلسة", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshRunnable?.let { handler.removeCallbacks(it) }
    }

    // RecyclerView Adapter
    inner class SessionAdapter(
        private val sessions: List<SessionItem>,
        private val onClick: (SessionItem) -> Unit
    ) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView = itemView.findViewById(R.id.cardSession)
            val tvAlias: TextView = itemView.findViewById(R.id.tvAlias)
            val tvId: TextView = itemView.findViewById(R.id.tvDeviceId)
            val tvModel: TextView = itemView.findViewById(R.id.tvModel)
            val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
            val tvBattery: TextView = itemView.findViewById(R.id.tvBattery)
            val tvLastSeen: TextView = itemView.findViewById(R.id.tvLastSeen)
            val tvCommands: TextView = itemView.findViewById(R.id.tvCommands)
            val btnControl: Button = itemView.findViewById(R.id.btnControl)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = sessions[position]
            
            holder.tvAlias.text = session.alias
            holder.tvId.text = session.id.take(16) + "..."
            holder.tvModel.text = session.model
            holder.tvStatus.text = if (session.isOnline) "🟢 متصل" else "🔴 غير متصل"
            holder.tvStatus.setTextColor(if (session.isOnline) 
                0xFF00FF88.toInt() else 0xFFFF4444.toInt())
            holder.tvBattery.text = "🔋 ${session.battery}%"
            holder.tvLastSeen.text = java.text.SimpleDateFormat("HH:mm:ss", 
                java.util.Locale.US).format(java.util.Date(session.lastSeen))
            holder.tvCommands.text = "⚡ ${session.commandsSent}"
            
            holder.card.setOnClickListener { onClick(session) }
            holder.btnControl.setOnClickListener { onClick(session) }
        }

        override fun getItemCount() = sessions.size
    }
}
