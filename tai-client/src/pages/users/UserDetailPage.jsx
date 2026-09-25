import { useEffect, useState } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { ArrowLeft, Pencil } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import { ErrorState } from '../../components/ui/ListStates'

const ROLE_BADGE_VARIANT = {
  ADMIN: 'orange',
  MANAGER: 'navy',
  STAFF: 'neutral',
}

export default function UserDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { hasRole } = useAuth()
  const canManage = hasRole('ADMIN')

  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    axiosClient
      .get(`/users/${id}`)
      .then((res) => {
        if (!cancelled) setUser(res.data.data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || 'Failed to load user')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [id])

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/users" className="inline-flex items-center gap-1 text-sm text-navy-500 hover:text-navy-700">
            <ArrowLeft size={14} /> Back to users
          </Link>
          <h2 className="mt-2 text-2xl font-bold text-navy-800">User details</h2>
        </div>
        {canManage && user && (
          <Button variant="secondary" onClick={() => navigate(`/users/${id}/edit`)}>
            <Pencil size={14} /> Edit
          </Button>
        )}
      </div>

      <div className="rounded-lg border border-navy-100 bg-white p-6 shadow-sm">
        {loading ? (
          <div className="space-y-4">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-5 animate-pulse rounded bg-navy-100" />
            ))}
          </div>
        ) : error ? (
          <ErrorState message={error} />
        ) : (
          <dl className="divide-y divide-navy-100">
            <Row label="Full name" value={user.fullName} />
            <Row label="Email" value={user.email} />
            <Row label="Phone" value={user.phoneNumber || '—'} />
            <Row
              label="Roles"
              value={
                <div className="flex flex-wrap gap-1">
                  {user.roles.map((r) => (
                    <Badge key={r} variant={ROLE_BADGE_VARIANT[r] || 'neutral'}>
                      {r}
                    </Badge>
                  ))}
                </div>
              }
            />
            <Row
              label="Status"
              value={<Badge variant={user.active ? 'success' : 'neutral'}>{user.active ? 'Active' : 'Inactive'}</Badge>}
            />
          </dl>
        )}
      </div>
    </div>
  )
}

function Row({ label, value }) {
  return (
    <div className="grid grid-cols-3 gap-4 py-3 text-sm">
      <dt className="font-medium text-navy-500">{label}</dt>
      <dd className="col-span-2 text-navy-800">{value}</dd>
    </div>
  )
}
