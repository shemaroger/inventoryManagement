import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, Pencil, BookOpen, Landmark } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/ui/ToastProvider'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import RowActionsMenu from '../../components/ui/RowActionsMenu'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import TableSearchInput from '../../components/ui/TableSearchInput'
import AccountModal from './AccountModal'

const TYPE_ORDER = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE']
const TYPE_LABEL = {
  ASSET: 'Assets',
  LIABILITY: 'Liabilities',
  EQUITY: 'Equity',
  REVENUE: 'Revenue',
  EXPENSE: 'Expenses',
}
const TYPE_BADGE = {
  ASSET: 'success',
  LIABILITY: 'orange',
  EQUITY: 'navy',
  REVENUE: 'success',
  EXPENSE: 'orange',
}

export default function AccountsList() {
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')
  const { showToast } = useToast()
  const navigate = useNavigate()

  const [accounts, setAccounts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [modalOpen, setModalOpen] = useState(false)
  const [editingAccount, setEditingAccount] = useState(null)
  const [saving, setSaving] = useState(false)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.get('/accounts')
      setAccounts(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load chart of accounts')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  function openCreateModal() {
    setEditingAccount(null)
    setModalOpen(true)
  }

  function openEditModal(account) {
    setEditingAccount(account)
    setModalOpen(true)
  }

  async function handleModalSubmit(payload) {
    setSaving(true)
    try {
      if (editingAccount) {
        const res = await axiosClient.put(`/accounts/${editingAccount.id}`, payload)
        setAccounts((prev) => prev.map((a) => (a.id === editingAccount.id ? res.data.data : a)))
        showToast('Account updated', { type: 'success' })
      } else {
        const res = await axiosClient.post('/accounts', payload)
        setAccounts((prev) => [...prev, res.data.data])
        showToast('Account created', { type: 'success' })
      }
      setModalOpen(false)
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to save account', { type: 'error' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDeactivate(account) {
    try {
      await axiosClient.patch(`/accounts/${account.id}/deactivate`)
      setAccounts((prev) => prev.map((a) => (a.id === account.id ? { ...a, active: false } : a)))
      showToast(`${account.name} deactivated`, { type: 'success' })
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to deactivate account', { type: 'error' })
    }
  }

  const [search, setSearch] = useState('')
  const q = search.trim().toLowerCase()
  const matchesSearch = (a) => !q || a.name.toLowerCase().includes(q) || a.code.toLowerCase().includes(q)

  const grouped = TYPE_ORDER.map((type) => ({
    type,
    items: accounts.filter((a) => a.accountType === type && matchesSearch(a)).sort((a, b) => a.code.localeCompare(b.code)),
  })).filter((g) => g.items.length > 0)

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-navy-800">Chart of Accounts</h2>
          <p className="text-navy-400">The accounts every journal entry posts against — deactivate rather than delete to keep the audit trail intact.</p>
        </div>
        {isAdmin && (
          <Button onClick={openCreateModal} className="w-full sm:w-auto">
            <Plus size={16} /> Add Account
          </Button>
        )}
      </div>

      {error && <ErrorState message={error} />}

      {accounts.length > 0 && (
        <TableSearchInput value={search} onChange={setSearch} placeholder="Search by name or code…" className="sm:w-80" />
      )}

      {loading ? (
        <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
          <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                <th className="px-4 py-3 font-medium">Code</th>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <SkeletonRows rows={6} columns={4} />
          </table>
          </div>
        </div>
      ) : !error && grouped.length > 0 ? (
        <div className="space-y-6">
          {grouped.map((group) => (
            <div key={group.type} className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
              <div className="flex items-center gap-2 border-b border-navy-100 bg-navy-50 px-4 py-3">
                <Badge variant={TYPE_BADGE[group.type]}>{TYPE_LABEL[group.type]}</Badge>
              </div>
              <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <tbody className="divide-y divide-navy-100">
                  {group.items.map((a) => (
                    <tr key={a.id} className="transition-colors duration-150 hover:bg-navy-50/50">
                      <td className="w-24 px-4 py-3 font-mono text-navy-600">{a.code}</td>
                      <td className="px-4 py-3">
                        <button
                          type="button"
                          onClick={() => navigate(`/accounts/${a.id}/ledger`)}
                          className="font-medium text-navy-800 hover:text-orange-600 hover:underline"
                        >
                          {a.name}
                        </button>
                        {a.parentAccountName && <span className="ml-2 text-xs text-navy-400">under {a.parentAccountName}</span>}
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant={a.active ? 'success' : 'neutral'}>{a.active ? 'Active' : 'Inactive'}</Badge>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <RowActionsMenu
                          label={`Actions for ${a.name}`}
                          items={[
                            { label: 'View ledger', icon: <BookOpen size={14} />, onClick: () => navigate(`/accounts/${a.id}/ledger`) },
                            ...(isAdmin
                              ? [{ label: 'Edit', icon: <Pencil size={14} />, onClick: () => openEditModal(a) }]
                              : []),
                            ...(isAdmin && a.active
                              ? [{ label: 'Deactivate', onClick: () => handleDeactivate(a) }]
                              : []),
                          ]}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              </div>
            </div>
          ))}
        </div>
      ) : (
        !error && (
          <EmptyState
            icon={Landmark}
            title={accounts.length === 0 ? 'No accounts yet' : 'No accounts match your search'}
            description={accounts.length === 0 ? 'Add your first account to start recording journal entries.' : 'Try a different search term.'}
            action={accounts.length === 0 && isAdmin && <Button onClick={openCreateModal}><Plus size={16} /> Add Account</Button>}
          />
        )
      )}

      <AccountModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onSubmit={handleModalSubmit}
        initial={editingAccount}
        accounts={accounts}
        saving={saving}
      />
    </div>
  )
}
