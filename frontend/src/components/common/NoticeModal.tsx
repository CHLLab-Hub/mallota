import { useEffect, useRef, type ReactNode } from 'react'
import './NoticeModal.css'

interface NoticeModalProps {
  icon: string
  title: string
  confirmLabel?: string
  onConfirm: () => void
  /** 팝업 안에 덧붙일 세부 정보 (승차권 요약 등) */
  children?: ReactNode
}

/**
 * 결제 완료처럼 꼭 확인하고 넘어가야 하는 안내를 화면 안에 띄운다.
 * 브라우저 alert는 메인 스레드를 멈춰 TTS 응답 처리까지 지연시키므로 음성 안내가 있는 화면에서는 이 팝업을 쓴다.
 */
export function NoticeModal({ icon, title, confirmLabel = '확인', onConfirm, children }: NoticeModalProps) {
  const confirmRef = useRef<HTMLButtonElement>(null)

  // 팝업이 뜨면 곧바로 확인 버튼에 포커스를 둔다 (키보드·스크린리더 사용자가 바로 닫을 수 있도록)
  useEffect(() => {
    confirmRef.current?.focus()
  }, [])

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onConfirm()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onConfirm])

  return (
    <div className="notice-overlay">
      <div className="notice-card" role="alertdialog" aria-modal="true" aria-labelledby="notice-title">
        <div className="notice-icon" aria-hidden="true">{icon}</div>
        <h2 className="notice-title" id="notice-title">{title}</h2>
        {children && <div className="notice-detail">{children}</div>}
        <button type="button" ref={confirmRef} className="notice-confirm" onClick={onConfirm}>
          {confirmLabel}
        </button>
      </div>
    </div>
  )
}
