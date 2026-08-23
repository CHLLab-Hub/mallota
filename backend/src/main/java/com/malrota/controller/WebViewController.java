package com.malrota.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebViewController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String index() {
        return """
        <!DOCTYPE html>
        <html lang="ko">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>말로타 음성 인식(STT) 테스트</title>
            <style>
                body { font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; background: #f1f5f9; }
                .card { background: white; padding: 2.5rem; border-radius: 16px; box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1); width: 440px; text-align: center; }
                h2 { margin-top: 0; color: #1e293b; }
                button { font-size: 1.1rem; padding: 14px 24px; margin: 8px; border: none; border-radius: 10px; cursor: pointer; font-weight: bold; }
                .btn-record { background: #ef4444; color: white; }
                .btn-stop { background: #2563eb; color: white; }
                button:disabled { background: #cbd5e1; cursor: not-allowed; }
                #status { margin: 15px 0; color: #64748b; font-weight: 500; }
                #result-box { margin-top: 20px; padding: 15px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; text-align: left; min-height: 70px; word-break: break-all; }
                pre { margin: 0; font-size: 0.95rem; color: #0f172a; white-space: pre-wrap; font-weight: bold; }
            </style>
        </head>
        <body>
            <div class="card">
                <h2>🎙️ 말로타 STT / 음성인식 테스트</h2>
                <div>
                    <button id="startBtn" class="btn-record">🔴 녹음 시작</button>
                    <button id="stopBtn" class="btn-stop" disabled>⏹️ 녹음 완료 및 전송</button>
                </div>
                <div id="status">버튼을 눌러 마이크로 말씀하세요.</div>
                
                <div id="result-box">
                    <div style="font-size:0.85rem; color:#64748b; margin-bottom:5px;">변환된 텍스트(transcript):</div>
                    <pre id="result">대기 중...</pre>
                </div>
            </div>
            <script>
                let mediaRecorder, audioChunks = [];
                const startBtn = document.getElementById('startBtn');
                const stopBtn = document.getElementById('stopBtn');
                const status = document.getElementById('status');
                const result = document.getElementById('result');

                startBtn.onclick = async () => {
                    try {
                        audioChunks = [];
                        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                        mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
                        mediaRecorder.ondataavailable = e => { if (e.data.size > 0) audioChunks.push(e.data); };
                        
                        mediaRecorder.onstop = async () => {
                            status.innerText = "⏳ IBM STT 서버로 전송 중...";
                            const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
                            
                            const formData = new FormData();
                            // VoiceController의 @RequestParam("audio")에 맞게 전달
                            formData.append('audio', audioBlob, 'record.webm');

                            try {
                                const response = await fetch('/api/voice/stt', {
                                    method: 'POST',
                                    body: formData
                                });
                                const data = await response.json();
                                result.innerText = data.transcript ? data.transcript : JSON.stringify(data, null, 2);
                                status.innerText = "✅ 음성 인식 완료!";
                            } catch (err) {
                                result.innerText = "에러: " + err.message;
                                status.innerText = "❌ 전송 실패";
                            }
                        };
                        
                        mediaRecorder.start();
                        startBtn.disabled = true;
                        stopBtn.disabled = false;
                        status.innerText = "🎙️ 녹음 중입니다... 말씀하세요!";
                    } catch (err) {
                        alert("마이크 사용 권한이 필요합니다: " + err.message);
                    }
                };

                stopBtn.onclick = () => {
                    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
                        mediaRecorder.stop();
                        startBtn.disabled = false;
                        stopBtn.disabled = true;
                    }
                };
            </script>
        </body>
        </html>
        """;
    }
}