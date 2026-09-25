import { useId } from 'react'

export default function Checkbox({ label, error, className = '', id, ...props }) {
  const generatedId = useId()
  const checkboxId = id || generatedId

  return (
    <div>
      <div className="flex items-center gap-2">
        <input
          id={checkboxId}
          type="checkbox"
          aria-invalid={!!error}
          className={`h-4 w-4 rounded border-navy-300 text-orange-500 transition-colors duration-150 focus:ring-orange-500 ${className}`}
          {...props}
        />
        {label && (
          <label htmlFor={checkboxId} className="text-sm text-navy-700">
            {label}
          </label>
        )}
      </div>
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  )
}
