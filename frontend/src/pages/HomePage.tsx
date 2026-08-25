import logo from '../assets/logo.png'
import { ConversationPanel } from '../features/conversation/ConversationPanel'
import './HomePage.css'
import { BottomTab } from './BottomTab'

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
      <BottomTab />
    </div>
  )
}