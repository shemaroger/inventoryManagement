import { useId } from 'react'

export default function Select({ label, error, className = '', id, children, ...props }) {
  const generatedId = useId()
  const selectId = id || generatedId

  return (
    <div className="space-y-1">
      {label && (
        <label htmlFor={selectId} className="block text-sm font-medium text-navy-700">
          {label}
        </label>
      )}
      <select
        id={selectId}
        aria-invalid={!!error}
        aria-describedby={error ? `${selectId}-error` : undefined}
        className={`w-full rounded-md border bg-white px-3 py-2 text-sm text-navy-800 transition-colors duration-150 focus:outline-none focus:ring-1 ${
          error
            ? 'border-red-400 focus:border-red-500 focus:ring-red-500'
            : 'border-navy-200 focus:border-orange-500 focus:ring-orange-500'
        } ${className}`}
        {...props}
      >
        {children}
      </select>
      {error && (
        <p id={`${selectId}-error`} className="text-xs text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}
