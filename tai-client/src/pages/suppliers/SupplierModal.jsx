import { useEffect, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'

export default function SupplierModal({ open, onClose, onSubmit, initial, saving }) {
  const isEdit = Boolean(initial?.id)
  const [form, setForm] = useState({ name: '', contactPerson: '', phone: '', email: '', address: '' })
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) {
      setForm({
        name: initial?.name || '',
        contactPerson: initial?.contactPerson || '',
        phone: initial?.phone || '',
        email: initial?.email || '',
        address: initial?.address || '',
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
    })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? `Edit ${initial.name}` : 'Add supplier'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="supplier-form" loading={saving}>
            {isEdit ? 'Save changes' : 'Create supplier'}
          </Button>
        </>
      }
    >
      <form id="supplier-form" onSubmit={handleSubmit} className="space-y-4">
        <Input label="Name" value={form.name} onChange={(e) => update('name', e.target.value)} error={error} placeholder="e.g. Cimerwa Ltd" />
        <Input label="Contact person" value={form.contactPerson} onChange={(e) => update('contactPerson', e.target.value)} />
        <Input label="Phone" value={form.phone} onChange={(e) => update('phone', e.target.value)} />
        <Input label="Email" type="email" value={form.email} onChange={(e) => update('email', e.target.value)} />
        <Input label="Address" value={form.address} onChange={(e) => update('address', e.target.value)} />
      </form>
    </Modal>
  )
}
