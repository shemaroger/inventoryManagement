import { NavLink } from 'react-router-dom'
import { LayoutDashboard, Package, ShoppingCart, BarChart3, Menu } from 'lucide-react'

const TABS = [
  { to: '/', label: 'Home', icon: LayoutDashboard, end: true },
  { to: '/products', label: 'Products', icon: Package },
  { to: '/sales', label: 'Sales', icon: ShoppingCart },
  { to: '/stock', label: 'Stock', icon: BarChart3 },
]

export default function BottomNav({ onMenuClick }) {
  return (
    <nav className="fixed inset-x-0 bottom-0 z-20 flex items-center justify-around border-t border-navy-600 bg-navy-700 pb-[env(safe-area-inset-bottom)] lg:hidden">
      {TABS.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) =>
            `flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-medium transition-all duration-150 active:scale-90 ${
              isActive ? 'text-orange-500' : 'text-navy-300'
            }`
          }
        >
          <Icon size={20} strokeWidth={2} className="transition-transform duration-150" />
          {label}
        </NavLink>
      ))}
      <button
        type="button"
        onClick={onMenuClick}
        className="flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-medium text-navy-300 transition-transform duration-150 active:scale-90"
      >
        <Menu size={20} strokeWidth={2} />
        Menu
      </button>
    </nav>
  )
}
