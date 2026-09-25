import { useEffect, useState } from 'react'
import { Building2, Save } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState } from '../../components/ui/ListStates'

const emptyForm = {
  legalName: '',
  tradingName: '',
  tinNumber: '',
  registrationNumber: '',
  addressLine: '',
  city: '',
  country: '',
  phone: '',
  email: '',
  website: '',
  logoUrl: '',
}

export default function CompanyProfilePage() {
  const { hasRole } = useAuth()
  const { showToast } = useToast()
  const canEdit = hasRole('ADMIN')

  const [form, setForm] = useState(emptyForm)
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [updatedAt, setUpdatedAt] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError(null)
    axiosClient
      .get('/company-settings')
      .then((res) => {
        if (cancelled) return
        const d = res.data.data
        setForm({
          legalName: d.legalName || '',
          tradingName: d.tradingName || '',
          tinNumber: d.tinNumber || '',
          registrationNumber: d.registrationNumber || '',
          addressLine: d.addressLine || '',
          city: d.city || '',
          country: d.country || '',
          phone: d.phone || '',
          email: d.email || '',
          website: d.website || '',
          logoUrl: d.logoUrl || '',
        })
        setUpdatedAt(d.updatedAt)
      })
      .catch((err) => {
        if (!cancelled) setLoadError(err.response?.data?.message || 'Failed to load company profile')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!form.legalName.trim()) {
      setErrors({ legalName: 'Legal name is required' })
      return
    }
    setSaving(true)
    try {
      const res = await axiosClient.put('/company-settings', form)
      setUpdatedAt(res.data.data.updatedAt)
      showToast('Company profile updated', { type: 'success' })
    } catch (err) {
      const message = err.response?.data?.message || 'Failed to update company profile'
      showToast(message, { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="max-w-2xl space-y-6">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Company Profile</h2>
          <p className="text-navy-400">Business details used across invoices, statutory records, and reports.</p>
        </div>
        <table className="w-full">
          <SkeletonRows rows={6} columns={1} />
        </table>
      </div>
    )
  }

  if (loadError) {
    return (
      <div className="max-w-2xl space-y-6">
        <h2 className="text-2xl font-bold text-navy-800">Company Profile</h2>
        <ErrorState message={loadError} />
      </div>
    )
  }

  return (
    <div className="max-w-2xl space-y-6">
      <div className="flex items-center gap-3">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-md bg-navy-600">
          <Building2 size={22} className="text-white" />
        </div>
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Company Profile</h2>
          <p className="text-navy-400">Business details used across invoices, statutory records, and reports.</p>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg border border-navy-100 bg-white p-6 shadow-sm">
        <fieldset disabled={!canEdit} className="space-y-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input
              label="Legal name"
              value={form.legalName}
              onChange={(e) => update('legalName', e.target.value)}
              error={errors.legalName}
              required
            />
            <Input
              label="Trading name"
              value={form.tradingName}
              onChange={(e) => update('tradingName', e.target.value)}
            />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input
              label="TIN number"
              value={form.tinNumber}
              onChange={(e) => update('tinNumber', e.target.value)}
              placeholder="e.g. 100000000"
            />
            <Input
              label="Registration number"
              value={form.registrationNumber}
              onChange={(e) => update('registrationNumber', e.target.value)}
              placeholder="RDB registration number"
            />
          </div>

          <Input
            label="Address"
            value={form.addressLine}
            onChange={(e) => update('addressLine', e.target.value)}
            placeholder="Street / KG address"
          />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input label="City" value={form.city} onChange={(e) => update('city', e.target.value)} />
            <Input label="Country" value={form.country} onChange={(e) => update('country', e.target.value)} />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input label="Phone" value={form.phone} onChange={(e) => update('phone', e.target.value)} />
            <Input
              label="Email"
              type="email"
              value={form.email}
              onChange={(e) => update('email', e.target.value)}
              error={errors.email}
            />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input label="Website" value={form.website} onChange={(e) => update('website', e.target.value)} />
            <Input
              label="Logo URL"
              value={form.logoUrl}
              onChange={(e) => update('logoUrl', e.target.value)}
              placeholder="/logo.png"
            />
          </div>
        </fieldset>

        {canEdit ? (
          <div className="flex items-center justify-between border-t border-navy-100 pt-4">
            {updatedAt && (
              <p className="text-xs text-navy-400">Last updated {new Date(updatedAt).toLocaleString()}</p>
            )}
            <Button type="submit" loading={saving} className="ml-auto">
              <Save size={16} /> Save changes
            </Button>
          </div>
        ) : (
          <p className="border-t border-navy-100 pt-4 text-sm text-navy-400">
            Only administrators can edit the company profile.
          </p>
        )}
      </form>
    </div>
  )
}
