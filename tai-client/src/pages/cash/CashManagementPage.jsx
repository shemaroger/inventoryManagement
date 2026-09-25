import { useEffect, useState } from 'react'
import { Plus, Wallet } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Badge from '../../components/ui/Badge'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'

const emptyForm = { date: new Date().toISOString().slice(0, 10), description: '', expenseAccountId: '', amount: '', paymentSource: 'CASH' }

export default function CashManagementPage() {
  const { showToast } = useToast()

  const [expenseAccounts, setExpenseAccounts] = useState([])
  const [form, setForm] = useState({ ...emptyForm })
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const [entries, setEntries] = useState([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    axiosClient.get('/accounts').then((res) => {
      setExpenseAccounts(res.data.data.filter((a) => a.accountType === 'EXPENSE' && a.active))
    }).catch(() => {})
  }, [])

  async function loadEntries() {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.get('/cash/expenditures', { params: { page, size: 10 } })
      const pageData = res.data.data
      setEntries(pageData.content)
      setTotalPages(pageData.totalPages)
      setTotalElements(pageData.totalElements)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load expenditures')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadEntries()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page])

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  function validate() {
    const next = {}
    if (!form.description.trim()) next.description = 'Description is required'
    if (!form.expenseAccountId) next.expenseAccountId = 'Choose an expense account'
    if (!form.amount || Number(form.amount) <= 0) next.amount = 'Enter a valid amount'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!validate()) return

    setSaving(true)
    try {
      await axiosClient.post('/cash/expenditures', {
        date: form.date,
        description: form.description.trim(),
        expenseAccountId: Number(form.expenseAccountId),
        amount: Number(form.amount),
        paymentSource: form.paymentSource,
      })
      showToast('Expenditure recorded', { type: 'success' })
      setForm({ ...emptyForm })
      setPage(0)
      loadEntries()
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to record expenditure', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-navy-800">Cash Management</h2>
        <p className="text-navy-400">Record cash or bank expenditures — posts the matching accounting entry automatically.</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4 rounded-lg border border-navy-100 bg-white p-6 shadow-sm">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input label="Date" type="date" value={form.date} onChange={(e) => update('date', e.target.value)} />
          <Select label="Paid from" value={form.paymentSource} onChange={(e) => update('paymentSource', e.target.value)}>
            <option value="CASH">Cash</option>
            <option value="BANK">Bank</option>
          </Select>
          <Select
            label="Expense category"
            value={form.expenseAccountId}
            onChange={(e) => update('expenseAccountId', e.target.value)}
            error={errors.expenseAccountId}
          >
            <option value="">— Select —</option>
            {expenseAccounts.map((a) => (
              <option key={a.id} value={a.id}>{a.name}</option>
            ))}
          </Select>
          <Input
            label="Amount"
            type="number"
            step="0.01"
            min="0"
            value={form.amount}
            onChange={(e) => update('amount', e.target.value)}
            error={errors.amount}
          />
        </div>
        <Input
          label="Description"
          value={form.description}
          onChange={(e) => update('description', e.target.value)}
          error={errors.description}
          placeholder="e.g. Fuel for delivery truck"
        />
        <div className="flex justify-end border-t border-navy-100 pt-4">
          <Button type="submit" loading={saving}>
            <Plus size={16} /> Record expenditure
          </Button>
        </div>
      </form>

      {error && <ErrorState message={error} />}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="border-b border-navy-100 px-4 py-3">
          <h3 className="text-sm font-semibold text-navy-700">Recent expenditures</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                <th className="px-4 py-3 font-medium">Date</th>
                <th className="px-4 py-3 font-medium">Description</th>
                <th className="px-4 py-3 font-medium">Category</th>
                <th className="px-4 py-3 font-medium">Paid from</th>
                <th className="px-4 py-3 font-medium text-right">Amount</th>
              </tr>
            </thead>
            {loading ? (
              <SkeletonRows rows={5} columns={5} />
            ) : (
              !error &&
              entries.length > 0 && (
                <tbody className="divide-y divide-navy-100">
                  {entries.map((e) => {
                    const expenseLine = e.lines.find((l) => Number(l.debitAmount) > 0)
                    const sourceLine = e.lines.find((l) => Number(l.creditAmount) > 0)
                    return (
                      <tr key={e.id}>
                        <td className="px-4 py-3 text-navy-600">{e.entryDate}</td>
                        <td className="px-4 py-3 font-medium text-navy-800">{e.description}</td>
                        <td className="px-4 py-3 text-navy-600">{expenseLine?.accountName || '—'}</td>
                        <td className="px-4 py-3">
                          <Badge variant={sourceLine?.accountCode === '1000' ? 'success' : 'navy'}>
                            {sourceLine?.accountName || '—'}
                          </Badge>
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums font-medium text-navy-800">
                          {Number(e.totalDebit).toFixed(2)}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              )
            )}
          </table>

          {!loading && !error && entries.length === 0 && (
            <EmptyState icon={Wallet} title="No expenditures recorded yet" description="Record your first cash or bank expenditure above." />
          )}
        </div>

        {!loading && !error && totalElements > 0 && (
          <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
        )}
      </div>
    </div>
  )
}
