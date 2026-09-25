import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { ArrowLeft, Plus, Trash2, CheckCircle2, AlertTriangle } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import SearchableSelect from '../../components/ui/SearchableSelect'

const emptyLine = { accountId: '', debitAmount: '', creditAmount: '' }

export default function JournalEntryFormPage() {
  const navigate = useNavigate()
  const { showToast } = useToast()

  const [accounts, setAccounts] = useState([])
  const [entryDate, setEntryDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [description, setDescription] = useState('')
  const [reference, setReference] = useState('')
  const [lines, setLines] = useState([{ ...emptyLine }, { ...emptyLine }])
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    axiosClient.get('/accounts').then((res) => setAccounts(res.data.data.filter((a) => a.active))).catch(() => {})
  }, [])

  function updateLine(index, field, value) {
    setLines((prev) =>
      prev.map((line, i) => {
        if (i !== index) return line
        const next = { ...line, [field]: value }
        // Entering an amount in one column clears the other — a line is debit XOR credit, never both.
        if (field === 'debitAmount' && value !== '') next.creditAmount = ''
        if (field === 'creditAmount' && value !== '') next.debitAmount = ''
        return next
      })
    )
  }

  function addLine() {
    setLines((prev) => [...prev, { ...emptyLine }])
  }

  function removeLine(index) {
    setLines((prev) => prev.filter((_, i) => i !== index))
  }

  const totalDebit = lines.reduce((sum, l) => sum + (Number(l.debitAmount) || 0), 0)
  const totalCredit = lines.reduce((sum, l) => sum + (Number(l.creditAmount) || 0), 0)
  const difference = totalDebit - totalCredit
  const isBalanced = Math.abs(difference) < 0.005 && totalDebit > 0

  function validate() {
    const next = {}
    if (!description.trim()) next.description = 'Description is required'
    if (lines.length < 2) next.lines = 'At least two lines are required'
    lines.forEach((l, i) => {
      if (!l.accountId) next[`line-${i}-account`] = 'Required'
      const debit = Number(l.debitAmount) || 0
      const credit = Number(l.creditAmount) || 0
      if (debit === 0 && credit === 0) next[`line-${i}-amount`] = 'Enter a debit or credit amount'
    })
    if (!isBalanced) next.balance = 'Debits must equal credits before this entry can be saved'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!validate()) return

    setSaving(true)
    try {
      const res = await axiosClient.post('/journal-entries', {
        entryDate,
        description: description.trim(),
        reference: reference.trim() || null,
        lines: lines
          .filter((l) => l.accountId)
          .map((l) => ({
            accountId: Number(l.accountId),
            debitAmount: Number(l.debitAmount) || 0,
            creditAmount: Number(l.creditAmount) || 0,
          })),
      })
      showToast('Journal entry created', { type: 'success' })
      navigate('/journal-entries')
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to create journal entry', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <Link to="/journal-entries" className="inline-flex items-center gap-1 text-sm text-navy-500 hover:text-navy-700">
          <ArrowLeft size={14} /> Back to journal entries
        </Link>
        <h2 className="mt-2 text-2xl font-bold text-navy-800">New manual journal entry</h2>
        <p className="text-navy-400">For corrections, opening balances, or transactions outside Sales/Purchasing.</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg border border-navy-100 bg-white p-6 shadow-sm">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input label="Entry date" type="date" value={entryDate} onChange={(e) => setEntryDate(e.target.value)} />
          <Input label="Reference" value={reference} onChange={(e) => setReference(e.target.value)} placeholder="Optional, e.g. Invoice #123" />
        </div>
        <Input label="Description" value={description} onChange={(e) => setDescription(e.target.value)} error={errors.description} />

        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-navy-700">Lines</h3>
            <Button type="button" variant="secondary" onClick={addLine} className="px-3 py-1.5 text-xs">
              <Plus size={14} /> Add line
            </Button>
          </div>

          <div className="space-y-3">
            {lines.map((line, i) => (
              <div key={i} className="grid grid-cols-1 gap-2 sm:grid-cols-[2fr_1fr_1fr_auto] sm:items-end">
                <SearchableSelect
                  label={i === 0 ? 'Account' : undefined}
                  value={line.accountId}
                  onChange={(value) => updateLine(i, 'accountId', value)}
                  options={accounts.map((a) => ({ value: String(a.id), label: `${a.code} — ${a.name}` }))}
                  placeholder="Search accounts…"
                  error={errors[`line-${i}-account`]}
                />
                <Input
                  label={i === 0 ? 'Debit' : undefined}
                  type="number"
                  step="0.01"
                  min="0"
                  value={line.debitAmount}
                  onChange={(e) => updateLine(i, 'debitAmount', e.target.value)}
                  error={errors[`line-${i}-amount`]}
                />
                <Input
                  label={i === 0 ? 'Credit' : undefined}
                  type="number"
                  step="0.01"
                  min="0"
                  value={line.creditAmount}
                  onChange={(e) => updateLine(i, 'creditAmount', e.target.value)}
                />
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => removeLine(i)}
                  disabled={lines.length <= 2}
                  aria-label="Remove line"
                  className="px-2 py-2 text-red-500 hover:bg-red-50"
                >
                  <Trash2 size={14} />
                </Button>
              </div>
            ))}
          </div>

          <div
            className={`flex items-center justify-between rounded-md px-4 py-3 text-sm ${
              isBalanced ? 'bg-green-50 text-green-700' : 'bg-orange-50 text-orange-700'
            }`}
          >
            <span className="flex items-center gap-2 font-medium">
              {isBalanced ? <CheckCircle2 size={16} /> : <AlertTriangle size={16} />}
              {isBalanced ? 'Balanced' : errors.balance || 'Not yet balanced'}
            </span>
            <span className="tabular-nums">
              Debit {totalDebit.toFixed(2)} &nbsp;/&nbsp; Credit {totalCredit.toFixed(2)}
              {!isBalanced && ` (diff ${Math.abs(difference).toFixed(2)})`}
            </span>
          </div>
        </div>

        <div className="flex justify-end gap-2 border-t border-navy-100 pt-4">
          <Button type="button" variant="ghost" onClick={() => navigate('/journal-entries')} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" loading={saving} disabled={!isBalanced}>
            Create journal entry
          </Button>
        </div>
      </form>
    </div>
  )
}
