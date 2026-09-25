import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CalendarRange, Search, ShoppingCart } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Select from '../../components/ui/Select'
import SearchableSelect from '../../components/ui/SearchableSelect'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import { EmptyState } from '../../components/ui/ListStates'

const CONFIDENCE_VARIANT = {
  insufficient_data: 'neutral',
  low: 'danger',
  medium: 'orange',
  high: 'success',
}

const CONFIDENCE_LABEL = {
  insufficient_data: 'Insufficient data',
  low: 'Low confidence',
  medium: 'Medium confidence',
  high: 'High confidence',
}

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

export default function SeasonalTab({ products }) {
  const navigate = useNavigate()
  const [productId, setProductId] = useState('')
  const [period, setPeriod] = useState('month')
  const [targetBucket, setTargetBucket] = useState('')
  const [loading, setLoading] = useState(false)
  const [prediction, setPrediction] = useState(null)
  const [error, setError] = useState(null)

  async function runPrediction() {
    if (!productId || !targetBucket) return
    setLoading(true)
    setError(null)
    setPrediction(null)
    try {
      const params = { productId, period }
      if (period === 'month') params.targetMonth = targetBucket
      else params.targetQuarter = targetBucket

      const res = await axiosClient.get('/ai/seasonal-demand/predict', { params })
      setPrediction(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load seasonal prediction')
    } finally {
      setLoading(false)
    }
  }

  async function handleCreatePo() {
    if (!prediction || prediction.suggestedQuantity == null) return
    let unitCost = 0
    try {
      const res = await axiosClient.get(`/products/${prediction.productId}`)
      unitCost = res.data.data.costPrice
    } catch {
      // fall back to 0 — user can fill it in on the form
    }
    navigate('/purchase-orders/new', {
      state: {
        suggestion: {
          warehouseId: null,
          supplierId: null,
          suggestedLines: [
            {
              productId: prediction.productId,
              suggestedQuantity: prediction.suggestedQuantity,
              unitCost,
              reasoning: prediction.explanation,
            },
          ],
        },
      },
    })
  }

  const maxQuantity = prediction?.yearlyBreakdown?.length
    ? Math.max(...prediction.yearlyBreakdown.map((y) => Number(y.quantity)))
    : 0

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <SearchableSelect
          label="Product"
          value={productId}
          onChange={(id) => { setProductId(id); setPrediction(null) }}
          options={products.map((p) => ({ value: String(p.id), label: `${p.name} (${p.sku})` }))}
          placeholder="Search products…"
          className="sm:w-64"
        />
        <Select
          label="Period type"
          value={period}
          onChange={(e) => { setPeriod(e.target.value); setTargetBucket(''); setPrediction(null) }}
          className="sm:w-40"
        >
          <option value="month">Month</option>
          <option value="quarter">Quarter</option>
        </Select>
        <Select
          label={period === 'month' ? 'Month' : 'Quarter'}
          value={targetBucket}
          onChange={(e) => setTargetBucket(e.target.value)}
          className="sm:w-44"
        >
          <option value="">— Select —</option>
          {period === 'month'
            ? MONTHS.map((m, i) => <option key={m} value={i + 1}>{m}</option>)
            : [1, 2, 3, 4].map((q) => <option key={q} value={q}>Q{q}</option>)}
        </Select>
        <Button onClick={runPrediction} loading={loading} disabled={!productId || !targetBucket}>
          Predict
        </Button>
      </div>

      {!prediction && !loading && (
        <EmptyState
          icon={CalendarRange}
          title="Pick a product and a period"
          description="See what demand typically looked like at that time of year, based on past history."
        />
      )}

      {loading && <div className="h-48 animate-pulse rounded-lg bg-navy-100" />}

      {error && <p className="text-sm text-red-600">{error}</p>}

      {prediction && !loading && (
        <div className="rounded-lg border border-navy-100 bg-white p-6 shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-lg font-semibold text-navy-800">{prediction.productName} — {prediction.targetPeriod}</h3>
            <Badge variant={CONFIDENCE_VARIANT[prediction.confidence]}>{CONFIDENCE_LABEL[prediction.confidence]}</Badge>
          </div>

          {prediction.suggestedQuantity != null ? (
            <p className="mt-3 text-3xl font-bold text-navy-800">
              ~{Number(prediction.suggestedQuantity)} units
              <span className="ml-2 text-sm font-normal text-navy-400">suggested demand</span>
            </p>
          ) : (
            <p className="mt-3 text-lg font-semibold text-navy-500">No projection available yet</p>
          )}

          <p className="mt-2 text-sm text-navy-500">{prediction.explanation}</p>
          <p className="mt-1 text-xs italic text-navy-300">{prediction.dataSource}</p>

          {prediction.yearlyBreakdown.length > 0 && (
            <div className="mt-6">
              <p className="mb-2 text-xs font-medium uppercase tracking-wide text-navy-400">Historical breakdown</p>
              <div className="flex items-end gap-4" style={{ height: 120 }}>
                {prediction.yearlyBreakdown.map((y) => (
                  <div key={y.year} className="flex flex-1 flex-col items-center justify-end gap-1">
                    <span className="text-xs font-medium text-navy-600">{Number(y.quantity)}</span>
                    <div
                      className="w-full rounded-t bg-orange-400"
                      style={{ height: `${maxQuantity > 0 ? (Number(y.quantity) / maxQuantity) * 80 : 0}px` }}
                    />
                    <span className="text-xs text-navy-400">{y.year}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {prediction.suggestedQuantity != null && (
            <div className="mt-6 border-t border-navy-100 pt-4">
              <Button variant="secondary" onClick={handleCreatePo}>
                <ShoppingCart size={14} /> Draft a Purchase Order with this quantity
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
