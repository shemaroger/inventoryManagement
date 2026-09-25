import { useEffect, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Button from '../../components/ui/Button'

export default function SalePaymentModal({ open, onClose, onSubmit, saving }) {
  const [amount, setAmount] = useState('')
  const [paymentMethod, setPaymentMethod] = useState('CASH')
  const [paymentDate, setPaymentDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [notes, setNotes] = useState('')
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) {
      setAmount('')
      setPaymentMethod('CASH')
      setPaymentDate(new Date().toISOString().slice(0, 10))
      setNotes('')
      setError(null)
    }
  }, [open])

  function handleSubmit(e) {
    e.preventDefault()
    if (!amount || Number(amount) <= 0) {
      setError('Enter a valid amount')
      return
    }
    onSubmit({
      amount: Number(amount),
      paymentMethod,
      paymentDate,
      notes: notes.trim() || null,
    })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Record payment"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="sale-payment-form" loading={saving}>
            Record payment
          </Button>
        </>
      }
    >
      <form id="sale-payment-form" onSubmit={handleSubmit} className="space-y-4">
        <Input label="Amount" type="number" step="0.01" min="0" value={amount} onChange={(e) => setAmount(e.target.value)} error={error} />
        <Select label="Payment method" value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}>
          <option value="CASH">Cash</option>
          <option value="MOBILE_MONEY">Mobile money</option>
          <option value="BANK_TRANSFER">Bank transfer</option>
        </Select>
        <Input label="Payment date" type="date" value={paymentDate} onChange={(e) => setPaymentDate(e.target.value)} />
        <Input label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Optional" />
      </form>
    </Modal>
  )
}
