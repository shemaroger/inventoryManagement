import { useEffect, useState } from 'react'
import { CheckCircle2, Circle, Landmark, AlertTriangle } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useToast } from '../../components/ui/ToastProvider'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'

// Manual match-and-flag reconciliation: no statement import, just checking each ledger line off
// against the physical bank/cash statement and comparing the reconciled total to what the
// statement says the balance should be.
export default function BankReconciliationPage() {
  const { showToast } = useToast()

  const [accounts, setAccounts] = useState([])
  const [accountId, setAccountId] = useState('')
  const [statementBalance, setStatementBalance] = useState('')

  const [ledger, setLedger] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [togglingId, setTogglingId] = useState(null)

  useEffect(() => {
    axiosClient.get('/accounts').then((res) => {
      const cashLike = res.data.data.filter((a) => a.code === '1000' || a.code === '1010')
      setAccounts(cashLike)
      if (cashLike.length > 0) setAccountId(String(cashLike.find((a) => a.code === '1010')?.id ?? cashLike[0].id))
    }).catch(() => {})
  }, [])

  async function loadLedger() {
    if (!accountId) return
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.get(`/accounts/${accountId}/ledger`)
      setLedger(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load ledger')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadLedger()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountId])

  async function toggleReconciled(line) {
    setTogglingId(line.lineId)
    try {
      const action = line.reconciled ? 'unreconcile' : 'reconcile'
      await axiosClient.patch(`/accounts/ledger-lines/${line.lineId}/${action}`)
      setLedger((prev) => ({
        ...prev,
        lines: prev.lines.map((l) => (l.lineId === line.lineId ? { ...l, reconciled: !l.reconciled } : l)),
      }))
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to update reconciliation status', { type: 'error' })
    } finally {
      setTogglingId(null)
    }
  }

  const reconciledTotal = ledger
    ? (ledger.openingBalance ?? 0) + ledger.lines.filter((l) => l.reconciled).reduce((sum, l) => sum + Number(l.netAmount), 0)
    : 0
  const unreconciledCount = ledger ? ledger.lines.filter((l) => !l.reconciled).length : 0
  const statementDiff = statementBalance !== '' ? Number(statementBalance) - reconciledTotal : null

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-navy-800">Bank Reconciliation</h2>
        <p className="text-navy-400">Check each ledger line off against your bank/cash statement, then compare totals.</p>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
        <Select label="Account" value={accountId} onChange={(e) => setAccountId(e.target.value)} className="w-48">
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>{a.code} — {a.name}</option>
          ))}
        </Select>
        <Input
          label="Statement balance (per bank/cash record)"
          type="number"
          step="0.01"
          value={statementBalance}
          onChange={(e) => setStatementBalance(e.target.value)}
          className="w-64"
        />
      </div>

      {error && <ErrorState message={error} />}

      {!error && ledger && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-navy-400">Reconciled balance</p>
            <p className="mt-1 text-xl font-bold text-navy-800">{reconciledTotal.toFixed(2)}</p>
          </div>
          <div className="rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-navy-400">Ledger balance (all lines)</p>
            <p className="mt-1 text-xl font-bold text-navy-800">{Number(ledger.closingBalance).toFixed(2)}</p>
          </div>
          <div className={`rounded-lg border p-4 shadow-sm ${
            statementDiff === null ? 'border-navy-100 bg-white' : Math.abs(statementDiff) < 0.005 ? 'border-green-200 bg-green-50' : 'border-orange-200 bg-orange-50'
          }`}>
            <p className="text-xs font-medium uppercase tracking-wide text-navy-400">Difference vs. statement</p>
            <p className={`mt-1 flex items-center gap-1 text-xl font-bold ${
              statementDiff === null ? 'text-navy-800' : Math.abs(statementDiff) < 0.005 ? 'text-green-700' : 'text-orange-700'
            }`}>
              {statementDiff !== null && Math.abs(statementDiff) >= 0.005 && <AlertTriangle size={18} />}
              {statementDiff === null ? '—' : statementDiff.toFixed(2)}
            </p>
          </div>
        </div>
      )}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-navy-100 px-4 py-3">
          <h3 className="text-sm font-semibold text-navy-700">Ledger lines</h3>
          {ledger && <span className="text-xs text-navy-400">{unreconciledCount} unreconciled</span>}
        </div>
        <div className="overflow-x-auto">
          {loading ? (
            <table className="w-full text-left text-sm">
              <SkeletonRows rows={5} columns={6} />
            </table>
          ) : !error && ledger?.lines.length > 0 ? (
            <table className="w-full text-left text-sm">
              <thead className="bg-navy-50 text-navy-500">
                <tr>
                  <th className="px-4 py-3 font-medium">Reconciled</th>
                  <th className="px-4 py-3 font-medium">Date</th>
                  <th className="px-4 py-3 font-medium">Description</th>
                  <th className="px-4 py-3 font-medium text-right">Debit</th>
                  <th className="px-4 py-3 font-medium text-right">Credit</th>
                  <th className="px-4 py-3 font-medium text-right">Balance</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-navy-100">
                {ledger.lines.map((l) => (
                  <tr key={l.lineId} className={l.reconciled ? 'bg-green-50/40' : ''}>
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        onClick={() => toggleReconciled(l)}
                        disabled={togglingId === l.lineId}
                        aria-label={l.reconciled ? 'Mark unreconciled' : 'Mark reconciled'}
                        className="text-navy-400 hover:text-orange-600 disabled:opacity-50"
                      >
                        {l.reconciled ? <CheckCircle2 size={18} className="text-green-600" /> : <Circle size={18} />}
                      </button>
                    </td>
                    <td className="px-4 py-3 text-navy-600">{l.date}</td>
                    <td className="px-4 py-3 text-navy-800">{l.description}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-navy-600">
                      {Number(l.debit) > 0 ? Number(l.debit).toFixed(2) : '—'}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums text-navy-600">
                      {Number(l.credit) > 0 ? Number(l.credit).toFixed(2) : '—'}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums font-medium text-navy-800">
                      {Number(l.runningBalance).toFixed(2)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            !error && <EmptyState icon={Landmark} title="No activity to reconcile" description="This account has no journal entries posted yet." />
          )}
        </div>
      </div>
    </div>
  )
}
