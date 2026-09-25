import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { UserCheck, UserX, Pencil, Trash2, Plus, Users as UsersIcon, Search, Eye } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import useDebouncedValue from '../../hooks/useDebouncedValue'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Badge from '../../components/ui/Badge'
import Table from '../../components/ui/Table'
import RowActionsMenu from '../../components/ui/RowActionsMenu'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { EmptyState, ErrorState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import usePagedList from '../../hooks/usePagedList'
import DeleteUserModal from './DeleteUserModal'

const ROLE_BADGE_VARIANT = {
  ADMIN: 'orange',
  MANAGER: 'navy',
  STAFF: 'neutral',
}

export default function UsersList() {
  const { hasRole } = useAuth()
  const canManage = hasRole('ADMIN')
  const { showToast } = useToast()
  const navigate = useNavigate()

  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actioningId, setActioningId] = useState(null)

  const [search, setSearch] = useState('')
  const debouncedSearch = useDebouncedValue(search, 300)
  const [roleFilter, setRoleFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')

  const [deletingUser, setDeletingUser] = useState(null)
  const [deleting, setDeleting] = useState(false)

  async function loadUsers() {
    setLoading(true)
    setError(null)
    try {
      // NOTE: filtering below is client-side. If the list grows large, switch to
      // server-side search/filter via query params, e.g. GET /users?search=&role=&status=
      const res = await axiosClient.get('/users')
      setUsers(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load users')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadUsers()
  }, [])

  const filteredUsers = useMemo(() => {
    const term = debouncedSearch.trim().toLowerCase()
    return users.filter((u) => {
      const matchesSearch =
        !term || u.fullName.toLowerCase().includes(term) || u.email.toLowerCase().includes(term)
      const matchesRole = roleFilter === 'ALL' || u.roles?.includes(roleFilter)
      const matchesStatus =
        statusFilter === 'ALL' || (statusFilter === 'ACTIVE' ? u.active : !u.active)
      return matchesSearch && matchesRole && matchesStatus
    })
  }, [users, debouncedSearch, roleFilter, statusFilter])

  const { page, setPage, totalPages, pageItems: pagedUsers } = usePagedList(filteredUsers, 10)

  async function toggleActive(userItem) {
    setActioningId(userItem.id)
    try {
      const action = userItem.active ? 'deactivate' : 'activate'
      await axiosClient.patch(`/users/${userItem.id}/${action}`)
      setUsers((prev) =>
        prev.map((u) => (u.id === userItem.id ? { ...u, active: !u.active } : u))
      )
      showToast(`${userItem.fullName} ${action}d`, { type: 'success' })
    } catch (err) {
      showToast(err.response?.data?.message || 'Action failed', { type: 'error' })
    } finally {
      setActioningId(null)
    }
  }

  async function handleDeleteConfirm() {
    if (!deletingUser) return
    setDeleting(true)
    try {
      await axiosClient.delete(`/users/${deletingUser.id}`)
      setUsers((prev) => prev.filter((u) => u.id !== deletingUser.id))
      showToast(`${deletingUser.fullName} deleted`, { type: 'success' })
      setDeletingUser(null)
    } catch (err) {
      showToast(err.response?.data?.message || 'Delete failed', { type: 'error' })
    } finally {
      setDeleting(false)
    }
  }

  const columns = [
    { key: 'fullName', label: 'Name', render: (u) => <span className="font-medium text-navy-800">{u.fullName}</span> },
    { key: 'email', label: 'Email', render: (u) => <span className="text-navy-600">{u.email}</span> },
    { key: 'phoneNumber', label: 'Phone', render: (u) => <span className="text-navy-600">{u.phoneNumber || '—'}</span> },
    {
      key: 'roles',
      label: 'Roles',
      render: (u) => (
        <div className="flex flex-wrap gap-1">
          {u.roles.map((r) => (
            <Badge key={r} variant={ROLE_BADGE_VARIANT[r] || 'neutral'}>
              {r}
            </Badge>
          ))}
        </div>
      ),
    },
    {
      key: 'active',
      label: 'Status',
      render: (u) => <Badge variant={u.active ? 'success' : 'neutral'}>{u.active ? 'Active' : 'Inactive'}</Badge>,
    },
    {
      key: 'actions',
      label: '',
      align: 'right',
      render: (u) => {
        const items = [{ label: 'View details', icon: <Eye size={14} />, onClick: () => navigate(`/users/${u.id}`) }]
        if (canManage) {
          items.push(
            { label: 'Edit', icon: <Pencil size={14} />, onClick: () => navigate(`/users/${u.id}/edit`) },
            {
              label: u.active ? 'Deactivate' : 'Activate',
              icon: u.active ? <UserX size={14} /> : <UserCheck size={14} />,
              onClick: () => toggleActive(u),
              disabled: actioningId === u.id,
            },
            {
              label: 'Delete',
              icon: <Trash2 size={14} />,
              onClick: () => setDeletingUser(u),
              danger: true,
            }
          )
        }
        return <RowActionsMenu label={`Actions for ${u.fullName}`} items={items} />
      },
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">User Management</h2>
          <p className="text-navy-400">Manage staff accounts and access.</p>
        </div>
        {canManage && (
          <Button onClick={() => navigate('/users/new')} className="w-full sm:w-auto">
            <Plus size={16} /> Add user
          </Button>
        )}
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative sm:w-72">
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-navy-300" />
          <Input
            placeholder="Search by name or email"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
            aria-label="Search users"
          />
        </div>
        <Select value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)} className="sm:w-40" aria-label="Filter by role">
          <option value="ALL">All roles</option>
          <option value="ADMIN">Admin</option>
          <option value="MANAGER">Manager</option>
          <option value="STAFF">Staff</option>
        </Select>
        <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="sm:w-40" aria-label="Filter by status">
          <option value="ALL">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </Select>
      </div>

      {error && <ErrorState message={error} />}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          {loading ? (
            <table className="w-full text-left text-sm">
              <thead className="bg-navy-50 text-navy-500">
                <tr>
                  {columns.map((c) => (
                    <th key={c.key} className="px-4 py-3 font-medium">{c.label}</th>
                  ))}
                </tr>
              </thead>
              <SkeletonRows rows={5} columns={columns.length} />
            </table>
          ) : error ? null : filteredUsers.length === 0 ? (
            users.length === 0 ? (
              <EmptyState
                icon={UsersIcon}
                title="No users yet"
                description="Invite your first teammate to get started."
                action={canManage && <Button onClick={() => navigate('/users/new')}><Plus size={16} /> Add user</Button>}
              />
            ) : (
              <EmptyState
                icon={Search}
                title="No matching users"
                description="Try a different search term or clear the filters."
              />
            )
          ) : (
            <Table columns={columns} data={pagedUsers} />
          )}
        </div>

        {!loading && !error && filteredUsers.length > 0 && (
          <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
        )}
      </div>

      <DeleteUserModal
        open={!!deletingUser}
        onClose={() => setDeletingUser(null)}
        onConfirm={handleDeleteConfirm}
        user={deletingUser}
        deleting={deleting}
      />
    </div>
  )
}
