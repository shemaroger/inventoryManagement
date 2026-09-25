import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ShieldCheck, CheckCircle2, XCircle } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Select from '../../components/ui/Select'
import Badge from '../../components/ui/Badge'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { TablePagination } from '../../components/ui/Table'
import ReviewAnomalyModal from './ReviewAnomalyModal'

const TYPE_LABEL = {
  QUANTITY_OUTLIER: 'Unusual quantity',
  FREQUENCY_OUTLIER: 'Unusual frequency',
  TIMING_OUTLIER: 'Unusual timing',
  RECONCILIATION_MISMATCH: 'Reconciliation mismatch',
}

const SEVERITY_VARIANT = { low: 'neutral', medium: 'orange', high: 'danger' }
const STATUS_VARIANT = { OPEN: 'orange', REVIEWED: 'success', DISMISSED: 'neutral' }

export default function AnomaliesList() {
  const { hasRole } = useAuth()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const isAdmin = hasRole('ADMIN')

  const [anomalies, setAnomalies] = useState([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [statusFilter, setStatusFilter] = useState('OPEN')
  const [severityFilter, setSeverityFilter] = useState('')

  const [reviewTarget, setReviewTarget] = useState(null)
  const [reviewAction, setReviewAction] = useState(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!isAdmin) navigate('/', { replace: true })
  }, [isAdmin, navigate])

  useEffect(() => {
    setPage(0)
  }, [statusFilter, severityFilter])

  useEffect(() => {
    if (!isAdmin) return
    let cancelled = false
    setLoading(true)
    setError(null)
    axiosClient
      .get('/anomalies', {
        params: {
          status: statusFilter || undefined,
          severity: severityFilter || undefined,
          page,
          size: 10,
        },
      })
      .then((res) => {
        if (cancelled) return
        const pageData = res.data.data
        setAnomalies(pageData.content)
        setTotalElements(pageData.totalElements)
        setTotalPages(pageData.totalPages)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load flagged activity')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [isAdmin, statusFilter, severityFilter, page])

  function openReview(anomaly, action) {
    setReviewTarget(anomaly)
    setReviewAction(action)
  }

  async function handleReviewSubmit(payload) {
    setSaving(true)
    try {
      await axiosClient.patch(`/anomalies/${reviewTarget.id}/review`, payload)
      showToast(payload.status === 'DISMISSED' ? 'Item dismissed' : 'Marked as reviewed', { type: 'success' })
      setReviewTarget(null)
      setAnomalies((prev) => prev.filter((a) => a.id !== reviewTarget.id))
      setTotalElements((prev) => prev - 1)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to update', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  if (!isAdmin) return null

  return (
    <div className="space-y-6">
      <div>
        <h2 className="flex items-center gap-2 text-2xl font-bold text-navy-800">
          <ShieldCheck size={22} className="text-orange-500" /> Activity Review
        </h2>
        <p className="text-navy-400">
          Statistically unusual stock adjustments flagged for a human look — not an accusation. Many flagged items turn out to be
          legitimate (bulk orders, promotions, corrections). Admin-only.
        </p>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="sm:w-48" aria-label="Filter by status">
          <option value="">All statuses</option>
          <option value="OPEN">Open</option>
          <option value="REVIEWED">Reviewed</option>
          <option value="DISMISSED">Dismissed</option>
        </Select>
        <Select value={severityFilter} onChange={(e) => setSeverityFilter(e.target.value)} className="sm:w-48" aria-label="Filter by severity">
          <option value="">All severities</option>
          <option value="low">Low</option>
          <option value="medium">Medium</option>
          <option value="high">High</option>
        </Select>
      </div>

      {error && <ErrorState message={error} />}

      {loading ? (
        <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
          <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <SkeletonRows rows={5} columns={5} />
          </table>
          </div>
        </div>
      ) : !error && anomalies.length === 0 ? (
        <EmptyState
          icon={ShieldCheck}
          title="No unusual activity to review"
          description="Everything looks consistent with normal patterns right now."
        />
      ) : (
        !error && (
          <div className="space-y-3">
            {anomalies.map((a) => (
              <div key={a.id} className="rounded-lg border border-navy-100 bg-white p-5 shadow-sm">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="font-semibold text-navy-800">{TYPE_LABEL[a.anomalyType] || a.anomalyType}</h3>
                      <Badge variant={SEVERITY_VARIANT[a.severityLevel]}>{a.severityLevel} severity</Badge>
                      <Badge variant={STATUS_VARIANT[a.status]}>{a.status.toLowerCase()}</Badge>
                    </div>
                    <p className="mt-1 text-sm text-navy-600">
                      {a.productName} · {a.warehouseName || '—'}
                    </p>
                    <p className="mt-2 text-sm text-navy-700">{a.contextNote}</p>
                    <p className="mt-2 text-xs text-navy-400">
                      Performed by {a.performedByName || 'unknown'}
                      {a.adjustmentCreatedAt && ` on ${new Date(a.adjustmentCreatedAt).toLocaleString()}`}
                      {' · '}Flagged {new Date(a.detectedAt).toLocaleString()}
                    </p>
                    {a.status !== 'OPEN' && a.reviewNote && (
                      <p className="mt-2 rounded-md bg-navy-50 px-3 py-2 text-xs text-navy-600">
                        <span className="font-medium">{a.reviewedByName}'s note:</span> {a.reviewNote}
                      </p>
                    )}
                  </div>

                  {a.status === 'OPEN' && (
                    <div className="flex shrink-0 gap-2">
                      <Button variant="secondary" onClick={() => openReview(a, 'REVIEWED')} className="px-3 py-1.5 text-xs">
                        <CheckCircle2 size={14} /> Mark Reviewed
                      </Button>
                      <Button variant="ghost" onClick={() => openReview(a, 'DISMISSED')} className="px-3 py-1.5 text-xs text-navy-500 hover:bg-navy-50">
                        <XCircle size={14} /> Dismiss
                      </Button>
                    </div>
                  )}
                </div>
              </div>
            ))}

            {totalElements > 0 && (
              <div className="rounded-lg border border-navy-100 bg-white shadow-sm">
                <TablePagination page={page} totalPages={totalPages} onPageChange={setPage} />
              </div>
            )}
          </div>
        )
      )}

      <ReviewAnomalyModal
        open={!!reviewTarget}
        onClose={() => setReviewTarget(null)}
        onSubmit={handleReviewSubmit}
        anomaly={reviewTarget}
        action={reviewAction}
        saving={saving}
      />
    </div>
  )
}
