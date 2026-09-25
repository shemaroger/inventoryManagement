import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, BookText } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Button from '../../components/ui/Button'
import Select from '../../components/ui/Select'
import Badge from '../../components/ui/Badge'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import TableSearchInput from '../../components/ui/TableSearchInput'
import useDebouncedValue from '../../hooks/useDebouncedValue'

const SOURCE_OPTIONS = ['MANUAL', 'SALE', 'PURCHASE', 'PAYMENT', 'ADJUSTMENT']
const SOURCE_VARIANT = {
  MANUAL: 'navy',
  SALE: 'success',
  PURCHASE: 'orange',
  PAYMENT: 'neutral',
  ADJUSTMENT: 'danger',
}

export default function JournalEntriesList() {
  const navigate = useNavigate()

  const [entries, setEntries] = useState([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [sourceFilter, setSourceFilter] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const debouncedSearch = useDebouncedValue(searchInput, 300)

  useEffect(() => {
    setPage(0)
  }, [sourceFilter, debouncedSearch])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    axiosClient
      .get('/journal-entries', {
        params: { sourceType: sourceFilter || undefined, search: debouncedSearch.trim() || undefined, page, size: 10 },
      })
      .then((res) => {
        if (cancelled) return
        const pageData = res.data.data
        setEntries(pageData.content)
        setTotalElements(pageData.totalElements)
        setTotalPages(pageData.totalPages)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load journal entries')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [sourceFilter, debouncedSearch, page])

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Journal Entries</h2>
          <p className="text-navy-400">Every double-entry posting — automatic from Sales/Purchasing, or manual for corrections and one-off transactions.</p>
        </div>
        <Button onClick={() => navigate('/journal-entries/new')} className="w-full sm:w-auto">
          <Plus size={16} /> New Manual Entry
        </Button>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <TableSearchInput value={searchInput} onChange={setSearchInput} placeholder="Search by description or reference…" className="sm:w-80" />
        <Select value={sourceFilter} onChange={(e) => setSourceFilter(e.target.value)} className="sm:w-48" aria-label="Filter by source">
          <option value="">All sources</option>
          {SOURCE_OPTIONS.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </Select>
      </div>

      {error && <ErrorState message={error} />}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                <th className="px-4 py-3 font-medium">Date</th>
                <th className="px-4 py-3 font-medium">Description</th>
                <th className="px-4 py-3 font-medium">Reference</th>
                <th className="px-4 py-3 font-medium">Source</th>
                <th className="px-4 py-3 font-medium text-right">Total</th>
              </tr>
            </thead>
            {loading ? (
              <SkeletonRows rows={6} columns={5} />
            ) : (
              !error &&
              entries.length > 0 && (
                <tbody className="divide-y divide-navy-100">
                  {entries.map((e) => (
                    <tr key={e.id} className="transition-colors duration-150 hover:bg-navy-50/50">
                      <td className="px-4 py-3 text-navy-600">{e.entryDate}</td>
                      <td className="px-4 py-3 font-medium text-navy-800">{e.description}</td>
                      <td className="px-4 py-3 text-navy-600">{e.reference || '—'}</td>
                      <td className="px-4 py-3">
                        <Badge variant={SOURCE_VARIANT[e.sourceType]}>{e.sourceType}</Badge>
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums font-medium text-navy-800">
                        {Number(e.totalDebit).toFixed(2)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              )
            )}
          </table>

          {!loading && !error && entries.length === 0 && (
            <EmptyState
              icon={BookText}
              title="No journal entries yet"
              description="Entries appear here automatically as sales complete and purchases are received, or you can create one manually."
              action={<Button onClick={() => navigate('/journal-entries/new')}><Plus size={16} /> New Manual Entry</Button>}
            />
          )}
        </div>

        {!loading && !error && totalElements > 0 && (
          <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
        )}
      </div>
    </div>
  )
}
