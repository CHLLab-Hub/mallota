import './App.css'
import { AppStateProvider, useAppState } from './features/conversation/AppState'
import { HomePage } from './pages/HomePage'

// 화면 전환: screen 상태에 따라 다른 화면 보여주기
function ScreenRouter() {
  const { screen } = useAppState()

  switch (screen) {
    case 'home':
      return <HomePage />
    // 나머지 화면은 다음 단계에서 추가
    // case 'bus': return <BusPage />
    // case 'seat': return <SeatPage />
    // case 'confirm': return <ConfirmPage />
    // case 'history': return <HistoryPage />
    // case 'mypage': return <MyPage />
    default:
      return <HomePage />
  }
}

function App() {
  return (
    <AppStateProvider>
      <ScreenRouter />
    </AppStateProvider>
  )
}

export default App