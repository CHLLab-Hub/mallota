import './App.css'
import { AppStateProvider, useAppState } from './features/conversation/AppState'
import { HomePage } from './pages/HomePage'
import { BusPage } from './pages/BusPage'
import { SeatPage } from './pages/SeatPage'
import { ConfirmPage } from './pages/ConfirmPage'

// 화면 전환: screen 상태에 따라 다른 화면 보여주기
function ScreenRouter() {
  const { screen } = useAppState()

    switch (screen) {
    case 'home':
      return <HomePage />
    case 'bus':
      return <BusPage />
    default:
      return <HomePage />
    case 'seat':
      return <SeatPage />
    case 'confirm':
      return <ConfirmPage />
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