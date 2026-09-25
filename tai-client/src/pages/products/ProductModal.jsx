import { useEffect, useState } from 'react'
import { RefreshCw } from 'lucide-react'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Button from '../../components/ui/Button'

// Client-side only — generates a readable-but-likely-unique SKU from the first
// 3 letters of the product name plus a short fragment of the current time.
// It's a starting point the user can freely edit before submitting, not a
// guaranteed-unique value (only the backend's unique constraint on `sku`
// truly enforces that).
function generateSku(productName) {
  const prefix = (productName || '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, 3) || 'GEN'
  const fragment = Date.now().toString(36).toUpperCase().slice(-5)
  return `${prefix}-${fragment}`
}

const emptyForm = {
  sku: '',
  barcode: '',
  name: '',
  description: '',
  categoryId: '',
  brandId: '',
  costPrice: '',
  sellingPrice: '',
  reorderLevel: '',
}

export default function ProductModal({ open, onClose, onSubmit, initial, categories, brands, saving }) {
  const isEdit = Boolean(initial?.id)
  const [form, setForm] = useState(emptyForm)
  const [errors, setErrors] = useState({})
  const [skuTouched, setSkuTouched] = useState(false)

  useEffect(() => {
    if (!open) return
    setSkuTouched(isEdit)
    setForm(
      initial
        ? {
            sku: initial.sku || '',
            barcode: initial.barcode || '',
            name: initial.name || '',
            description: initial.description || '',
            categoryId: initial.categoryId ?? '',
            brandId: initial.brandId ?? '',
            costPrice: initial.costPrice ?? '',
            sellingPrice: initial.sellingPrice ?? '',
            reorderLevel: initial.reorderLevel ?? '',
          }
        : emptyForm
    )
    setErrors({})
  }, [open, initial]) // eslint-disable-line react-hooks/exhaustive-deps

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  function handleSkuChange(value) {
    setSkuTouched(true)
    update('sku', value)
  }

  function handleNameChange(value) {
    update('name', value)
    // Re-suggest the SKU as the name is typed, but only while the user
    // hasn't edited the SKU themselves — never overwrite their own value.
    if (!isEdit && !skuTouched) {
      update('sku', generateSku(value))
    }
  }

  function regenerateSku() {
    setForm((prev) => ({ ...prev, sku: generateSku(prev.name) }))
    setSkuTouched(false)
  }

  const margin =
    form.sellingPrice && Number(form.sellingPrice) > 0
      ? ((Number(form.sellingPrice) - Number(form.costPrice || 0)) / Number(form.sellingPrice)) * 100
      : null
  const priceWarning =
    form.costPrice !== '' &&
    form.sellingPrice !== '' &&
    Number(form.sellingPrice) < Number(form.costPrice)

  function validate() {
    const next = {}
    if (!form.sku.trim()) next.sku = 'SKU is required'
    if (!form.name.trim()) next.name = 'Name is required'
    if (form.costPrice === '' || Number(form.costPrice) < 0) next.costPrice = 'Enter a valid cost price'
    if (form.sellingPrice === '' || Number(form.sellingPrice) < 0) next.sellingPrice = 'Enter a valid selling price'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  function handleSubmit(e) {
    e.preventDefault()
    if (!validate()) return
    onSubmit({
      sku: form.sku.trim(),
      barcode: form.barcode.trim() || null,
      name: form.name.trim(),
      description: form.description.trim() || null,
      categoryId: form.categoryId ? Number(form.categoryId) : null,
      brandId: form.brandId ? Number(form.brandId) : null,
      unitId: initial?.unitId ?? null,
      costPrice: Number(form.costPrice),
      sellingPrice: Number(form.sellingPrice),
      reorderLevel: form.reorderLevel === '' ? 0 : Number(form.reorderLevel),
    })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title={isEdit ? `Edit ${initial.name}` : 'Add product'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="product-form" loading={saving}>
            {isEdit ? 'Save changes' : 'Create product'}
          </Button>
        </>
      }
    >
      <form id="product-form" onSubmit={handleSubmit} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label className="block text-sm font-medium text-navy-700">SKU</label>
          <div className="mt-1 flex gap-2">
            <Input
              value={form.sku}
              onChange={(e) => handleSkuChange(e.target.value)}
              error={errors.sku}
              className="flex-1"
            />
            {!isEdit && (
              <button
                type="button"
                onClick={regenerateSku}
                aria-label="Regenerate SKU"
                title="Regenerate SKU"
                className="shrink-0 rounded-full border border-navy-200 p-2 text-navy-500 hover:bg-navy-50 hover:text-navy-700"
              >
                <RefreshCw size={16} />
              </button>
            )}
          </div>
          {!isEdit && (
            <p className="mt-1 text-xs text-navy-400">Auto-generated from the product name — edit freely before saving.</p>
          )}
        </div>
        <Input label="Barcode (optional)" value={form.barcode} onChange={(e) => update('barcode', e.target.value)} />

        <div className="sm:col-span-2">
          <Input label="Name" value={form.name} onChange={(e) => handleNameChange(e.target.value)} error={errors.name} />
        </div>

        <div className="sm:col-span-2 space-y-1">
          <label className="block text-sm font-medium text-navy-700">Description</label>
          <textarea
            rows={2}
            value={form.description}
            onChange={(e) => update('description', e.target.value)}
            className="w-full rounded-md border border-navy-200 px-3 py-2 text-sm text-navy-800 focus:border-orange-500 focus:outline-none focus:ring-1 focus:ring-orange-500"
          />
        </div>

        <Select label="Category" value={form.categoryId} onChange={(e) => update('categoryId', e.target.value)}>
          <option value="">— None —</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </Select>

        <Select label="Brand" value={form.brandId} onChange={(e) => update('brandId', e.target.value)}>
          <option value="">— None —</option>
          {brands.map((b) => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </Select>

        <Input
          label="Cost price"
          type="number"
          step="0.01"
          min="0"
          value={form.costPrice}
          onChange={(e) => update('costPrice', e.target.value)}
          error={errors.costPrice}
        />
        <Input
          label="Selling price"
          type="number"
          step="0.01"
          min="0"
          value={form.sellingPrice}
          onChange={(e) => update('sellingPrice', e.target.value)}
          error={errors.sellingPrice}
        />

        <div className="sm:col-span-2 flex items-center justify-between rounded-md bg-navy-50 px-3 py-2 text-sm">
          <span className="text-navy-500">Margin</span>
          <span className={`font-medium ${priceWarning ? 'text-orange-600' : 'text-navy-700'}`}>
            {margin === null ? '—' : `${margin.toFixed(1)}%`}
          </span>
        </div>
        {priceWarning && (
          <div className="sm:col-span-2 -mt-2 text-xs text-orange-600">
            Selling price is below cost price — this product will sell at a loss. That may be intentional (loss-leader), so this isn't blocked.
          </div>
        )}

        <Input
          label="Reorder level"
          type="number"
          step="0.01"
          min="0"
          value={form.reorderLevel}
          onChange={(e) => update('reorderLevel', e.target.value)}
        />
      </form>
    </Modal>
  )
}
