import { useEffect, useState } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState } from '../../components/ui/ListStates'

const ROLE_OPTIONS = ['ADMIN', 'MANAGER', 'STAFF']

const emptyForm = { fullName: '', email: '', phoneNumber: '', password: '', roleName: 'STAFF' }

export default function UserFormPage() {
  const { id } = useParams()
  const isEdit = !!id
  const navigate = useNavigate()
  const { showToast } = useToast()

  const [form, setForm] = useState(emptyForm)
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(isEdit)
  const [loadError, setLoadError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [userName, setUserName] = useState('')

  useEffect(() => {
    if (!isEdit) return
    let cancelled = false
    setLoading(true)
    setLoadError(null)
    axiosClient
      .get(`/users/${id}`)
      .then((res) => {
        if (cancelled) return
        const u = res.data.data
        setUserName(u.fullName)
        setForm({
          fullName: u.fullName,
          email: u.email,
          phoneNumber: u.phoneNumber || '',
          password: '',
          roleName: u.roles?.[0] || 'STAFF',
        })
      })
      .catch((err) => {
        if (!cancelled) setLoadError(err.response?.data?.message || 'Failed to load user')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [id, isEdit])

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  function validate() {
    const next = {}
    if (!form.fullName.trim()) next.fullName = 'Full name is required'
    if (!isEdit) {
      if (!form.email.trim()) next.email = 'Email is required'
      if (!form.password || form.password.length < 8) next.password = 'Minimum 8 characters'
    }
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!validate()) return

    setSaving(true)
    try {
      if (isEdit) {
        await axiosClient.put(`/users/${id}`, {
          fullName: form.fullName,
          phoneNumber: form.phoneNumber,
          roleName: form.roleName,
        })
        showToast('User updated', { type: 'success' })
      } else {
        await axiosClient.post('/users', form)
        showToast('User created', { type: 'success' })
      }
      navigate('/users')
    } catch (err) {
      showToast(err.response?.data?.message || 'Save failed', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <div>
        <Link to="/users" className="inline-flex items-center gap-1 text-sm text-navy-500 hover:text-navy-700">
          <ArrowLeft size={14} /> Back to users
        </Link>
        <h2 className="mt-2 text-2xl font-bold text-navy-800">
          {isEdit ? `Edit ${userName || 'user'}` : 'Add user'}
        </h2>
      </div>

      <div className="rounded-lg border border-navy-100 bg-white p-6 shadow-sm">
        {loading ? (
          <div className="space-y-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-10 animate-pulse rounded bg-navy-100" />
            ))}
          </div>
        ) : loadError ? (
          <ErrorState message={loadError} />
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input
              label="Full name"
              value={form.fullName}
              onChange={(e) => update('fullName', e.target.value)}
              error={errors.fullName}
            />
            <Input
              label="Email"
              type="email"
              value={form.email}
              onChange={(e) => update('email', e.target.value)}
              error={errors.email}
              disabled={isEdit}
            />
            <Input
              label="Phone"
              value={form.phoneNumber}
              onChange={(e) => update('phoneNumber', e.target.value)}
            />
            {!isEdit && (
              <Input
                label="Temporary password"
                type="password"
                value={form.password}
                onChange={(e) => update('password', e.target.value)}
                error={errors.password}
              />
            )}
            <Select label="Role" value={form.roleName} onChange={(e) => update('roleName', e.target.value)}>
              {ROLE_OPTIONS.map((role) => (
                <option key={role} value={role}>
                  {role}
                </option>
              ))}
            </Select>

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="ghost" type="button" onClick={() => navigate('/users')} disabled={saving}>
                Cancel
              </Button>
              <Button type="submit" loading={saving}>
                {isEdit ? 'Save changes' : 'Create user'}
              </Button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
