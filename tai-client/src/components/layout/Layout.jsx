import { useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import BottomNav from './BottomNav'

const PAGE_TITLES = {
  '/': 'Dashboard',
  '/products': 'Products',
  '/categories': 'Categories',
  '/brands': 'Brands',
  '/warehouses': 'Warehouses',
  '/stock': 'Stock',
  '/categories': 'Categories',
  '/users': 'Users',
  '/suppliers': 'Suppliers',
  '/purchase-orders': 'Purchase Orders',
  '/ask': 'Ask Tai',
  '/forecast': 'AI Demand Forecast',
  '/anomalies': 'Activity Review',
  '/reorders': 'Reorder Suggestions',
}

export default function Layout() {
  const location = useLocation()
  const title = PAGE_TITLES[location.pathname] ?? 'Tai'
  const [sidebarOpen, setSidebarOpen] = useState(false)

  // Close the mobile drawer automatically on every navigation so it never
  // stays open after tapping a link.
  useEffect(() => {
    setSidebarOpen(false)
  }, [location.pathname])

  return (
    <div className="flex h-screen bg-navy-50">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Topbar title={title} onMenuClick={() => setSidebarOpen(true)} />
        <main className="flex-1 overflow-y-auto p-4 pb-20 sm:p-6 lg:pb-6">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -4 }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </main>
        <BottomNav onMenuClick={() => setSidebarOpen(true)} />
      </div>
    </div>
  )
}
