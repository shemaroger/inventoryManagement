import { useEffect, useId, useRef, useState } from 'react'
import { ChevronDown, Search } from 'lucide-react'

const MAX_VISIBLE_OPTIONS = 50

// A searchable dropdown for long option lists (e.g. a 200-product picker) where a plain
// <select> means endless scrolling. Same label/error/className API as the plain Select
// component, plus `options` as [{ value, label }] and `placeholder`.
export default function SearchableSelect({
  label,
  error,
  value,
  onChange,
  options,
  placeholder = 'Search…',
  className = '',
  id,
  disabled = false,
}) {
  const generatedId = useId()
  const fieldId = id || generatedId
  const containerRef = useRef(null)
  const inputRef = useRef(null)

  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')

  const selected = options.find((o) => String(o.value) === String(value))

  useEffect(() => {
    if (!open) return
    function handleClick(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false)
      }
    }
    function handleKey(e) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('mousedown', handleClick)
      document.removeEventListener('keydown', handleKey)
    }
  }, [open])

  function openDropdown() {
    if (disabled) return
    setQuery('')
    setOpen(true)
    setTimeout(() => inputRef.current?.focus(), 0)
  }

  function selectOption(option) {
    onChange(option.value)
    setOpen(false)
    setQuery('')
  }

  const filtered = query.trim()
    ? options.filter((o) => o.label.toLowerCase().includes(query.trim().toLowerCase()))
    : options
  const visible = filtered.slice(0, MAX_VISIBLE_OPTIONS)

  return (
    <div className="space-y-1" ref={containerRef}>
      {label && (
        <label htmlFor={fieldId} className="block text-sm font-medium text-navy-700">
          {label}
        </label>
      )}
      <div className="relative">
        {open ? (
          <>
            <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-navy-300" />
            <input
              ref={inputRef}
              id={fieldId}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={placeholder}
              className={`w-full rounded-md border px-3 py-2 pl-9 text-sm text-navy-800 focus:outline-none focus:ring-1 ${
                error ? 'border-red-400 focus:border-red-500 focus:ring-red-500' : 'border-orange-500 ring-1 ring-orange-500'
              } ${className}`}
            />
          </>
        ) : (
          <button
            type="button"
            id={fieldId}
            onClick={openDropdown}
            disabled={disabled}
            className={`flex w-full items-center justify-between rounded-md border bg-white px-3 py-2 text-left text-sm transition-colors duration-150 focus:outline-none focus:ring-1 disabled:cursor-not-allowed disabled:bg-navy-50 disabled:text-navy-400 ${
              error ? 'border-red-400 focus:border-red-500 focus:ring-red-500' : 'border-navy-200 focus:border-orange-500 focus:ring-orange-500'
            } ${className}`}
          >
            <span className={selected ? 'text-navy-800' : 'text-navy-400'}>
              {selected ? selected.label : placeholder}
            </span>
            <ChevronDown size={16} className="shrink-0 text-navy-400" />
          </button>
        )}

        {open && (
          <div className="absolute z-30 mt-1 max-h-64 w-full overflow-y-auto rounded-md border border-navy-100 bg-white py-1 shadow-lg">
            {visible.length === 0 ? (
              <p className="px-3 py-2 text-sm text-navy-400">No matches</p>
            ) : (
              visible.map((o) => (
                <button
                  key={o.value}
                  type="button"
                  onClick={() => selectOption(o)}
                  className={`block w-full px-3 py-2 text-left text-sm hover:bg-navy-50 ${
                    String(o.value) === String(value) ? 'bg-orange-50 font-medium text-orange-700' : 'text-navy-700'
                  }`}
                >
                  {o.label}
                </button>
              ))
            )}
            {filtered.length > MAX_VISIBLE_OPTIONS && (
              <p className="border-t border-navy-100 px-3 py-1.5 text-xs text-navy-400">
                {filtered.length - MAX_VISIBLE_OPTIONS} more — keep typing to narrow it down
              </p>
            )}
          </div>
        )}
      </div>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  )
}
