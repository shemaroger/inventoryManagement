import { useEffect, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import {
  LayoutDashboard,
  Package,
  Tags,
  BookmarkCheck,
  Warehouse,
  BarChart3,
  Users,
  UserSquare2,
  ShoppingCart,
  Truck,
  ClipboardList,
  Sparkles,
  CalendarClock,
  ShieldCheck,
  History,
  Building2,
  Briefcase,
  PackageSearch,
  LogOut,
  ChevronDown,
  FileText,
  Landmark,
  BookText,
  Wallet,
  Scale,
  Percent,
  TrendingUp,
  FileBarChart,
  LineChart as LineChartIcon,
  X,
} from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'

const NAV_GROUPS = [
  {
    section: 'Overview',
    items: [
      { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
      { to: '/reports/business', label: 'Business Report', icon: Briefcase, managerUp: true },
      { to: '/ask', label: 'Ask Tai', icon: Sparkles },
    ],
  },
  {
    section: 'Inventory',
    items: [
      { to: '/products', label: 'Products', icon: Package },
      { to: '/categories', label: 'Categories', icon: Tags },
      { to: '/brands', label: 'Brands', icon: BookmarkCheck },
      { to: '/warehouses', label: 'Warehouses', icon: Warehouse },
      { to: '/stock', label: 'Stock', icon: BarChart3 },
    ],
  },
  {
    section: 'Sales',
    items: [
      { to: '/customers', label: 'Customers', icon: UserSquare2 },
      { to: '/sales', label: 'Sales', icon: ShoppingCart },
    ],
  },
  {
    section: 'Purchasing',
    items: [
      { to: '/suppliers', label: 'Suppliers', icon: Truck },
      { to: '/purchase-orders', label: 'Purchase Orders', icon: ClipboardList },
    ],
  },
  {
    section: 'Intelligence',
    items: [
      { to: '/forecast', label: 'Forecast', icon: CalendarClock },
      { to: '/reorders', label: 'Reorders', icon: PackageSearch, badgeKey: 'reorderOpenCount' },
      { to: '/analytics', label: 'Analytics', icon: LineChartIcon, managerUp: true },
    ],
  },
  {
    section: 'Accounting',
    items: [
      { to: '/accounts', label: 'Chart of Accounts', icon: Landmark, adminOnly: true },
      { to: '/journal-entries', label: 'Journal Entries', icon: BookText, adminOnly: true },
      { to: '/cash/expenditures', label: 'Cash Management', icon: Wallet, adminOnly: true },
      { to: '/cash/reconciliation', label: 'Bank Reconciliation', icon: Scale, adminOnly: true },
      { to: '/reports/vat', label: 'VAT Report', icon: Percent, managerUp: true },
      { to: '/reports/profit-loss', label: 'Profit & Loss', icon: TrendingUp, managerUp: true },
      { to: '/reports/balance-sheet', label: 'Balance Sheet', icon: FileBarChart, managerUp: true },
    ],
  },
  {
    section: 'Administration',
    items: [
      { to: '/users', label: 'Users', icon: Users, adminOnly: true },
      { to: '/anomalies', label: 'Activity Review', icon: ShieldCheck, adminOnly: true },
      { to: '/audit-log', label: 'Audit Log', icon: History, adminOnly: true },
      { to: '/reports/daily-records', label: 'Daily Records', icon: FileText, managerUp: true },
      { to: '/settings/company', label: 'Company Profile', icon: Building2 },
    ],
  },
]

const COLLAPSED_STORAGE_KEY = 'tai_sidebar_collapsed'

function isItemActive(item, pathname) {
  if (item.end) return pathname === item.to
  return pathname === item.to || pathname.startsWith(`${item.to}/`)
}

function findActiveSection(pathname) {
  const group = NAV_GROUPS.find((g) => g.items.some((item) => isItemActive(item, pathname)))
  return group?.section ?? null
}

function getDefaultCollapsedState(pathname) {
  const activeSection = findActiveSection(pathname)
  return NAV_GROUPS.reduce((acc, group) => {
    acc[group.section] = group.section !== activeSection
    return acc
  }, {})
}

export default function Sidebar({ open = false, onClose = () => {} }) {
  const { user, logout, hasRole } = useAuth()
  const location = useLocation()
  const [badgeCounts, setBadgeCounts] = useState({})
  const [collapsedSections, setCollapsedSections] = useState(() => {
    try {
      const stored = localStorage.getItem(COLLAPSED_STORAGE_KEY)
      if (stored) return JSON.parse(stored)
    } catch {
      // ignore malformed storage, fall through to computed default
    }
    return getDefaultCollapsedState(location.pathname)
  })

  useEffect(() => {
    axiosClient
      .get('/reorder-suggestions/summary')
      .then((res) => setBadgeCounts((prev) => ({ ...prev, reorderOpenCount: res.data.data.openCount })))
      .catch(() => {})
  }, [])

  useEffect(() => {
    localStorage.setItem(COLLAPSED_STORAGE_KEY, JSON.stringify(collapsedSections))
  }, [collapsedSections])

  const toggleSection = (section) => {
    setCollapsedSections((prev) => ({ ...prev, [section]: !prev[section] }))
  }

  return (
    <>
      {/* Backdrop, mobile-only: click to dismiss the drawer */}
      <AnimatePresence>
        {open && (
          <motion.div
            className="fixed inset-0 z-30 bg-black/40 lg:hidden"
            onClick={onClose}
            aria-hidden="true"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
          />
        )}
      </AnimatePresence>

      <aside
        className={`fixed inset-y-0 left-0 z-40 flex h-screen w-64 flex-shrink-0 -translate-x-full flex-col bg-navy-700 text-white transition-transform duration-300 ease-in-out lg:static lg:translate-x-0 lg:transition-none ${
          open ? 'translate-x-0' : ''
        }`}
      >
        <div className="relative flex items-center border-b border-navy-600">
          <img src="/logo.png" alt="Mapleco" className="h-20 w-full object-contain bg-white" />
          <button
            type="button"
            onClick={onClose}
            className="absolute right-2 top-2 rounded-full p-1.5 text-navy-500 transition-colors hover:bg-black/10 lg:hidden"
            aria-label="Close menu"
          >
            <X size={20} />
          </button>
        </div>

      <nav className="flex-1 space-y-4 overflow-y-auto px-3 py-4">
        {NAV_GROUPS.map((group) => {
          const visibleItems = group.items.filter((item) => {
            if (item.adminOnly) return hasRole('ADMIN')
            if (item.managerUp) return hasRole('ADMIN') || hasRole('MANAGER')
            return true
          })
          if (visibleItems.length === 0) return null

          const isCollapsed = collapsedSections[group.section]

          return (
            <div key={group.section}>
              <button
                type="button"
                onClick={() => toggleSection(group.section)}
                className="flex w-full items-center justify-between px-3 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-navy-400 hover:text-navy-200"
              >
                <span>{group.section}</span>
                <ChevronDown
                  size={14}
                  strokeWidth={2.5}
                  className={`transition-transform duration-150 ${isCollapsed ? '-rotate-90' : 'rotate-0'}`}
                />
              </button>
              <div
                className={`grid transition-[grid-template-rows] duration-150 ease-in-out ${
                  isCollapsed ? 'grid-rows-[0fr]' : 'grid-rows-[1fr]'
                }`}
              >
                <div className="overflow-hidden">
                  <div className="space-y-1">
                    {visibleItems.map(({ to, label, icon: Icon, end, badgeKey }) => {
                      const badgeCount = badgeKey ? badgeCounts[badgeKey] : null
                      return (
                        <NavLink
                          key={to}
                          to={to}
                          end={end}
                          className={({ isActive }) =>
                            `flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-all duration-150 ${
                              isActive
                                ? 'bg-orange-500 text-white'
                                : 'text-navy-100 hover:translate-x-0.5 hover:bg-navy-600 hover:text-white'
                            }`
                          }
                        >
                          <Icon size={18} strokeWidth={2} />
                          <span className="flex-1">{label}</span>
                          {badgeCount > 0 && (
                            <span className="rounded-full bg-orange-500 px-1.5 py-0.5 text-[11px] font-semibold leading-none text-white">
                              {badgeCount}
                            </span>
                          )}
                        </NavLink>
                      )
                    })}
                  </div>
                </div>
              </div>
            </div>
          )
        })}
      </nav>

      <div className="border-t border-navy-600 px-4 py-4">
        <p className="truncate text-sm font-medium">{user?.fullName}</p>
        <p className="truncate text-xs text-navy-300">{user?.email}</p>
        <button
          onClick={logout}
          className="mt-3 flex w-full items-center justify-center gap-2 rounded-full bg-navy-600 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-navy-500"
        >
          <LogOut size={16} />
          Log out
        </button>
      </div>
      </aside>
    </>
  )
}
