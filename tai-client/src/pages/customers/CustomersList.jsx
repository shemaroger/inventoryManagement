import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, Pencil, Trash2, Users, FileText } from 'lucide-react'
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
import CustomerModal from './CustomerModal'
import DeleteCustomerModal from './DeleteCustomerModal'

export default function CustomersList() {
  const { hasRole } = useAuth()
  const canEdit = hasRole('ADMIN') || hasRole('MANAGER')
  const canDeactivate = hasRole('ADMIN')
  const { showToast } = useToast()
  const navigate = useNavigate()

  const [customers, setCustomers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [modalOpen, setModalOpen] = useState(false)
  const [editingCustomer, setEditingCustomer] = useState(null)
  const [saving, setSaving] = useState(false)

  const [deletingCustomer, setDeletingCustomer] = useState(null)
  const [deleting, setDeleting] = useState(false)

  const [search, setSearch] = useState('')
  const filteredCustomers = customers.filter((c) => {
    const q = search.trim().toLowerCase()
    if (!q) return true
    return (
      c.name.toLowerCase().includes(q) ||
      (c.contactPerson || '').toLowerCase().includes(q) ||
      (c.phone || '').includes(q) ||
      (c.customerCategory || '').toLowerCase().includes(q)
    )
  })
  const { page, setPage, totalPages, pageItems: pagedCustomers } = usePagedList(filteredCustomers, 10)

  async function loadCustomers() {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.get('/customers')
      setCustomers(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load customers')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadCustomers()
  }, [])

  function openCreateModal() {
    setEditingCustomer(null)
    setModalOpen(true)
  }

  function openEditModal(customer) {
    setEditingCustomer(customer)
    setModalOpen(true)
  }

  async function handleModalSubmit(payload) {
    setSaving(true)
    try {
      if (editingCustomer) {
        const res = await axiosClient.put(`/customers/${editingCustomer.id}`, payload)
        setCustomers((prev) => prev.map((c) => (c.id === editingCustomer.id ? res.data.data : c)))
        showToast('Customer updated', { type: 'success' })
      } else {
        const res = await axiosClient.post('/customers', payload)
        setCustomers((prev) => [...prev, res.data.data])
        showToast('Customer created', { type: 'success' })
      }
      setModalOpen(false)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to save customer', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDeactivate(customer) {
    try {
      await axiosClient.patch(`/customers/${customer.id}/deactivate`)
      setCustomers((prev) => prev.map((c) => (c.id === customer.id ? { ...c, active: false } : c)))
      showToast(`${customer.name} deactivated`, { type: 'success' })
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to deactivate customer', { type: 'error' })
    }
  }

  async function handleDeleteConfirm() {
    if (!deletingCustomer) return
    setDeleting(true)
    try {
      await axiosClient.delete(`/customers/${deletingCustomer.id}`)
      setCustomers((prev) => prev.filter((c) => c.id !== deletingCustomer.id))
      showToast(`${deletingCustomer.name} deleted`, { type: 'success' })
      setDeletingCustomer(null)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to delete customer', { type: 'error' })
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Customers</h2>
          <p className="text-navy-400">Manage the customers you sell to and their credit standing.</p>
        </div>
        {canEdit && (
          <Button onClick={openCreateModal} className="w-full sm:w-auto">
            <Plus size={16} /> Add Customer
          </Button>
        )}
      </div>

      {error && <ErrorState message={error} />}

      <TableSearchInput value={search} onChange={setSearch} placeholder="Search by name, contact, phone, or category…" className="sm:w-80" />

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Contact</th>
                <th className="px-4 py-3 font-medium">Category</th>
                <th className="px-4 py-3 font-medium text-right">Credit limit</th>
                <th className="px-4 py-3 font-medium text-right">Balance owed</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            {loading ? (
              <SkeletonRows rows={5} columns={7} />
            ) : (
              !error &&
              pagedCustomers.length > 0 && (
                <tbody className="divide-y divide-navy-100">
                  {pagedCustomers.map((c) => (
                    <tr key={c.id} className="transition-colors duration-150 hover:bg-navy-50/50">
                      <td className="px-4 py-3 font-medium text-navy-800">{c.name}</td>
                      <td className="px-4 py-3 text-navy-600">{c.contactPerson || '—'}</td>
                      <td className="px-4 py-3 text-navy-600">{c.customerCategory || '—'}</td>
                      <td className="px-4 py-3 text-right tabular-nums text-navy-600">{Number(c.creditLimit).toFixed(2)}</td>
                      <td className="px-4 py-3 text-right tabular-nums">
                        <span className={Number(c.currentBalance) > 0 ? 'font-medium text-orange-600' : 'text-navy-600'}>
                          {Number(c.currentBalance).toFixed(2)}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant={c.active ? 'success' : 'neutral'}>{c.active ? 'Active' : 'Inactive'}</Badge>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <RowActionsMenu
                          label={`Actions for ${c.name}`}
                          items={[
                            { label: 'View statement', icon: <FileText size={14} />, onClick: () => navigate(`/customers/${c.id}/statement`) },
                            ...(canEdit
                              ? [{ label: 'Edit', icon: <Pencil size={14} />, onClick: () => openEditModal(c) }]
                              : []),
                            ...(canDeactivate && c.active
                              ? [{ label: 'Deactivate', onClick: () => handleDeactivate(c) }]
                              : []),
                            ...(canDeactivate
                              ? [
                                  {
                                    label: 'Delete',
                                    icon: <Trash2 size={14} />,
                                    onClick: () => setDeletingCustomer(c),
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

          {!loading && !error && pagedCustomers.length === 0 && (
            <EmptyState
              icon={Users}
              title={customers.length === 0 ? 'No customers yet' : 'No customers match your search'}
              description={customers.length === 0 ? 'Add your first customer to start creating sales.' : 'Try a different search term.'}
              action={customers.length === 0 && canEdit && <Button onClick={openCreateModal}><Plus size={16} /> Add Customer</Button>}
            />
          )}
        </div>

        {!loading && !error && filteredCustomers.length > 0 && (
          <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
        )}
      </div>

      <CustomerModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onSubmit={handleModalSubmit}
        initial={editingCustomer}
        saving={saving}
      />

      <DeleteCustomerModal
        open={!!deletingCustomer}
        onClose={() => setDeletingCustomer(null)}
        onConfirm={handleDeleteConfirm}
        customer={deletingCustomer}
        deleting={deleting}
      />
    </div>
  )
}
