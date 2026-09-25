import { useEffect, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Button from '../../components/ui/Button'

export default function CategoryModal({ open, onClose, onSubmit, initial, defaultParentId, flatCategories, saving }) {
  const isEdit = Boolean(initial?.id)
  const [name, setName] = useState('')
  const [parentId, setParentId] = useState('')
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) {
      setName(initial?.name || '')
      setParentId(initial?.parentId ?? defaultParentId ?? '')
      setError(null)
    }
  }, [open, initial, defaultParentId])

  function handleSubmit(e) {
    e.preventDefault()
    if (!name.trim()) {
      setError('Name is required')
      return
    }
    onSubmit({ name: name.trim(), parentId: parentId ? Number(parentId) : null })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? `Edit ${initial.name}` : 'Add category'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="category-form" loading={saving}>
            {isEdit ? 'Save changes' : 'Create category'}
          </Button>
        </>
      }
    >
      <form id="category-form" onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={error}
          placeholder="e.g. Cement & Concrete"
        />
        <Select
          label="Parent category"
          value={parentId ?? ''}
          onChange={(e) => setParentId(e.target.value)}
        >
          <option value="">— None (root category) —</option>
          {flatCategories
            .filter((c) => c.id !== initial?.id)
            .map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
        </Select>
      </form>
    </Modal>
  )
}
