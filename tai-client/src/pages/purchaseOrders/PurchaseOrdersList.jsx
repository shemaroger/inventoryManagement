import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, ClipboardList, Sparkles } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Select from '../../components/ui/Select'
import Badge from '../../components/ui/Badge'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import TableSearchInput from '../../components/ui/TableSearchInput'
import useDebouncedValue from '../../hooks/useDebouncedValue'
import { PO_STATUS_VARIANT, PO_STATUS_LABEL } from './statusBadge'

const STATUS_OPTIONS = ['DRAFT', 'SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED']

export default function PurchaseOrdersList() {
  const { hasRole } = useAuth()
  const canCreate = hasRole('ADMIN') || hasRole('MANAGER')
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [suggesting, setSuggesting] = useState(false)

  const [orders, setOrders] = useState([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [suppliers, setSuppliers] = useState([])
  const [supplierFilter, setSupplierFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const debouncedSearch = useDebouncedValue(searchInput, 300)

  useEffect(() => {
    axiosClient.get('/suppliers').then((res) => setSuppliers(res.data.data)).catch(() => {})
  }, [])

  useEffect(() => {
    setPage(0)
  }, [supplierFilter, statusFilter, debouncedSearch])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    axiosClient
      .get('/purchase-orders', {
        params: {
          supplierId: supplierFilter || undefined,
          status: statusFilter || undefined,
          search: debouncedSearch.trim() || undefined,
          page,
          size: 10,
        },
      })
      .then((res) => {
        if (cancelled) return
        const pageData = res.data.data
        setOrders(pageData.content)
        setTotalElements(pageData.totalElements)
        setTotalPages(pageData.totalPages)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load purchase orders')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [supplierFilter, statusFilter, debouncedSearch, page])

  async function handleAiSuggest() {
    setSuggesting(true)
    try {
      const res = await axiosClient.post('/ai/suggest-purchase-order', {})
      const suggestion = res.data.data
      if (!suggestion.suggestedLines || suggestion.suggestedLines.length === 0) {
        showToast(suggestion.message || 'Nothing to suggest right now', { type: 'error' })
        return
      }
      navigate('/purchase-orders/new', { state: { suggestion } })
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to generate a suggestion', { type: 'error' })
    } finally {
      setSuggesting(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Purchase Orders</h2>
          <p className="text-navy-400">Create and track orders placed with suppliers.</p>
        </div>
        {canCreate && (
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button variant="secondary" onClick={handleAiSuggest} loading={suggesting} className="w-full sm:w-auto">
              <Sparkles size={16} /> AI Suggest
            </Button>
            <Button onClick={() => navigate('/purchase-orders/new')} className="w-full sm:w-auto">
              <Plus size={16} /> New Purchase Order
            </Button>
          </div>
        )}
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <TableSearchInput value={searchInput} onChange={setSearchInput} placeholder="Search by PO # or supplier name…" className="sm:w-72" />
        <Select value={supplierFilter} onChange={(e) => setSupplierFilter(e.target.value)} className="sm:w-56" aria-label="Filter by supplier">
          <option value="">All suppliers</option>
          {suppliers.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </Select>
        <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="sm:w-48" aria-label="Filter by status">
          <option value="">All statuses</option>
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>{PO_STATUS_LABEL[s]}</option>
          ))}
        </Select>
      </div>

      {error && <ErrorState message={error} />}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                <th className="px-4 py-3 font-medium">PO #</th>
                <th className="px-4 py-3 font-medium">Supplier</th>
                <th className="px-4 py-3 font-medium">Warehouse</th>
                <th className="px-4 py-3 font-medium">Order date</th>
                <th className="px-4 py-3 font-medium">Status</th>
              </tr>
            </thead>
            {loading ? (
              <SkeletonRows rows={6} columns={5} />
            ) : (
              !error &&
              orders.length > 0 && (
                <tbody className="divide-y divide-navy-100">
                  {orders.map((po) => (
                    <tr
                      key={po.id}
                      onClick={() => navigate(`/purchase-orders/${po.id}`)}
                      className="cursor-pointer transition-colors duration-150 hover:bg-navy-50/50"
                    >
                      <td className="px-4 py-3 font-medium text-navy-800">#{po.id}</td>
                      <td className="px-4 py-3 text-navy-600">{po.supplierName}</td>
                      <td className="px-4 py-3 text-navy-600">{po.warehouseName}</td>
                      <td className="px-4 py-3 text-navy-600">{po.orderDate}</td>
                      <td className="px-4 py-3">
                        <Badge variant={PO_STATUS_VARIANT[po.status]}>{PO_STATUS_LABEL[po.status]}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              )
            )}
          </table>

          {!loading && !error && orders.length === 0 && (
            <EmptyState
              icon={ClipboardList}
              title="No purchase orders yet"
              description="Create your first purchase order to start restocking."
              action={canCreate && <Button onClick={() => navigate('/purchase-orders/new')}><Plus size={16} /> New Purchase Order</Button>}
            />
          )}
        </div>

        {!loading && !error && totalElements > 0 && (
          <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
        )}
      </div>
    </div>
  )
}
