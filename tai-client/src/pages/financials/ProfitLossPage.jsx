import { useEffect, useState } from 'react'
import { Download } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ListStates'
import Skeleton from '../../components/ui/Skeleton'

function firstOfMonth() {
  const d = new Date()
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10)
}

export default function ProfitLossPage() {
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
      .get('/reports/profit-loss', { params: { startDate, endDate } })
      .then((res) => {
        if (!cancelled) setReport(res.data.data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load P&L report')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [startDate, endDate])

  async function handleExport() {
    const res = await axiosClient.get('/reports/profit-loss/export', { params: { startDate, endDate }, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `profit-loss-${startDate}-to-${endDate}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  const isProfit = report && Number(report.netIncome) >= 0

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Profit &amp; Loss</h2>
          <p className="text-navy-400">Revenue and expenses for the selected period, from the General Ledger.</p>
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
        <Skeleton className="h-64 rounded-lg" />
      ) : (
        !error &&
        report && (
          <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
            <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <tbody className="divide-y divide-navy-100">
                <tr className="bg-navy-50">
                  <td colSpan={2} className="px-4 py-2 text-xs font-semibold uppercase tracking-wide text-navy-500">Revenue</td>
                </tr>
                {report.revenue.map((l) => (
                  <tr key={l.accountCode}>
                    <td className="px-4 py-2 text-navy-600">{l.accountName}</td>
                    <td className="px-4 py-2 text-right tabular-nums text-navy-800">{Number(l.amount).toFixed(2)}</td>
                  </tr>
                ))}
                <tr className="border-t border-navy-200">
                  <td className="px-4 py-2 font-medium text-navy-700">Total Revenue</td>
                  <td className="px-4 py-2 text-right tabular-nums font-medium text-navy-800">{Number(report.totalRevenue).toFixed(2)}</td>
                </tr>

                <tr className="bg-navy-50">
                  <td colSpan={2} className="px-4 py-2 text-xs font-semibold uppercase tracking-wide text-navy-500">Expenses</td>
                </tr>
                {report.expenses.map((l) => (
                  <tr key={l.accountCode}>
                    <td className="px-4 py-2 text-navy-600">{l.accountName}</td>
                    <td className="px-4 py-2 text-right tabular-nums text-navy-800">{Number(l.amount).toFixed(2)}</td>
                  </tr>
                ))}
                <tr className="border-t border-navy-200">
                  <td className="px-4 py-2 font-medium text-navy-700">Total Expenses</td>
                  <td className="px-4 py-2 text-right tabular-nums font-medium text-navy-800">{Number(report.totalExpenses).toFixed(2)}</td>
                </tr>

                <tr className={`border-t-2 ${isProfit ? 'bg-green-50' : 'bg-orange-50'}`}>
                  <td className={`px-4 py-3 font-bold ${isProfit ? 'text-green-700' : 'text-orange-700'}`}>
                    Net {isProfit ? 'Profit' : 'Loss'}
                  </td>
                  <td className={`px-4 py-3 text-right tabular-nums font-bold ${isProfit ? 'text-green-700' : 'text-orange-700'}`}>
                    {Number(report.netIncome).toFixed(2)}
                  </td>
                </tr>
              </tbody>
            </table>
            </div>
          </div>
        )
      )}
    </div>
  )
}
