import { useState } from 'react'
import { speechToText, textToSpeech } from '../../api/voiceApi'
import { useVoiceRecorder } from './useVoiceRecorder'
import { useAppState } from './AppState'
import logo from '../../assets/logo.png'
import './ConversationPanel.css'

interface VoicePanelProps {
  onUserSpeak: (text: string) => void | Promise<void>
  loading?: boolean
}

export function VoicePanel({ onUserSpeak, loading }: VoicePanelProps) {
  const { messages } = useAppState()
  const [text, setText] = useState('')
  const [showInput, setShowInput] = useState(false)
  const [transcribing, setTranscribing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { recording, startRecording, stopRecording } = useVoiceRecorder()

  async function handleMicClick() {
    if (recording) {
      setTranscribing(true)
      setError(null)
      try {
        const audio = await stopRecording()
        const data = await speechToText(audio)
        if (data.transcript) await onUserSpeak(data.transcript)
      } catch (e) {
        setError('음성 인식에 실패했습니다. 다시 시도해 주세요.')
      } finally {
        setTranscribing(false)
      }
    } else {
      setError(null)
      try {
        await startRecording()
      } catch (e) {
        setError('마이크를 사용할 수 없습니다. 권한을 확인해 주세요.')
      }
    }
  }

  async function handleSendInput() {
    if (!text.trim()) return
    const t = text
    setText('')
    await onUserSpeak(t)
  }

  return (
    <div>
      <div className="chat-container">
        {messages.slice(-4).map((m, i) => (
          <div key={i} className={`chat-row ${m.role}`}>
            {m.role === 'app' && <img src={logo} alt="" className="chat-avatar" />}
            <div className={`chat-bubble ${m.role}`}>{m.text}</div>
          </div>
        ))}
      </div>

      {error && <p style={{ color: 'red' }}>{error}</p>}

      <div className="mic-area">
        <button
          type="button"
          className={`mic-button ${recording ? 'recording' : ''}`}
          onClick={handleMicClick}
          disabled={transcribing || loading}
        >
          {recording ? '⏹' : '🎤'}
        </button>
        <div className="mic-label">
          {recording ? '녹음 중... (누르면 완료)' : transcribing ? '인식 중...' : loading ? '처리 중...' : '눌러서 말하기'}
        </div>
        <button type="button" className="mic-sublabel" onClick={() => setShowInput((v) => !v)}>
          직접 글씨로 입력하기
        </button>

        {showInput && (
          <div style={{ width: '100%' }}>
            <textarea
              className="chat-input"
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="여기에 입력하세요"
            />
            <button
              type="button"
              className="send-button"
              onClick={handleSendInput}
              disabled={loading || !text.trim()}
            >
              보내기
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

// 앱이 말하기 (TTS) — 어디서든 쓸 수 있게 export
export async function speak(t: string) {
  if (!t.trim()) return
  try {
    const data = await textToSpeech(t)
    if (data.audio) {
      const audio = new Audio('data:audio/mp3;base64,' + data.audio)
      await audio.play()
    }
  } catch (e) {
    // 무시
  }
}