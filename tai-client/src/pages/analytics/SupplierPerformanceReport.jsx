import { useEffect, useState } from 'react'
import { Download, Truck } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Button from '../../components/ui/Button'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import Skeleton from '../../components/ui/Skeleton'
import SortableTable from './SortableTable'
import ApproximateNotice from './ApproximateNotice'

function fmt(n) {
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function SupplierPerformanceReport({ startDate, endDate }) {
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setReport(null)
    setError(null)
    axiosClient
      .get('/analytics/supplier-performance', { params: { startDate, endDate } })
      .then((res) => {
        if (!cancelled) setReport(res.data.data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load supplier performance')
      })
    return () => {
      cancelled = true
    }
  }, [startDate, endDate])

  async function handleExport() {
    const res = await axiosClient.get('/analytics/supplier-performance/export', { params: { startDate, endDate }, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `supplier-performance-${startDate}-to-${endDate}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  const columns = [
    { key: 'supplierName', label: 'Supplier', sortable: true },
    { key: 'totalSpend', label: 'Total Spend', align: 'right', sortable: true, render: (r) => fmt(r.totalSpend) },
    { key: 'purchaseOrderCount', label: 'POs', align: 'right', sortable: true },
    {
      key: 'averageLeadTimeDays', label: 'Avg Lead Time', align: 'right', sortable: true,
      render: (r) => r.averageLeadTimeDays != null ? `${Number(r.averageLeadTimeDays).toFixed(1)}d` : '—',
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

      {report && report.lines.some((l) => l.isApproximateLeadTime) && (
        <ApproximateNotice>
          Lead time is measured from PO creation to full receipt, not true submission date — purchase orders don't track a
          separate submission timestamp, so this is an approximation.
        </ApproximateNotice>
      )}

      {report && report.lines.length === 0 && (
        <EmptyState icon={Truck} title="No purchase orders in this period" description="Try widening the date range." />
      )}

      {report && report.lines.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
          <SortableTable columns={columns} data={report.lines} rowKey="supplierId" defaultSortKey="totalSpend" />
        </div>
      )}
    </div>
  )
}
