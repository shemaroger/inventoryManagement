import { useEffect, useState } from 'react'
import { Download, RefreshCw } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Button from '../../components/ui/Button'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import Skeleton from '../../components/ui/Skeleton'
import SortableTable from './SortableTable'
import ApproximateNotice from './ApproximateNotice'

export default function InventoryTurnoverReport({ startDate, endDate }) {
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setReport(null)
    setError(null)
    axiosClient
      .get('/analytics/inventory-turnover', { params: { startDate, endDate } })
      .then((res) => {
        if (!cancelled) setReport(res.data.data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load inventory turnover')
      })
    return () => {
      cancelled = true
    }
  }, [startDate, endDate])

  async function handleExport() {
    const res = await axiosClient.get('/analytics/inventory-turnover/export', { params: { startDate, endDate }, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `inventory-turnover-${startDate}-to-${endDate}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  const columns = [
    { key: 'productName', label: 'Product', sortable: true },
    { key: 'productSku', label: 'SKU', sortable: true },
    { key: 'unitsSold', label: 'Units Sold', align: 'right', sortable: true },
    { key: 'averageStock', label: 'Avg Stock', align: 'right', sortable: true },
    { key: 'turnoverRatio', label: 'Turnover Ratio', align: 'right', sortable: true, render: (r) => r.turnoverRatio != null ? Number(r.turnoverRatio).toFixed(2) : '—' },
    { key: 'daysOfStockOnHand', label: 'Days of Stock', align: 'right', sortable: true, render: (r) => r.daysOfStockOnHand != null ? Number(r.daysOfStockOnHand).toFixed(0) : '—' },
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

      {report && report.dataScarce && (
        <ApproximateNotice>
          This period is shorter than 30 days — turnover ratios and days-of-stock estimates will be more reliable with a longer date range.
        </ApproximateNotice>
      )}

      {report && report.lines.length === 0 && (
        <EmptyState icon={RefreshCw} title="No stock data to evaluate" description="Try widening the date range." />
      )}

      {report && report.lines.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
          <SortableTable columns={columns} data={report.lines} rowKey="productId" defaultSortKey="unitsSold" />
        </div>
      )}
    </div>
  )
}
