import { useEffect, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'

// A minimal fast-path version of CustomerModal for creating a walk-in/new customer without
// leaving the Sale form — only what's needed to complete a sale, not the full /customers
// create form. Credit limit defaults to 0 (cash-safe) since a customer created this way has
// no history yet.
export default function QuickCreateCustomerModal({ open, onClose, onCreated, saving }) {
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [creditLimit, setCreditLimit] = useState('0')
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) {
      setName('')
      setPhone('')
      setEmail('')
      setCreditLimit('0')
      setError(null)
    }
  }, [open])

  function handleSubmit(e) {
    e.preventDefault()
    if (!name.trim()) {
      setError('Name is required')
      return
    }
    setError(null)
    onCreated(
      {
        name: name.trim(),
        contactPerson: null,
        phone: phone.trim() || null,
        email: email.trim() || null,
        address: null,
        creditLimit: Number(creditLimit) || 0,
        customerCategory: null,
      },
      setError
    )
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="New customer"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="quick-customer-form" loading={saving}>
            Create &amp; select
          </Button>
        </>
      }
    >
      <form id="quick-customer-form" onSubmit={handleSubmit} className="space-y-4">
        <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} error={error} placeholder="e.g. Jane Uwimana" autoFocus />
        <Input label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
        <Input label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Input
          label="Credit limit"
          type="number"
          step="0.01"
          min="0"
          value={creditLimit}
          onChange={(e) => setCreditLimit(e.target.value)}
        />
      </form>
    </Modal>
  )
}
