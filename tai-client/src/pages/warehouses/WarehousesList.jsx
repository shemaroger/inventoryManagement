import { useEffect, useMemo, useState } from 'react'
import { Plus, Warehouse as WarehouseIcon, LayoutGrid, List, Boxes, AlertTriangle, MapPin, Search, Trash2, Pencil, PowerOff } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import useDebouncedValue from '../../hooks/useDebouncedValue'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Badge from '../../components/ui/Badge'
import { SkeletonRows, default as Skeleton } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import usePagedList from '../../hooks/usePagedList'
import WarehouseModal from './WarehouseModal'
import DeleteWarehouseModal from './DeleteWarehouseModal'

const SORT_OPTIONS = [
  { value: 'name', label: 'Name' },
  { value: 'totalUnits', label: 'Total stock' },
  { value: 'totalSkus', label: 'SKU count' },
]

// Same pragmatic stand-in as the Stock page: no unpaginated products endpoint exists,
// so a large page is fetched once and reused for the reorder-level join. Flagged via
// `productsTruncated` if the catalog exceeds this page size.
function useProductLookup() {
  const [lookup, setLookup] = useState({})
  const [truncated, setTruncated] = useState(false)

  useEffect(() => {
    axiosClient
      .get('/products', { params: { size: 200 } })
      .then((res) => {
        const pageData = res.data.data
        const map = {}
        pageData.content.forEach((p) => {
          map[p.id] = p
        })
        setLookup(map)
        setTruncated(pageData.totalElements > pageData.content.length)
      })
      .catch(() => {})
  }, [])

  return { lookup, truncated }
}

function summarize(stockItems, productLookup) {
  const skuIds = new Set()
  let totalUnits = 0
  let lowStockCount = 0

  for (const item of stockItems) {
    skuIds.add(item.productId)
    const qty = Number(item.quantity)
    totalUnits += qty
    const reorderLevel = Number(productLookup[item.productId]?.reorderLevel || 0)
    if (reorderLevel > 0 && qty <= reorderLevel) lowStockCount += 1
  }

  return { totalSkus: skuIds.size, totalUnits, lowStockCount }
}

