import logo from '../assets/logo.png'
import { ConversationPanel } from '../features/conversation/ConversationPanel'
import { useAppState } from '../features/conversation/AppState'
import './HomePage.css'
import { BottomTab } from './BottomTab'

export function HomePage() {
  const { recommendations, setScreen } = useAppState()

  return (
    <div className="phone-frame">
      {/* 상단: 로고 + 이용 안내 */}
      <header className="home-header">
        <div className="home-brand">
          <img src={logo} alt="말로타" />
        </div>
      </header>

      {/* 제목 */}
      <h1 className="home-title">
        편한 길,<br />
        <span className="accent">말로타</span>가 알아서 골라드립니다
      </h1>

      {/* 추천 버스 화면에서 뒤로 나온 뒤에도 처음부터 다시 대화하지 않고 바로 돌아갈 수 있게 한다 */}
      {recommendations.length > 0 && (
        <button
          type="button"
          className="send-button"
          onClick={() => setScreen('bus')}
          style={{ marginBottom: '12px' }}
        >
          추천 버스 목록으로 돌아가기
        </button>
      )}

      {/* 본문: 대화창 */}
      <div className="home-body">
        <ConversationPanel />
      </div>

      {/* 하단 탭 */}
      <BottomTab />
    </div>
  )
}