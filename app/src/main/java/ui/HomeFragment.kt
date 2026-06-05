package com.phantom.rat.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.cardview.widget.CardView
import com.phantom.rat.R
import com.phantom.rat.core.SessionManager
import com.phantom.rat.core.C2Client
import kotlinx.coroutines.*

class HomeFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var c2Client: C2Client
    private var statsJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sessionManager = SessionManager(requireContext())
        c2Client = C2Client(requireContext())
        
        setupStats(view)
        startLiveStats(view)
    }

    private fun setupStats(view: View) {
        view.findViewById<TextView>(R.id.tvDeviceName).text = android.os.Build.MODEL
        view.findViewById<TextView>(R.id.tvAndroidVersion).text = "Android ${android.os.Build.VERSION.RELEASE}"
        view.findViewById<TextView>(R.id.tvDeviceId).text = "ID: ${sessionManager.getDeviceId()}"
        view.findViewById<TextView>(R.id.tvStatus).text = "🟢 متصل"
        
        view.findViewById<CardView>(R.id.cardPayloads).setOnClickListener {
            // Navigate to builder
        }
        
        view.findViewById<CardView>(R.id.cardSessions).setOnClickListener {
            // Navigate to sessions
        }
        
        view.findViewById<CardView>(R.id.cardControls).setOnClickListener {
            // Navigate to controls
        }
    }

    private fun startLiveStats(view: View) {
        statsJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val stats = sessionManager.getSessionStats()
                withContext(Dispatchers.Main) {
                    view.findViewById<TextView>(R.id.tvActiveSessions).text = 
                        "الجلسات النشطة: ${stats["active"]}"
                    view.findViewById<TextView>(R.id.tvTotalPayloads).text = 
                        "إجمالي البايلودات: ${stats["payloads"]}"
                }
                delay(2000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        statsJob?.cancel()
    }
}
