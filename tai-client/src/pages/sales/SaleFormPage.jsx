import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { ArrowLeft, Plus, Trash2, AlertTriangle, UserPlus } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import SearchableSelect from '../../components/ui/SearchableSelect'
import QuickCreateCustomerModal from './QuickCreateCustomerModal'

const emptyLine = { productId: '', quantity: '', unitPrice: '', discountPercent: '0' }

export default function SaleFormPage() {
  const navigate = useNavigate()
  const { showToast } = useToast()

  const [customers, setCustomers] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [products, setProducts] = useState([])
  const [stockCache, setStockCache] = useState({}) // productId -> [{warehouseId, quantity}]

  const [customerId, setCustomerId] = useState('')
  const [warehouseId, setWarehouseId] = useState('')
  const [paymentType, setPaymentType] = useState('CASH')
  const [saleDate, setSaleDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState([{ ...emptyLine }])
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const [customerModalOpen, setCustomerModalOpen] = useState(false)
  const [creatingCustomer, setCreatingCustomer] = useState(false)

  useEffect(() => {
    axiosClient.get('/customers').then((res) => setCustomers(res.data.data.filter((c) => c.active))).catch(() => {})
    axiosClient.get('/warehouses').then((res) => setWarehouses(res.data.data.filter((w) => w.active))).catch(() => {})
    axiosClient.get('/products', { params: { size: 500 } }).then((res) => setProducts(res.data.data.content)).catch(() => {})
  }, [])

  function productPrice(productId) {
    const product = products.find((p) => String(p.id) === String(productId))
    return product ? product.sellingPrice : null
  }

  function stockFor(productId) {
    if (!productId || !warehouseId) return null
    const entries = stockCache[productId]
    if (!entries) return undefined // not yet loaded
    const match = entries.find((e) => String(e.warehouseId) === String(warehouseId))
    return match ? match.quantity : 0
  }

  function ensureStockLoaded(productId) {
    if (!productId || stockCache[productId]) return
    axiosClient
      .get(`/stock/product/${productId}`)
      .then((res) => setStockCache((prev) => ({ ...prev, [productId]: res.data.data })))
      .catch(() => {})
  }

  function updateLine(index, field, value) {
    setLines((prev) =>
      prev.map((line, i) => {
        if (i !== index) return line
        const next = { ...line, [field]: value }
        if (field === 'productId') {
          ensureStockLoaded(value)
          if (!line.unitPrice) {
            const price = productPrice(value)
            if (price != null) next.unitPrice = String(price)
          }
        }
        return next
      })
    )
  }

  async function handleCreateCustomer(payload, setModalError) {
    setCreatingCustomer(true)
    try {
      const res = await axiosClient.post('/customers', payload)
      const created = res.data.data
      setCustomers((prev) => [...prev, created])
      setCustomerId(String(created.id))
      setCustomerModalOpen(false)
      showToast('Customer created', { type: 'success' })
    } catch (err) {
      setModalError(err.response?.data?.message || 'Failed to create customer')
    } finally {
      setCreatingCustomer(false)
    }
  }

  function focusProductField(index) {
    requestAnimationFrame(() => {
      document.getElementById(`line-product-${index}`)?.focus()
    })
  }

  function addLine(focusNew = false) {
    setLines((prev) => {
      const newIndex = prev.length
      if (focusNew) focusProductField(newIndex)
      return [...prev, { ...emptyLine }]
    })
  }

  function removeLine(index) {
    setLines((prev) => prev.filter((_, i) => i !== index))
  }

  // Enter on Quantity moves to Discount (the next logical field in the row); Enter on Discount
  // — the row's last field — either jumps to the next row's product picker or, on the last row,
  // adds a fresh row and focuses it, so a fast-moving sales team never has to reach for the mouse.
  function handleQuantityKeyDown(e, index) {
    if (e.key !== 'Enter') return
    e.preventDefault()
    document.getElementById(`line-discount-${index}`)?.focus()
  }

  function handleDiscountKeyDown(e, index) {
    if (e.key !== 'Enter') return
    e.preventDefault()
    if (index === lines.length - 1) {
      addLine(true)
    } else {
      focusProductField(index + 1)
    }
  }

  function lineSubtotal(l) {
    return (Number(l.quantity) || 0) * (Number(l.unitPrice) || 0)
  }

  function lineDiscountAmount(l) {
    return lineSubtotal(l) * ((Number(l.discountPercent) || 0) / 100)
  }

  function lineTotal(l) {
    return lineSubtotal(l) - lineDiscountAmount(l)
  }

  const subtotal = lines.reduce((sum, l) => sum + lineSubtotal(l), 0)
  const discountTotal = lines.reduce((sum, l) => sum + lineDiscountAmount(l), 0)
  const netTotal = subtotal - discountTotal
  const vatAmount = netTotal * 0.18
  const grandTotal = netTotal + vatAmount

  function validate() {
    const next = {}
    if (!customerId) next.customerId = 'Customer is required'
    if (!warehouseId) next.warehouseId = 'Warehouse is required'
    if (lines.length === 0) next.lines = 'At least one line item is required'
    lines.forEach((l, i) => {
      if (!l.productId) next[`line-${i}-product`] = 'Required'
      if (!l.quantity || Number(l.quantity) <= 0) next[`line-${i}-qty`] = 'Required'
    })
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!validate()) return

    setSaving(true)
    try {
      const res = await axiosClient.post('/sales', {
        customerId: Number(customerId),
        warehouseId: Number(warehouseId),
        saleDate,
        paymentType,
        notes: notes.trim() || null,
        lines: lines.map((l) => ({
          productId: Number(l.productId),
          quantity: Number(l.quantity),
          unitPrice: l.unitPrice !== '' ? Number(l.unitPrice) : null,
          discountPercent: l.discountPercent !== '' ? Number(l.discountPercent) : 0,
        })),
      })
      showToast('Quotation created', { type: 'success' })
      navigate(`/sales/${res.data.data.id}`)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to create sale', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <Link to="/sales" className="inline-flex items-center gap-1 text-sm text-navy-500 hover:text-navy-700">
          <ArrowLeft size={14} /> Back to sales
        </Link>
        <h2 className="mt-2 text-2xl font-bold text-navy-800">New sale</h2>
        <p className="text-navy-400">Starts as a quotation — stock isn't touched until it's confirmed and completed.</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg border border-navy-100 bg-white p-6 shadow-sm">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <div className="flex items-end gap-2">
              <div className="flex-1">
                <SearchableSelect
                  label="Customer"
                  value={customerId}
                  onChange={setCustomerId}
                  options={customers.map((c) => ({ value: String(c.id), label: c.name }))}
                  placeholder="Search customers…"
                  error={errors.customerId}
                />
              </div>
              <Button
                type="button"
                variant="secondary"
                onClick={() => setCustomerModalOpen(true)}
                className="px-3 py-2 text-xs"
                aria-label="New customer"
              >
                <UserPlus size={14} /> New
              </Button>
            </div>
          </div>
          <Select label="Warehouse" value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} error={errors.warehouseId}>
            <option value="">— Select —</option>
            {warehouses.map((w) => (
              <option key={w.id} value={w.id}>{w.name}</option>
            ))}
          </Select>
          <Select label="Payment type" value={paymentType} onChange={(e) => setPaymentType(e.target.value)}>
            <option value="CASH">Cash</option>
            <option value="CREDIT">Credit</option>
          </Select>
          <Input label="Sale date" type="date" value={saleDate} onChange={(e) => setSaleDate(e.target.value)} />
        </div>

        <Input label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Optional" />

        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-navy-700">Line items</h3>
            <Button type="button" variant="secondary" onClick={() => addLine(true)} className="px-3 py-1.5 text-xs">
              <Plus size={14} /> Add line
            </Button>
          </div>

          <div className="space-y-3">
            {lines.map((line, i) => {
              const available = stockFor(line.productId)
              const requested = Number(line.quantity) || 0
              const short = warehouseId && line.productId && available != null && available !== undefined && requested > available
              return (
                <div key={i} className="rounded-md border border-navy-100 p-3">
                  <div className="grid grid-cols-1 gap-2 sm:grid-cols-[2fr_1fr_1fr_1fr_1fr_auto] sm:items-end">
                    <SearchableSelect
                      id={`line-product-${i}`}
                      label={i === 0 ? 'Product' : undefined}
                      value={line.productId}
                      onChange={(value) => updateLine(i, 'productId', value)}
                      options={products.map((p) => ({ value: String(p.id), label: `${p.name} (${p.sku})` }))}
                      placeholder="Search products…"
                      error={errors[`line-${i}-product`]}
                    />
                    <Input
                      id={`line-qty-${i}`}
                      label={i === 0 ? 'Quantity' : undefined}
                      type="number"
                      step="0.01"
                      min="0"
                      value={line.quantity}
                      onChange={(e) => updateLine(i, 'quantity', e.target.value)}
                      onKeyDown={(e) => handleQuantityKeyDown(e, i)}
                      error={errors[`line-${i}-qty`]}
                    />
                    <Input
                      label={i === 0 ? 'Unit price' : undefined}
                      type="number"
                      step="0.01"
                      min="0"
                      value={line.unitPrice}
                      onChange={(e) => updateLine(i, 'unitPrice', e.target.value)}
                    />
                    <Input
                      id={`line-discount-${i}`}
                      label={i === 0 ? 'Discount %' : undefined}
                      type="number"
                      step="0.01"
                      min="0"
                      max="100"
                      value={line.discountPercent}
                      onChange={(e) => updateLine(i, 'discountPercent', e.target.value)}
                      onKeyDown={(e) => handleDiscountKeyDown(e, i)}
                    />
                    <div className={i === 0 ? 'space-y-1' : ''}>
                      {i === 0 && <p className="block text-sm font-medium text-navy-700">Line total</p>}
                      <p className="px-1 py-2 text-right text-sm font-medium tabular-nums text-navy-800">
                        {lineTotal(line).toFixed(2)}
                      </p>
                    </div>
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
                  {line.productId && warehouseId && (
                    <p className={`mt-2 flex items-center gap-1 text-xs ${short ? 'text-orange-600' : 'text-navy-400'}`}>
                      {short && <AlertTriangle size={12} />}
                      {available === undefined
                        ? 'Checking stock…'
                        : `${available} in stock at the selected warehouse`}
                      {short && ' — exceeds what is currently available'}
                    </p>
                  )}
                </div>
              )
            })}
          </div>

          <div className="ml-auto w-full space-y-1 rounded-md bg-navy-50 px-4 py-3 text-sm sm:w-64">
            <div className="flex justify-between text-navy-500">
              <span>Subtotal</span>
              <span className="tabular-nums">{subtotal.toFixed(2)}</span>
            </div>
            {discountTotal > 0 && (
              <div className="flex justify-between text-orange-600">
                <span>Discounts</span>
                <span className="tabular-nums">−{discountTotal.toFixed(2)}</span>
              </div>
            )}
            <div className="flex justify-between text-navy-500">
              <span>VAT (18%)</span>
              <span className="tabular-nums">{vatAmount.toFixed(2)}</span>
            </div>
            <div className="flex justify-between border-t border-navy-200 pt-1 font-semibold text-navy-800">
              <span>Total</span>
              <span className="tabular-nums">{grandTotal.toFixed(2)}</span>
            </div>
          </div>
        </div>

        <div className="flex justify-end gap-2 border-t border-navy-100 pt-4">
          <Button type="button" variant="ghost" onClick={() => navigate('/sales')} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" loading={saving}>
            Create quotation
          </Button>
        </div>
      </form>

      <QuickCreateCustomerModal
        open={customerModalOpen}
        onClose={() => setCustomerModalOpen(false)}
        onCreated={handleCreateCustomer}
        saving={creatingCustomer}
      />
    </div>
  )
}
