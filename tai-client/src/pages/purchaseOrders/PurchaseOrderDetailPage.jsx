import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, PackageCheck, Ban, Send } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import { ErrorState } from '../../components/ui/ListStates'
import { PO_STATUS_VARIANT, PO_STATUS_LABEL } from './statusBadge'
import ReceivePurchaseOrderModal from './ReceivePurchaseOrderModal'

export default function PurchaseOrderDetailPage() {
  const { id } = useParams()
  const { hasRole } = useAuth()
  const canManage = hasRole('ADMIN') || hasRole('MANAGER')
  const canReceive = canManage || hasRole('STAFF')
  const { showToast } = useToast()

  const [po, setPo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actioning, setActioning] = useState(false)
  const [receiveOpen, setReceiveOpen] = useState(false)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.get(`/purchase-orders/${id}`)
      setPo(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load purchase order')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [id])

  async function handleSubmitPo() {
    setActioning(true)
    try {
      const res = await axiosClient.patch(`/purchase-orders/${id}/submit`)
      setPo(res.data.data)
      showToast('Purchase order submitted', { type: 'success' })
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to submit', { type: 'error' })
    } finally {
      setActioning(false)
    }
  }

  async function handleCancelPo() {
    if (!window.confirm('Cancel this purchase order?')) return
    setActioning(true)
    try {
      const res = await axiosClient.patch(`/purchase-orders/${id}/cancel`)
      setPo(res.data.data)
      showToast('Purchase order cancelled', { type: 'success' })
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to cancel', { type: 'error' })
    } finally {
      setActioning(false)
    }
  }

  async function handleReceiveSubmit(payload) {
    setActioning(true)
    try {
      const res = await axiosClient.post(`/purchase-orders/${id}/receive`, payload)
      setPo(res.data.data)
      showToast('Goods received', { type: 'success' })
      setReceiveOpen(false)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to record receipt', { type: 'error' })
    } finally {
      setActioning(false)
    }
  }

  if (loading) {
    return <div className="h-40 animate-pulse rounded-lg bg-navy-100" />
  }

  if (error || !po) {
    return <ErrorState message={error || 'Purchase order not found'} />
  }

  const canSubmit = canManage && po.status === 'DRAFT'
  const canCancel = canManage && po.status !== 'RECEIVED' && po.status !== 'CANCELLED'
  const canReceiveNow = canReceive && (po.status === 'SUBMITTED' || po.status === 'PARTIALLY_RECEIVED')

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <Link to="/purchase-orders" className="inline-flex items-center gap-1 text-sm text-navy-500 hover:text-navy-700">
          <ArrowLeft size={14} /> Back to purchase orders
        </Link>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-2xl font-bold text-navy-800">Purchase Order #{po.id}</h2>
            <Badge variant={PO_STATUS_VARIANT[po.status]}>{PO_STATUS_LABEL[po.status]}</Badge>
            <Badge variant={po.paymentType === 'CASH' ? 'success' : 'orange'}>{po.paymentType}</Badge>
          </div>
          <p className="text-navy-400">{po.supplierName} → {po.warehouseName} · {po.orderDate}</p>
        </div>
        <div className="flex gap-2">
          {canSubmit && (
            <Button onClick={handleSubmitPo} loading={actioning}>
              <Send size={14} /> Submit
            </Button>
          )}
          {canReceiveNow && (
            <Button variant="secondary" onClick={() => setReceiveOpen(true)} disabled={actioning}>
              <PackageCheck size={14} /> Receive
            </Button>
          )}
          {canCancel && (
            <Button variant="ghost" onClick={handleCancelPo} disabled={actioning} className="text-red-600 hover:bg-red-50">
              <Ban size={14} /> Cancel
            </Button>
          )}
        </div>
      </div>

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="bg-navy-50 text-navy-500">
            <tr>
              <th className="px-4 py-3 font-medium">Product</th>
              <th className="px-4 py-3 font-medium text-right">Ordered</th>
              <th className="px-4 py-3 font-medium text-right">Received</th>
              <th className="px-4 py-3 font-medium text-right">Unit cost</th>
              <th className="px-4 py-3 font-medium text-right">Line total</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-navy-100">
            {po.lines.map((l) => (
              <tr key={l.id}>
                <td className="px-4 py-3">
                  <p className="font-medium text-navy-800">{l.productName}</p>
                  <p className="text-xs text-navy-400">{l.productSku}</p>
                </td>
                <td className="px-4 py-3 text-right tabular-nums text-navy-600">{l.quantityOrdered}</td>
                <td className="px-4 py-3 text-right tabular-nums text-navy-600">{l.quantityReceived}</td>
                <td className="px-4 py-3 text-right tabular-nums text-navy-600">{Number(l.unitCost).toFixed(2)}</td>
                <td className="px-4 py-3 text-right tabular-nums font-medium text-navy-800">
                  {(Number(l.quantityOrdered) * Number(l.unitCost)).toFixed(2)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      </div>

      <ReceivePurchaseOrderModal
        open={receiveOpen}
        onClose={() => setReceiveOpen(false)}
        onSubmit={handleReceiveSubmit}
        purchaseOrder={po}
        saving={actioning}
      />
    </div>
  )
}
