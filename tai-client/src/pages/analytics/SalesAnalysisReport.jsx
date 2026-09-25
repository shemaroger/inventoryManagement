import { useEffect, useState } from 'react'
import { Download, TrendingUp } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import axiosClient from '../../api/axiosClient'
import Button from '../../components/ui/Button'
import Select from '../../components/ui/Select'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import Skeleton from '../../components/ui/Skeleton'
import SortableTable from './SortableTable'

function fmt(n) {
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function measuredAxisWidth(values, { minWidth = 40, maxWidth = 100, charWidth = 7, padding = 16 } = {}) {
  const longest = values.reduce((max, v) => Math.max(max, String(v ?? '').length), 0)
  return Math.min(maxWidth, Math.max(minWidth, longest * charWidth + padding))
}

export default function SalesAnalysisReport({ startDate, endDate }) {
  const [groupBy, setGroupBy] = useState('day')
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setReport(null)
    setError(null)
    axiosClient
      .get('/analytics/sales', { params: { startDate, endDate, groupBy } })
      .then((res) => {
        if (!cancelled) setReport(res.data.data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load sales analysis')
      })
    return () => {
      cancelled = true
    }
  }, [startDate, endDate, groupBy])

  async function handleExport() {
    const res = await axiosClient.get('/analytics/sales/export', { params: { startDate, endDate, groupBy }, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `sales-analysis-${startDate}-to-${endDate}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  const columns = [
    { key: 'bucketLabel', label: 'Period', sortable: true },
    { key: 'saleCount', label: 'Sales', align: 'right', sortable: true },
    { key: 'total', label: 'Total', align: 'right', sortable: true, render: (r) => fmt(r.total) },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Select value={groupBy} onChange={(e) => setGroupBy(e.target.value)} className="w-40">
          <option value="day">By day</option>
          <option value="week">By week</option>
          <option value="month">By month</option>
        </Select>
        <Button variant="secondary" onClick={handleExport} disabled={!report}>
          <Download size={16} /> Export CSV
        </Button>
      </div>

      {error && <ErrorState message={error} />}
      {!report && !error && <Skeleton className="h-72 rounded-lg" />}

      {report && report.points.length === 0 && (
        <EmptyState icon={TrendingUp} title="No completed sales in this period" description="Try widening the date range." />
      )}

      {report && report.points.length > 0 && (
        <>
          <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
            <p className="mb-2 text-sm text-navy-500">
              Grand total: <span className="font-semibold text-navy-800">{fmt(report.grandTotal)}</span>
            </p>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={report.points} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e5e9f0" />
                <XAxis dataKey="bucketLabel" tick={{ fontSize: 11, fill: '#8996ac' }} interval="preserveStartEnd" />
                <YAxis
                  tick={{ fontSize: 11, fill: '#8996ac' }}
                  width={measuredAxisWidth(report.points.map((p) => fmt(p.total)))}
                />
                <Tooltip formatter={(value) => [fmt(value), 'Sales']} contentStyle={{ fontSize: 12, borderRadius: 8, borderColor: '#e5e9f0' }} />
                <Bar dataKey="total" fill="#f97316" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
            <SortableTable columns={columns} data={report.points} rowKey="bucketStart" defaultSortKey="bucketStart" defaultSortDir="asc" />
          </div>
        </>
      )}
    </div>
  )
}