function WarehouseAvatar({ active }) {
  return (
    <div
      className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${
        active ? 'bg-navy-100 text-navy-600' : 'bg-navy-50 text-navy-300'
      }`}
    >
      <WarehouseIcon size={17} />
    </div>
  )
}

export default function WarehousesList() {
  const { hasRole } = useAuth()
  const canEdit = hasRole('ADMIN') || hasRole('MANAGER')
  const canDeactivate = hasRole('ADMIN')
  const { showToast } = useToast()

  const { lookup: productLookup, truncated: productsTruncated } = useProductLookup()

  const [warehouses, setWarehouses] = useState([])
  const [summaries, setSummaries] = useState({}) // warehouseId -> { totalSkus, totalUnits, lowStockCount }
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [viewMode, setViewMode] = useState('list')

  const [search, setSearch] = useState('')
  const debouncedSearch = useDebouncedValue(search, 300)
  const [sortKey, setSortKey] = useState('name')
  const [sortDir, setSortDir] = useState('asc')

  const [modalOpen, setModalOpen] = useState(false)
  const [editingWarehouse, setEditingWarehouse] = useState(null)
  const [saving, setSaving] = useState(false)

  const [deletingWarehouse, setDeletingWarehouse] = useState(null)
  const [deleting, setDeleting] = useState(false)

  async function loadWarehouses() {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.get('/warehouses')
      const list = res.data.data
      setWarehouses(list)

      // No aggregate "stock per warehouse" endpoint exists, so summaries are built by
      // fetching each warehouse's stock in parallel — fine at the scale of a handful of
      // warehouses, but would be worth a dedicated backend summary endpoint if this list
      // grows large.
      const results = await Promise.all(
        list.map((w) =>
          axiosClient
            .get(`/stock/warehouse/${w.id}`)
            .then((r) => [w.id, r.data.data])
            .catch(() => [w.id, []])
        )
      )
      setSummaries(Object.fromEntries(results.map(([id, items]) => [id, items])))
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load warehouses')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadWarehouses()
  }, [])

  const filteredWarehouses = useMemo(() => {
    const term = debouncedSearch.trim().toLowerCase()
    if (!term) return warehouses
    return warehouses.filter(
      (w) => w.name.toLowerCase().includes(term) || (w.location || '').toLowerCase().includes(term)
    )
  }, [warehouses, debouncedSearch])

  const warehouseSummaries = useMemo(() => {
    const result = {}
    for (const w of warehouses) {
      result[w.id] = summarize(summaries[w.id] || [], productLookup)
    }
    return result
  }, [warehouses, summaries, productLookup])

  const sortedWarehouses = useMemo(() => {
    const copy = [...filteredWarehouses]
    copy.sort((a, b) => {
      let av, bv
      if (sortKey === 'name') {
        av = a.name.toLowerCase()
        bv = b.name.toLowerCase()
      } else {
        av = warehouseSummaries[a.id]?.[sortKey] || 0
        bv = warehouseSummaries[b.id]?.[sortKey] || 0
      }
      if (av < bv) return sortDir === 'asc' ? -1 : 1
      if (av > bv) return sortDir === 'asc' ? 1 : -1
      return 0
    })
    return copy
  }, [filteredWarehouses, warehouseSummaries, sortKey, sortDir])

  const { page, setPage, totalPages, pageItems: pagedWarehouses } = usePagedList(sortedWarehouses, 10)

  function openCreateModal() {
    setEditingWarehouse(null)
    setModalOpen(true)
  }

  function openEditModal(warehouse) {
    setEditingWarehouse(warehouse)
    setModalOpen(true)
  }

  async function handleModalSubmit(payload) {
    setSaving(true)
    try {
      if (editingWarehouse) {
        await axiosClient.put(`/warehouses/${editingWarehouse.id}`, payload)
        showToast('Warehouse updated', { type: 'success' })
      } else {
        await axiosClient.post('/warehouses', payload)
        showToast('Warehouse created', { type: 'success' })
      }
      setModalOpen(false)
      await loadWarehouses()
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to save warehouse', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDeactivate(warehouse) {
    if (!window.confirm(`Deactivate "${warehouse.name}"?`)) return
    try {
      await axiosClient.patch(`/warehouses/${warehouse.id}/deactivate`)
      showToast(`${warehouse.name} deactivated`, { type: 'success' })
      await loadWarehouses()
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to deactivate warehouse', { type: 'error' })
    }
  }

  async function handleDeleteConfirm() {
    if (!deletingWarehouse) return
    setDeleting(true)
    try {
      await axiosClient.delete(`/warehouses/${deletingWarehouse.id}`)
      showToast(`${deletingWarehouse.name} deleted`, { type: 'success' })
      setDeletingWarehouse(null)
      await loadWarehouses()
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to delete warehouse', { type: 'error' })
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Warehouses</h2>
          <p className="text-navy-400">Manage warehouses and see what stock they hold.</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex rounded-md border border-navy-200 p-0.5">
            <button
              onClick={() => setViewMode('cards')}
              aria-label="Card view"
              className={`rounded p-1.5 ${viewMode === 'cards' ? 'bg-navy-100 text-navy-700' : 'text-navy-400 hover:text-navy-600'}`}
            >
              <LayoutGrid size={16} />
            </button>
            <button
              onClick={() => setViewMode('list')}
              aria-label="List view"
              className={`rounded p-1.5 ${viewMode === 'list' ? 'bg-navy-100 text-navy-700' : 'text-navy-400 hover:text-navy-600'}`}
            >
              <List size={16} />
            </button>
          </div>
          {canEdit && (
            <Button onClick={openCreateModal}>
              <Plus size={16} /> Add Warehouse
            </Button>
          )}
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative sm:w-72">
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-navy-300" />
          <Input
            placeholder="Search by name or location"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
            aria-label="Search warehouses"
          />
        </div>
        <div className="flex items-center gap-2">
          <Select value={sortKey} onChange={(e) => setSortKey(e.target.value)} className="sm:w-40" aria-label="Sort by">
            {SORT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>Sort: {opt.label}</option>
            ))}
          </Select>
          <button
            onClick={() => setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))}
            aria-label={sortDir === 'asc' ? 'Sort ascending, click for descending' : 'Sort descending, click for ascending'}
            className="rounded-full border border-navy-200 px-2.5 py-2 text-xs font-medium text-navy-500 hover:bg-navy-50"
          >
            {sortDir === 'asc' ? '↑ Asc' : '↓ Desc'}
          </button>
        </div>
      </div>

      {productsTruncated && (
        <div className="rounded-md border border-orange-200 bg-orange-50 px-3 py-2 text-xs text-orange-700">
          Product catalog has more than 200 items — low-stock counts below may be incomplete for products beyond that limit.
        </div>
      )}

      {error && <ErrorState message={error} />}

      {loading ? (
        viewMode === 'cards' ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="space-y-3 rounded-lg border border-navy-100 bg-white p-5 shadow-sm">
                <Skeleton className="h-5 w-2/3" />
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-4 w-full" />
              </div>
            ))}
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
            <table className="w-full text-left text-sm">
              <thead className="bg-navy-50 text-navy-500">
                <tr>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide">Warehouse</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide">Location</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide">SKUs</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide">Units</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide">Low stock</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide">Status</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide">Actions</th>
                </tr>
              </thead>
              <SkeletonRows rows={4} columns={7} />
            </table>
          </div>
        )
      ) : !error && warehouses.length === 0 ? (
        <EmptyState
          icon={WarehouseIcon}
          title="No warehouses yet"
          description="Add your first warehouse to start tracking stock."
          action={canEdit && <Button onClick={openCreateModal}><Plus size={16} /> Add your first warehouse</Button>}
        />
      ) : !error && filteredWarehouses.length === 0 ? (
        <EmptyState
          icon={Search}
          title="No matching warehouses"
          description="Try a different search term."
          action={<Button variant="secondary" onClick={() => setSearch('')}>Clear search</Button>}
        />
      ) : !error && viewMode === 'cards' ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {pagedWarehouses.map((w) => {
            const summary = warehouseSummaries[w.id] || { totalSkus: 0, totalUnits: 0, lowStockCount: 0 }
            return (
              <div
                key={w.id}
                className="space-y-3 rounded-lg border border-navy-100 bg-white p-5 shadow-sm"
              >
                <div className="flex items-start justify-between">
                  <div>
                    <h3 className="font-semibold text-navy-800">{w.name}</h3>
                    {w.location && (
                      <p className="mt-0.5 flex items-center gap-1 text-xs text-navy-400">
                        <MapPin size={12} /> {w.location}
                      </p>
                    )}
                  </div>
                  <Badge variant={w.active ? 'success' : 'neutral'}>{w.active ? 'Active' : 'Inactive'}</Badge>
                </div>

                <div className="grid grid-cols-3 gap-2 rounded-md bg-navy-50 px-3 py-2 text-center">
                  <div>
                    <p className="text-lg font-bold text-navy-800">{summary.totalSkus}</p>
                    <p className="text-[11px] text-navy-400">SKUs</p>
                  </div>
                  <div>
                    <p className="text-lg font-bold text-navy-800">{summary.totalUnits}</p>
                    <p className="text-[11px] text-navy-400">Units</p>
                  </div>
                  <div>
                    <p className={`text-lg font-bold ${summary.lowStockCount > 0 ? 'text-orange-600' : 'text-navy-800'}`}>
                      {summary.lowStockCount}
                    </p>
                    <p className="text-[11px] text-navy-400">Low stock</p>
                  </div>
                </div>

                {canEdit && (
                  <div className="flex justify-end gap-2 pt-1">
                    <Button variant="ghost" onClick={() => openEditModal(w)} className="px-2 py-1 text-xs">
                      Edit
                    </Button>
                    {canDeactivate && w.active && (
                      <Button variant="ghost" onClick={() => handleDeactivate(w)} className="px-2 py-1 text-xs text-red-600 hover:bg-red-50">
                        Deactivate
                      </Button>
                    )}
                    {canDeactivate && (
                      <Button
                        variant="ghost"
                        onClick={() => setDeletingWarehouse(w)}
                        aria-label={`Delete ${w.name}`}
                        className="px-2 py-1 text-xs text-red-600 hover:bg-red-50"
                      >
                        <Trash2 size={13} />
                      </Button>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      ) : (
        !error && (
          <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-navy-50 text-navy-500">
                  <tr>
                    <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide">Warehouse</th>
                    <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide">Location</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide">SKUs</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide">Units</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide">Low stock</th>
                    <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide">Status</th>
                    {canEdit && <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide">Actions</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-navy-100">
                  {pagedWarehouses.map((w) => {
                    const summary = warehouseSummaries[w.id] || { totalSkus: 0, totalUnits: 0, lowStockCount: 0 }
                    return (
                      <tr key={w.id} className="transition-colors duration-150 hover:bg-navy-50/50">
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <WarehouseAvatar active={w.active} />
                            <div className="min-w-0">
                              <p className="truncate font-medium text-navy-800">{w.name}</p>
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-3 text-navy-600">
                          {w.location ? (
                            <span className="inline-flex items-center gap-1">
                              <MapPin size={12} className="text-navy-300" /> {w.location}
                            </span>
                          ) : (
                            <span className="italic text-navy-300">Not set</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums text-navy-600">{summary.totalSkus}</td>
                        <td className="px-4 py-3 text-right tabular-nums font-medium text-navy-800">{summary.totalUnits}</td>
                        <td className="px-4 py-3 text-right">
                          {summary.lowStockCount > 0 ? (
                            <span className="inline-flex items-center gap-1 tabular-nums text-orange-600">
                              <AlertTriangle size={12} /> {summary.lowStockCount}
                            </span>
                          ) : (
                            <span className="tabular-nums text-navy-300">0</span>
                          )}
                        </td>
                        <td className="px-4 py-3">
                          <Badge variant={w.active ? 'success' : 'neutral'}>{w.active ? 'Active' : 'Inactive'}</Badge>
                        </td>
                        {canEdit && (
                          <td className="px-4 py-3 text-right">
                            <div className="flex justify-end gap-1">
                              <button
                                onClick={() => openEditModal(w)}
                                aria-label={`Edit ${w.name}`}
                                className="flex items-center gap-1.5 rounded-full px-2.5 py-1.5 text-xs font-medium text-navy-500 transition-colors duration-150 hover:bg-navy-50 hover:text-navy-700"
                              >
                                <Pencil size={13} /> Edit
                              </button>
                              {canDeactivate && w.active && (
                                <button
                                  onClick={() => handleDeactivate(w)}
                                  aria-label={`Deactivate ${w.name}`}
                                  className="flex items-center gap-1.5 rounded-full px-2.5 py-1.5 text-xs font-medium text-navy-500 transition-colors duration-150 hover:bg-navy-50 hover:text-navy-700"
                                >
                                  <PowerOff size={13} /> Deactivate
                                </button>
                              )}
                              {canDeactivate && (
                                <button
                                  onClick={() => setDeletingWarehouse(w)}
                                  aria-label={`Delete ${w.name}`}
                                  className="flex items-center gap-1.5 rounded-full px-2.5 py-1.5 text-xs font-medium text-red-500 transition-colors duration-150 hover:bg-red-50 hover:text-red-700"
                                >
                                  <Trash2 size={13} />
                                </button>
                              )}
                            </div>
                          </td>
                        )}
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )
      )}

      {!error && filteredWarehouses.length > 0 && (
        <div className="rounded-lg border border-navy-100 bg-white shadow-sm">
          <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </div>
      )}

      <WarehouseModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onSubmit={handleModalSubmit}
        initial={editingWarehouse}
        saving={saving}
      />

      <DeleteWarehouseModal
        open={!!deletingWarehouse}
        onClose={() => setDeletingWarehouse(null)}
        onConfirm={handleDeleteConfirm}
        warehouse={deletingWarehouse}
        summary={deletingWarehouse && warehouseSummaries[deletingWarehouse.id]}
        deleting={deleting}
      />
    </div>
  )
}
