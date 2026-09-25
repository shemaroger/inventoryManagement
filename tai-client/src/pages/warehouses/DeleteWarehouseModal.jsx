import { AlertTriangle } from 'lucide-react'
import Modal from '../../components/ui/Modal'
import Button from '../../components/ui/Button'

export default function DeleteWarehouseModal({ open, onClose, onConfirm, warehouse, summary, deleting }) {
  const hasStock = summary && summary.totalUnits > 0

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Delete warehouse"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={deleting}>
            Cancel
          </Button>
          <Button variant="danger" onClick={onConfirm} loading={deleting}>
            Delete
          </Button>
        </>
      }
    >
      <p className="text-sm text-navy-600">
        Are you sure you want to delete <span className="font-medium text-navy-800">{warehouse?.name}</span>?
        This cannot be undone.
      </p>
      {hasStock && (
        <div className="mt-3 flex items-start gap-2 rounded-md border border-orange-200 bg-orange-50 px-3 py-2 text-xs text-orange-700">
          <AlertTriangle size={14} className="mt-0.5 shrink-0" />
          <span>
            This warehouse still holds {summary.totalUnits} units across {summary.totalSkus} products. The
            backend will reject this delete until that stock is moved out or adjusted to zero.
          </span>
        </div>
      )}
    </Modal>
  )
}
