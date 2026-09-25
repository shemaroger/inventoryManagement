import { useEffect, useMemo, useState } from 'react'
import { Search, ChevronUp, ChevronDown, Package, X, Plus, Pencil } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import useDebouncedValue from '../../hooks/useDebouncedValue'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import RowActionsMenu from '../../components/ui/RowActionsMenu'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import ProductModal from './ProductModal'

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

const COLUMNS = [
  { key: 'sku', label: 'SKU', sortable: true },
  { key: 'name', label: 'Name', sortable: true },
  { key: 'categoryName', label: 'Category' },
  { key: 'brandName', label: 'Brand' },
  { key: 'costPrice', label: 'Cost', sortable: false, align: 'right' },
  { key: 'sellingPrice', label: 'Price', sortable: true, align: 'right' },
  { key: 'margin', label: 'Margin', align: 'right' },
  { key: 'totalStock', label: 'Stock', sortable: true, align: 'right' },
  { key: 'active', label: 'Status' },
  { key: 'actions', label: '', align: 'right' },
]

function marginPercent(product) {
  const selling = Number(product.sellingPrice)
  const cost = Number(product.costPrice)
  if (!selling) return null
  return ((selling - cost) / selling) * 100
}

function stockLevel(product) {
  const stock = Number(product.totalStock)
  const reorder = Number(product.reorderLevel)
  if (stock <= 0) return 'danger'
  if (reorder > 0 && stock <= reorder) return 'warning'
  return 'ok'
}

const STOCK_DOT_CLASS = {
  danger: 'bg-red-500',
  warning: 'bg-orange-500',
  ok: 'bg-green-500',
}

