import { useAppState } from '../features/conversation/AppState'
import type { Screen } from '../features/conversation/AppState'

export function BottomTab() {
  const { screen, setScreen } = useAppState()

  const tabs: { key: Screen; icon: string; label: string }[] = [
    { key: 'history', icon: '📋', label: '예매내역' },
    { key: 'home', icon: '🏠', label: '홈' },
    { key: 'mypage', icon: '👤', label: '마이페이지' },
  ]

  return (
    <nav className="bottom-tab">
      {tabs.map((t) => (
        <button
          key={t.key}
          type="button"
          className={`tab-item ${screen === t.key ? 'active' : ''}`}
          onClick={() => setScreen(t.key)}
        >
          <span className="tab-icon">{t.icon}</span>
          {t.label}
        </button>
      ))}
    </nav>
  )
}