import { useState } from 'react'
import { speechToText, textToSpeech } from '../../api/voiceApi'
import { useVoiceRecorder } from './useVoiceRecorder'
import { useAppState } from './AppState'
import logo from '../../assets/logo.png'
import './ConversationPanel.css'

// 화면이 바뀌거나 다음 안내가 시작되면 이전 음성과 네트워크 요청까지 멈춘다.
// 모듈 범위로 두어 Home/Bus/Seat/Confirm 페이지가 서로 달라도 하나의 음성만 재생된다.
let currentAudio: HTMLAudioElement | null = null
let currentTtsController: AbortController | null = null

export function stopSpeaking() {
  currentTtsController?.abort()
  currentTtsController = null
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.currentTime = 0
    currentAudio = null
  }
}

interface VoicePanelProps {
  onUserSpeak: (text: string) => void | Promise<void>
  loading?: boolean
  /** 좌석 선택처럼 화면 공간이 중요한 단계에서는 마이크만 표시한다. */
  compact?: boolean
}

export function VoicePanel({ onUserSpeak, loading, compact = false }: VoicePanelProps) {
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
        // 사용자가 답하기 시작할 때 이전 안내가 마이크 입력에 섞이지 않게 한다.
        stopSpeaking()
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
    stopSpeaking()
    await onUserSpeak(t)
  }

  return (
    <div>
      <div className="chat-container">
        {!compact && messages.slice(-2).map((m, i) => (
          <div key={i} className={`chat-row ${m.role}`}>
            {m.role === 'app' && <img src={logo} alt="" className="chat-avatar" />}
            <div className={`chat-bubble ${m.role}`}>{m.text}</div>
          </div>
        ))}

        {error && <p style={{ color: 'red' }}>{error}</p>}

        <div className="mic-area">
          <button
            type="button"
            className={`mic-button ${recording ? 'recording' : ''}`}
            onClick={handleMicClick}
            disabled={transcribing || loading}
          >
            {recording ? (
              <svg width="36" height="36" viewBox="0 0 24 24" fill="white">
                <rect x="6" y="6" width="12" height="12" rx="2" />
              </svg>
            ) : (
              <svg width="40" height="40" viewBox="0 0 24 24" fill="white">
                <path d="M12 14a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5a3 3 0 0 0 3 3z" />
                <path d="M17 11a1 1 0 0 1 2 0 7 7 0 0 1-6 6.92V20h2a1 1 0 0 1 0 2H9a1 1 0 0 1 0-2h2v-2.08A7 7 0 0 1 5 11a1 1 0 0 1 2 0 5 5 0 0 0 10 0z" />
              </svg>
            )}
          </button>
          <div className="mic-label">
            {recording ? '녹음 중... (누르면 완료)' : transcribing ? '인식 중...' : loading ? '처리 중...' : '눌러서 말하기'}
          </div>
          {!compact && (
            <button type="button" className="mic-sublabel" onClick={() => setShowInput((v) => !v)}>
              직접 글씨로 입력하기
            </button>
          )}

          {!compact && showInput && (
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
    </div>
  )
}

// 앱이 말하기 (TTS) — 어디서든 쓸 수 있게 export
export async function speak(t: string) {
  if (!t.trim()) return
  stopSpeaking()
  const controller = new AbortController()
  currentTtsController = controller
  try {
    const data = await textToSpeech(t, controller.signal)
    // 늦게 끝난 이전 요청은 이미 새 안내가 시작된 상태이므로 재생하지 않는다.
    if (data.audio && currentTtsController === controller) {
      const audio = new Audio('data:audio/mp3;base64,' + data.audio)
      currentAudio = audio
      audio.onended = () => {
        if (currentAudio === audio) currentAudio = null
        if (currentTtsController === controller) currentTtsController = null
      }
      await audio.play()
    }
  } catch (e) {
    // 중단(AbortError)과 TTS 실패는 화면에서 별도 안내하지 않는다.
  }
}
