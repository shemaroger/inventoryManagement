import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { History, ArrowUp, ArrowDown } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import useDebouncedValue from '../../hooks/useDebouncedValue'
import Select from '../../components/ui/Select'
import TableSearchInput from '../../components/ui/TableSearchInput'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import Badge from '../../components/ui/Badge'

const ACTION_VARIANT = {
  CREATE: 'success',
  UPDATE: 'orange',
  DELETE: 'danger',
}

function actionVariant(action) {
  if (ACTION_VARIANT[action]) return ACTION_VARIANT[action]
  if (action === 'LOGIN' || action === 'REGISTER' || action === 'VERIFY_OTP') return 'navy'
  if (action === 'DEACTIVATE' || action === 'CANCEL' || action === 'DISMISS') return 'danger'
  if (action === 'ACTIVATE' || action === 'CONFIRM' || action === 'COMPLETE' || action === 'APPROVE') return 'success'
  return 'neutral'
}




const SORT_COLUMNS = [
  { key: 'createdAt', label: 'When' },
  { key: 'actorId', label: 'Actor' },
  { key: 'action', label: 'Action' },
  { key: 'targetType', label: 'Target' },
]

export default function AuditLogList() {
  const { hasRole } = useAuth()
  const navigate = useNavigate()
  const isAdmin = hasRole('ADMIN')

  const [entries, setEntries] = useState([])
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [search, setSearch] = useState('')
  const debouncedSearch = useDebouncedValue(search, 300)
  const [actionFilter, setActionFilter] = useState('')
  const [targetTypeFilter, setTargetTypeFilter] = useState('')
  const [actions, setActions] = useState([])
  const [targetTypes, setTargetTypes] = useState([])

  const [sortBy, setSortBy] = useState('createdAt')
  const [sortDir, setSortDir] = useState('desc')

  useEffect(() => {
    if (!isAdmin) navigate('/', { replace: true })
  }, [isAdmin, navigate])

  useEffect(() => {
    if (!isAdmin) return
    axiosClient.get('/audit-log/actions').then((res) => setActions(res.data.data)).catch(() => {})
    axiosClient.get('/audit-log/target-types').then((res) => setTargetTypes(res.data.data)).catch(() => {})
  }, [isAdmin])

  useEffect(() => {
    setPage(0)
  }, [debouncedSearch, actionFilter, targetTypeFilter, sortBy, sortDir])

  useEffect(() => {
    if (!isAdmin) return
    let cancelled = false
    setLoading(true)
    setError(null)
    axiosClient
      .get('/audit-log', {
        params: {
          page,
          size: 10,
          search: debouncedSearch.trim() || undefined,
          action: actionFilter || undefined,
          targetType: targetTypeFilter || undefined,
          sortBy,
          sortDir,
        },
      })
      .then((res) => {
        if (cancelled) return
        setEntries(res.data.data.content)
        setTotalPages(res.data.data.totalPages)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load audit log')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [isAdmin, page, debouncedSearch, actionFilter, targetTypeFilter, sortBy, sortDir])

  const toggleSort = (column) => {
    if (sortBy === column) {
      setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortBy(column)
      setSortDir('desc')
    }
  }

  if (!isAdmin) return null

  const hasActiveFilters = search || actionFilter || targetTypeFilter

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-navy-800">Audit Log</h2>
        <p className="text-navy-400">Every create, update, and delete performed across the system, with who did it and from where.</p>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <TableSearchInput
          value={search}
          onChange={setSearch}
          placeholder="Search by actor, path, or detail…"
          className="sm:w-80"
        />
        <Select value={actionFilter} onChange={(e) => setActionFilter(e.target.value)} className="sm:w-44" aria-label="Filter by action">
          <option value="">All actions</option>
          {actions.map((a) => (
            <option key={a} value={a}>
              {a.replaceAll('_', ' ')}
            </option>
          ))}
        </Select>
        <Select value={targetTypeFilter} onChange={(e) => setTargetTypeFilter(e.target.value)} className="sm:w-48" aria-label="Filter by module">
          <option value="">All modules</option>
          {targetTypes.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </Select>
      </div>

      {error && <ErrorState message={error} />}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                {SORT_COLUMNS.map((col) => (
                  <th key={col.key} className="px-4 py-3 font-medium">
                    <button
                      type="button"
                      onClick={() => toggleSort(col.key)}
                      className="flex items-center gap-1 hover:text-navy-700"
                    >
                      {col.label}
                      {sortBy === col.key &&
                        (sortDir === 'asc' ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}
                    </button>
                  </th>
                ))}
                <th className="px-4 py-3 font-medium">Detail</th>
                <th className="px-4 py-3 font-medium">IP</th>
              </tr>
            </thead>
            {loading ? (
              <SkeletonRows rows={8} columns={6} />
            ) : (
              !error &&
              entries.length > 0 && (
                <tbody className="divide-y divide-navy-100">
                  {entries.map((e) => (
                    <tr key={e.id} className="transition-colors duration-150 hover:bg-navy-50/50">
                      <td className="whitespace-nowrap px-4 py-3 text-navy-600">
                        {new Date(e.createdAt).toLocaleString()}
                      </td>
                      <td className="px-4 py-3 font-medium text-navy-800">{e.actorName}</td>
                      <td className="px-4 py-3">
                        <Badge variant={actionVariant(e.action)}>{e.action?.replaceAll('_', ' ')}</Badge>
                      </td>
                      <td className="px-4 py-3 text-navy-600">
                        {e.targetType}
                        {e.targetId ? ` #${e.targetId}` : ''}
                      </td>
                      <td className="max-w-md truncate px-4 py-3 text-navy-600" title={e.detail}>
                        {e.detail}
                      </td>
                      <td className="px-4 py-3 text-navy-400">{e.ipAddress || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              )
            )}
          </table>

          {!loading && !error && entries.length === 0 && (
            <EmptyState
              icon={History}
              title="No matching activity"
              description={hasActiveFilters ? 'Try a different search term or filter combination.' : 'Actions across the system will appear here as they happen.'}
            />
          )}
        </div>

        {!loading && !error && entries.length > 0 && (
          <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
        )}
      </div>
    </div>
  )
}
