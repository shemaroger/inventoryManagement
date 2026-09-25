import { useEffect, useState } from 'react'
import { PackageSearch, ShoppingCart, XCircle } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Select from '../../components/ui/Select'
import Badge from '../../components/ui/Badge'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import CreatePoFromSuggestionModal from './CreatePoFromSuggestionModal'
import DismissSuggestionModal from './DismissSuggestionModal'

const URGENCY_VARIANT = { low: 'neutral', medium: 'orange', high: 'danger' }
const STATUS_VARIANT = { OPEN: 'orange', ORDERED: 'success', DISMISSED: 'neutral', RESOLVED: 'success' }

export default function ReordersList() {
  const { hasRole } = useAuth()
  const canAct = hasRole('ADMIN') || hasRole('MANAGER')
  const { showToast } = useToast()

  const [suggestions, setSuggestions] = useState([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [statusFilter, setStatusFilter] = useState('OPEN')
  const [warehouses, setWarehouses] = useState([])
  const [warehouseFilter, setWarehouseFilter] = useState('')

  const [poTarget, setPoTarget] = useState(null)
  const [dismissTarget, setDismissTarget] = useState(null)
  const [saving, setSaving] = useState(false)
  const [refreshing, setRefreshing] = useState(false)

  useEffect(() => {
    axiosClient.get('/warehouses').then((res) => setWarehouses(res.data.data)).catch(() => {})
  }, [])

  useEffect(() => {
    setPage(0)
  }, [statusFilter, warehouseFilter])

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.get('/reorder-suggestions', {
        params: { status: statusFilter || undefined, warehouseId: warehouseFilter || undefined, page, size: 10 },
      })
      const pageData = res.data.data
      setSuggestions(pageData.content)
      setTotalElements(pageData.totalElements)
      setTotalPages(pageData.totalPages)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load reorder suggestions')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, warehouseFilter, page])

  async function handleRefresh() {
    setRefreshing(true)
    try {
      const res = await axiosClient.post('/reorder-suggestions/refresh')
      showToast(`Checked ${res.data.data.evaluated} product/warehouse combinations`, { type: 'success' })
      await load()
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to refresh', { type: 'error' })
    } finally {
      setRefreshing(false)
    }
  }

  async function handleCreatePo(payload) {
    setSaving(true)
    try {
      await axiosClient.post(`/reorder-suggestions/${poTarget.id}/create-po`, payload)
      showToast('Draft purchase order created', { type: 'success' })
      setPoTarget(null)
      setSuggestions((prev) => prev.filter((s) => s.id !== poTarget.id))
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to create purchase order', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDismiss(payload) {
    setSaving(true)
    try {
      await axiosClient.patch(`/reorder-suggestions/${dismissTarget.id}/dismiss`, payload)
      showToast('Suggestion dismissed', { type: 'success' })
      setDismissTarget(null)
      setSuggestions((prev) => prev.filter((s) => s.id !== dismissTarget.id))
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to dismiss', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Reorder Suggestions</h2>
          <p className="text-navy-400">Automatically tracked across every product and warehouse — no need to check one by one.</p>
        </div>
        {canAct && (
          <Button variant="secondary" onClick={handleRefresh} loading={refreshing} className="w-full sm:w-auto">
            Refresh now
          </Button>
        )}
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="sm:w-44" aria-label="Filter by status">
          <option value="">All statuses</option>
          <option value="OPEN">Open</option>
          <option value="ORDERED">Ordered</option>
          <option value="DISMISSED">Dismissed</option>
          <option value="RESOLVED">Resolved</option>
        </Select>
        <Select value={warehouseFilter} onChange={(e) => setWarehouseFilter(e.target.value)} className="sm:w-48" aria-label="Filter by warehouse">
          <option value="">All warehouses</option>
          {warehouses.map((w) => (
            <option key={w.id} value={w.id}>{w.name}</option>
          ))}
        </Select>
      </div>

      {error && <ErrorState message={error} />}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                <th className="px-4 py-3 font-medium">Product</th>
                <th className="px-4 py-3 font-medium">Warehouse</th>
                <th className="px-4 py-3 font-medium text-right">Current</th>
                <th className="px-4 py-3 font-medium text-right">Reorder level</th>
                <th className="px-4 py-3 font-medium text-right">Suggested qty</th>
                <th className="px-4 py-3 font-medium">Urgency</th>
                <th className="px-4 py-3 font-medium">Stockout</th>
                <th className="px-4 py-3 font-medium">Status</th>
                {canAct && <th className="px-4 py-3 font-medium text-right">Actions</th>}
              </tr>
            </thead>
            {loading ? (
              <SkeletonRows rows={6} columns={canAct ? 9 : 8} />
            ) : (
              !error &&
              suggestions.length > 0 && (
                <tbody className="divide-y divide-navy-100">
                  {suggestions.map((s) => (
                    <tr key={s.id} className="transition-colors duration-150 hover:bg-navy-50/50">
                      <td className="px-4 py-3">
                        <p className="font-medium text-navy-800">{s.productName}</p>
                        <p className="text-xs text-navy-400">{s.productSku}</p>
                      </td>
                      <td className="px-4 py-3 text-navy-600">{s.warehouseName}</td>
                      <td className="px-4 py-3 text-right tabular-nums text-navy-600">{Number(s.currentQuantity)}</td>
                      <td className="px-4 py-3 text-right tabular-nums text-navy-600">{Number(s.reorderLevel)}</td>
                      <td className="px-4 py-3 text-right tabular-nums font-medium text-navy-800">{Number(s.suggestedQuantity)}</td>
                      <td className="px-4 py-3">
                        <Badge variant={URGENCY_VARIANT[s.urgencyLevel]}>{s.urgencyLevel}</Badge>
                      </td>
                      <td className="px-4 py-3 text-navy-600">{s.projectedStockoutDate || '—'}</td>
                      <td className="px-4 py-3">
                        <Badge variant={STATUS_VARIANT[s.status]}>{s.status.toLowerCase()}</Badge>
                      </td>
                      {canAct && (
                        <td className="px-4 py-3 text-right">
                          {s.status === 'OPEN' ? (
                            <div className="flex justify-end gap-1">
                              <Button variant="secondary" onClick={() => setPoTarget(s)} className="px-2 py-1.5 text-xs">
                                <ShoppingCart size={13} /> Create PO
                              </Button>
                              <button
                                onClick={() => setDismissTarget(s)}
                                aria-label={`Dismiss suggestion for ${s.productName}`}
                                className="rounded-full p-1.5 text-navy-400 hover:bg-navy-50 hover:text-navy-600"
                              >
                                <XCircle size={16} />
                              </button>
                            </div>
                          ) : (
                            <span className="text-xs text-navy-300">—</span>
                          )}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              )
            )}
          </table>

          {!loading && !error && suggestions.length === 0 && (
            <EmptyState
              icon={PackageSearch}
              title="Nothing needs reordering right now"
              description="Every tracked product is above its reorder level."
            />
          )}
        </div>

        {!loading && !error && totalElements > 0 && (
          <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
        )}
      </div>

      <CreatePoFromSuggestionModal
        open={!!poTarget}
        onClose={() => setPoTarget(null)}
        onSubmit={handleCreatePo}
        suggestion={poTarget}
        saving={saving}
      />

      <DismissSuggestionModal
        open={!!dismissTarget}
        onClose={() => setDismissTarget(null)}
        onSubmit={handleDismiss}
        suggestion={dismissTarget}
        saving={saving}
      />
    </div>
  )
}
