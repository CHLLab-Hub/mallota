import { StatusCard } from '../components/common/StatusCard'
import { ConversationPanel } from '../features/conversation/ConversationPanel'
import logo from '../assets/logo.png'

const setupItems = [
  {
    title: 'React 프론트엔드',
    description: '접근성을 고려한 반응형 화면 골격과 API 연결 위치를 준비했습니다.',
  },
  {
    title: 'Spring Boot 백엔드',
    description: 'Java 17 기반 서버와 도메인·외부 연동 패키지 구조를 준비했습니다.',
  },
  {
    title: '외부 연동 대기',
    description: 'TAGO, watsonx, STT/TTS는 키 발급 이후 기능 브랜치에서 연결합니다.',
  },
]

export function HomePage() {
  return (
    <main className="app-shell">
      <header className="site-header">
        <div className="brand">
  <img src={logo} alt="말로타" style={{ height: '48px' }} />
</div>
        <span className="setup-badge">Project scaffold</span>
      </header>

      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">말로 타는 고속버스</p>
        <h1 id="page-title">복잡한 예매 대신, 편안하게 말씀하세요.</h1>
        <p className="hero-description">
          음성으로 운행편을 찾고 상황에 맞는 좌석을 추천받는 접근성 중심 고속버스 예매 도우미입니다.
        </p>
      </section>

      <ConversationPanel />

      <section className="status-grid" aria-label="프로젝트 준비 상태">
        {setupItems.map((item) => (
          <StatusCard key={item.title} {...item} />
        ))}
      </section>
    </main>
  )
}
