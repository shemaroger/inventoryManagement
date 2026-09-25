import { useEffect, useState } from 'react'
import { Download, AlertTriangle } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ListStates'
import Skeleton from '../../components/ui/Skeleton'

export default function BalanceSheetPage() {
  const [asOfDate, setAsOfDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    axiosClient
      .get('/reports/balance-sheet', { params: { asOfDate } })
      .then((res) => {
        if (!cancelled) setReport(res.data.data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load balance sheet')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [asOfDate])

  async function handleExport() {
    const res = await axiosClient.get('/reports/balance-sheet/export', { params: { asOfDate }, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `balance-sheet-${asOfDate}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  const liabilitiesPlusEquity = report ? Number(report.totalLiabilities) + Number(report.totalEquity) : 0
  const isBalanced = report && Math.abs(Number(report.totalAssets) - liabilitiesPlusEquity) < 0.005

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Balance Sheet</h2>
          <p className="text-navy-400">Assets, liabilities, and equity as of a single date — accumulated from every journal entry since inception.</p>
        </div>
        <Button variant="secondary" onClick={handleExport} disabled={loading || !report}>
          <Download size={16} /> Export CSV
        </Button>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
        <Input label="As of date" type="date" value={asOfDate} onChange={(e) => setAsOfDate(e.target.value)} className="w-44" />
      </div>

      {error && <ErrorState message={error} />}

      {!error && report && !isBalanced && (
        <div className="flex items-center gap-2 rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-700">
          <AlertTriangle size={16} />
          Assets ({Number(report.totalAssets).toFixed(2)}) do not equal Liabilities + Equity ({liabilitiesPlusEquity.toFixed(2)}) —
          this should never happen if every journal entry is balanced; please investigate.
        </div>
      )}

      {loading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Skeleton className="h-64 rounded-lg" />
          <Skeleton className="h-64 rounded-lg" />
        </div>
      ) : (
        !error &&
        report && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
              <div className="border-b border-navy-100 bg-navy-50 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-navy-500">
                Assets
              </div>
              <table className="w-full text-left text-sm">
                <tbody className="divide-y divide-navy-100">
                  {report.assets.map((l) => (
                    <tr key={l.accountCode}>
                      <td className="px-4 py-2 text-navy-600">{l.accountName}</td>
                      <td className="px-4 py-2 text-right tabular-nums text-navy-800">{Number(l.amount).toFixed(2)}</td>
                    </tr>
                  ))}
                  <tr className="border-t-2 border-navy-200 bg-navy-50">
                    <td className="px-4 py-3 font-bold text-navy-800">Total Assets</td>
                    <td className="px-4 py-3 text-right tabular-nums font-bold text-navy-800">{Number(report.totalAssets).toFixed(2)}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div className="space-y-4">
              <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
                <div className="border-b border-navy-100 bg-navy-50 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-navy-500">
                  Liabilities
                </div>
                <table className="w-full text-left text-sm">
                  <tbody className="divide-y divide-navy-100">
                    {report.liabilities.map((l) => (
                      <tr key={l.accountCode}>
                        <td className="px-4 py-2 text-navy-600">{l.accountName}</td>
                        <td className="px-4 py-2 text-right tabular-nums text-navy-800">{Number(l.amount).toFixed(2)}</td>
                      </tr>
                    ))}
                    <tr className="border-t-2 border-navy-200 bg-navy-50">
                      <td className="px-4 py-3 font-bold text-navy-800">Total Liabilities</td>
                      <td className="px-4 py-3 text-right tabular-nums font-bold text-navy-800">{Number(report.totalLiabilities).toFixed(2)}</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
                <div className="border-b border-navy-100 bg-navy-50 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-navy-500">
                  Equity
                </div>
                <table className="w-full text-left text-sm">
                  <tbody className="divide-y divide-navy-100">
                    {report.equity.map((l) => (
                      <tr key={l.accountCode}>
                        <td className="px-4 py-2 text-navy-600">{l.accountName}</td>
                        <td className="px-4 py-2 text-right tabular-nums text-navy-800">{Number(l.amount).toFixed(2)}</td>
                      </tr>
                    ))}
                    <tr>
                      <td className="px-4 py-2 text-navy-600">Current Earnings</td>
                      <td className="px-4 py-2 text-right tabular-nums text-navy-800">{Number(report.currentEarnings).toFixed(2)}</td>
                    </tr>
                    <tr className="border-t-2 border-navy-200 bg-navy-50">
                      <td className="px-4 py-3 font-bold text-navy-800">Total Equity</td>
                      <td className="px-4 py-3 text-right tabular-nums font-bold text-navy-800">{Number(report.totalEquity).toFixed(2)}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )
      )}
    </div>
  )
}
