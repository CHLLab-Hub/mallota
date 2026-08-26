import { request } from './httpClient'

// STT: 녹음한 오디오(Blob) → 텍스트
export function speechToText(audio: Blob, signal?: AbortSignal) {
  const formData = new FormData()
  formData.append('audio', audio, 'recording.webm')

  return request<{ transcript: string }>('/api/voice/stt', {
    method: 'POST',
    body: formData, // multipart — json 대신 body에 FormData
    signal,
  })
}

// TTS: 텍스트 → 음성(base64 mp3)
export function textToSpeech(text: string, signal?: AbortSignal) {
  return request<{ audio: string }>('/api/voice/tts', {
    method: 'POST',
    json: { text },
    signal,
  })
}