export default function ProductsList() {
  const { hasRole } = useAuth()
  const canEdit = hasRole('ADMIN') || hasRole('MANAGER')
  const canDeactivate = hasRole('ADMIN')
  const { showToast } = useToast()

  const [products, setProducts] = useState([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)

  const [searchInput, setSearchInput] = useState('')
  const debouncedSearch = useDebouncedValue(searchInput, 300)
  const [categoryFilter, setCategoryFilter] = useState('')
  const [brandFilter, setBrandFilter] = useState('')

  const [categories, setCategories] = useState([])
  const [brands, setBrands] = useState([])

  const [sortKey, setSortKey] = useState(null)
  const [sortDir, setSortDir] = useState('asc')

  const [actioningId, setActioningId] = useState(null)

  const [modalOpen, setModalOpen] = useState(false)
  const [editingProduct, setEditingProduct] = useState(null)
  const [saving, setSaving] = useState(false)

  const hasFilters = debouncedSearch.trim() !== '' || categoryFilter !== '' || brandFilter !== ''

  useEffect(() => {
    axiosClient.get('/categories').then((res) => setCategories(res.data.data)).catch(() => {})
    axiosClient.get('/brands').then((res) => setBrands(res.data.data)).catch(() => {})
  }, [])

  // Reset to page 0 whenever the effective filters change.
  useEffect(() => {
    setPage(0)
  }, [debouncedSearch, categoryFilter, brandFilter, size])

  async function loadProducts() {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.get('/products', {
        params: {
          search: debouncedSearch || undefined,
          categoryId: categoryFilter || undefined,
          brandId: brandFilter || undefined,
          page,
          size,
        },
      })
      const pageData = res.data.data
      setProducts(pageData.content)
      setTotalElements(pageData.totalElements)
      setTotalPages(pageData.totalPages)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load products')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadProducts()
  }, [debouncedSearch, categoryFilter, brandFilter, page, size])

  // Sorting is client-side, applied only to the current page — the backend
  // doesn't support a `sort` query param yet, so this can't sort across pages.
  const sortedProducts = useMemo(() => {
    if (!sortKey) return products
    const copy = [...products]
    copy.sort((a, b) => {
      let av = a[sortKey]
      let bv = b[sortKey]
      if (sortKey === 'sellingPrice' || sortKey === 'totalStock') {
        av = Number(av)
        bv = Number(bv)
      } else {
        av = String(av ?? '').toLowerCase()
        bv = String(bv ?? '').toLowerCase()
      }
      if (av < bv) return sortDir === 'asc' ? -1 : 1
      if (av > bv) return sortDir === 'asc' ? 1 : -1
      return 0
    })
    return copy
  }, [products, sortKey, sortDir])

  function toggleSort(key) {
    if (sortKey !== key) {
      setSortKey(key)
      setSortDir('asc')
    } else if (sortDir === 'asc') {
      setSortDir('desc')
    } else {
      setSortKey(null)
    }
  }

  function clearFilters() {
    setSearchInput('')
    setCategoryFilter('')
    setBrandFilter('')
  }

  function openCreateModal() {
    setEditingProduct(null)
    setModalOpen(true)
  }

  function openEditModal(product) {
    setEditingProduct(product)
    setModalOpen(true)
  }

  async function handleModalSubmit(payload) {
    setSaving(true)
    try {
      if (editingProduct) {
        await axiosClient.put(`/products/${editingProduct.id}`, payload)
        showToast('Product updated', { type: 'success' })
      } else {
        await axiosClient.post('/products', payload)
        showToast('Product created', { type: 'success' })
      }
      setModalOpen(false)
      await loadProducts()
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to save product', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDeactivate(product) {
    setActioningId(product.id)
    try {
      await axiosClient.patch(`/products/${product.id}/deactivate`)
      setProducts((prev) => prev.map((p) => (p.id === product.id ? { ...p, active: false } : p)))
      showToast(`${product.name} deactivated`, { type: 'success' })
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to deactivate product', { type: 'error' })
    } finally {
      setActioningId(null)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Products</h2>
          <p className="text-navy-400">Browse and manage the product catalog.</p>
        </div>
        {canEdit && (
          <Button onClick={openCreateModal} className="w-full sm:w-auto">
            <Plus size={16} /> Add Product
          </Button>
        )}
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative sm:w-72">
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-navy-300" />
          <Input
            placeholder="Search by name or SKU"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            className="pl-9"
            aria-label="Search products"
          />
        </div>
        <Select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)} className="sm:w-48" aria-label="Filter by category">
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </Select>
        <Select value={brandFilter} onChange={(e) => setBrandFilter(e.target.value)} className="sm:w-48" aria-label="Filter by brand">
          <option value="">All brands</option>
          {brands.map((b) => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </Select>
        {hasFilters && (
          <Button variant="ghost" onClick={clearFilters} className="text-xs">
            <X size={14} /> Clear filters
          </Button>
        )}
      </div>

      {error && <ErrorState message={error} />}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                {COLUMNS.map((col) => (
                  <th key={col.key} className={`px-4 py-3 font-medium ${col.align === 'right' ? 'text-right' : ''}`}>
                    {col.sortable ? (
                      <button
                        onClick={() => toggleSort(col.key)}
                        className={`inline-flex items-center gap-1 hover:text-navy-700 ${col.align === 'right' ? 'flex-row-reverse' : ''}`}
                      >
                        {col.label}
                        {sortKey === col.key && (sortDir === 'asc' ? <ChevronUp size={14} /> : <ChevronDown size={14} />)}
                      </button>
                    ) : (
                      col.label
                    )}
                  </th>
                ))}
              </tr>
            </thead>
            {loading ? (
              <SkeletonRows rows={size > 10 ? 10 : size} columns={COLUMNS.length} />
            ) : (
              !error &&
              sortedProducts.length > 0 && (
                <tbody className="divide-y divide-navy-100">
                  {sortedProducts.map((p) => {
                    const level = stockLevel(p)
                    const margin = marginPercent(p)
                    return (
                      <tr
                        key={p.id}
                        className={`transition-colors duration-150 hover:bg-navy-50/50 ${
                          level !== 'ok' ? 'bg-orange-50/40' : ''
                        }`}
                      >
                        <td className="px-4 py-3 text-navy-600">{p.sku}</td>
                        <td className="px-4 py-3 font-medium text-navy-800">{p.name}</td>
                        <td className="px-4 py-3 text-navy-600">{p.categoryName || '—'}</td>
                        <td className="px-4 py-3 text-navy-600">{p.brandName || '—'}</td>
                        <td className="px-4 py-3 text-right text-navy-600">{Number(p.costPrice).toFixed(2)}</td>
                        <td className="px-4 py-3 text-right text-navy-800">{Number(p.sellingPrice).toFixed(2)}</td>
                        <td className="px-4 py-3 text-right text-navy-600">
                          {margin === null ? '—' : `${margin.toFixed(1)}%`}
                        </td>
                        <td className="px-4 py-3 text-right">
                          <span className="inline-flex items-center justify-end gap-2">
                            <span className={`h-2 w-2 rounded-full ${STOCK_DOT_CLASS[level]}`} aria-hidden="true" />
                            {Number(p.totalStock)}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <Badge variant={p.active ? 'success' : 'neutral'}>{p.active ? 'Active' : 'Inactive'}</Badge>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <RowActionsMenu
                            label={`Actions for ${p.name}`}
                            items={[
                              ...(canEdit
                                ? [{ label: 'Edit', icon: <Pencil size={14} />, onClick: () => openEditModal(p) }]
                                : []),
                              ...(canDeactivate
                                ? [
                                    {
                                      label: 'Deactivate',
                                      onClick: () => handleDeactivate(p),
                                      disabled: !p.active || actioningId === p.id,
                                      danger: true,
                                    },
                                  ]
                                : []),
                            ]}
                          />
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              )
            )}
          </table>

          {!loading && !error && sortedProducts.length === 0 && (
            hasFilters ? (
              <EmptyState
                icon={Search}
                title="No products match your filters"
                description="Try a different search term or clear the filters."
                action={<Button variant="secondary" onClick={clearFilters}>Clear filters</Button>}
              />
            ) : (
              <EmptyState
                icon={Package}
                title="No products yet"
                description="Add your first product to start building the catalog."
                action={canEdit && <Button onClick={openCreateModal}><Plus size={16} /> Add Product</Button>}
              />
            )
          )}
        </div>

        {!loading && !error && totalElements > 0 && (
          <div className="flex flex-col gap-3 border-t border-navy-100 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2 text-sm text-navy-500">
              <span>{totalElements} products</span>
              <Select value={size} onChange={(e) => setSize(Number(e.target.value))} className="w-auto py-1" aria-label="Page size">
                {PAGE_SIZE_OPTIONS.map((opt) => (
                  <option key={opt} value={opt}>{opt} / page</option>
                ))}
              </Select>
            </div>
            <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
          </div>
        )}
      </div>

      <ProductModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onSubmit={handleModalSubmit}
        initial={editingProduct}
        categories={categories}
        brands={brands}
        saving={saving}
      />
    </div>
  )
}
