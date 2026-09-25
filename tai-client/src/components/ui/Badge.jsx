const VARIANTS = {
  neutral: 'bg-navy-50 text-navy-500',
  navy: 'bg-navy-100 text-navy-700',
  orange: 'bg-orange-100 text-orange-700',
  success: 'bg-green-100 text-green-700',
  danger: 'bg-red-100 text-red-700',
}

export default function Badge({ variant = 'neutral', children, className = '' }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${VARIANTS[variant]} ${className}`}
    >
      {children}
    </span>
  )
}
