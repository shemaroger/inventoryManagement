import { X } from 'lucide-react'
import useFocusTrap from './useFocusTrap'

export default function Drawer({ open, onClose, title, children, footer }) {
  const containerRef = useFocusTrap(open, onClose)

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div
        className="absolute inset-0 bg-navy-900/40 transition-opacity duration-150"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        ref={containerRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="drawer-title"
        className="relative flex h-full w-full max-w-sm flex-col bg-white shadow-lg transition-transform duration-200"
      >
        <div className="flex items-center justify-between border-b border-navy-100 px-6 py-4">
          <h3 id="drawer-title" className="text-lg font-semibold text-navy-800">
            {title}
          </h3>
          <button
            onClick={onClose}
            aria-label="Close panel"
            className="rounded-full p-1 text-navy-400 hover:bg-navy-50 hover:text-navy-600"
          >
            <X size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-6 py-4">{children}</div>
        {footer && <div className="border-t border-navy-100 px-6 py-4 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  )
}
