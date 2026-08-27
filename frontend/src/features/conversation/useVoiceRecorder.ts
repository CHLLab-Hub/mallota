import { useRef, useState } from 'react'

// opus 코덱을 지원하는 첫 mimeType을 고른다 (없으면 브라우저 기본값에 맡김).
function pickSupportedMimeType(): string | undefined {
  const candidates = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/webm']
  return candidates.find((type) => typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(type))
}

export function useVoiceRecorder() {
  const [recording, setRecording] = useState(false)
  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])

  async function startRecording() {
    // STT 인식률에 직접 영향을 주는 부분: 에코/잡음 제거와 자동 게인을 켜고, 통화 품질 하한선인
    // 16kHz보다 넉넉한 샘플레이트를 요청해서 STT가 받는 원본 오디오 자체의 품질을 높인다.
    // sampleRate는 반드시 ideal로 감싸야 한다 — 맨값으로 주면 정확히 그 값을 못 내는 마이크에서
    // getUserMedia 자체가 OverconstrainedError로 실패해 버린다.
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
        channelCount: 1,
        sampleRate: { ideal: 48000 },
      },
    })

    const mimeType = pickSupportedMimeType()
    // 브라우저 기본 비트레이트(보통 opus 24~32kbps 수준)는 음성치고도 낮은 편이라, 목소리에
    // 충분한 64kbps로 올려서 STT가 받는 오디오 압축 손실을 줄인다.
    const mediaRecorder = new MediaRecorder(stream, {
      ...(mimeType ? { mimeType } : {}),
      audioBitsPerSecond: 64000,
    })
    chunksRef.current = []

    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunksRef.current.push(e.data)
    }

    mediaRecorder.start()
    mediaRecorderRef.current = mediaRecorder
    setRecording(true)
  }

  function stopRecording(): Promise<Blob> {
    return new Promise((resolve) => {
      const mediaRecorder = mediaRecorderRef.current
      if (!mediaRecorder) {
        resolve(new Blob())
        return
      }

      mediaRecorder.onstop = () => {
        // 실제로 녹음된 mimeType을 그대로 써야 백엔드가 STT에 정확한 Content-Type을 전달한다.
        const blob = new Blob(chunksRef.current, { type: mediaRecorder.mimeType || 'audio/webm' })
        mediaRecorder.stream.getTracks().forEach((track) => track.stop())
        setRecording(false)
        resolve(blob)
      }

      mediaRecorder.stop()
    })
  }

  return { recording, startRecording, stopRecording }
}
