import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CalendarClock, CalendarDays, CalendarRange, Search, ShoppingCart } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import Select from '../../components/ui/Select'
import SearchableSelect from '../../components/ui/SearchableSelect'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import { EmptyState } from '../../components/ui/ListStates'

const HORIZONS = [
  { key: 'week', label: 'Next Week', icon: CalendarClock },
  { key: 'month', label: 'Next Month', icon: CalendarDays },
  { key: 'season', label: 'Season', icon: CalendarRange },
]

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

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

export default function ForecastPage() {
  const navigate = useNavigate()
  const [products, setProducts] = useState([])
  const [productId, setProductId] = useState('')
  const [horizon, setHorizon] = useState('week')
  const [targetPeriod, setTargetPeriod] = useState('')

  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    axiosClient.get('/products', { params: { size: 200 } })
      .then((res) => setProducts(res.data.data.content))
      .catch(() => {})
  }, [])

  useEffect(() => {
    setResult(null)
    setError(null)
  }, [horizon])

  async function runForecast() {
    if (!productId) return
    if (horizon === 'season' && !targetPeriod) return

    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const params = { productId, horizon }
      if (horizon === 'season') params.targetPeriod = targetPeriod
      const res = await axiosClient.get('/ai/forecast', { params })
      setResult(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load forecast')
    } finally {
      setLoading(false)
    }
  }

  async function handleCreatePo() {
    if (!result || result.suggestedQuantity == null) return
    let unitCost = 0
    try {
      const res = await axiosClient.get(`/products/${result.productId}`)
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
              productId: result.productId,
              suggestedQuantity: result.suggestedQuantity,
              unitCost,
              reasoning: result.explanation,
            },
          ],
        },
      },
    })
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-navy-800">AI Demand Forecast</h2>
        <p className="text-navy-400">Predict how much stock you'll need — grounded in your actual history, not a guess.</p>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-navy-100">
        {HORIZONS.map((h) => {
          const Icon = h.icon
          return (
            <button
              key={h.key}
              onClick={() => setHorizon(h.key)}
              className={`flex items-center gap-2 border-b-2 px-4 py-2 text-sm font-medium transition-colors duration-150 ${
                horizon === h.key
                  ? 'border-orange-500 text-orange-600'
                  : 'border-transparent text-navy-400 hover:text-navy-600'
              }`}
            >
              <Icon size={16} /> {h.label}
            </button>
          )
        })}
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <SearchableSelect
          label="Product"
          value={productId}
          onChange={setProductId}
          options={products.map((p) => ({ value: String(p.id), label: `${p.name} (${p.sku})` }))}
          placeholder="Search products…"
          className="sm:w-64"
        />
        {horizon === 'season' && (
          <Select label="Period" value={targetPeriod} onChange={(e) => setTargetPeriod(e.target.value)} className="sm:w-48">
            <option value="">— Select —</option>
            {MONTHS.map((m) => <option key={m} value={m}>{m}</option>)}
            {['Q1', 'Q2', 'Q3', 'Q4'].map((q) => <option key={q} value={q}>{q}</option>)}
          </Select>
        )}
        <Button onClick={runForecast} loading={loading} disabled={!productId || (horizon === 'season' && !targetPeriod)}>
          Forecast
        </Button>
      </div>

      {!result && !loading && !error && (
        <EmptyState
          icon={Search}
          title="Pick a product and horizon"
          description="Week/Month use your recent consumption trend. Season compares this same period across past years."
        />
      )}

      {loading && <div className="h-48 animate-pulse rounded-lg bg-navy-100" />}

      {error && <p className="text-sm text-red-600">{error}</p>}

      {result && !loading && (
        <div className="rounded-lg border border-navy-100 bg-white p-6 shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-lg font-semibold text-navy-800">{result.productName} — {result.targetPeriod}</h3>
            <Badge variant={CONFIDENCE_VARIANT[result.confidence]}>{CONFIDENCE_LABEL[result.confidence]}</Badge>
          </div>

          {result.suggestedQuantity != null ? (
            <p className="mt-3 text-3xl font-bold text-navy-800">
              ~{Number(result.suggestedQuantity)} units
              <span className="ml-2 text-sm font-normal text-navy-400">
                {result.method === 'trend' ? 'projected from recent trend' : 'seasonal average'}
              </span>
            </p>
          ) : (
            <p className="mt-3 text-lg font-semibold text-navy-500">No projection available yet</p>
          )}

          <p className="mt-2 text-sm text-navy-500">{result.explanation}</p>

          <div className="mt-4 grid grid-cols-2 gap-2 rounded-md bg-navy-50 px-3 py-2 text-sm sm:grid-cols-3">
            <div>
              <p className="font-bold text-navy-800">{result.basedOn.dataPoints}</p>
              <p className="text-[11px] text-navy-400">Data points</p>
            </div>
            {result.basedOn.dateRangeStart && (
              <div className="col-span-2 sm:col-span-1">
                <p className="text-xs font-medium text-navy-800">{result.basedOn.dateRangeStart} → {result.basedOn.dateRangeEnd}</p>
                <p className="text-[11px] text-navy-400">Date range</p>
              </div>
            )}
            {result.basedOn.yearsIncluded.length > 0 && (
              <div>
                <p className="text-xs font-medium text-navy-800">{result.basedOn.yearsIncluded.join(', ')}</p>
                <p className="text-[11px] text-navy-400">Years included</p>
              </div>
            )}
          </div>

          <p className="mt-3 text-xs italic text-navy-300">{result.dataSource}</p>

          {result.suggestedQuantity != null && (
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
