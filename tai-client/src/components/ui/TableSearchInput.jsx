import { Search, X } from 'lucide-react'

// A consistent search box for list pages — either filters an already-loaded array client-side
// (small master-data lists) or drives a debounced server-side query (paginated lists), the
// caller decides which by what it does with `value`/`onChange`.
export default function TableSearchInput({ value, onChange, placeholder = 'Search…', className = '' }) {
  return (
    <div className={`relative ${className}`}>
      <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-navy-300" />
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        className="w-full rounded-md border border-navy-200 bg-white py-2 pl-9 pr-9 text-sm text-navy-800 transition-colors duration-150 focus:border-orange-500 focus:outline-none focus:ring-1 focus:ring-orange-500"
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange('')}
          aria-label="Clear search"
          className="absolute right-2.5 top-1/2 -translate-y-1/2 text-navy-300 hover:text-navy-600"
        >
          <X size={14} />
        </button>
      )}
    </div>
  )
}
