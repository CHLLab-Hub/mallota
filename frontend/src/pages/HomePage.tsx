import logo from '../assets/logo.png'
import { ConversationPanel } from '../features/conversation/ConversationPanel'
import './HomePage.css'

export function HomePage() {
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

      {/* 본문: 대화창 */}
      <div className="home-body">
        <ConversationPanel />
      </div>

      {/* 하단 탭 */}
      <nav className="bottom-tab">
        <button type="button" className="tab-item">
          <span className="tab-icon">📋</span>
          예매내역
        </button>
        <button type="button" className="tab-item active">
          <span className="tab-icon">🏠</span>
          홈
        </button>
        <button type="button" className="tab-item">
          <span className="tab-icon">👤</span>
          마이페이지
        </button>
      </nav>
    </div>
  )
}