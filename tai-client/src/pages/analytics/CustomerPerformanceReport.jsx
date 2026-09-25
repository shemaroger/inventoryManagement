import { useEffect, useState } from 'react'
import { Download, Users } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Button from '../../components/ui/Button'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import Skeleton from '../../components/ui/Skeleton'
import SortableTable from './SortableTable'

function fmt(n) {
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function CustomerPerformanceReport({ startDate, endDate }) {
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setReport(null)
    setError(null)
    axiosClient
      .get('/analytics/customer-performance', { params: { startDate, endDate } })
      .then((res) => {
        if (!cancelled) setReport(res.data.data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load customer performance')
      })
    return () => {
      cancelled = true
    }
  }, [startDate, endDate])

  async function handleExport() {
    const res = await axiosClient.get('/analytics/customer-performance/export', { params: { startDate, endDate }, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `customer-performance-${startDate}-to-${endDate}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  const columns = [
    { key: 'customerName', label: 'Customer', sortable: true },
    { key: 'totalRevenue', label: 'Revenue', align: 'right', sortable: true, render: (r) => fmt(r.totalRevenue) },
    { key: 'saleCount', label: 'Sales', align: 'right', sortable: true },
    {
      key: 'averagePaymentDelayDays', label: 'Avg Payment Delay', align: 'right', sortable: true,
      render: (r) => r.averagePaymentDelayDays != null ? `${Number(r.averagePaymentDelayDays).toFixed(1)}d` : '—',
    },
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

      {report && report.lines.length === 0 && (
        <EmptyState icon={Users} title="No completed sales in this period" description="Try widening the date range." />
      )}

      {report && report.lines.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
          <SortableTable columns={columns} data={report.lines} rowKey="customerId" defaultSortKey="totalRevenue" />
        </div>
      )}
    </div>
  )
}
