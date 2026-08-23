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
            <title>말로타 음성 대화 에이전트</title>
            <style>
                body { font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; margin: 0; background: #0f172a; color: #f8fafc; }
                .card { background: #1e293b; padding: 2.5rem; border-radius: 24px; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.5); width: 440px; text-align: center; border: 1px solid #334155; }
                h1 { font-size: 1.5rem; margin: 0 0 8px; color: #38bdf8; }
                p.sub { font-size: 0.9rem; color: #94a3b8; margin: 0 0 20px; }
                button { font-size: 1.1rem; padding: 14px 28px; border: none; border-radius: 12px; cursor: pointer; font-weight: bold; }
                .btn-record { background: #ef4444; color: white; margin-right: 8px; }
                .btn-stop { background: #3b82f6; color: white; }
                button:disabled { background: #475569; cursor: not-allowed; }
                #status { margin: 16px 0; color: #38bdf8; font-size: 0.95rem; font-weight: bold; min-height: 24px; }
                .chat-box { margin-top: 15px; text-align: left; background: #0f172a; border-radius: 14px; padding: 16px; border: 1px solid #334155; }
                .chat-title { font-size: 0.8rem; color: #64748b; margin-bottom: 4px; font-weight: bold; }
                .chat-text { font-size: 1rem; color: #f1f5f9; margin-bottom: 12px; font-weight: 500; }
                .ai-reply { color: #4ade80; font-weight: bold; }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>🚌 말로타 (Malrota)</h1>
                <p class="sub">음성 대화형 고속버스 예매 어시스턴트</p>
                
                <div>
                    <button id="startBtn" class="btn-record">🔴 마이크 켜기</button>
                    <button id="stopBtn" class="btn-stop" disabled>⏹️ 말 끝남</button>
                </div>
                
                <div id="status">마이크를 켜고 편하게 말씀하세요.</div>

                <div class="chat-box">
                    <div class="chat-title">🗣️ 내가 한 말 (STT)</div>
                    <div id="mySpeech" class="chat-text">대기 중...</div>

                    <div class="chat-title">🤖 말로타 음성 답변 (watsonx ➔ TTS)</div>
                    <div id="aiSpeech" class="chat-text ai-reply">대기 중...</div>
                </div>
            </div>

            <script>
                let mediaRecorder, chunks = [];
                const startBtn = document.getElementById('startBtn');
                const stopBtn = document.getElementById('stopBtn');
                const status = document.getElementById('status');
                const mySpeech = document.getElementById('mySpeech');
                const aiSpeech = document.getElementById('aiSpeech');

                startBtn.onclick = async () => {
                    chunks = [];
                    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                    mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
                    mediaRecorder.ondataavailable = e => { if (e.data.size > 0) chunks.push(e.data); };
                    
                    mediaRecorder.onstop = async () => {
                        status.innerText = "⏳ 1. 목소리를 인식하고 있습니다...";
                        const blob = new Blob(chunks, { type: 'audio/webm' });
                        const fd = new FormData();
                        fd.append('audio', blob, 'voice.webm');

                        try {
                            // [1단계] STT 호출
                            const sttRes = await fetch('/api/voice/stt', { method: 'POST', body: fd });
                            const sttData = await sttRes.json();
                            const transcript = sttData.transcript || "";
                            
                            if (!transcript.trim()) {
                                mySpeech.innerText = "(음성이 작거나 인식되지 않았습니다)";
                                speakText("음성을 잘 듣지 못했습니다. 다시 말씀해 주세요.");
                                return;
                            }
                            mySpeech.innerText = `"${transcript}"`;

                            // [2단계] watsonx 조건 파싱
                            status.innerText = "🧠 2. watsonx AI가 조건을 분석하고 있습니다...";
                            const parseRes = await fetch('/api/conversation/parse', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ text: transcript })
                            });
                            const parseData = await parseRes.json();

                            // [3단계] 답변 문장 생성
                            let replyText = "";
                            if (parseData.arrival && !parseData.departure) {
                                replyText = `목적지가 ${parseData.arrival}이 맞나요? 어디에서 출발하시나요?`;
                            } else if (parseData.departure && parseData.arrival) {
                                replyText = `${parseData.departure}에서 ${parseData.arrival}로 가는 ${parseData.date || '내일'} 버스를 조회할까요?`;
                            } else if (parseData.departure) {
                                replyText = `출발지가 ${parseData.departure}이 맞나요? 목적지는 어디인가요?`;
                            } else {
                                replyText = `"${transcript}"라고 말씀하셨군요. 목적지나 출발지를 말씀해 주세요.`;
                            }

                            aiSpeech.innerText = replyText;

                            // [4단계] TTS 음성 재생 (스피커 출력)
                            status.innerText = "🔊 3. 말로타가 음성으로 답변합니다...";
                            await speakText(replyText);
                            status.innerText = "✅ 대화 완료! 다음 말씀을 들려주세요.";

                        } catch (err) {
                            console.error(err);
                            status.innerText = "❌ 오류: " + err.message;
                        }
                    };

                    mediaRecorder.start();
                    startBtn.disabled = true;
                    stopBtn.disabled = false;
                    status.innerText = "🎙️ 듣고 있습니다... 말씀하세요!";
                };

                stopBtn.onclick = () => {
                    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
                        mediaRecorder.stop();
                        startBtn.disabled = false;
                        stopBtn.disabled = true;
                    }
                };

                // TTS 호출 및 스피커 재생 함수
                async function speakText(text) {
                    try {
                        const res = await fetch('/api/voice/tts', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ text: text })
                        });
                        const data = await res.json();
                        if (data.audio) {
                            const audio = new Audio("data:audio/mp3;base64," + data.audio);
                            await audio.play();
                        }
                    } catch (e) {
                        console.error("TTS 에러:", e);
                    }
                }
            </script>
        </body>
        </html>
        """;
    }
}