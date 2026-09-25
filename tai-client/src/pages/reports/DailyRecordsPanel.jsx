import { useEffect, useState } from 'react'
import { Download, FileText } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Badge from '../../components/ui/Badge'
import Table from '../../components/ui/Table'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'

const today = () => new Date().toISOString().slice(0, 10)

// Self-contained Sales/Purchases × Single-day/Date-range statutory record — used standalone on
// the Daily Records page and embedded inside the Business Report's Daily Records section, so
// both places share one implementation instead of two copies drifting apart.
export default function DailyRecordsPanel({ showExport = true }) {
  const [view, setView] = useState('sales') // 'sales' | 'purchases'
  const [mode, setMode] = useState('day') // 'day' | 'range'
  const [date, setDate] = useState(today())
  const [startDate, setStartDate] = useState(today())
  const [endDate, setEndDate] = useState(today())

  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [profit, setProfit] = useState(null)
  const [profitError, setProfitError] = useState(null)

  async function load() {
    setLoading(true)
    setError(null)
    setReport(null)
    try {
      const base = view === 'sales' ? '/reports/daily-sales' : '/reports/daily-purchases'
      const url = mode === 'day' ? base : `${base}/range`
      const params = mode === 'day' ? { date } : { startDate, endDate }
      const res = await axiosClient.get(url, { params })
      setReport(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load report')
    } finally {
      setLoading(false)
    }
  }

  // Profit for the same date/range, independent of the Sales/Purchases tab — reuses the same
  // revenue-minus-expenses P&L logic as the Profit & Loss report and the dashboard's gross
  // margin figure, rather than approximating it from just this panel's cash/credit totals.
  async function loadProfit() {
    setProfitError(null)
    try {
      const periodStart = mode === 'day' ? date : startDate
      const periodEnd = mode === 'day' ? date : endDate
      const res = await axiosClient.get('/reports/profit-loss', {
        params: { startDate: periodStart, endDate: periodEnd },
      })
      setProfit(res.data.data)
    } catch (err) {
      setProfitError(err.response?.data?.message || 'Failed to load profit')
    }
  }

  useEffect(() => {
    load()
    loadProfit()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, mode, date, startDate, endDate])

  async function handleExport() {
    // Only single-day export exists on the backend; use the day currently selected
    // (or the range's start date as a reasonable fallback) rather than disabling export in range mode.
    const exportDate = mode === 'day' ? date : startDate
    const base = view === 'sales' ? '/reports/daily-sales/export' : '/reports/daily-purchases/export'
    const res = await axiosClient.get(base, { params: { date: exportDate }, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `${view === 'sales' ? 'daily-sales' : 'daily-purchases'}-${exportDate}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  const isSales = view === 'sales'
  const cashRows = (isSales ? report?.cashSales : report?.cashPurchases) ?? []
  const creditRows = (isSales ? report?.creditSales : report?.creditPurchases) ?? []
  const allRows = [
    ...cashRows.map((r) => ({ ...r, _cashOrCredit: 'CASH' })),
    ...creditRows.map((r) => ({ ...r, _cashOrCredit: 'CREDIT' })),
  ]

  const columns = isSales
    ? [
        { key: 'saleId', label: 'Sale #', render: (r) => `#${r.saleId}` },
        { key: 'saleDate', label: 'Date' },
        { key: 'customerName', label: 'Customer' },
        {
          key: '_cashOrCredit',
          label: 'Cash / Credit',
          render: (r) => <Badge variant={r._cashOrCredit === 'CASH' ? 'success' : 'orange'}>{r._cashOrCredit}</Badge>,
        },
        { key: 'amount', label: 'Amount', align: 'right', render: (r) => Number(r.amount).toFixed(2) },
      ]
    : [
        { key: 'purchaseOrderId', label: 'PO #', render: (r) => `#${r.purchaseOrderId}` },
        { key: 'receivedDate', label: 'Received' },
        { key: 'supplierName', label: 'Supplier' },
        {
          key: '_cashOrCredit',
          label: 'Cash / Credit',
          render: (r) => <Badge variant={r._cashOrCredit === 'CASH' ? 'success' : 'orange'}>{r._cashOrCredit}</Badge>,
        },
        { key: 'amountReceived', label: 'Amount', align: 'right', render: (r) => Number(r.amountReceived).toFixed(2) },
      ]

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
        <div className="flex rounded-md border border-navy-200 p-0.5">
          <button
            type="button"
            onClick={() => setView('sales')}
            className={`rounded px-3 py-1.5 text-sm font-medium transition-colors duration-150 ${
              view === 'sales' ? 'bg-orange-500 text-white' : 'text-navy-600 hover:bg-navy-50'
            }`}
          >
            Sales
          </button>
          <button
            type="button"
            onClick={() => setView('purchases')}
            className={`rounded px-3 py-1.5 text-sm font-medium transition-colors duration-150 ${
              view === 'purchases' ? 'bg-orange-500 text-white' : 'text-navy-600 hover:bg-navy-50'
            }`}
          >
            Purchases
          </button>
        </div>

        <div className="flex rounded-md border border-navy-200 p-0.5">
          <button
            type="button"
            onClick={() => setMode('day')}
            className={`rounded px-3 py-1.5 text-sm font-medium transition-colors duration-150 ${
              mode === 'day' ? 'bg-navy-700 text-white' : 'text-navy-600 hover:bg-navy-50'
            }`}
          >
            Single day
          </button>
          <button
            type="button"
            onClick={() => setMode('range')}
            className={`rounded px-3 py-1.5 text-sm font-medium transition-colors duration-150 ${
              mode === 'range' ? 'bg-navy-700 text-white' : 'text-navy-600 hover:bg-navy-50'
            }`}
          >
            Date range
          </button>
        </div>

        {mode === 'day' ? (
          <Input label="Date" type="date" value={date} onChange={(e) => setDate(e.target.value)} className="w-44" />
        ) : (
          <>
            <Input label="Start date" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-44" />
            <Input label="End date" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-44" />
          </>
        )}

        {showExport && (
          <Button variant="secondary" onClick={handleExport} disabled={loading || !report} className="ml-auto">
            <Download size={16} /> Export CSV
          </Button>
        )}
      </div>

      {error && <ErrorState message={error} />}

      {!error && report && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-navy-400">Cash total</p>
            <p className="mt-1 text-xl font-bold text-green-700">{Number(report.cashTotal).toFixed(2)}</p>
          </div>
          <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-navy-400">Credit total</p>
            <p className="mt-1 text-xl font-bold text-orange-700">{Number(report.creditTotal).toFixed(2)}</p>
          </div>
          <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-navy-400">Grand total</p>
            <p className="mt-1 text-xl font-bold text-navy-800">{Number(report.grandTotal).toFixed(2)}</p>
          </div>
          <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-navy-400">
              Profit {mode === 'day' ? '(day)' : '(period)'}
            </p>
            {profitError ? (
              <p className="mt-1 text-sm text-navy-300">Not available to your role</p>
            ) : profit ? (
              <p className={`mt-1 text-xl font-bold ${Number(profit.netIncome) >= 0 ? 'text-green-700' : 'text-red-600'}`}>
                {Number(profit.netIncome).toFixed(2)}
              </p>
            ) : (
              <p className="mt-1 text-xl font-bold text-navy-300">—</p>
            )}
          </div>
        </div>
      )}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          {loading ? (
            <table className="w-full text-left text-sm">
              <thead className="bg-navy-50 text-navy-500">
                <tr>
                  {columns.map((c) => (
                    <th key={c.key} className="px-4 py-3 font-medium">{c.label}</th>
                  ))}
                </tr>
              </thead>
              <SkeletonRows rows={5} columns={columns.length} />
            </table>
          ) : !error && allRows.length > 0 ? (
            <Table columns={columns} data={allRows} rowKey={isSales ? 'saleId' : 'purchaseOrderId'} />
          ) : (
            !error && (
              <EmptyState
                icon={FileText}
                title={`No ${isSales ? 'completed sales' : 'goods received'} for this ${mode === 'day' ? 'day' : 'period'}`}
                description={
                  isSales
                    ? 'Only COMPLETED sales appear in the statutory record.'
                    : 'Purchases are recorded on the day goods were actually received, not when the order was placed.'
                }
              />
            )
          )}
        </div>
      </div>
    </div>
  )
}
