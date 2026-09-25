import { useEffect, useState } from 'react'
import { Download, DollarSign } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Button from '../../components/ui/Button'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import Skeleton from '../../components/ui/Skeleton'
import SortableTable from './SortableTable'
import ApproximateNotice from './ApproximateNotice'

function fmt(n) {
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function ProfitByProductReport({ startDate, endDate }) {
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setReport(null)
    setError(null)
    axiosClient
      .get('/analytics/profit-by-product', { params: { startDate, endDate } })
      .then((res) => {
        if (!cancelled) setReport(res.data.data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load profit by product')
      })
    return () => {
      cancelled = true
    }
  }, [startDate, endDate])

  async function handleExport() {
    const res = await axiosClient.get('/analytics/profit-by-product/export', { params: { startDate, endDate }, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `profit-by-product-${startDate}-to-${endDate}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  const columns = [
    { key: 'productName', label: 'Product', sortable: true },
    { key: 'productSku', label: 'SKU', sortable: true },
    { key: 'categoryName', label: 'Category', sortable: true, render: (r) => r.categoryName || '—' },
    { key: 'quantitySold', label: 'Qty Sold', align: 'right', sortable: true },
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
          Cost is based on each product's <strong>current</strong> cost price, not the historical cost at time of sale —
          connect per-product cost tracking in the General Ledger for exact historical margins. Treat these figures as approximate.
        </ApproximateNotice>
      )}

      {report && report.lines.length === 0 && (
        <EmptyState icon={DollarSign} title="No completed sales in this period" description="Try widening the date range." />
      )}

      {report && report.lines.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
          <SortableTable columns={columns} data={report.lines} rowKey="productId" defaultSortKey="margin" />
          <div className="flex justify-end gap-6 border-t border-navy-100 bg-navy-50 px-4 py-3 text-sm">
            <span className="text-navy-500">Total Revenue: <strong className="text-navy-800">{fmt(report.totalRevenue)}</strong></span>
            <span className="text-navy-500">Total Cost: <strong className="text-navy-800">{fmt(report.totalCost)}</strong></span>
            <span className="text-navy-500">Total Margin: <strong className="text-navy-800">{fmt(report.totalMargin)}</strong></span>
          </div>
        </div>
      )}
    </div>
  )
}
