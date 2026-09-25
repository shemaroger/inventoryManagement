import { useEffect, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import SearchableSelect from '../../components/ui/SearchableSelect'
import Button from '../../components/ui/Button'

// TRANSFER_IN/TRANSFER_OUT are deliberately excluded here — they only make sense as the
// paired calls the dedicated Transfer tab issues itself, not as a free-standing choice.
const ADJUSTMENT_TYPES = [
  { value: 'INCREASE', label: 'Increase' },
  { value: 'DECREASE', label: 'Decrease' },
  { value: 'DAMAGE', label: 'Damage' },
  { value: 'LOST', label: 'Lost' },
  { value: 'RECOUNT', label: 'Recount' },
]

export default function StockAdjustmentModal({
  open,
  onClose,
  onSubmit,
  products,
  warehouses,
  initialProductId,
  initialWarehouseId,
  lockProduct = false,
  lockWarehouse = false,
  initialAdjustmentType = 'INCREASE',
  title = 'Adjust stock',
  saving,
}) {
  const [productId, setProductId] = useState('')
  const [warehouseId, setWarehouseId] = useState('')
  const [adjustmentType, setAdjustmentType] = useState('INCREASE')
  const [quantity, setQuantity] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!open) return
    setProductId(initialProductId ?? '')
    setWarehouseId(initialWarehouseId ?? '')
    setAdjustmentType(initialAdjustmentType)
    setQuantity('')
    setReason('')
    setError(null)
  }, [open, initialProductId, initialWarehouseId, initialAdjustmentType])

  const selectedProduct = products.find((p) => String(p.id) === String(productId))
  const selectedWarehouse = warehouses.find((w) => String(w.id) === String(warehouseId))

  function handleSubmit(e) {
    e.preventDefault()
    if (!productId || !warehouseId) {
      setError('Product and warehouse are required')
      return
    }
    if (!quantity || Number(quantity) <= 0) {
      setError('Enter a quantity greater than zero')
      return
    }
    onSubmit({
      productId: Number(productId),
      warehouseId: Number(warehouseId),
      adjustmentType,
      quantity: Number(quantity),
      reason: reason.trim() || null,
    })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="stock-adjustment-form" loading={saving}>
            Apply adjustment
          </Button>
        </>
      }
    >
      <form id="stock-adjustment-form" onSubmit={handleSubmit} className="space-y-4">
        {lockProduct ? (
          <div>
            <label className="block text-sm font-medium text-navy-700">Product</label>
            <p className="mt-1 rounded-md bg-navy-50 px-3 py-2 text-sm text-navy-800">
              {selectedProduct?.name || '—'}
            </p>
          </div>
        ) : (
          <SearchableSelect
            label="Product"
            value={productId}
            onChange={setProductId}
            options={products.map((p) => ({ value: String(p.id), label: `${p.name} (${p.sku})` }))}
            placeholder="Search products…"
          />
        )}

        {lockWarehouse ? (
          <div>
            <label className="block text-sm font-medium text-navy-700">Warehouse</label>
            <p className="mt-1 rounded-md bg-navy-50 px-3 py-2 text-sm text-navy-800">
              {selectedWarehouse?.name || '—'}
            </p>
          </div>
        ) : (
          <Select label="Warehouse" value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)}>
            <option value="">— Select a warehouse —</option>
            {warehouses.map((w) => (
              <option key={w.id} value={w.id}>{w.name}</option>
            ))}
          </Select>
        )}

        <Select label="Adjustment type" value={adjustmentType} onChange={(e) => setAdjustmentType(e.target.value)}>
          {ADJUSTMENT_TYPES.map((t) => (
            <option key={t.value} value={t.value}>{t.label}</option>
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

        <Input label="Reason (optional)" value={reason} onChange={(e) => setReason(e.target.value)} />

        {error && <p className="text-sm text-red-600">{error}</p>}
      </form>
    </Modal>
  )
}
