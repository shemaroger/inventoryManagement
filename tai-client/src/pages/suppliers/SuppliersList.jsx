import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2, Truck } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import RowActionsMenu from '../../components/ui/RowActionsMenu'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import TableSearchInput from '../../components/ui/TableSearchInput'
import usePagedList from '../../hooks/usePagedList'
import SupplierModal from './SupplierModal'
import DeleteSupplierModal from './DeleteSupplierModal'

export default function SuppliersList() {
  const { hasRole } = useAuth()
  const canEdit = hasRole('ADMIN') || hasRole('MANAGER')
  const canDeactivate = hasRole('ADMIN')
  const { showToast } = useToast()

  const [suppliers, setSuppliers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [modalOpen, setModalOpen] = useState(false)
  const [editingSupplier, setEditingSupplier] = useState(null)
  const [saving, setSaving] = useState(false)

  const [deletingSupplier, setDeletingSupplier] = useState(null)
  const [deleting, setDeleting] = useState(false)

  const [search, setSearch] = useState('')
  const filteredSuppliers = suppliers.filter((s) => {
    const q = search.trim().toLowerCase()
    if (!q) return true
    return s.name.toLowerCase().includes(q) || (s.contactPerson || '').toLowerCase().includes(q) || (s.phone || '').includes(q)
  })
  const { page, setPage, totalPages, pageItems: pagedSuppliers } = usePagedList(filteredSuppliers, 10)

  async function loadSuppliers() {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.get('/suppliers')
      setSuppliers(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load suppliers')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadSuppliers()
  }, [])

  function openCreateModal() {
    setEditingSupplier(null)
    setModalOpen(true)
  }

  function openEditModal(supplier) {
    setEditingSupplier(supplier)
    setModalOpen(true)
  }

  async function handleModalSubmit(payload) {
    setSaving(true)
    try {
      if (editingSupplier) {
        const res = await axiosClient.put(`/suppliers/${editingSupplier.id}`, payload)
        setSuppliers((prev) => prev.map((s) => (s.id === editingSupplier.id ? res.data.data : s)))
        showToast('Supplier updated', { type: 'success' })
      } else {
        const res = await axiosClient.post('/suppliers', payload)
        setSuppliers((prev) => [...prev, res.data.data])
        showToast('Supplier created', { type: 'success' })
      }
      setModalOpen(false)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to save supplier', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDeactivate(supplier) {
    try {
      await axiosClient.patch(`/suppliers/${supplier.id}/deactivate`)
      setSuppliers((prev) => prev.map((s) => (s.id === supplier.id ? { ...s, active: false } : s)))
      showToast(`${supplier.name} deactivated`, { type: 'success' })
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to deactivate supplier', { type: 'error' })
    }
  }

  async function handleDeleteConfirm() {
    if (!deletingSupplier) return
    setDeleting(true)
    try {
      await axiosClient.delete(`/suppliers/${deletingSupplier.id}`)
      setSuppliers((prev) => prev.filter((s) => s.id !== deletingSupplier.id))
      showToast(`${deletingSupplier.name} deleted`, { type: 'success' })
      setDeletingSupplier(null)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to delete supplier', { type: 'error' })
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Suppliers</h2>
          <p className="text-navy-400">Manage the suppliers you purchase stock from.</p>
        </div>
        {canEdit && (
          <Button onClick={openCreateModal} className="w-full sm:w-auto">
            <Plus size={16} /> Add Supplier
          </Button>
        )}
      </div>

      {error && <ErrorState message={error} />}

      <TableSearchInput value={search} onChange={setSearch} placeholder="Search by name, contact, or phone…" className="sm:w-80" />

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Contact</th>
                <th className="px-4 py-3 font-medium">Phone</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            {loading ? (
              <SkeletonRows rows={5} columns={5} />
            ) : (
              !error &&
              pagedSuppliers.length > 0 && (
                <tbody className="divide-y divide-navy-100">
                  {pagedSuppliers.map((s) => (
                    <tr key={s.id} className="transition-colors duration-150 hover:bg-navy-50/50">
                      <td className="px-4 py-3 font-medium text-navy-800">{s.name}</td>
                      <td className="px-4 py-3 text-navy-600">{s.contactPerson || '—'}</td>
                      <td className="px-4 py-3 text-navy-600">{s.phone || '—'}</td>
                      <td className="px-4 py-3">
                        <Badge variant={s.active ? 'success' : 'neutral'}>{s.active ? 'Active' : 'Inactive'}</Badge>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <RowActionsMenu
                          label={`Actions for ${s.name}`}
                          items={[
                            ...(canEdit
                              ? [{ label: 'Edit', icon: <Pencil size={14} />, onClick: () => openEditModal(s) }]
                              : []),
                            ...(canDeactivate && s.active
                              ? [{ label: 'Deactivate', onClick: () => handleDeactivate(s) }]
                              : []),
                            ...(canDeactivate
                              ? [
                                  {
                                    label: 'Delete',
                                    icon: <Trash2 size={14} />,
                                    onClick: () => setDeletingSupplier(s),
                                    danger: true,
                                  },
                                ]
                              : []),
                          ]}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              )
            )}
          </table>

          {!loading && !error && pagedSuppliers.length === 0 && (
            <EmptyState
              icon={Truck}
              title={suppliers.length === 0 ? 'No suppliers yet' : 'No suppliers match your search'}
              description={suppliers.length === 0 ? 'Add your first supplier to start creating purchase orders.' : 'Try a different search term.'}
              action={suppliers.length === 0 && canEdit && <Button onClick={openCreateModal}><Plus size={16} /> Add Supplier</Button>}
            />
          )}
        </div>

        {!loading && !error && filteredSuppliers.length > 0 && (
          <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
        )}
      </div>

      <SupplierModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onSubmit={handleModalSubmit}
        initial={editingSupplier}
        saving={saving}
      />

      <DeleteSupplierModal
        open={!!deletingSupplier}
        onClose={() => setDeletingSupplier(null)}
        onConfirm={handleDeleteConfirm}
        supplier={deletingSupplier}
        deleting={deleting}
      />
    </div>
  )
}
