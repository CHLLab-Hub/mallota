import { useEffect, useRef, useState } from 'react'
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
  const bottomRef = useRef<HTMLDivElement>(null)

  const { recording, startRecording, stopRecording } = useVoiceRecorder()

  // 새 메시지가 오면 대화창을 최신 메시지로 자동 스크롤한다 — 예전 메시지는 위로 스크롤해서 볼 수 있도록
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages.length])

  async function handleMicClick() {
    if (recording) {
      setTranscribing(true)
      setError(null)
      try {
        const audio = await stopRecording()
        const data = await speechToText(audio)
        if (data.transcript) {
          await onUserSpeak(data.transcript)
        } else {
          setError('무슨 말씀인지 못 들었어요. 다시 한 번 말씀해 주세요.')
        }
      } catch (e) {
        setError('음성 인식에 실패했습니다. 다시 시도해 주세요.')
      } finally {
        setTranscribing(false)
      }
    } else {
      setError(null)
      stopSpeaking() // 겹쳐 들리는 문제 방지
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
    setError(null)
    await onUserSpeak(t)
  }

  return (
    <div>
      <div className="chat-container">
        <div className="chat-messages">
          {messages.map((m, i) => (
            <div key={i} className={`chat-row ${m.role}`}>
              {m.role === 'app' && <img src={logo} alt="" className="chat-avatar" />}
              <div className={`chat-bubble ${m.role}`}>{m.text}</div>
            </div>
          ))}
          <div ref={bottomRef} />
        </div>

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
    </div>
  )
}

// 모듈 스코프 상태: "지금 재생 중인 오디오"와 "가장 최근 요청"을 추적해 TTS끼리 겹치지 않도록
let currentAudio: HTMLAudioElement | null = null
let latestRequestId = 0

// 앱이 말하기 (TTS) — 어디서든 쓸 수 있게 export
export async function speak(t: string) {
  if (!t.trim()) return
  const requestId = ++latestRequestId
  try {
    const data = await textToSpeech(t)
    // textToSpeech가 응답을 기다리는 사이에 더 최신 speak() 호출이 들어왔다면, 이 오래된 응답은 재생하지 않고 버림
    if (requestId !== latestRequestId) return
    if (data.audio) {
      // 아직 재생 중인 이전 TTS가 있으면 먼저 멈추어 다음 텍스트가 나올 때 이전 안내가 겹치지 않게 함
      if (currentAudio) {
        currentAudio.pause()
        currentAudio.currentTime = 0
      }
      const audio = new Audio('data:audio/mp3;base64,' + data.audio)
      currentAudio = audio
      await audio.play()
    }
  } catch (e) {
    // 무시
  }
}

// 사용자가 마이크를 눌러 말하기 시작할 때, 아직 재생 중인 안내 음성이 있으면 멈춤
export function stopSpeaking() {
  latestRequestId++ // 응답 대기 중이던 이전 speak() 호출이 뒤늦게 재생을 시작하지 못하게 막음
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.currentTime = 0
    currentAudio = null
  }
}