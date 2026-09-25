import DailyRecordsPanel from './DailyRecordsPanel'

export default function DailyRecordsPage() {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-navy-800">Daily Records</h2>
        <p className="text-navy-400">
          Statutory daily sales and purchases records (cash vs. credit) per Rwanda's Tax Procedures Law.
        </p>
      </div>

      <DailyRecordsPanel />
    </div>
  )
}
