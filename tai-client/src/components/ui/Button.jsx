import { Loader2 } from 'lucide-react'

const VARIANTS = {
  primary: 'bg-orange-500 text-white hover:bg-orange-600 disabled:bg-orange-300',
  secondary: 'bg-navy-100 text-navy-700 hover:bg-navy-200 disabled:bg-navy-50 disabled:text-navy-300',
  danger: 'bg-red-600 text-white hover:bg-red-700 disabled:bg-red-300',
  ghost: 'bg-transparent text-navy-600 hover:bg-navy-50 disabled:text-navy-300',
}

export default function Button({
  variant = 'primary',
  loading = false,
  disabled = false,
  className = '',
  children,
  type = 'button',
  ...props
}) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded-full px-4 py-2 text-sm font-medium transition-all duration-150 active:scale-[0.97] disabled:cursor-not-allowed disabled:active:scale-100 ${VARIANTS[variant]} ${className}`}
      {...props}
    >
      {loading && <Loader2 size={16} className="animate-spin" aria-hidden="true" />}
      {children}
    </button>
  )
}
