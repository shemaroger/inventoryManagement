import { useState } from 'react'
import Modal from '../../components/ui/Modal'
import Button from '../../components/ui/Button'

export default function ReviewAnomalyModal({ open, onClose, onSubmit, anomaly, action, saving }) {
  const [note, setNote] = useState('')

  const isDismiss = action === 'DISMISSED'

  function handleSubmit(e) {
    e.preventDefault()
    onSubmit({ status: action, reviewNote: note.trim() || null })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isDismiss ? 'Dismiss item' : 'Mark as reviewed'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="review-form" variant={isDismiss ? 'secondary' : 'primary'} loading={saving}>
            {isDismiss ? 'Dismiss' : 'Mark reviewed'}
          </Button>
        </>
      }
    >
      <form id="review-form" onSubmit={handleSubmit} className="space-y-4">
        <p className="text-sm text-navy-600">
          {anomaly?.productName} in {anomaly?.warehouseName} — this note is for your own future reference, not a report against the person who made the adjustment.
        </p>
        <div className="space-y-1">
          <label className="block text-sm font-medium text-navy-700">Note (optional)</label>
          <textarea
            rows={3}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="e.g. Confirmed: end-of-month bulk order, not an issue."
            className="w-full rounded-md border border-navy-200 px-3 py-2 text-sm text-navy-800 focus:border-orange-500 focus:outline-none focus:ring-1 focus:ring-orange-500"
          />
        </div>
      </form>
    </Modal>
  )
}
