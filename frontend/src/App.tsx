import './App.css'
import { AppStateProvider, useAppState } from './features/conversation/AppState'
import { HomePage } from './pages/HomePage'
import { BusPage } from './pages/BusPage'
import { SeatPage } from './pages/SeatPage'
import { ConfirmPage } from './pages/ConfirmPage'
import { HistoryPage } from './pages/HistoryPage'

// 화면 전환: screen 상태에 따라 다른 화면 보여주기
function ScreenRouter() {
  const { screen } = useAppState()

      switch (screen) {
    case 'home':
      return <HomePage />
    case 'bus':
      return <BusPage />
    case 'seat':
      return <SeatPage />
    case 'confirm':
      return <ConfirmPage />
    case 'history':
      return <HistoryPage />
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