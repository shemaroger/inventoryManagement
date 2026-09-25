import { useEffect, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Button from '../../components/ui/Button'

const ACCOUNT_TYPES = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE']

export default function AccountModal({ open, onClose, onSubmit, initial, accounts, saving }) {
  const isEdit = Boolean(initial?.id)
  const [form, setForm] = useState({ code: '', name: '', accountType: 'ASSET', parentAccountId: '' })
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) {
      setForm({
        code: initial?.code || '',
        name: initial?.name || '',
        accountType: initial?.accountType || 'ASSET',
        parentAccountId: initial?.parentAccountId ? String(initial.parentAccountId) : '',
      })
      setError(null)
    }
  }, [open, initial])

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  function handleSubmit(e) {
    e.preventDefault()
    if (!form.code.trim() || !form.name.trim()) {
      setError('Code and name are required')
      return
    }
    onSubmit({
      code: form.code.trim(),
      name: form.name.trim(),
      accountType: form.accountType,
      parentAccountId: form.parentAccountId ? Number(form.parentAccountId) : null,
    })
  }

  const parentOptions = accounts.filter((a) => !initial || a.id !== initial.id)

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? `Edit ${initial.name}` : 'Add account'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="account-form" loading={saving}>
            {isEdit ? 'Save changes' : 'Create account'}
          </Button>
        </>
      }
    >
      <form id="account-form" onSubmit={handleSubmit} className="space-y-4">
        <Input label="Code" value={form.code} onChange={(e) => update('code', e.target.value)} error={error} placeholder="e.g. 1050" />
        <Input label="Name" value={form.name} onChange={(e) => update('name', e.target.value)} placeholder="e.g. Petty Cash" />
        <Select label="Account type" value={form.accountType} onChange={(e) => update('accountType', e.target.value)}>
          {ACCOUNT_TYPES.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </Select>
        <Select label="Parent account (optional)" value={form.parentAccountId} onChange={(e) => update('parentAccountId', e.target.value)}>
          <option value="">— None —</option>
          {parentOptions.map((a) => (
            <option key={a.id} value={a.id}>{a.code} — {a.name}</option>
          ))}
        </Select>
      </form>
    </Modal>
  )
}
