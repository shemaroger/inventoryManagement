import { useState } from 'react'
import { TrendingDown, Search } from 'lucide-react'
import axiosClient from '../../api/axiosClient'
import SearchableSelect from '../../components/ui/SearchableSelect'
import Badge from '../../components/ui/Badge'
import { EmptyState } from '../../components/ui/ListStates'

const CONFIDENCE_VARIANT = { low: 'neutral', medium: 'orange', high: 'success' }

export default function ForecastTab({ products }) {
  const [productId, setProductId] = useState('')
  const [loading, setLoading] = useState(false)
  const [forecast, setForecast] = useState(null)
  const [error, setError] = useState(null)

  async function handleChange(id) {
    setProductId(id)
    setForecast(null)
    setError(null)
    if (!id) return
    setLoading(true)
    try {
      const res = await axiosClient.get(`/ai/forecast/${id}`)
      setForecast(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load forecast')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      <SearchableSelect
        value={productId}
        onChange={handleChange}
        options={products.map((p) => ({ value: String(p.id), label: `${p.name} (${p.sku})` }))}
        placeholder="Search products…"
        className="sm:w-72"
      />

      {!productId && (
        <EmptyState icon={Search} title="Pick a product" description="Select a product above to see its reorder forecast." />
      )}

      {loading && <div className="h-32 animate-pulse rounded-lg bg-navy-100" />}

      {error && <p className="text-sm text-red-600">{error}</p>}

      {forecast && !loading && (
        <div className="rounded-lg border border-navy-100 bg-white p-6 shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-lg font-semibold text-navy-800">{forecast.productName}</h3>
            <Badge variant={CONFIDENCE_VARIANT[forecast.confidence]}>{forecast.confidence} confidence</Badge>
          </div>

          <div className="mt-4 flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-orange-50 text-orange-500">
              <TrendingDown size={22} />
            </div>
            <div>
              {forecast.daysUntilReorderNeeded != null ? (
                <p className="text-xl font-bold text-navy-800">
                  Reorder in ~{forecast.daysUntilReorderNeeded} day{forecast.daysUntilReorderNeeded === 1 ? '' : 's'}
                </p>
              ) : (
                <p className="text-xl font-bold text-navy-800">Not enough data to project</p>
              )}
              {forecast.projectedStockoutDate && (
                <p className="text-sm text-navy-400">Projected stockout: {forecast.projectedStockoutDate}</p>
              )}
            </div>
          </div>

          <div className="mt-4 grid grid-cols-3 gap-2 rounded-md bg-navy-50 px-3 py-2 text-center text-sm">
            <div>
              <p className="font-bold text-navy-800">{Number(forecast.currentStock)}</p>
              <p className="text-[11px] text-navy-400">Current stock</p>
            </div>
            <div>
              <p className="font-bold text-navy-800">{forecast.avgDailyConsumption}</p>
              <p className="text-[11px] text-navy-400">Avg. units/day</p>
            </div>
            <div>
              <p className="font-bold text-navy-800">{Number(forecast.reorderLevel)}</p>
              <p className="text-[11px] text-navy-400">Reorder level</p>
            </div>
          </div>

          <p className="mt-4 text-xs text-navy-400">{forecast.explanation}</p>
        </div>
      )}
    </div>
  )
}
