import { useEffect, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'

export default function BrandModal({ open, onClose, onSubmit, initial, saving }) {
  const isEdit = Boolean(initial?.id)
  const [name, setName] = useState('')
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) {
      setName(initial?.name || '')
      setError(null)
    }
  }, [open, initial])

  function handleSubmit(e) {
    e.preventDefault()
    if (!name.trim()) {
      setError('Name is required')
      return
    }
    onSubmit({ name: name.trim() })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? `Edit ${initial.name}` : 'Add brand'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="brand-form" loading={saving}>
            {isEdit ? 'Save changes' : 'Create brand'}
          </Button>
        </>
      }
    >
      <form id="brand-form" onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={error}
          placeholder="e.g. Dangote"
        />
      </form>
    </Modal>
  )
}
