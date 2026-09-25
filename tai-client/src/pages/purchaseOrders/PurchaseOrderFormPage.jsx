import { useEffect, useState } from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { ArrowLeft, Plus, Trash2, Sparkles } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import SearchableSelect from '../../components/ui/SearchableSelect'

const emptyLine = { productId: '', quantityOrdered: '', unitCost: '' }

export default function PurchaseOrderFormPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { showToast } = useToast()
  const suggestion = location.state?.suggestion

  const [suppliers, setSuppliers] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [products, setProducts] = useState([])

  const [supplierId, setSupplierId] = useState(suggestion?.supplierId ? String(suggestion.supplierId) : '')
  const [warehouseId, setWarehouseId] = useState(suggestion?.warehouseId ? String(suggestion.warehouseId) : '')
  const [orderDate, setOrderDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [paymentType, setPaymentType] = useState('CREDIT')
  const [lines, setLines] = useState(() =>
    suggestion?.suggestedLines?.length
      ? suggestion.suggestedLines.map((l) => ({
          productId: String(l.productId),
          quantityOrdered: String(l.suggestedQuantity),
          unitCost: String(l.unitCost ?? ''),
        }))
      : [{ ...emptyLine }]
  )
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const reasoningByProductId = Object.fromEntries(
    (suggestion?.suggestedLines || []).map((l) => [String(l.productId), l.reasoning])
  )

  useEffect(() => {
    axiosClient.get('/suppliers').then((res) => setSuppliers(res.data.data.filter((s) => s.active))).catch(() => {})
    axiosClient.get('/warehouses').then((res) => setWarehouses(res.data.data.filter((w) => w.active))).catch(() => {})
    axiosClient.get('/products', { params: { size: 200 } }).then((res) => setProducts(res.data.data.content)).catch(() => {})
  }, [])

  function updateLine(index, field, value) {
    setLines((prev) => prev.map((line, i) => (i === index ? { ...line, [field]: value } : line)))
  }

  function addLine() {
    setLines((prev) => [...prev, { ...emptyLine }])
  }

  function removeLine(index) {
    setLines((prev) => prev.filter((_, i) => i !== index))
  }

  const total = lines.reduce((sum, l) => sum + (Number(l.quantityOrdered) || 0) * (Number(l.unitCost) || 0), 0)

  function validate() {
    const next = {}
    if (!supplierId) next.supplierId = 'Supplier is required'
    if (!warehouseId) next.warehouseId = 'Warehouse is required'
    if (lines.length === 0) next.lines = 'At least one line item is required'
    lines.forEach((l, i) => {
      if (!l.productId) next[`line-${i}-product`] = 'Required'
      if (!l.quantityOrdered || Number(l.quantityOrdered) <= 0) next[`line-${i}-qty`] = 'Required'
      if (l.unitCost === '' || Number(l.unitCost) < 0) next[`line-${i}-cost`] = 'Required'
    })
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!validate()) return

    setSaving(true)
    try {
      const res = await axiosClient.post('/purchase-orders', {
        supplierId: Number(supplierId),
        warehouseId: Number(warehouseId),
        orderDate,
        paymentType,
        lines: lines.map((l) => ({
          productId: Number(l.productId),
          quantityOrdered: Number(l.quantityOrdered),
          unitCost: Number(l.unitCost),
        })),
      })
      showToast('Purchase order created', { type: 'success' })
      navigate(`/purchase-orders/${res.data.data.id}`)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to create purchase order', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <Link to="/purchase-orders" className="inline-flex items-center gap-1 text-sm text-navy-500 hover:text-navy-700">
          <ArrowLeft size={14} /> Back to purchase orders
        </Link>
        <h2 className="mt-2 text-2xl font-bold text-navy-800">New purchase order</h2>
      </div>

      {suggestion && (
        <div className="flex items-start gap-2 rounded-md border border-orange-200 bg-orange-50 px-4 py-3 text-sm text-orange-700">
          <Sparkles size={16} className="mt-0.5 shrink-0" />
          <span>
            <strong>AI-suggested draft</strong> — quantities are computed from consumption trends, not guaranteed.
            Review and adjust every line before submitting; nothing has been saved yet.
          </span>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg border border-navy-100 bg-white p-6 shadow-sm">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Select label="Supplier" value={supplierId} onChange={(e) => setSupplierId(e.target.value)} error={errors.supplierId}>
            <option value="">— Select —</option>
            {suppliers.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </Select>
          <Select label="Warehouse" value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} error={errors.warehouseId}>
            <option value="">— Select —</option>
            {warehouses.map((w) => (
              <option key={w.id} value={w.id}>{w.name}</option>
            ))}
          </Select>
          <Select label="Payment type" value={paymentType} onChange={(e) => setPaymentType(e.target.value)}>
            <option value="CREDIT">Credit</option>
            <option value="CASH">Cash</option>
          </Select>
          <Input label="Order date" type="date" value={orderDate} onChange={(e) => setOrderDate(e.target.value)} />
        </div>

        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-navy-700">Line items</h3>
            <Button type="button" variant="secondary" onClick={addLine} className="px-3 py-1.5 text-xs">
              <Plus size={14} /> Add line
            </Button>
          </div>

          <div className="space-y-3">
            {lines.map((line, i) => (
              <div key={i} className="rounded-md border border-navy-100 p-3">
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-[2fr_1fr_1fr_auto] sm:items-end">
                  <SearchableSelect
                    label={i === 0 ? 'Product' : undefined}
                    value={line.productId}
                    onChange={(value) => updateLine(i, 'productId', value)}
                    options={products.map((p) => ({ value: String(p.id), label: `${p.name} (${p.sku})` }))}
                    placeholder="Search products…"
                    error={errors[`line-${i}-product`]}
                  />
                  <Input
                    label={i === 0 ? 'Quantity' : undefined}
                    type="number"
                    step="0.01"
                    min="0"
                    value={line.quantityOrdered}
                    onChange={(e) => updateLine(i, 'quantityOrdered', e.target.value)}
                    error={errors[`line-${i}-qty`]}
                  />
                  <Input
                    label={i === 0 ? 'Unit cost' : undefined}
                    type="number"
                    step="0.01"
                    min="0"
                    value={line.unitCost}
                    onChange={(e) => updateLine(i, 'unitCost', e.target.value)}
                    error={errors[`line-${i}-cost`]}
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => removeLine(i)}
                    disabled={lines.length === 1}
                    aria-label="Remove line"
                    className="px-2 py-2 text-red-500 hover:bg-red-50"
                  >
                    <Trash2 size={14} />
                  </Button>
                </div>
                {reasoningByProductId[line.productId] && (
                  <p className="mt-2 flex items-start gap-1 text-xs text-orange-600">
                    <Sparkles size={12} className="mt-0.5 shrink-0" />
                    {reasoningByProductId[line.productId]}
                  </p>
                )}
              </div>
            ))}
          </div>

          <div className="flex justify-end rounded-md bg-navy-50 px-3 py-2 text-sm">
            <span className="text-navy-500">Estimated total:&nbsp;</span>
            <span className="font-semibold text-navy-800">{total.toFixed(2)}</span>
          </div>
        </div>

        <div className="flex justify-end gap-2 border-t border-navy-100 pt-4">
          <Button type="button" variant="ghost" onClick={() => navigate('/purchase-orders')} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" loading={saving}>
            Create purchase order
          </Button>
        </div>
      </form>
    </div>
  )
}
