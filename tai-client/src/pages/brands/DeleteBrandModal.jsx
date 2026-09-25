import Modal from '../../components/ui/Modal'
import Button from '../../components/ui/Button'

export default function DeleteBrandModal({ open, onClose, onConfirm, brand, deleting }) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Delete brand"
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
        Are you sure you want to delete <span className="font-medium text-navy-800">{brand?.name}</span>?
        This cannot be undone.
      </p>
    </Modal>
  )
}
