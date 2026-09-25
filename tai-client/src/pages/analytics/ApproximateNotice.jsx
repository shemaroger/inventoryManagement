import { AlertTriangle } from 'lucide-react'

export default function ApproximateNotice({ children }) {
  return (
    <div className="mb-4 flex items-start gap-2 rounded-md border border-orange-200 bg-orange-50 px-4 py-3 text-sm text-orange-700">
      <AlertTriangle size={16} className="mt-0.5 shrink-0" />
      <span>{children}</span>
    </div>
  )
}
