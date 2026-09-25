import { AlertTriangle, Inbox } from 'lucide-react'

export function ErrorState({ message }) {
  return (
    <div className="animate-tai-fade-in-up flex items-center gap-2 rounded-md border border-orange-300 bg-orange-50 px-4 py-3 text-sm text-orange-700">
      <AlertTriangle size={16} aria-hidden="true" />
      {message}
    </div>
  )
}

export function EmptyState({ icon: Icon = Inbox, title, description, action }) {
  return (
    <div className="animate-tai-fade-in-up flex flex-col items-center justify-center gap-2 py-12 text-center text-navy-400">
      <Icon size={32} aria-hidden="true" />
      <p className="font-medium text-navy-600">{title}</p>
      {description && <p className="text-sm">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}
