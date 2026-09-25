import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  Package,
  AlertTriangle,
  Sparkles,
  PackageSearch,
  DollarSign,
  Calendar,
  Boxes,
  TrendingUp,
  TrendingDown,
  ShoppingCart,
  Truck,
  History,
  ArrowUpRight,
  ArrowDownRight,
  RotateCcw,
} from 'lucide-react'
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import Badge from '../../components/ui/Badge'
import { EmptyState } from '../../components/ui/ListStates'
import Skeleton from '../../components/ui/Skeleton'

const statCardVariants = {
  hidden: { opacity: 0, y: 12, scale: 0.98 },
  visible: { opacity: 1, y: 0, scale: 1, transition: { type: 'spring', stiffness: 320, damping: 26 } },
}

function StatCard({ icon: Icon, label, value, accent = false, unavailable = false }) {
  return (
    // Hover feedback is transform-only (translateY) — box-shadow is never animated, since it
    // forces a paint on every frame instead of running on the compositor like transform/opacity.
    <motion.div
      variants={statCardVariants}
      whileHover={{ y: -3 }}
      transition={{ type: 'spring', stiffness: 400, damping: 22 }}
      className="flex items-center gap-4 rounded-lg border border-navy-100 bg-white p-5 shadow-sm transition-shadow duration-150 hover:shadow-md"
    >
      <div
        className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-md ${
          accent ? 'bg-orange-500' : 'bg-navy-600'
        }`}
      >
        <Icon size={22} className="text-white" />
      </div>
      <div className="min-w-0">
        <p className="truncate text-sm text-navy-400">{label}</p>
        {unavailable ? (
          <p className="text-sm text-navy-300">Not available to your role</p>
        ) : (
          <p className="break-words text-xl font-bold text-navy-800 sm:text-2xl">{value}</p>
        )}
      </div>
    </motion.div>
  )
}

function fmt(n) {
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function measuredAxisWidth(values, { minWidth = 40, maxWidth = 90, charWidth = 7, padding = 16 } = {}) {
  const longest = values.reduce((max, v) => Math.max(max, String(v ?? '').length), 0)
  return Math.min(maxWidth, Math.max(minWidth, longest * charWidth + padding))
}

function timeAgo(isoString) {
  const seconds = Math.floor((Date.now() - new Date(isoString).getTime()) / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

const ACTIVITY_ICON = {
  SALE: ShoppingCart,
  PURCHASE: Truck,
  STOCK_ADJUSTMENT: RotateCcw,
}

function SalesTrendChart() {
  const [trend, setTrend] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    axiosClient
      .get('/dashboard/sales-trend', { params: { days: 30 } })
      .then((res) => setTrend(res.data.data))
      .catch((err) => setError(err.response?.data?.message || 'Failed to load sales trend'))
  }, [])

  return (
    <div className="rounded-lg border border-navy-100 bg-white p-5 shadow-sm">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-navy-700">
          <TrendingUp size={16} className="text-orange-500" /> Sales Trend (30 days)
        </h3>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {!trend && !error && <Skeleton className="h-56 w-full rounded" />}

      {trend && trend.points.every((p) => Number(p.total) === 0) && (
        <EmptyState
          icon={TrendingUp}
          title="No completed sales yet"
          description="Sales trend will appear here once the Sales module starts recording completed transactions."
        />
      )}

      {trend && !trend.points.every((p) => Number(p.total) === 0) && (
        <>
          {trend.daysWithActivity < 7 && (
            <p className="mb-2 text-xs text-navy-400">
              Limited data — only {trend.daysWithActivity} day{trend.daysWithActivity === 1 ? '' : 's'} with recorded sales so far; insights will improve as more activity is recorded.
            </p>
          )}
          <ResponsiveContainer width="100%" height={220}>
            <AreaChart data={trend.points} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
              <defs>
                <linearGradient id="salesTrendFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#f97316" stopOpacity={0.35} />
                  <stop offset="95%" stopColor="#f97316" stopOpacity={0.02} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e9f0" />
              <XAxis
                dataKey="date"
                tick={{ fontSize: 11, fill: '#8996ac' }}
                tickFormatter={(d) => d.slice(5)}
                interval="preserveStartEnd"
              />
              <YAxis
                tick={{ fontSize: 11, fill: '#8996ac' }}
                width={measuredAxisWidth(trend.points.map((p) => fmt(p.total)))}
              />
              <Tooltip
                formatter={(value) => [fmt(value), 'Sales']}
                labelFormatter={(d) => d}
                contentStyle={{ fontSize: 12, borderRadius: 8, borderColor: '#e5e9f0' }}
              />
              <Area type="monotone" dataKey="total" stroke="#f97316" strokeWidth={2} fill="url(#salesTrendFill)" />
            </AreaChart>
          </ResponsiveContainer>
        </>
      )}
    </div>
  )
}

function TopProductsWidget() {
  const [products, setProducts] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    axiosClient
      .get('/dashboard/top-products', { params: { limit: 8, period: 30 } })
      .then((res) => setProducts(res.data.data))
      .catch((err) => setError(err.response?.data?.message || 'Failed to load top products'))
  }, [])

  return (
    <div className="rounded-lg border border-navy-100 bg-white p-5 shadow-sm">
      <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-navy-700">
        <ArrowUpRight size={16} className="text-green-600" /> Top Selling (30 days)
      </h3>

      {error && <p className="text-sm text-red-600">{error}</p>}
      {!products && !error && (
        <div className="space-y-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-9 w-full" />
          ))}
        </div>
      )}
      {products && products.length === 0 && (
        <EmptyState icon={ArrowUpRight} title="No sales in this period" description="Top sellers will appear once sales are completed." />
      )}
      {products && products.length > 0 && (
        <div className="divide-y divide-navy-100">
          {products.map((p) => (
            <div key={p.productId} className="flex items-center justify-between py-2.5 text-sm">
              <div className="min-w-0">
                <p className="truncate font-medium text-navy-800">{p.productName}</p>
                <p className="text-xs text-navy-400">{p.productSku}</p>
              </div>
              <div className="shrink-0 text-right">
                <p className="font-medium text-navy-800">{Number(p.quantitySold)} sold</p>
                {p.revenue != null && <p className="text-xs text-navy-400">{fmt(p.revenue)}</p>}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function SlowMovingWidget() {
  const [products, setProducts] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    axiosClient
      .get('/dashboard/slow-moving', { params: { limit: 8, period: 30 } })
      .then((res) => setProducts(res.data.data))
      .catch((err) => setError(err.response?.data?.message || 'Failed to load slow-moving products'))
  }, [])

  return (
    <div className="rounded-lg border border-navy-100 bg-white p-5 shadow-sm">
      <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-navy-700">
        <ArrowDownRight size={16} className="text-navy-400" /> Slow Moving (30 days)
      </h3>

      {error && <p className="text-sm text-red-600">{error}</p>}
      {!products && !error && (
        <div className="space-y-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-9 w-full" />
          ))}
        </div>
      )}
      {products && products.length === 0 && (
        <EmptyState icon={ArrowDownRight} title="Nothing to show" description="No products currently in stock to evaluate." />
      )}
      {products && products.length > 0 && (
        <div className="divide-y divide-navy-100">
          {products.map((p) => (
            <div key={p.productId} className="flex items-center justify-between py-2.5 text-sm">
              <div className="min-w-0">
                <p className="truncate font-medium text-navy-800">{p.productName}</p>
                <p className="text-xs text-navy-400">{p.productSku}</p>
              </div>
              <div className="shrink-0 text-right">
                <p className="font-medium text-navy-800">{Number(p.currentStock)} in stock</p>
                <p className="text-xs text-navy-400">{Number(p.quantitySoldInPeriod)} sold</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function RecentActivityWidget() {
  const [activity, setActivity] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    axiosClient
      .get('/dashboard/recent-activity', { params: { limit: 15 } })
      .then((res) => setActivity(res.data.data))
      .catch((err) => setError(err.response?.data?.message || 'Failed to load recent activity'))
  }, [])

  return (
    <div className="rounded-lg border border-navy-100 bg-white p-5 shadow-sm">
      <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-navy-700">
        <History size={16} className="text-navy-400" /> Recent Activity
      </h3>

      {error && <p className="text-sm text-red-600">{error}</p>}
      {!activity && !error && (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-9 w-full" />
          ))}
        </div>
      )}
      {activity && activity.length === 0 && (
        <EmptyState icon={History} title="No activity yet" description="Sales, purchases, and stock changes will show up here." />
      )}
      {activity && activity.length > 0 && (
        <div className="space-y-3">
          {activity.map((a, i) => {
            const Icon = ACTIVITY_ICON[a.type] || History
            return (
              <div key={i} className="flex items-start gap-3 text-sm">
                <div className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-navy-50 text-navy-500">
                  <Icon size={14} />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-navy-700">{a.description}</p>
                  <p className="text-xs text-navy-400">{timeAgo(a.timestamp)}</p>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

const CONFIDENCE_VARIANT = {
  insufficient_data: 'neutral',
  low: 'danger',
  medium: 'orange',
  high: 'success',
}

function AtRiskWidget() {
  const [items, setItems] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    axiosClient
      .get('/ai/forecast/at-risk', { params: { horizon: 'week' } })
      .then((res) => setItems(res.data.data))
      .catch((err) => setError(err.response?.data?.message || 'Failed to load at-risk items'))
  }, [])

  return (
    <div className="rounded-lg border border-navy-100 bg-white p-5 shadow-sm">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-navy-700">
          <Sparkles size={16} className="text-orange-500" /> At Risk This Week
        </h3>
        <Link to="/forecast" className="text-xs text-navy-400 hover:text-navy-600">
          Full forecast →
        </Link>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {items === null && !error && (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      )}

      {items && items.length === 0 && (
        <EmptyState icon={AlertTriangle} title="Nothing urgent" description="No low-stock items with a near-term stockout right now." />
      )}

      {items && items.length > 0 && (
        <div className="divide-y divide-navy-100">
          {items.slice(0, 5).map((item) => (
            <div key={`${item.productId}-${item.warehouseId}`} className="flex items-center justify-between gap-2 py-2.5 text-sm">
              <div className="min-w-0">
                <p className="truncate font-medium text-navy-800">{item.productName}</p>
                <p className="truncate text-xs text-navy-400">{item.warehouseName} · {Number(item.currentStock)} in stock</p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                {item.daysUntilStockout != null ? (
                  <span className="text-xs font-medium text-navy-600">~{item.daysUntilStockout}d left</span>
                ) : (
                  <span className="text-xs text-navy-300">no trend</span>
                )}
                <Badge variant={CONFIDENCE_VARIANT[item.confidence]}>{item.confidence.replace('_', ' ')}</Badge>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const URGENCY_VARIANT = { low: 'neutral', medium: 'orange', high: 'danger' }

function ReorderWidget() {
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    axiosClient
      .get('/reorder-suggestions/summary')
      .then((res) => setSummary(res.data.data))
      .catch((err) => setError(err.response?.data?.message || 'Failed to load reorder suggestions'))
  }, [])

  const urgent = summary?.topUrgent?.length > 0

  return (
    <div className={`rounded-lg border p-5 shadow-sm ${urgent ? 'border-orange-300 bg-orange-50/40' : 'border-navy-100 bg-white'}`}>
      <div className="mb-3 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-navy-700">
          <PackageSearch size={16} className="text-orange-500" /> Reorder Suggestions
          {summary && summary.openCount > 0 && (
            <span className="rounded-full bg-orange-100 px-2 py-0.5 text-xs font-semibold text-orange-700">
              {summary.openCount}
            </span>
          )}
        </h3>
        <Link to="/reorders" className="text-xs font-medium text-orange-600 hover:text-orange-700">
          Full list →
        </Link>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {summary === null && !error && (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      )}

      {summary && summary.topUrgent.length === 0 && (
        <EmptyState icon={PackageSearch} title="Nothing needs reordering right now" description="Every tracked product is above its reorder level." />
      )}

      {summary && summary.topUrgent.length > 0 && (
        <div className="divide-y divide-navy-100">
          {summary.topUrgent.map((s) => (
            <div key={s.id} className="flex items-center justify-between gap-2 py-2.5 text-sm">
              <div className="min-w-0">
                <p className="truncate font-medium text-navy-800">{s.productName}</p>
                <p className="truncate text-xs text-navy-400">{s.warehouseName} · {Number(s.currentQuantity)} in stock</p>
              </div>
              <Badge variant={URGENCY_VARIANT[s.urgencyLevel]}>{s.urgencyLevel}</Badge>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function Dashboard() {
  const { hasRole } = useAuth()
  const canSeeFinancials = hasRole('ADMIN') || hasRole('MANAGER')

  const [summary, setSummary] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    axiosClient
      .get('/dashboard/summary')
      .then((res) => setSummary(res.data.data))
      .catch((err) => setError(err.response?.data?.message || 'Failed to load dashboard data'))
  }, [])

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-navy-800">Welcome back</h2>
        <p className="text-navy-400">Here's what's happening across your inventory.</p>
      </div>

      {error && (
        <div className="rounded-md border border-orange-300 bg-orange-50 px-4 py-3 text-sm text-orange-700">
          {error}
        </div>
      )}

      <motion.div
        initial="hidden"
        animate="visible"
        variants={{ visible: { transition: { staggerChildren: 0.06 } } }}
        className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6"
      >
        <StatCard
          icon={DollarSign}
          label="Today's Sales"
          value={summary ? fmt(summary.todaySalesTotal ?? 0) : '—'}
          unavailable={summary && summary.todaySalesTotal === null}
          accent
        />
        <StatCard
          icon={Calendar}
          label="This Month's Sales"
          value={summary ? fmt(summary.monthSalesTotal ?? 0) : '—'}
          unavailable={summary && summary.monthSalesTotal === null}
        />
        <StatCard
          icon={Boxes}
          label="Stock Value"
          value={summary ? fmt(summary.totalStockValue) : '—'}
        />
        <StatCard
          icon={Package}
          label="Active Products"
          value={summary ? summary.totalActiveProducts : '—'}
        />
        <StatCard
          icon={AlertTriangle}
          label="Low Stock Items"
          value={summary ? summary.lowStockCount : '—'}
          accent={summary && summary.lowStockCount > 0}
        />
        <StatCard
          icon={PackageSearch}
          label="Open Reorders"
          value={summary ? summary.openReorderCount : '—'}
        />
        {canSeeFinancials && (
          <StatCard
            icon={summary && Number(summary.grossMarginThisMonth) >= 0 ? TrendingUp : TrendingDown}
            label="Gross Margin (month)"
            value={summary ? fmt(summary.grossMarginThisMonth ?? 0) : '—'}
            unavailable={summary && summary.grossMarginThisMonth === null}
          />
        )}
      </motion.div>

      {canSeeFinancials && <SalesTrendChart />}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <TopProductsWidget />
        <SlowMovingWidget />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <ReorderWidget />
        <AtRiskWidget />
      </div>

      <RecentActivityWidget />
    </div>
  )
}
