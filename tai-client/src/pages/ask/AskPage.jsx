import { useState } from 'react'
import { Sparkles, Search, AlertCircle } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import { SkeletonRows } from '../../components/ui/Skeleton'
import { EmptyState } from '../../components/ui/ListStates'

const EXAMPLE_QUESTIONS = [
  'show me all cement products low on stock in Kigali',
  'show me products from Dangote',
  'which items need to be reordered',
]

export default function AskPage() {
  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)

  async function runQuery(q) {
    const text = (q ?? question).trim()
    if (!text) return
    setQuestion(text)
    setLoading(true)
    setResult(null)
    try {
      const res = await axiosClient.post('/ai/query', { question: text })
      setResult(res.data.data)
    } catch (err) {
      setResult({ understood: false, message: err.response?.data?.message || 'Something went wrong running that search.' })
    } finally {
      setLoading(false)
    }
  }

  function handleSubmit(e) {
    e.preventDefault()
    runQuery()
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div>
        <h2 className="flex items-center gap-2 text-2xl font-bold text-navy-800">
          <Sparkles size={22} className="text-orange-500" /> Ask Tai
        </h2>
        <p className="text-navy-400">
          Ask a question in plain language — this searches your real product and stock data, it never makes numbers up.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-navy-300" />
          <Input
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="e.g. show me all cement products low on stock in Kigali"
            className="pl-9"
            aria-label="Ask a question"
          />
        </div>
        <Button type="submit" loading={loading}>
          Ask
        </Button>
      </form>

      {!result && !loading && (
        <div className="flex flex-wrap gap-2">
          {EXAMPLE_QUESTIONS.map((q) => (
            <button
              key={q}
              onClick={() => runQuery(q)}
              className="rounded-full border border-navy-200 px-3 py-1.5 text-xs text-navy-500 transition-colors duration-150 hover:bg-navy-50"
            >
              {q}
            </button>
          ))}
        </div>
      )}

      {loading && (
        <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
          <table className="w-full text-left text-sm">
            <SkeletonRows rows={4} columns={4} />
          </table>
        </div>
      )}

      {result && !loading && !result.understood && (
        <div className="flex items-start gap-2 rounded-md border border-orange-200 bg-orange-50 px-4 py-3 text-sm text-orange-700">
          <AlertCircle size={16} className="mt-0.5 shrink-0" />
          {result.message}
        </div>
      )}

      {result && !loading && result.understood && (
        <div className="space-y-3">
          <p className="text-sm text-navy-500">{result.message}</p>

          {result.products && (
            <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="bg-navy-50 text-navy-500">
                    <tr>
                      <th className="px-4 py-3 font-medium">SKU</th>
                      <th className="px-4 py-3 font-medium">Name</th>
                      <th className="px-4 py-3 font-medium">Category</th>
                      <th className="px-4 py-3 font-medium">Brand</th>
                      <th className="px-4 py-3 font-medium text-right">Price</th>
                      <th className="px-4 py-3 font-medium text-right">Stock</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-navy-100">
                    {result.products.content.map((p) => (
                      <tr key={p.id} className="hover:bg-navy-50/50">
                        <td className="px-4 py-3 text-navy-600">{p.sku}</td>
                        <td className="px-4 py-3 font-medium text-navy-800">{p.name}</td>
                        <td className="px-4 py-3 text-navy-600">{p.categoryName || '—'}</td>
                        <td className="px-4 py-3 text-navy-600">{p.brandName || '—'}</td>
                        <td className="px-4 py-3 text-right text-navy-600">{Number(p.sellingPrice).toFixed(2)}</td>
                        <td className="px-4 py-3 text-right tabular-nums text-navy-600">{Number(p.totalStock)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {result.products.content.length === 0 && (
                <EmptyState icon={Search} title="No matching products" description="Try a different question." />
              )}
            </div>
          )}

          {result.stockItems && (
            <div className="overflow-hidden rounded-lg border border-navy-100 bg-white shadow-sm">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="bg-navy-50 text-navy-500">
                    <tr>
                      <th className="px-4 py-3 font-medium">Product</th>
                      <th className="px-4 py-3 font-medium">Warehouse</th>
                      <th className="px-4 py-3 font-medium text-right">Quantity</th>
                      <th className="px-4 py-3 font-medium">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-navy-100">
                    {result.stockItems.map((s) => (
                      <tr key={s.id} className="hover:bg-navy-50/50">
                        <td className="px-4 py-3 font-medium text-navy-800">{s.productName}</td>
                        <td className="px-4 py-3 text-navy-600">{s.warehouseName}</td>
                        <td className="px-4 py-3 text-right tabular-nums text-navy-600">{Number(s.quantity)}</td>
                        <td className="px-4 py-3">
                          <Badge variant="orange">Low</Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {result.stockItems.length === 0 && (
                <EmptyState icon={Search} title="Nothing matches" description="No low-stock items fit that description right now." />
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
