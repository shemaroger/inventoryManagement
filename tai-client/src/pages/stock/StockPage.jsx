import { useEffect, useMemo, useState } from 'react'
import { Warehouse as WarehouseIcon, AlertTriangle, ArrowLeftRight, Boxes, TrendingDown, CalendarRange } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import TableSearchInput from '../../components/ui/TableSearchInput'
import usePagedList from '../../hooks/usePagedList'
import StockAdjustmentModal from './StockAdjustmentModal'
import ForecastTab from './ForecastTab'
import SeasonalTab from './SeasonalTab'

// Easy to retune: "watch" zone is the % above reorder level still worth flagging amber.
const WATCH_ZONE_MULTIPLIER = 1.2

const TABS = [
  { key: 'warehouse', label: 'By Warehouse', icon: WarehouseIcon },
  { key: 'lowStock', label: 'Low Stock', icon: AlertTriangle },
  { key: 'transfer', label: 'Transfer', icon: ArrowLeftRight },
  { key: 'forecast', label: 'Forecast', icon: TrendingDown },
  { key: 'seasonal', label: 'Seasonal', icon: CalendarRange },
]

// A product's reorder level and SKU live on ProductDto, not StockItemDto — so the
// low-stock indicator needs stock items joined against a product lookup. There's no
// unpaginated "all products" endpoint, so this fetches a large page (size=200) as a
// pragmatic stand-in; if the catalog grows past that, this lookup silently misses the
// rest (surfaced below via `productsTruncated`) — a real `?all=true` endpoint would be
// the clean fix.
function useProductLookup() {
  const [lookup, setLookup] = useState({})
  const [truncated, setTruncated] = useState(false)
  const [loading, setLoading] = useState(true)

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
      .finally(() => setLoading(false))
  }, [])

  return { lookup, truncated, loading }
}

function stockLevel(quantity, reorderLevel) {
  const qty = Number(quantity)
  const reorder = Number(reorderLevel || 0)
  if (reorder <= 0) return 'ok'
  if (qty <= reorder) return 'danger'
  if (qty <= reorder * WATCH_ZONE_MULTIPLIER) return 'warning'
  return 'ok'
}

const LEVEL_DOT_CLASS = {
  danger: 'bg-red-500',
  warning: 'bg-orange-500',
  ok: 'bg-green-500',
}

const LEVEL_BADGE_VARIANT = {
  danger: 'danger',
  warning: 'orange',
  ok: 'success',
}

