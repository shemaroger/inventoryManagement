import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import { ErrorState, EmptyState } from '../../components/ui/ListStates'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { FileText } from 'lucide-react'

export default function CustomerStatementPage() {
  const { id } = useParams()
  const [customer, setCustomer] = useState(null)
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    Promise.all([
      axiosClient.get('/customers').then((res) => res.data.data.find((c) => String(c.id) === id)),
      axiosClient.get(`/customers/${id}/statement`).then((res) => res.data.data),
    ])
      .then(([cust, stmt]) => {
        setCustomer(cust)
        setEntries(stmt)
      })
      .catch((err) => setError(err.response?.data?.message || 'Failed to load statement'))
      .finally(() => setLoading(false))
  }, [id])

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <Link to="/customers" className="inline-flex items-center gap-1 text-sm text-navy-500 hover:text-navy-700">
          <ArrowLeft size={14} /> Back to customers
        </Link>
        <h2 className="mt-2 text-2xl font-bold text-navy-800">
          {customer ? `${customer.name} — Statement` : 'Customer statement'}
        </h2>
        {customer && (
          <p className="text-navy-400">
            Credit limit {Number(customer.creditLimit).toFixed(2)} · Currently owing{' '}
            <span className={Number(customer.currentBalance) > 0 ? 'font-medium text-orange-600' : ''}>
              {Number(customer.currentBalance).toFixed(2)}
            </span>
          </p>
        )}
      </div>

      {error && <ErrorState message={error} />}

      <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-navy-50 text-navy-500">
              <tr>
                <th className="px-4 py-3 font-medium">Date</th>
                <th className="px-4 py-3 font-medium">Description</th>
                <th className="px-4 py-3 font-medium text-right">Debit</th>
                <th className="px-4 py-3 font-medium text-right">Credit</th>
                <th className="px-4 py-3 font-medium text-right">Balance</th>
              </tr>
            </thead>
            {loading ? (
              <SkeletonRows rows={5} columns={5} />
            ) : (
              !error &&
              entries.length > 0 && (
                <tbody className="divide-y divide-navy-100">
                  {entries.map((e, i) => (
                    <tr key={i}>
                      <td className="px-4 py-3 text-navy-600">{e.date}</td>
                      <td className="px-4 py-3 text-navy-800">{e.description}</td>
                      <td className="px-4 py-3 text-right tabular-nums text-navy-600">
                        {Number(e.debit) > 0 ? Number(e.debit).toFixed(2) : '—'}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums text-navy-600">
                        {Number(e.credit) > 0 ? Number(e.credit).toFixed(2) : '—'}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums font-medium text-navy-800">
                        {Number(e.runningBalance).toFixed(2)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              )
            )}
          </table>

          {!loading && !error && entries.length === 0 && (
            <EmptyState icon={FileText} title="No activity yet" description="This customer has no sales or payments on record." />
          )}
        </div>
      </div>
    </div>
  )
}
