import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Briefcase,
  DollarSign,
  TrendingUp,
  Wallet,
  Percent,
  Boxes,
  AlertTriangle,
  Users,
  Truck,
  FileDown,
  FileSpreadsheet,
  FileText,
} from 'lucide-react'
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  PieChart,
  Pie,
  Cell,
  Legend,
} from 'recharts'
import axiosClient from '../../api/axiosClient'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import DailyRecordsPanel from './DailyRecordsPanel'

const PIE_COLORS = ['#e2690a', '#102540', '#4a6b95', '#f5811f', '#7e9bbd', '#bd5407']

const REPORT_KINDS = [
  { key: 'financial', label: 'Financial', icon: DollarSign },
  { key: 'sales', label: 'Sales & Customers', icon: Users },
  { key: 'inventory', label: 'Inventory & Stock', icon: Boxes },
  { key: 'purchasing', label: 'Purchasing & Suppliers', icon: Truck },
  { key: 'dailyRecords', label: 'Daily Records', icon: FileText },
]
const ALL_KINDS = REPORT_KINDS.reduce((acc, k) => ({ ...acc, [k.key]: true }), {})

function firstOfMonth() {
  const d = new Date()
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10)
}

function fmt(n) {
  return Number(n ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function compactNumber(n) {
  return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(Number(n ?? 0))
}

function shortLabel(s, max = 14) {
  if (!s) return s
  return s.length > max ? s.slice(0, max - 1) + '…' : s
}

// Fixed axis widths break down the moment the underlying numbers/names grow past what was
// eyeballed when the chart was built (e.g. "300K" fits in 44px, "12.4M" doesn't). Instead,
// size the axis off the actual longest rendered label in the current dataset, so a busier
// period or a bigger business just gets a wider axis instead of clipped text.
function measuredAxisWidth(labels, { minWidth = 40, maxWidth = 220, charWidth = 7, padding = 16 } = {}) {
  const longest = labels.reduce((max, l) => Math.max(max, (l ?? '').length), 0)
  return Math.min(maxWidth, Math.max(minWidth, longest * charWidth + padding))
}

// Recharts' XAxis `tick` render prop — draws each label angled and right-aligned under its
// bar instead of truncating, so a chart with only 5 bars can afford real product/supplier
// names instead of "Cemen…"/"R…".
function AngledTick({ x, y, payload }) {
  return (
    <g transform={`translate(${x},${y})`}>
      <text dx={-4} dy={10} textAnchor="end" transform="rotate(-35)" fontSize={11} fill="#4a6b95">
        {shortLabel(payload.value, 18)}
      </text>
    </g>
  )
}

function StatCard({ icon: Icon, label, value, tone = 'navy' }) {
  const toneClass = tone === 'positive' ? 'text-green-700' : tone === 'negative' ? 'text-red-600' : 'text-navy-800'
  return (
    <div className="flex items-center gap-4 rounded-lg border border-navy-100 bg-white p-5 shadow-sm">
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-md bg-navy-600">
        <Icon size={22} className="text-white" />
      </div>
      <div className="min-w-0">
        <p className="truncate text-sm text-navy-400">{label}</p>
        <p className={`break-words text-xl font-bold ${toneClass}`}>{fmt(value)}</p>
      </div>
    </div>
  )
}

function SectionCard({ title, icon: Icon, children }) {
  return (
    <div className="rounded-lg border border-navy-100 bg-white shadow-sm">
      <div className="flex items-center gap-2 border-b border-navy-100 px-4 py-3">
        <Icon size={16} className="text-navy-500" />
        <h3 className="text-sm font-semibold text-navy-800">{title}</h3>
      </div>
      <div className="p-4">{children}</div>
    </div>
  )
}

export default function OwnerReportPage() {
  const [startDate, setStartDate] = useState(firstOfMonth())
  const [endDate, setEndDate] = useState(() => new Date().toISOString().slice(0, 10))

  const [report, setReport] = useState(null)
  const [dailyRecords, setDailyRecords] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [exporting, setExporting] = useState(null)
  const [visibleKinds, setVisibleKinds] = useState(ALL_KINDS)

  function toggleKind(key) {
    setVisibleKinds((prev) => {
      const next = { ...prev, [key]: !prev[key] }
      // Never allow zero sections selected — that's a broken/empty report, not a valid filter.
      return Object.values(next).some(Boolean) ? next : prev
    })
  }

  const onlyDailyRecords = visibleKinds.dailyRecords && Object.entries(visibleKinds).filter(([, v]) => v).length === 1

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    Promise.all([
      axiosClient.get('/reports/owner-summary', { params: { startDate, endDate } }),
      axiosClient.get('/reports/daily-sales/range', { params: { startDate, endDate } }),
      axiosClient.get('/reports/daily-purchases/range', { params: { startDate, endDate } }),
    ])
      .then(([ownerRes, salesRes, purchasesRes]) => {
        if (cancelled) return
        setReport(ownerRes.data.data)
        setDailyRecords({ sales: salesRes.data.data, purchases: purchasesRes.data.data })
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load business report')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [startDate, endDate])

  // jsPDF and exceljs are sizeable dependencies only needed once someone actually exports —
  // loading them lazily keeps them out of the initial bundle for every other page/visit.
  async function handleExportPdf() {
    setExporting('pdf')
    try {
      const { exportOwnerReportPdf } = await import('./ownerReportExport')
      exportOwnerReportPdf(report, visibleKinds, dailyRecords)
    } finally {
      setExporting(null)
    }
  }

  async function handleExportExcel() {
    setExporting('excel')
    try {
      const { exportOwnerReportExcel } = await import('./ownerReportExport')
      await exportOwnerReportExcel(report, visibleKinds, dailyRecords)
    } finally {
      setExporting(null)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-md bg-orange-500">
            <Briefcase size={22} className="text-white" />
          </div>
          <div>
            <h2 className="text-2xl font-bold text-navy-800">Business Report</h2>
            <p className="text-navy-400">Everything you need as the owner, in one place — money, sales, stock, and suppliers.</p>
          </div>
        </div>
        {report && (
          <div className="flex gap-2">
            <Button variant="secondary" onClick={handleExportPdf} loading={exporting === 'pdf'}>
              <FileDown size={16} /> Export PDF
            </Button>
            <Button variant="secondary" onClick={handleExportExcel} loading={exporting === 'excel'}>
              <FileSpreadsheet size={16} /> Export Excel
            </Button>
          </div>
        )}
      </div>

      {/* This date range drives Financial/Sales/Inventory/Purchasing — Daily Records has its
          own independent Sales-vs-Purchases and Single-day-vs-range controls below, so this
          picker is hidden when Daily Records is the only section showing since it wouldn't
          do anything in that state. */}
      {!onlyDailyRecords && (
        <div className="flex flex-wrap items-end gap-3 rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
          <Input label="Start date" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-44" />
          <Input label="End date" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-44" />
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs font-semibold uppercase tracking-wide text-navy-400">Show:</span>
        {REPORT_KINDS.map(({ key, label, icon: Icon }) => {
          const active = visibleKinds[key]
          return (
            <button
              key={key}
              type="button"
              onClick={() => toggleKind(key)}
              aria-pressed={active}
              className={`flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-medium transition-colors duration-150 ${
                active
                  ? 'border-orange-500 bg-orange-500 text-white'
                  : 'border-navy-200 bg-white text-navy-500 hover:bg-navy-50'
              }`}
            >
              <Icon size={13} />
              {label}
            </button>
          )
        })}
      </div>

      {error && <ErrorState message={error} />}

      {loading ? (
        <table className="w-full">
          <SkeletonRows rows={8} columns={1} />
        </table>
      ) : (
        !error &&
        report && (
          <div className="space-y-8">
            {/* ---------- Financial summary ---------- */}
            {visibleKinds.financial && (
            <section className="space-y-3">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-navy-400">Financial summary</h3>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                <StatCard icon={DollarSign} label="Total revenue" value={report.financial.totalRevenue} />
                <StatCard icon={TrendingUp} label="Gross margin" value={report.financial.grossMargin} />
                <StatCard
                  icon={TrendingUp}
                  label="Net profit"
                  value={report.financial.netIncome}
                  tone={Number(report.financial.netIncome) >= 0 ? 'positive' : 'negative'}
                />
                <StatCard icon={Wallet} label="Cash on hand" value={report.financial.cashBalance} />
                <StatCard icon={Percent} label="VAT payable" value={report.financial.vatPayable} />
                <StatCard icon={DollarSign} label="Total expenses" value={report.financial.totalExpenses} />
              </div>
            </section>
            )}

            {/* ---------- Sales & customers ---------- */}
            {visibleKinds.sales && (
            <section className="space-y-3">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-navy-400">Sales &amp; customers</h3>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <StatCard icon={DollarSign} label="Sales this period" value={report.sales.totalSales} />
                <StatCard
                  icon={Users}
                  label="Outstanding from customers"
                  value={report.sales.totalReceivables}
                  tone={Number(report.sales.totalReceivables) > 0 ? 'negative' : undefined}
                />
              </div>
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                <SectionCard title="Top-selling products" icon={Boxes}>
                  {report.sales.topProducts.length === 0 ? (
                    <EmptyState icon={Boxes} title="No sales in this period" />
                  ) : (
                    <ResponsiveContainer width="100%" height={260}>
                      <BarChart data={report.sales.topProducts} margin={{ left: 4, right: 8, bottom: 50 }}>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} />
                        <XAxis dataKey="productName" interval={0} tick={<AngledTick />} height={70} />
                        <YAxis
                          tick={{ fontSize: 11 }}
                          width={measuredAxisWidth(report.sales.topProducts.map((p) => compactNumber(p.revenue)))}
                          tickFormatter={compactNumber}
                        />
                        <Tooltip formatter={(v) => fmt(v)} labelFormatter={(l) => l} />
                        <Bar dataKey="revenue" fill="#e2690a" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  )}
                </SectionCard>
                <SectionCard title="Top customers" icon={Users}>
                  {report.sales.topCustomers.length === 0 ? (
                    <EmptyState icon={Users} title="No sales in this period" />
                  ) : (
                    <ResponsiveContainer width="100%" height={260}>
                      <BarChart data={report.sales.topCustomers} layout="vertical" margin={{ left: 20 }}>
                        <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                        <XAxis type="number" tick={{ fontSize: 11 }} tickFormatter={compactNumber} />
                        <YAxis
                          dataKey="customerName"
                          type="category"
                          tickFormatter={(v) => shortLabel(v, 30)}
                          tick={{ fontSize: 11 }}
                          width={measuredAxisWidth(
                            report.sales.topCustomers.map((c) => shortLabel(c.customerName, 30)),
                            { minWidth: 90, maxWidth: 260 }
                          )}
                        />
                        <Tooltip formatter={(v) => fmt(v)} labelFormatter={(l) => l} />
                        <Bar dataKey="totalRevenue" fill="#102540" radius={[0, 4, 4, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  )}
                </SectionCard>
              </div>
            </section>
            )}

            {/* ---------- Inventory & stock ---------- */}
            {visibleKinds.inventory && (
            <section className="space-y-3">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-navy-400">Inventory &amp; stock</h3>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <StatCard icon={Boxes} label="Total stock value" value={report.inventory.totalStockValue} />
                <StatCard
                  icon={AlertTriangle}
                  label="Low-stock items"
                  value={report.inventory.lowStockCount}
                  tone={report.inventory.lowStockCount > 0 ? 'negative' : undefined}
                />
              </div>
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                <SectionCard title="Stock value by warehouse" icon={Boxes}>
                  {report.inventory.stockByWarehouse.length === 0 ? (
                    <EmptyState icon={Boxes} title="No stock recorded" />
                  ) : (
                    <ResponsiveContainer width="100%" height={220}>
                      <PieChart>
                        <Pie
                          data={report.inventory.stockByWarehouse}
                          dataKey="stockValue"
                          nameKey="warehouseName"
                          innerRadius={45}
                          outerRadius={75}
                          paddingAngle={2}
                        >
                          {report.inventory.stockByWarehouse.map((_, i) => (
                            <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                          ))}
                        </Pie>
                        <Tooltip formatter={(v) => fmt(v)} />
                        <Legend wrapperStyle={{ fontSize: 11 }} />
                      </PieChart>
                    </ResponsiveContainer>
                  )}
                </SectionCard>
                <SectionCard title="Needs reordering soon" icon={AlertTriangle}>
                  {report.inventory.lowStockItems.length === 0 ? (
                    <EmptyState icon={Boxes} title="Nothing below reorder level" />
                  ) : (
                    <ul className="divide-y divide-navy-100">
                      {report.inventory.lowStockItems.map((item, i) => (
                        <li key={i} className="flex items-center justify-between py-2 text-sm">
                          <span className="text-navy-700">
                            {item.productName} <span className="text-navy-400">({item.warehouseName})</span>
                          </span>
                          <Badge variant="orange">
                            {Number(item.quantity)} / {Number(item.reorderLevel)}
                          </Badge>
                        </li>
                      ))}
                    </ul>
                  )}
                  <Link to="/reorders" className="mt-3 inline-block text-xs font-medium text-orange-600 hover:text-orange-700">
                    View all reorder suggestions →
                  </Link>
                </SectionCard>
              </div>
            </section>
            )}

            {/* ---------- Purchasing & suppliers ---------- */}
            {visibleKinds.purchasing && (
            <section className="space-y-3">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-navy-400">Purchasing &amp; suppliers</h3>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <StatCard icon={Truck} label="Purchases this period" value={report.purchasing.totalPurchases} />
                <StatCard
                  icon={Truck}
                  label="Owed to suppliers"
                  value={report.purchasing.totalPayables}
                  tone={Number(report.purchasing.totalPayables) > 0 ? 'negative' : undefined}
                />
              </div>
              <SectionCard title="Top suppliers" icon={Truck}>
                {report.purchasing.topSuppliers.length === 0 ? (
                  <EmptyState icon={Truck} title="No purchases in this period" />
                ) : (
                  <ResponsiveContainer width="100%" height={260}>
                    <BarChart data={report.purchasing.topSuppliers} margin={{ left: 4, right: 8, bottom: 50 }}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="supplierName" interval={0} tick={<AngledTick />} height={70} />
                      <YAxis
                        tick={{ fontSize: 11 }}
                        width={measuredAxisWidth(report.purchasing.topSuppliers.map((s) => compactNumber(s.totalSpend)))}
                        tickFormatter={compactNumber}
                      />
                      <Tooltip formatter={(v) => fmt(v)} labelFormatter={(l) => l} />
                      <Bar dataKey="totalSpend" fill="#4a6b95" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </SectionCard>
            </section>
            )}

            {/* ---------- Daily Records ---------- */}
            {visibleKinds.dailyRecords && dailyRecords && (
            <section className="space-y-3">
              <div>
                <h3 className="text-xs font-semibold uppercase tracking-wide text-navy-400">Daily Records</h3>
                <p className="text-xs text-navy-400">
                  Statutory daily sales and purchases records (cash vs. credit) per Rwanda's Tax Procedures Law.
                </p>
              </div>

              {/* Full interactive record — its own Sales/Purchases + Single day/Date range
                  controls, independent of the date range above, so you can drill into a
                  specific day's statutory detail without losing the report-wide period. */}
              <DailyRecordsPanel showExport={false} />

              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                <SectionCard title="Sales — cash vs. credit" icon={DollarSign}>
                  <div className="grid grid-cols-3 gap-2 text-center">
                    <div>
                      <p className="text-xs text-navy-400">Cash</p>
                      <p className="font-bold text-green-700">{fmt(dailyRecords.sales.cashTotal)}</p>
                    </div>
                    <div>
                      <p className="text-xs text-navy-400">Credit</p>
                      <p className="font-bold text-orange-700">{fmt(dailyRecords.sales.creditTotal)}</p>
                    </div>
                    <div>
                      <p className="text-xs text-navy-400">Total</p>
                      <p className="font-bold text-navy-800">{fmt(dailyRecords.sales.grandTotal)}</p>
                    </div>
                  </div>
                </SectionCard>
                <SectionCard title="Purchases — cash vs. credit" icon={Truck}>
                  <div className="grid grid-cols-3 gap-2 text-center">
                    <div>
                      <p className="text-xs text-navy-400">Cash</p>
                      <p className="font-bold text-green-700">{fmt(dailyRecords.purchases.cashTotal)}</p>
                    </div>
                    <div>
                      <p className="text-xs text-navy-400">Credit</p>
                      <p className="font-bold text-orange-700">{fmt(dailyRecords.purchases.creditTotal)}</p>
                    </div>
                    <div>
                      <p className="text-xs text-navy-400">Total</p>
                      <p className="font-bold text-navy-800">{fmt(dailyRecords.purchases.grandTotal)}</p>
                    </div>
                  </div>
                </SectionCard>
              </div>
            </section>
            )}
          </div>
        )
      )}
    </div>
  )
}
