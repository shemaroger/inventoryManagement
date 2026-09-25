import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, PackageCheck, Ban, AlertTriangle, CreditCard } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import { ErrorState } from '../../components/ui/ListStates'
import { SALE_STATUS_VARIANT, SALE_STATUS_LABEL, PAYMENT_TYPE_VARIANT } from './statusBadge'
import SalePaymentModal from './SalePaymentModal'

export default function SaleDetailPage() {
  const { id } = useParams()
  const { hasRole } = useAuth()
  const canManage = hasRole('ADMIN') || hasRole('MANAGER')
  const isAdmin = hasRole('ADMIN')
  const { showToast } = useToast()

  const [sale, setSale] = useState(null)
  const [payments, setPayments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actioning, setActioning] = useState(false)
  const [stockWarnings, setStockWarnings] = useState([])
  const [creditBlock, setCreditBlock] = useState(null)
  const [paymentModalOpen, setPaymentModalOpen] = useState(false)
  const [recordingPayment, setRecordingPayment] = useState(false)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const [saleRes, paymentsRes] = await Promise.all([
        axiosClient.get(`/sales/${id}`),
        axiosClient.get(`/sales/${id}/payments`),
      ])
      setSale(saleRes.data.data)
      setPayments(paymentsRes.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load sale')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [id])

  async function handleConfirm() {
    setActioning(true)
    setStockWarnings([])
    try {
      const res = await axiosClient.patch(`/sales/${id}/confirm`)
      setSale(res.data.data.sale)
      if (res.data.data.stockWarnings?.length) {
        setStockWarnings(res.data.data.stockWarnings)
        showToast('Confirmed with stock warnings — review before completing', { type: 'error' })
      } else {
        showToast('Sale confirmed', { type: 'success' })
      }
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to confirm', { type: 'error' })
    } finally {
      setActioning(false)
    }
  }

  async function handleComplete(override = false) {
    setActioning(true)
    setCreditBlock(null)
    try {
      const res = await axiosClient.patch(`/sales/${id}/complete`, null, { params: override ? { override: true } : {} })
      setSale(res.data.data)
      showToast('Sale completed', { type: 'success' })
    } catch (err) {
      const message = err.response?.data?.message || 'Failed to complete'
      if (message.toLowerCase().includes('credit limit') && isAdmin && !override) {
        setCreditBlock(message)
      } else {
        showToast(message, { type: 'error' })
      }
    } finally {
      setActioning(false)
    }
  }

  async function handleCancel() {
    if (!window.confirm('Cancel this sale?')) return
    setActioning(true)
    try {
      const res = await axiosClient.patch(`/sales/${id}/cancel`)
      setSale(res.data.data)
      showToast('Sale cancelled', { type: 'success' })
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to cancel', { type: 'error' })
    } finally {
      setActioning(false)
    }
  }

  async function handleRecordPayment(payload) {
    setRecordingPayment(true)
    try {
      const res = await axiosClient.post(`/sales/${id}/payments`, payload)
      setPayments((prev) => [res.data.data, ...prev])
      showToast('Payment recorded', { type: 'success' })
      setPaymentModalOpen(false)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to record payment', { type: 'error' })
    } finally {
      setRecordingPayment(false)
    }
  }

  if (loading) {
    return <div className="h-40 animate-pulse rounded-lg bg-navy-100" />
  }

  if (error || !sale) {
    return <ErrorState message={error || 'Sale not found'} />
  }

  const canConfirm = canManage && sale.status === 'QUOTATION'
  const canComplete = canManage && sale.status === 'CONFIRMED'
  const canCancel = canManage && (sale.status === 'QUOTATION' || sale.status === 'CONFIRMED')
  const canPay = sale.status === 'COMPLETED' && sale.paymentType === 'CREDIT'

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <Link to="/sales" className="inline-flex items-center gap-1 text-sm text-navy-500 hover:text-navy-700">
          <ArrowLeft size={14} /> Back to sales
        </Link>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-2xl font-bold text-navy-800">Sale #{sale.id}</h2>
            <Badge variant={SALE_STATUS_VARIANT[sale.status]}>{SALE_STATUS_LABEL[sale.status]}</Badge>
            <Badge variant={PAYMENT_TYPE_VARIANT[sale.paymentType]}>{sale.paymentType}</Badge>
          </div>
          <p className="text-navy-400">{sale.customerName} · {sale.warehouseName} · {sale.saleDate}</p>
          {sale.notes && <p className="mt-1 text-sm text-navy-500">{sale.notes}</p>}
        </div>
        <div className="flex flex-wrap gap-2">
          {canConfirm && (
            <Button onClick={handleConfirm} loading={actioning}>
              <CheckCircle2 size={14} /> Confirm
            </Button>
          )}
          {canComplete && (
            <Button variant="secondary" onClick={() => handleComplete(false)} loading={actioning}>
              <PackageCheck size={14} /> Complete
            </Button>
          )}
          {canPay && (
            <Button variant="secondary" onClick={() => setPaymentModalOpen(true)} disabled={actioning}>
              <CreditCard size={14} /> Record payment
            </Button>
          )}
          {canCancel && (
            <Button variant="ghost" onClick={handleCancel} disabled={actioning} className="text-red-600 hover:bg-red-50">
              <Ban size={14} /> Cancel
            </Button>
          )}
        </div>
      </div>

      {stockWarnings.length > 0 && (
        <div className="space-y-1 rounded-md border border-orange-200 bg-orange-50 px-4 py-3 text-sm text-orange-700">
          {stockWarnings.map((w, i) => (
            <p key={i} className="flex items-start gap-2">
              <AlertTriangle size={14} className="mt-0.5 shrink-0" /> {w}
            </p>
          ))}
        </div>
      )}

      {creditBlock && (
        <div className="space-y-2 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          <p className="flex items-start gap-2">
            <AlertTriangle size={14} className="mt-0.5 shrink-0" /> {creditBlock}
          </p>
          <Button variant="danger" onClick={() => handleComplete(true)} loading={actioning} className="text-xs">
            Complete anyway (admin override)
          </Button>
        </div>
      )}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="bg-navy-50 text-navy-500">
            <tr>
              <th className="px-4 py-3 font-medium">Product</th>
              <th className="px-4 py-3 font-medium text-right">Quantity</th>
              <th className="px-4 py-3 font-medium text-right">Unit price</th>
              <th className="px-4 py-3 font-medium text-right">Discount</th>
              <th className="px-4 py-3 font-medium text-right">Line total</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-navy-100">
            {sale.lines.map((l) => (
              <tr key={l.id}>
                <td className="px-4 py-3">
                  <p className="font-medium text-navy-800">{l.productName}</p>
                  <p className="text-xs text-navy-400">{l.productSku}</p>
                </td>
                <td className="px-4 py-3 text-right tabular-nums text-navy-600">{l.quantity}</td>
                <td className="px-4 py-3 text-right tabular-nums text-navy-600">{Number(l.unitPrice).toFixed(2)}</td>
                <td className="px-4 py-3 text-right tabular-nums text-navy-600">{Number(l.discountPercent)}%</td>
                <td className="px-4 py-3 text-right tabular-nums font-medium text-navy-800">{Number(l.lineTotal).toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-navy-100">
              <td colSpan={4} className="px-4 py-2 text-right text-navy-500">Subtotal</td>
              <td className="px-4 py-2 text-right tabular-nums text-navy-600">{Number(sale.subtotal).toFixed(2)}</td>
            </tr>
            <tr>
              <td colSpan={4} className="px-4 py-2 text-right text-navy-500">VAT (18%)</td>
              <td className="px-4 py-2 text-right tabular-nums text-navy-600">{Number(sale.vatAmount).toFixed(2)}</td>
            </tr>
            <tr className="border-t border-navy-100 bg-navy-50">
              <td colSpan={4} className="px-4 py-3 text-right font-medium text-navy-600">Total</td>
              <td className="px-4 py-3 text-right tabular-nums font-bold text-navy-800">{Number(sale.totalAmount).toFixed(2)}</td>
            </tr>
          </tfoot>
        </table>
        </div>
      </div>

      {sale.paymentType === 'CREDIT' && (
        <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
          <div className="border-b border-navy-100 px-4 py-3">
            <h3 className="text-sm font-semibold text-navy-700">Payments</h3>
          </div>
          {payments.length === 0 ? (
            <p className="px-4 py-6 text-center text-sm text-navy-400">No payments recorded yet.</p>
          ) : (
            <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-navy-50 text-navy-500">
                <tr>
                  <th className="px-4 py-3 font-medium">Date</th>
                  <th className="px-4 py-3 font-medium">Method</th>
                  <th className="px-4 py-3 font-medium">Notes</th>
                  <th className="px-4 py-3 font-medium text-right">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-navy-100">
                {payments.map((p) => (
                  <tr key={p.id}>
                    <td className="px-4 py-3 text-navy-600">{p.paymentDate}</td>
                    <td className="px-4 py-3 text-navy-600">{p.paymentMethod}</td>
                    <td className="px-4 py-3 text-navy-600">{p.notes || '—'}</td>
                    <td className="px-4 py-3 text-right tabular-nums font-medium text-navy-800">{Number(p.amount).toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          )}
        </div>
      )}

      <SalePaymentModal
        open={paymentModalOpen}
        onClose={() => setPaymentModalOpen(false)}
        onSubmit={handleRecordPayment}
        saving={recordingPayment}
      />
    </div>
  )
}
