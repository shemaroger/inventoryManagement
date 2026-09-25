import { useEffect, useMemo, useState } from 'react'
import { Plus, FolderTree, ChevronsDown, ChevronsUp } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import TableSearchInput from '../../components/ui/TableSearchInput'
import CategoryTreeNode from './CategoryTreeNode'
import CategoryModal from './CategoryModal'
import { buildTree, getVisibleIds, getAllExpandableIds } from './categoryTree'

export default function CategoriesList() {
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN') || hasRole('MANAGER')
  const { showToast } = useToast()

  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [expandedIds, setExpandedIds] = useState(new Set())
  const [counts, setCounts] = useState({})

  const [modalOpen, setModalOpen] = useState(false)
  const [editingCategory, setEditingCategory] = useState(null)
  const [defaultParentId, setDefaultParentId] = useState(null)
  const [saving, setSaving] = useState(false)

  const tree = useMemo(() => buildTree(categories), [categories])

  const [search, setSearch] = useState('')
  // A node survives the filter if its own name matches, or any descendant's does — so a match
  // deep in the tree still shows its ancestor chain for context, rather than floating in
  // isolation. When searching, every surviving node is force-expanded so matches are visible
  // without the user having to manually drill down.
  const filteredTree = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return tree
    function filterNodes(nodes) {
      const kept = []
      for (const node of nodes) {
        const children = filterNodes(node.children)
        if (node.name.toLowerCase().includes(q) || children.length > 0) {
          kept.push({ ...node, children })
        }
      }
      return kept
    }
    return filterNodes(tree)
  }, [tree, search])

  async function loadCategories() {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.get('/categories')
      setCategories(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load categories')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadCategories()
  }, [])

  // Default: roots visible+expanded one level, everything below starts collapsed.
  useEffect(() => {
    setExpandedIds(new Set(tree.map((root) => root.id)))
  }, [tree.length]) // eslint-disable-line react-hooks/exhaustive-deps

  // While actively searching, force-expand every surviving node so matches are never hidden
  // behind a collapsed ancestor.
  useEffect(() => {
    if (search.trim()) {
      setExpandedIds(new Set(getAllExpandableIds(filteredTree)))
    }
  }, [search, filteredTree])

  // Lazily fetch product counts only for currently-visible nodes (avoids an N+1 storm).
  useEffect(() => {
    const visibleIds = getVisibleIds(filteredTree, expandedIds)
    const missing = visibleIds.filter((id) => !(id in counts))
    if (missing.length === 0) return

    let cancelled = false
    missing.forEach(async (id) => {
      try {
        const res = await axiosClient.get('/products', { params: { categoryId: id, size: 1 } })
        if (!cancelled) {
          setCounts((prev) => ({ ...prev, [id]: res.data.data.totalElements }))
        }
      } catch {
        if (!cancelled) setCounts((prev) => ({ ...prev, [id]: 0 }))
      }
    })
    return () => {
      cancelled = true
    }
  }, [filteredTree, expandedIds, counts])

  function toggleNode(id) {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function expandAll() {
    setExpandedIds(new Set(getAllExpandableIds(filteredTree)))
  }

  function collapseAll() {
    setExpandedIds(new Set())
  }

  function openCreateModal() {
    setEditingCategory(null)
    setDefaultParentId(null)
    setModalOpen(true)
  }

  function openAddChildModal(parent) {
    setEditingCategory(null)
    setDefaultParentId(parent.id)
    setModalOpen(true)
  }

  function openEditModal(category) {
    setEditingCategory(category)
    setDefaultParentId(null)
    setModalOpen(true)
  }

  async function handleModalSubmit(payload) {
    setSaving(true)
    try {
      if (editingCategory) {
        await axiosClient.put(`/categories/${editingCategory.id}`, payload)
        showToast('Category updated', { type: 'success' })
      } else {
        await axiosClient.post('/categories', payload)
        showToast('Category created', { type: 'success' })
      }
      setModalOpen(false)
      await loadCategories()
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to save category', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  // NOTE: delete-safety (blocking on children/products) lands in a follow-up pass.
  async function handleDelete(category) {
    if (!window.confirm(`Delete category "${category.name}"? This cannot be undone.`)) return
    try {
      await axiosClient.delete(`/categories/${category.id}`)
      showToast(`${category.name} deleted`, { type: 'success' })
      await loadCategories()
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to delete category', { type: 'error' })
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Categories</h2>
          <p className="text-navy-400">Organize products into categories and subcategories.</p>
        </div>
        {isAdmin && (
          <Button onClick={openCreateModal} className="w-full sm:w-auto">
            <Plus size={16} /> Add Category
          </Button>
        )}
      </div>

      {tree.length > 0 && (
        <div className="flex gap-2">
          <Button variant="ghost" onClick={expandAll} className="text-xs">
            <ChevronsDown size={14} /> Expand all
          </Button>
          <Button variant="ghost" onClick={collapseAll} className="text-xs">
            <ChevronsUp size={14} /> Collapse all
          </Button>
        </div>
      )}

      {error && <ErrorState message={error} />}

      {tree.length > 0 && (
        <TableSearchInput value={search} onChange={setSearch} placeholder="Search categories by name…" className="sm:w-80" />
      )}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        {loading ? (
          <div className="space-y-0">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-center gap-3 border-b border-navy-100 px-4 py-3">
                <div className="h-4 w-4 animate-pulse rounded bg-navy-100" />
                <div className="h-4 w-40 animate-pulse rounded bg-navy-100" />
              </div>
            ))}
          </div>
        ) : error ? null : tree.length === 0 ? (
          <EmptyState
            icon={FolderTree}
            title="No categories yet"
            description="Add your first category to start organizing products."
            action={isAdmin && <Button onClick={openCreateModal}><Plus size={16} /> Add your first category</Button>}
          />
        ) : filteredTree.length === 0 ? (
          <EmptyState icon={FolderTree} title="No categories match your search" description="Try a different search term." />
        ) : (
          <div>
            {filteredTree.map((root) => (
              <CategoryTreeNode
                key={root.id}
                node={root}
                depth={0}
                expandedIds={expandedIds}
                onToggle={toggleNode}
                isAdmin={isAdmin}
                counts={counts}
                onEdit={openEditModal}
                onAddChild={openAddChildModal}
                onDelete={handleDelete}
              />
            ))}
          </div>
        )}
      </div>

      <CategoryModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onSubmit={handleModalSubmit}
        initial={editingCategory}
        defaultParentId={defaultParentId}
        flatCategories={categories}
        saving={saving}
      />
    </div>
  )
}
