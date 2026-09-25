import { useEffect, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'

export default function CustomerModal({ open, onClose, onSubmit, initial, saving }) {
  const isEdit = Boolean(initial?.id)
  const [form, setForm] = useState({
    name: '', contactPerson: '', phone: '', email: '', address: '', creditLimit: '0', customerCategory: '',
  })
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) {
      setForm({
        name: initial?.name || '',
        contactPerson: initial?.contactPerson || '',
        phone: initial?.phone || '',
        email: initial?.email || '',
        address: initial?.address || '',
        creditLimit: initial?.creditLimit != null ? String(initial.creditLimit) : '0',
        customerCategory: initial?.customerCategory || '',
      })
      setError(null)
    }
  }, [open, initial])

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  function handleSubmit(e) {
    e.preventDefault()
    if (!form.name.trim()) {
      setError('Name is required')
      return
    }
    onSubmit({
      name: form.name.trim(),
      contactPerson: form.contactPerson.trim() || null,
      phone: form.phone.trim() || null,
      email: form.email.trim() || null,
      address: form.address.trim() || null,
      creditLimit: Number(form.creditLimit) || 0,
      customerCategory: form.customerCategory.trim() || null,
    })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? `Edit ${initial.name}` : 'Add customer'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="customer-form" loading={saving}>
            {isEdit ? 'Save changes' : 'Create customer'}
          </Button>
        </>
      }
    >
      <form id="customer-form" onSubmit={handleSubmit} className="space-y-4">
        <Input label="Name" value={form.name} onChange={(e) => update('name', e.target.value)} error={error} placeholder="e.g. Kigali Builders Ltd" />
        <Input label="Contact person" value={form.contactPerson} onChange={(e) => update('contactPerson', e.target.value)} />
        <Input label="Phone" value={form.phone} onChange={(e) => update('phone', e.target.value)} />
        <Input label="Email" type="email" value={form.email} onChange={(e) => update('email', e.target.value)} />
        <Input label="Address" value={form.address} onChange={(e) => update('address', e.target.value)} />
        <div className="grid grid-cols-2 gap-4">
          <Input
            label="Credit limit"
            type="number"
            step="0.01"
            min="0"
            value={form.creditLimit}
            onChange={(e) => update('creditLimit', e.target.value)}
          />
          <Input
            label="Category"
            value={form.customerCategory}
            onChange={(e) => update('customerCategory', e.target.value)}
            placeholder="e.g. Wholesale"
          />
        </div>
      </form>
    </Modal>
  )
}
