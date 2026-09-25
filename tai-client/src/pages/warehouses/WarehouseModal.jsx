import { useEffect, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'

// No branch field here on purpose — there's no GET /api/branches endpoint yet (only the
// entity/repository exist on the backend), so branch assignment can't be built without
// either faking a hardcoded list or adding that endpoint first.
export default function WarehouseModal({ open, onClose, onSubmit, initial, saving }) {
  const isEdit = Boolean(initial?.id)
  const [name, setName] = useState('')
  const [location, setLocation] = useState('')
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) {
      setName(initial?.name || '')
      setLocation(initial?.location || '')
      setError(null)
    }
  }, [open, initial])

  function handleSubmit(e) {
    e.preventDefault()
    if (!name.trim()) {
      setError('Name is required')
      return
    }
    onSubmit({ name: name.trim(), location: location.trim() || null, branchId: initial?.branchId ?? null })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? `Edit ${initial.name}` : 'Add warehouse'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="warehouse-form" loading={saving}>
            {isEdit ? 'Save changes' : 'Create warehouse'}
          </Button>
        </>
      }
    >
      <form id="warehouse-form" onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={error}
          placeholder="e.g. Kigali Central Warehouse"
        />
        <Input
          label="Location (optional)"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
          placeholder="e.g. KG 15 Ave, Kigali"
        />
      </form>
    </Modal>
  )
}
