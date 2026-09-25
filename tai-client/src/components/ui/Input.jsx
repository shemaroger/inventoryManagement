import { useId } from 'react'

export default function Input({ label, error, className = '', id, ...props }) {
  const generatedId = useId()
  const inputId = id || generatedId

  return (
    <div className="space-y-1">
      {label && (
        <label htmlFor={inputId} className="block text-sm font-medium text-navy-700">
          {label}
        </label>
      )}
      <input
        id={inputId}
        aria-invalid={!!error}
        aria-describedby={error ? `${inputId}-error` : undefined}
        className={`w-full rounded-md border px-3 py-2 text-sm text-navy-800 transition-colors duration-150 focus:outline-none focus:ring-1 ${
          error
            ? 'border-red-400 focus:border-red-500 focus:ring-red-500'
            : 'border-navy-200 focus:border-orange-500 focus:ring-orange-500'
        } ${className}`}
        {...props}
      />
      {error && (
        <p id={`${inputId}-error`} className="text-xs text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}
