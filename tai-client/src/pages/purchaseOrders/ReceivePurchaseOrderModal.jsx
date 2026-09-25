import { useEffect, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'

export default function ReceivePurchaseOrderModal({ open, onClose, onSubmit, purchaseOrder, saving }) {
  const [quantities, setQuantities] = useState({})
  const [error, setError] = useState(null)

  const outstandingLines = (purchaseOrder?.lines || []).filter(
    (l) => Number(l.quantityOrdered) - Number(l.quantityReceived) > 0
  )

  useEffect(() => {
    if (open) {
      const initial = {}
      outstandingLines.forEach((l) => {
        initial[l.id] = String(Number(l.quantityOrdered) - Number(l.quantityReceived))
      })
      setQuantities(initial)
      setError(null)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, purchaseOrder?.id])

  function handleSubmit(e) {
    e.preventDefault()
    const lines = outstandingLines
      .map((l) => ({ lineId: l.id, quantityReceived: Number(quantities[l.id] || 0) }))
      .filter((l) => l.quantityReceived > 0)

    if (lines.length === 0) {
      setError('Enter a quantity for at least one line')
      return
    }
    for (const l of lines) {
      const line = outstandingLines.find((ol) => ol.id === l.lineId)
      const remaining = Number(line.quantityOrdered) - Number(line.quantityReceived)
      if (l.quantityReceived > remaining) {
        setError(`Cannot receive more than ${remaining} of ${line.productName} — that's all that remains outstanding`)
        return
      }
    }
    onSubmit({ lines })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title={`Receive goods — PO #${purchaseOrder?.id}`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="receive-form" loading={saving}>
            Record receipt
          </Button>
        </>
      }
    >
      <form id="receive-form" onSubmit={handleSubmit} className="space-y-4">
        <p className="text-sm text-navy-500">
          Enter the quantity arriving now for each line. You can receive a purchase order across multiple deliveries —
          leave a field at 0 to skip it this time.
        </p>
        <div className="space-y-3">
          {outstandingLines.map((l) => {
            const remaining = Number(l.quantityOrdered) - Number(l.quantityReceived)
            return (
              <div key={l.id} className="flex items-center justify-between gap-3 rounded-md border border-navy-100 p-3">
                <div>
                  <p className="text-sm font-medium text-navy-800">{l.productName}</p>
                  <p className="text-xs text-navy-400">{remaining} of {l.quantityOrdered} remaining</p>
                </div>
                <Input
                  type="number"
                  step="0.01"
                  min="0"
                  max={remaining}
                  value={quantities[l.id] ?? ''}
                  onChange={(e) => setQuantities((prev) => ({ ...prev, [l.id]: e.target.value }))}
                  className="w-28"
                />
              </div>
            )
          })}
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
      </form>
    </Modal>
  )
}
