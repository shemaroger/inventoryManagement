import { useEffect, useState } from 'react'
import { Download, Tags } from 'lucide-react'
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import axiosClient from '../../api/axiosClient'
import Button from '../../components/ui/Button'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import Skeleton from '../../components/ui/Skeleton'
import SortableTable from './SortableTable'
import ApproximateNotice from './ApproximateNotice'

function fmt(n) {
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const SLICE_COLORS = ['#f97316', '#1e3a5f', '#22c55e', '#a855f7', '#ef4444', '#06b6d4', '#eab308', '#64748b']

export default function ProfitByCategoryReport({ startDate, endDate }) {
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setReport(null)
    setError(null)
    axiosClient
      .get('/analytics/profit-by-category', { params: { startDate, endDate } })
      .then((res) => {
        if (!cancelled) setReport(res.data.data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load profit by category')
      })
    return () => {
      cancelled = true
    }
  }, [startDate, endDate])

  async function handleExport() {
    const res = await axiosClient.get('/analytics/profit-by-category/export', { params: { startDate, endDate }, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `profit-by-category-${startDate}-to-${endDate}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  const columns = [
    { key: 'categoryName', label: 'Category', sortable: true },
    { key: 'revenue', label: 'Revenue', align: 'right', sortable: true, render: (r) => fmt(r.revenue) },
    { key: 'cost', label: 'Cost', align: 'right', sortable: true, render: (r) => fmt(r.cost) },
    { key: 'margin', label: 'Margin', align: 'right', sortable: true, render: (r) => fmt(r.margin) },
    { key: 'marginPercent', label: 'Margin %', align: 'right', sortable: true, render: (r) => `${Number(r.marginPercent).toFixed(1)}%` },
  ]

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button variant="secondary" onClick={handleExport} disabled={!report}>
          <Download size={16} /> Export CSV
        </Button>
      </div>

      {error && <ErrorState message={error} />}
      {!report && !error && <Skeleton className="h-72 rounded-lg" />}

      {report && report.isApproximate && (
        <ApproximateNotice>
          Cost is rolled up from each product's <strong>current</strong> cost price, not historical cost at time of sale.
          Treat these margins as approximate.
        </ApproximateNotice>
      )}

      {report && report.lines.length === 0 && (
        <EmptyState icon={Tags} title="No completed sales in this period" description="Try widening the date range." />
      )}

      {report && report.lines.length > 0 && (
        <>
          <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
            <p className="mb-2 text-xs text-navy-400">Share of revenue by category</p>
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie
                  data={report.lines}
                  dataKey="revenue"
                  nameKey="categoryName"
                  innerRadius="55%"
                  outerRadius="80%"
                  paddingAngle={2}
                >
                  {report.lines.map((entry, i) => (
                    <Cell key={entry.categoryId} fill={SLICE_COLORS[i % SLICE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(value) => fmt(value)} contentStyle={{ fontSize: 12, borderRadius: 8, borderColor: '#e5e9f0' }} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
              </PieChart>
            </ResponsiveContainer>
          </div>

          <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
            <SortableTable columns={columns} data={report.lines} rowKey="categoryId" defaultSortKey="margin" />
          </div>
        </>
      )}
    </div>
  )
}
