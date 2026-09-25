import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, BookOpen } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Input from '../../components/ui/Input'
import Table from '../../components/ui/Table'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'

export default function AccountLedgerPage() {
  const { id } = useParams()
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState(() => new Date().toISOString().slice(0, 10))

  const [ledger, setLedger] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    setLedger(null)
    axiosClient
      .get(`/accounts/${id}/ledger`, { params: { startDate: startDate || undefined, endDate } })
      .then((res) => {
        if (!cancelled) setLedger(res.data.data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load ledger')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [id, startDate, endDate])

  const columns = [
    { key: 'date', label: 'Date' },
    { key: 'description', label: 'Description' },
    { key: 'reference', label: 'Reference', render: (r) => r.reference || '—' },
    { key: 'debit', label: 'Debit', align: 'right', render: (r) => (Number(r.debit) > 0 ? Number(r.debit).toFixed(2) : '—') },
    { key: 'credit', label: 'Credit', align: 'right', render: (r) => (Number(r.credit) > 0 ? Number(r.credit).toFixed(2) : '—') },
    { key: 'runningBalance', label: 'Balance', align: 'right', render: (r) => Number(r.runningBalance).toFixed(2) },
  ]

  return (
    <div className="space-y-6">
      <div>
        <Link to="/accounts" className="inline-flex items-center gap-1 text-sm text-navy-500 hover:text-navy-700">
          <ArrowLeft size={14} /> Back to chart of accounts
        </Link>
        <h2 className="mt-2 text-2xl font-bold text-navy-800">{ledger ? `${ledger.accountName} — Ledger` : 'General Ledger'}</h2>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
        <Input label="Start date" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-44" />
        <Input label="End date" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-44" />
      </div>

      {error && <ErrorState message={error} />}

      {!error && ledger && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-navy-400">Opening balance</p>
            <p className="mt-1 text-xl font-bold text-navy-800">{Number(ledger.openingBalance).toFixed(2)}</p>
          </div>
          <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-navy-400">Closing balance</p>
            <p className="mt-1 text-xl font-bold text-navy-800">{Number(ledger.closingBalance).toFixed(2)}</p>
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
          ) : !error && ledger?.lines.length > 0 ? (
            <Table columns={columns} data={ledger.lines.map((l, i) => ({ ...l, _rowId: i }))} rowKey="_rowId" />
          ) : (
            !error && (
              <EmptyState
                icon={BookOpen}
                title="No activity in this period"
                description="No journal entries have posted to this account for the selected date range."
              />
            )
          )}
        </div>
      </div>
    </div>
  )
}
