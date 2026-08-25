import { useRef, useState } from 'react'

export function useVoiceRecorder() {
  const [recording, setRecording] = useState(false)
  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])

  async function startRecording() {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const mediaRecorder = new MediaRecorder(stream)
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
        const blob = new Blob(chunksRef.current, { type: 'audio/webm' })
        mediaRecorder.stream.getTracks().forEach((track) => track.stop())
        setRecording(false)
        resolve(blob)
      }

      mediaRecorder.stop()
    })
  }

  return { recording, startRecording, stopRecording }
}