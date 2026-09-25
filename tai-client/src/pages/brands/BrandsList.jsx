import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2, BookmarkCheck } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import RowActionsMenu from '../../components/ui/RowActionsMenu'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import TableSearchInput from '../../components/ui/TableSearchInput'
import usePagedList from '../../hooks/usePagedList'
import BrandModal from './BrandModal'
import DeleteBrandModal from './DeleteBrandModal'

export default function BrandsList() {
  const { hasRole } = useAuth()
  const canEdit = hasRole('ADMIN') || hasRole('MANAGER')
  const canDelete = hasRole('ADMIN')
  const { showToast } = useToast()

  const [brands, setBrands] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [modalOpen, setModalOpen] = useState(false)
  const [editingBrand, setEditingBrand] = useState(null)
  const [saving, setSaving] = useState(false)

  const [deletingBrand, setDeletingBrand] = useState(null)
  const [deleting, setDeleting] = useState(false)

  const [search, setSearch] = useState('')
  const filteredBrands = brands.filter((b) => b.name.toLowerCase().includes(search.trim().toLowerCase()))
  const { page, setPage, totalPages, pageItems: pagedBrands } = usePagedList(filteredBrands, 10)

  async function loadBrands() {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.get('/brands')
      setBrands(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load brands')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadBrands()
  }, [])

  function openCreateModal() {
    setEditingBrand(null)
    setModalOpen(true)
  }

  function openEditModal(brand) {
    setEditingBrand(brand)
    setModalOpen(true)
  }

  async function handleModalSubmit(payload) {
    setSaving(true)
    try {
      if (editingBrand) {
        const res = await axiosClient.put(`/brands/${editingBrand.id}`, payload)
        setBrands((prev) => prev.map((b) => (b.id === editingBrand.id ? res.data.data : b)))
        showToast('Brand updated', { type: 'success' })
      } else {
        const res = await axiosClient.post('/brands', payload)
        setBrands((prev) => [...prev, res.data.data])
        showToast('Brand created', { type: 'success' })
      }
      setModalOpen(false)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to save brand', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDeleteConfirm() {
    if (!deletingBrand) return
    setDeleting(true)
    try {
      await axiosClient.delete(`/brands/${deletingBrand.id}`)
      setBrands((prev) => prev.filter((b) => b.id !== deletingBrand.id))
      showToast(`${deletingBrand.name} deleted`, { type: 'success' })
      setDeletingBrand(null)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to delete brand', { type: 'error' })
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Brands</h2>
          <p className="text-navy-400">Manage the brands products can be tagged with.</p>
        </div>
        {canEdit && (
          <Button onClick={openCreateModal} className="w-full sm:w-auto">
            <Plus size={16} /> Add Brand
          </Button>
        )}
      </div>

      {error && <ErrorState message={error} />}

      <TableSearchInput value={search} onChange={setSearch} placeholder="Search by name…" className="sm:w-80" />

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            {loading ? (
              <SkeletonRows rows={5} columns={2} />
            ) : (
              !error &&
              pagedBrands.length > 0 && (
                <tbody className="divide-y divide-navy-100">
                  {pagedBrands.map((b) => (
                    <tr key={b.id} className="transition-colors duration-150 hover:bg-navy-50/50">
                      <td className="px-4 py-3 font-medium text-navy-800">{b.name}</td>
                      <td className="px-4 py-3 text-right">
                        <RowActionsMenu
                          label={`Actions for ${b.name}`}
                          items={[
                            ...(canEdit
                              ? [{ label: 'Edit', icon: <Pencil size={14} />, onClick: () => openEditModal(b) }]
                              : []),
                            ...(canDelete
                              ? [
                                  {
                                    label: 'Delete',
                                    icon: <Trash2 size={14} />,
                                    onClick: () => setDeletingBrand(b),
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

          {!loading && !error && pagedBrands.length === 0 && (
            <EmptyState
              icon={BookmarkCheck}
              title={brands.length === 0 ? 'No brands yet' : 'No brands match your search'}
              description={brands.length === 0 ? 'Add your first brand to start tagging products.' : 'Try a different search term.'}
              action={brands.length === 0 && canEdit && <Button onClick={openCreateModal}><Plus size={16} /> Add Brand</Button>}
            />
          )}
        </div>

        {!loading && !error && filteredBrands.length > 0 && (
          <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
        )}
      </div>

      <BrandModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onSubmit={handleModalSubmit}
        initial={editingBrand}
        saving={saving}
      />

      <DeleteBrandModal
        open={!!deletingBrand}
        onClose={() => setDeletingBrand(null)}
        onConfirm={handleDeleteConfirm}
        brand={deletingBrand}
        deleting={deleting}
      />
    </div>
  )
}
