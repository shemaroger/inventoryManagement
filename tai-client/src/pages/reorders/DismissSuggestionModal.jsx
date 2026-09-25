import { useState } from 'react'
import Modal from '../../components/ui/Modal'
import Button from '../../components/ui/Button'

export default function DismissSuggestionModal({ open, onClose, onSubmit, suggestion, saving }) {
  const [reason, setReason] = useState('')

  function handleSubmit(e) {
    e.preventDefault()
    onSubmit({ reason: reason.trim() || null })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Dismiss suggestion"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="dismiss-form" variant="secondary" loading={saving}>
            Dismiss
          </Button>
        </>
      }
    >
      <form id="dismiss-form" onSubmit={handleSubmit} className="space-y-4">
        <p className="text-sm text-navy-600">
          {suggestion?.productName} in {suggestion?.warehouseName}
        </p>
        <div className="space-y-1">
          <label className="block text-sm font-medium text-navy-700">Reason (optional)</label>
          <textarea
            rows={2}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. Already reordered manually, or discontinuing this product"
            className="w-full rounded-md border border-navy-200 px-3 py-2 text-sm text-navy-800 focus:border-orange-500 focus:outline-none focus:ring-1 focus:ring-orange-500"
          />
        </div>
      </form>
    </Modal>
  )
}