export default function StockPage() {
  const { showToast } = useToast()
  const [activeTab, setActiveTab] = useState('warehouse')

  const { lookup: productLookup, truncated: productsTruncated, loading: productsLoading } = useProductLookup()

  const [warehouses, setWarehouses] = useState([])
  const [warehousesLoading, setWarehousesLoading] = useState(true)
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(null)

  const [stockItems, setStockItems] = useState([])
  const [stockLoading, setStockLoading] = useState(false)
  const [stockError, setStockError] = useState(null)

  const [stockSearch, setStockSearch] = useState('')

  const [adjustModalOpen, setAdjustModalOpen] = useState(false)
  const [adjustTarget, setAdjustTarget] = useState(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    axiosClient
      .get('/warehouses')
      .then((res) => {
        setWarehouses(res.data.data)
        if (res.data.data.length > 0) setSelectedWarehouseId(res.data.data[0].id)
      })
      .catch(() => {})
      .finally(() => setWarehousesLoading(false))
  }, [])

  async function loadWarehouseStock(warehouseId) {
    setStockLoading(true)
    setStockError(null)
    try {
      const res = await axiosClient.get(`/stock/warehouse/${warehouseId}`)
      setStockItems(res.data.data)
    } catch (err) {
      setStockError(err.response?.data?.message || 'Failed to load stock')
    } finally {
      setStockLoading(false)
    }
  }

  useEffect(() => {
    if (selectedWarehouseId != null) loadWarehouseStock(selectedWarehouseId)
    setStockPage(0)
  }, [selectedWarehouseId])

  const enrichedStockItems = useMemo(() => {
    return stockItems.map((item) => {
      const product = productLookup[item.productId]
      return {
        ...item,
        sku: product?.sku,
        reorderLevel: product?.reorderLevel,
        level: stockLevel(item.quantity, product?.reorderLevel),
      }
    })
  }, [stockItems, productLookup])

  const filteredStockItems = useMemo(() => {
    const q = stockSearch.trim().toLowerCase()
    if (!q) return enrichedStockItems
    return enrichedStockItems.filter(
      (item) => item.productName.toLowerCase().includes(q) || (item.sku || '').toLowerCase().includes(q)
    )
  }, [enrichedStockItems, stockSearch])

  const { page: stockPage, setPage: setStockPage, totalPages: stockTotalPages, pageItems: pagedStockItems } = usePagedList(filteredStockItems, 10)

  const productsForModal = useMemo(
    () => Object.values(productLookup).map((p) => ({ id: p.id, name: p.name, sku: p.sku })),
    [productLookup]
  )

  function openAdjustModal(item) {
    setAdjustTarget({ productId: item.productId, warehouseId: item.warehouseId })
    setAdjustModalOpen(true)
  }

  function openNewAdjustment() {
    setAdjustTarget({ productId: null, warehouseId: selectedWarehouseId })
    setAdjustModalOpen(true)
  }

  async function handleAdjustSubmit(payload) {
    setSaving(true)
    try {
      await axiosClient.post('/stock/adjustments', payload)
      showToast('Stock adjustment applied', { type: 'success' })
      setAdjustModalOpen(false)
      if (selectedWarehouseId != null) await loadWarehouseStock(selectedWarehouseId)
    } catch (err) {
      showToast(err.response?.data?.message || 'Adjustment failed', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-navy-800">Stock</h2>
        <p className="text-navy-400">View and adjust inventory across warehouses.</p>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-navy-100">
        {TABS.map((tab) => {
          const Icon = tab.icon
          return (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-2 border-b-2 px-4 py-2 text-sm font-medium transition-colors duration-150 ${
                activeTab === tab.key
                  ? 'border-orange-500 text-orange-600'
                  : 'border-transparent text-navy-400 hover:text-navy-600'
              }`}
            >
              <Icon size={16} /> {tab.label}
            </button>
          )
        })}
      </div>

      {productsTruncated && (
        <div className="rounded-md border border-orange-200 bg-orange-50 px-3 py-2 text-xs text-orange-700">
          Product catalog has more than 200 items — stock/reorder data below may be incomplete for products beyond that limit. A dedicated unpaginated product lookup endpoint would fix this.
        </div>
      )}

      {activeTab === 'warehouse' && (
        <div className="space-y-4">
          {warehousesLoading ? (
            <div className="h-9 w-64 animate-pulse rounded-md bg-navy-100" />
          ) : warehouses.length === 0 ? (
            <EmptyState icon={WarehouseIcon} title="No warehouses yet" description="Add a warehouse to start tracking stock." />
          ) : (
            <>
              <div className="flex flex-wrap gap-2">
                {warehouses.map((w) => (
                  <button
                    key={w.id}
                    onClick={() => setSelectedWarehouseId(w.id)}
                    className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors duration-150 ${
                      selectedWarehouseId === w.id
                        ? 'bg-navy-700 text-white'
                        : 'bg-navy-50 text-navy-600 hover:bg-navy-100'
                    }`}
                  >
                    {w.name}
                  </button>
                ))}
              </div>

              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <TableSearchInput value={stockSearch} onChange={setStockSearch} placeholder="Search by product name or SKU…" className="sm:w-80" />
                <Button onClick={openNewAdjustment} className="w-full px-4 py-1.5 text-sm sm:w-auto">
                  Record Adjustment
                </Button>
              </div>

              {stockError && <ErrorState message={stockError} />}

              <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead className="bg-navy-50 text-navy-500">
                      <tr>
                        <th className="px-4 py-3 font-medium">Product</th>
                        <th className="px-4 py-3 font-medium">SKU</th>
                        <th className="px-4 py-3 font-medium text-right">Quantity</th>
                        <th className="px-4 py-3 font-medium">Status</th>
                        <th className="px-4 py-3 font-medium text-right">Action</th>
                      </tr>
                    </thead>
                    {(stockLoading || productsLoading) ? (
                      <SkeletonRows rows={5} columns={5} />
                    ) : (
                      !stockError &&
                      pagedStockItems.length > 0 && (
                        <tbody className="divide-y divide-navy-100">
                          {pagedStockItems.map((item) => (
                            <tr key={item.id} className="transition-colors duration-150 hover:bg-navy-50/50">
                              <td className="px-4 py-3 font-medium text-navy-800">{item.productName}</td>
                              <td className="px-4 py-3 text-navy-600">{item.sku || '—'}</td>
                              <td className="px-4 py-3 text-right">
                                <span className="inline-flex items-center justify-end gap-2">
                                  <span className={`h-2 w-2 rounded-full ${LEVEL_DOT_CLASS[item.level]}`} aria-hidden="true" />
                                  {Number(item.quantity)}
                                </span>
                              </td>
                              <td className="px-4 py-3">
                                <Badge variant={LEVEL_BADGE_VARIANT[item.level]}>
                                  {item.level === 'danger' ? 'Low' : item.level === 'warning' ? 'Watch' : 'Healthy'}
                                </Badge>
                              </td>
                              <td className="px-4 py-3 text-right">
                                <Button variant="secondary" onClick={() => openAdjustModal(item)} className="px-3 py-1.5 text-xs">
                                  Adjust
                                </Button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      )
                    )}
                  </table>

                  {!stockLoading && !productsLoading && !stockError && pagedStockItems.length === 0 && (
                    <EmptyState
                      icon={Boxes}
                      title={enrichedStockItems.length === 0 ? 'No stock recorded' : 'No stock items match your search'}
                      description={enrichedStockItems.length === 0 ? 'This warehouse has no stock items yet.' : 'Try a different search term.'}
                    />
                  )}
                </div>

                {!stockLoading && !productsLoading && !stockError && filteredStockItems.length > 0 && (
                  <TablePagination page={stockPage} totalPages={stockTotalPages} onPageChange={setStockPage} />
                )}
              </div>
            </>
          )}
        </div>
      )}

      {activeTab === 'lowStock' && (
        <div className="rounded-lg border border-dashed border-navy-200 bg-white p-8 text-center text-sm text-navy-400">
          Low Stock tab coming next.
        </div>
      )}

      {activeTab === 'transfer' && (
        <div className="rounded-lg border border-dashed border-navy-200 bg-white p-8 text-center text-sm text-navy-400">
          Transfer tab coming next.
        </div>
      )}

      {activeTab === 'forecast' && <ForecastTab products={productsForModal} />}

      {activeTab === 'seasonal' && <SeasonalTab products={productsForModal} />}

      <StockAdjustmentModal
        open={adjustModalOpen}
        onClose={() => setAdjustModalOpen(false)}
        onSubmit={handleAdjustSubmit}
        products={productsForModal}
        warehouses={warehouses}
        initialProductId={adjustTarget?.productId}
        initialWarehouseId={adjustTarget?.warehouseId}
        lockProduct={Boolean(adjustTarget?.productId)}
        lockWarehouse={Boolean(adjustTarget?.warehouseId)}
        saving={saving}
      />
    </div>
  )
}
