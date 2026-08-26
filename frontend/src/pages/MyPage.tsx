import { useAppState } from '../features/conversation/AppState'
import { BottomTab } from './BottomTab'
import './HomePage.css'

export function MyPage() {
  const { bookings } = useAppState()

  const menuItems = [
    { icon: '🧾', label: '이용 내역', desc: `총 ${bookings.length}건의 예매` },
    { icon: '💳', label: '결제 내역', desc: '결제한 내역을 확인해요' },
    { icon: '⚙️', label: '결제 수단 관리', desc: '카드 등록·변경' },
  ]

  return (
    <div className="phone-frame">
      <h1 className="home-title" style={{ fontSize: '1.5rem' }}>마이페이지</h1>

      <div className="home-body">
        {menuItems.map((item) => (
          <div
            key={item.label}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '16px',
              border: '2px solid #f0e6d8',
              borderRadius: '16px',
              padding: '18px',
              marginBottom: '12px',
              background: '#fff',
            }}
          >
            <span style={{ fontSize: '1.8rem' }}>{item.icon}</span>
            <div>
              <div style={{ fontSize: '1.15rem', fontWeight: 700 }}>{item.label}</div>
              <div style={{ fontSize: '0.95rem', color: '#58665f' }}>{item.desc}</div>
            </div>
          </div>
        ))}
      </div>

      <BottomTab />
    </div>
  )
}