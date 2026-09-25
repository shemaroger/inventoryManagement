import { useState } from 'react'
import { LineChart as LineChartIcon, DollarSign, Tags, RefreshCw, Users, Truck } from 'lucide-react'
import Input from '../../components/ui/Input'
import SalesAnalysisReport from './SalesAnalysisReport'
import ProfitByProductReport from './ProfitByProductReport'
import ProfitByCategoryReport from './ProfitByCategoryReport'
import InventoryTurnoverReport from './InventoryTurnoverReport'
import CustomerPerformanceReport from './CustomerPerformanceReport'
import SupplierPerformanceReport from './SupplierPerformanceReport'

function firstOfMonth() {
  const d = new Date()
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10)
}

const TABS = [
  { key: 'sales', label: 'Sales Analysis', icon: LineChartIcon, Component: SalesAnalysisReport },
  { key: 'profit-product', label: 'Profit by Product', icon: DollarSign, Component: ProfitByProductReport },
  { key: 'profit-category', label: 'Profit by Category', icon: Tags, Component: ProfitByCategoryReport },
  { key: 'turnover', label: 'Inventory Turnover', icon: RefreshCw, Component: InventoryTurnoverReport },
  { key: 'customers', label: 'Customer Performance', icon: Users, Component: CustomerPerformanceReport },
  { key: 'suppliers', label: 'Supplier Performance', icon: Truck, Component: SupplierPerformanceReport },
]

export default function AnalyticsPage() {
  const [startDate, setStartDate] = useState(firstOfMonth())
  const [endDate, setEndDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [activeTab, setActiveTab] = useState(TABS[0].key)

  const ActiveComponent = TABS.find((t) => t.key === activeTab)?.Component

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-navy-800">Analytics</h2>
        <p className="text-navy-400">Pick a period and dig into the numbers — for deliberate review, not the daily at-a-glance view.</p>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-navy-100 bg-white p-4 shadow-sm">
        <Input label="Start date" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-44" />
        <Input label="End date" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-44" />
      </div>

      <div className="flex flex-wrap gap-1 border-b border-navy-100">
        {TABS.map((tab) => {
          const Icon = tab.icon
          const active = tab.key === activeTab
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors duration-150 ${
                active
                  ? 'border-orange-500 text-orange-600'
                  : 'border-transparent text-navy-400 hover:text-navy-600'
              }`}
            >
              <Icon size={15} />
              {tab.label}
            </button>
          )
        })}
      </div>

      {ActiveComponent && <ActiveComponent startDate={startDate} endDate={endDate} />}
    </div>
  )
}
