import { useState } from 'react'
import { parseConversation, searchBuses } from '../../api/conversationApi'
import { speechToText, textToSpeech } from '../../api/voiceApi'
import { ApiError } from '../../api/httpClient'
import { useVoiceRecorder } from './useVoiceRecorder'
import { useAppState } from './AppState'
import logo from '../../assets/logo.png'
import './ConversationPanel.css'
import type { ConversationSessionResult } from './types'

function buildQuestion(session: ConversationSessionResult): string {
  if (!session.departure) return '어디에서 출발하시나요?'
  if (!session.arrival) return '어디로 가시나요?'
  if (!session.date) return '언제 출발하시나요?'
  return ''
}

export function ConversationPanel() {
  const {
    sessionId, setSessionId,
    messages, addMessage,
    setBuses, setScreen,
  } = useAppState()

  const [text, setText] = useState('')
  const [showInput, setShowInput] = useState(false)
  const [loading, setLoading] = useState(false)
  const [transcribing, setTranscribing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { recording, startRecording, stopRecording } = useVoiceRecorder()

  async function speak(t: string) {
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

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  async function handleMicClick() {
    if (recording) {
      setTranscribing(true)
      setError(null)
      try {
        const audio = await stopRecording()
        const data = await speechToText(audio)
        if (data.transcript) await handleSend(data.transcript)
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

  async function handleSend(inputText?: string) {
    const sendText = (inputText ?? text).trim()
    if (!sendText) return

    addMessage('user', sendText)
    setText('')
    setLoading(true)
    setError(null)

    try {
      const session: ConversationSessionResult = await parseConversation(sendText, sessionId)
      setSessionId(session.sessionId)

      if (session.state === 'COLLECTING_CONDITIONS') {
        appSay(buildQuestion(session))
      } else {
        const buses = await searchBuses({
          departure: session.departure!,
          arrival: session.arrival!,
          date: session.date!,
        })

        if (buses.length === 0) {
          appSay('해당 조건의 버스를 찾지 못했습니다.')
        } else {
          appSay('조건에 맞는 버스를 찾았어요. 추천 버스를 보여드릴게요.')
          setBuses(buses)
          setTimeout(() => setScreen('bus'), 800) // 잠깐 뒤 버스 화면으로
        }
      }
    } catch (error) {
      if (error instanceof ApiError) {
        setError(error.errors[0]?.message ?? error.message)
      } else {
        setError('처리 중 문제가 발생했습니다. 서버 상태를 확인해 주세요.')
      }
    } finally {
      setLoading(false)
    }
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
              onClick={() => handleSend()}
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