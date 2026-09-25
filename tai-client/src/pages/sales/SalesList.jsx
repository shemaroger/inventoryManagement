import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, ShoppingCart } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import Button from '../../components/ui/Button'
import Select from '../../components/ui/Select'
import Badge from '../../components/ui/Badge'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import TableSearchInput from '../../components/ui/TableSearchInput'
import useDebouncedValue from '../../hooks/useDebouncedValue'
import { SALE_STATUS_VARIANT, SALE_STATUS_LABEL, PAYMENT_TYPE_VARIANT } from './statusBadge'

const STATUS_OPTIONS = ['QUOTATION', 'CONFIRMED', 'COMPLETED', 'CANCELLED']

export default function SalesList() {
  const { hasRole } = useAuth()
  const canCreate = hasRole('ADMIN') || hasRole('MANAGER')
  const navigate = useNavigate()

  const [sales, setSales] = useState([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [customers, setCustomers] = useState([])
  const [customerFilter, setCustomerFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const debouncedSearch = useDebouncedValue(searchInput, 300)

  useEffect(() => {
    axiosClient.get('/customers').then((res) => setCustomers(res.data.data)).catch(() => {})
  }, [])

  useEffect(() => {
    setPage(0)
  }, [customerFilter, statusFilter, debouncedSearch])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    axiosClient
      .get('/sales', {
        params: {
          customerId: customerFilter || undefined,
          status: statusFilter || undefined,
          search: debouncedSearch.trim() || undefined,
          page,
          size: 10,
        },
      })
      .then((res) => {
        if (cancelled) return
        const pageData = res.data.data
        setSales(pageData.content)
        setTotalElements(pageData.totalElements)
        setTotalPages(pageData.totalPages)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load sales')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [customerFilter, statusFilter, debouncedSearch, page])

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Sales</h2>
          <p className="text-navy-400">Quotations, confirmed orders, and completed sales.</p>
        </div>
        {canCreate && (
          <Button onClick={() => navigate('/sales/new')} className="w-full sm:w-auto">
            <Plus size={16} /> New Sale
          </Button>
        )}
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <TableSearchInput value={searchInput} onChange={setSearchInput} placeholder="Search by sale # or customer name…" className="sm:w-72" />
        <Select value={customerFilter} onChange={(e) => setCustomerFilter(e.target.value)} className="sm:w-56" aria-label="Filter by customer">
          <option value="">All customers</option>
          {customers.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </Select>
        <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="sm:w-48" aria-label="Filter by status">
          <option value="">All statuses</option>
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>{SALE_STATUS_LABEL[s]}</option>
          ))}
        </Select>
      </div>

      {error && <ErrorState message={error} />}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                <th className="px-4 py-3 font-medium">Sale #</th>
                <th className="px-4 py-3 font-medium">Customer</th>
                <th className="px-4 py-3 font-medium">Warehouse</th>
                <th className="px-4 py-3 font-medium">Date</th>
                <th className="px-4 py-3 font-medium">Payment</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium text-right">Total (incl. VAT)</th>
              </tr>
            </thead>
            {loading ? (
              <SkeletonRows rows={6} columns={7} />
            ) : (
              !error &&
              sales.length > 0 && (
                <tbody className="divide-y divide-navy-100">
                  {sales.map((s) => (
                    <tr
                      key={s.id}
                      onClick={() => navigate(`/sales/${s.id}`)}
                      className="cursor-pointer transition-colors duration-150 hover:bg-navy-50/50"
                    >
                      <td className="px-4 py-3 font-medium text-navy-800">#{s.id}</td>
                      <td className="px-4 py-3 text-navy-600">{s.customerName}</td>
                      <td className="px-4 py-3 text-navy-600">{s.warehouseName}</td>
                      <td className="px-4 py-3 text-navy-600">{s.saleDate}</td>
                      <td className="px-4 py-3">
                        <Badge variant={PAYMENT_TYPE_VARIANT[s.paymentType]}>{s.paymentType}</Badge>
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant={SALE_STATUS_VARIANT[s.status]}>{SALE_STATUS_LABEL[s.status]}</Badge>
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums font-medium text-navy-800">
                        {Number(s.totalAmount).toFixed(2)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              )
            )}
          </table>

          {!loading && !error && sales.length === 0 && (
            <EmptyState
              icon={ShoppingCart}
              title="No sales yet"
              description="Create your first quotation to start selling."
              action={canCreate && <Button onClick={() => navigate('/sales/new')}><Plus size={16} /> New Sale</Button>}
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
