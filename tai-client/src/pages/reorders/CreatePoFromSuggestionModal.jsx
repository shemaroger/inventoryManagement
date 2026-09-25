import { useEffect, useState } from 'react'
import axiosClient from '../../api/axiosClient'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Button from '../../components/ui/Button'

export default function CreatePoFromSuggestionModal({ open, onClose, onSubmit, suggestion, saving }) {
  const [suppliers, setSuppliers] = useState([])
  const [supplierId, setSupplierId] = useState('')
  const [quantity, setQuantity] = useState('')
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) {
      axiosClient.get('/suppliers').then((res) => setSuppliers(res.data.data.filter((s) => s.active))).catch(() => {})
      setQuantity(suggestion?.suggestedQuantity != null ? String(suggestion.suggestedQuantity) : '')
      setSupplierId('')
      setError(null)
    }
  }, [open, suggestion])

  function handleSubmit(e) {
    e.preventDefault()
    if (!supplierId) {
      setError('Supplier is required')
      return
    }
    onSubmit({
      supplierId: Number(supplierId),
      quantity: quantity ? Number(quantity) : null,
    })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Draft PO — ${suggestion?.productName || ''}`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="create-po-suggestion-form" loading={saving}>
            Create draft PO
          </Button>
        </>
      }
    >
      <form id="create-po-suggestion-form" onSubmit={handleSubmit} className="space-y-4">
        <p className="text-sm text-navy-500">
          This creates a DRAFT purchase order for {suggestion?.warehouseName} — you'll still submit and receive it through the normal Purchase Orders flow.
        </p>
        <Select label="Supplier" value={supplierId} onChange={(e) => setSupplierId(e.target.value)} error={error}>
          <option value="">— Select —</option>
          {suppliers.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </Select>
        <Input
          label="Quantity"
          type="number"
          step="0.01"
          min="0"
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
        />
      </form>
    </Modal>
  )
}
