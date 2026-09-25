import { useEffect, useState } from 'react'
import { Download, Percent, AlertTriangle } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ListStates'
import Skeleton from '../../components/ui/Skeleton'

function firstOfMonth() {
  const d = new Date()
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10)
}

export default function VatReportPage() {
  const [startDate, setStartDate] = useState(firstOfMonth())
  const [endDate, setEndDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    axiosClient
      .get('/reports/vat', { params: { startDate, endDate } })
      .then((res) => {
        if (!cancelled) setReport(res.data.data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load VAT report')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [startDate, endDate])

  async function handleExport() {
    const res = await axiosClient.get('/reports/vat/export', { params: { startDate, endDate }, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `vat-report-${startDate}-to-${endDate}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  const isRefund = report && Number(report.netVatPayable) < 0

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">VAT Report</h2>
          <p className="text-navy-400">Output VAT collected on sales, input VAT reclaimable on purchases, and the net amount owed to RRA — derived directly from the General Ledger's VAT control accounts.</p>
        </div>
        <Button variant="secondary" onClick={handleExport} disabled={loading || !report}>
          <Download size={16} /> Export CSV
        </Button>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
        <Input label="Start date" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-44" />
        <Input label="End date" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-44" />
      </div>

      {error && <ErrorState message={error} />}

      {loading ? (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-24 rounded-lg" />
          ))}
        </div>
      ) : (
        !error &&
        report && (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-navy-400">Output VAT (on sales)</p>
              <p className="mt-1 text-2xl font-bold text-navy-800">{Number(report.outputVat).toFixed(2)}</p>
            </div>
            <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-navy-400">Input VAT (on purchases)</p>
              <p className="mt-1 text-2xl font-bold text-navy-800">{Number(report.inputVat).toFixed(2)}</p>
            </div>
            <div className={`rounded-lg border p-4 shadow-sm ${isRefund ? 'border-green-200 bg-green-50' : 'border-orange-200 bg-orange-50'}`}>
              <p className="text-xs font-medium uppercase tracking-wide text-navy-400">
                {isRefund ? 'Net VAT refundable' : 'Net VAT payable to RRA'}
              </p>
              <p className={`mt-1 flex items-center gap-1 text-2xl font-bold ${isRefund ? 'text-green-700' : 'text-orange-700'}`}>
                {!isRefund && Number(report.netVatPayable) > 0 && <AlertTriangle size={18} />}
                {Math.abs(Number(report.netVatPayable)).toFixed(2)}
              </p>
            </div>
          </div>
        )
      )}

      {!loading && !error && report && Number(report.outputVat) === 0 && Number(report.inputVat) === 0 && (
        <p className="flex items-center gap-2 text-sm text-navy-400">
          <Percent size={14} /> No VAT activity posted for this period yet.
        </p>
      )}
    </div>
  )
}
