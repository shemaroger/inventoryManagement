import { AnimatePresence, motion } from 'framer-motion'
import { X } from 'lucide-react'
import useFocusTrap from './useFocusTrap'

const SIZE_CLASS = {
  md: 'max-w-md',
  lg: 'max-w-2xl',
}

export default function Modal({ open, onClose, title, children, footer, size = 'md' }) {
  const containerRef = useFocusTrap(open, onClose)

  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <motion.div
            className="absolute inset-0 bg-navy-900/40"
            onClick={onClose}
            aria-hidden="true"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
          />
          <motion.div
            ref={containerRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="modal-title"
            className={`relative w-full ${SIZE_CLASS[size]} max-h-[90vh] overflow-y-auto rounded-lg bg-white p-6 shadow-lg`}
            initial={{ opacity: 0, scale: 0.98, y: 6 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.98, y: 4 }}
            transition={{ type: 'spring', stiffness: 400, damping: 32 }}
          >
            <div className="mb-4 flex items-center justify-between">
              <h3 id="modal-title" className="text-lg font-semibold text-navy-800">
                {title}
              </h3>
              <button
                onClick={onClose}
                aria-label="Close dialog"
                className="rounded-full p-1 text-navy-400 transition-colors hover:bg-navy-50 hover:text-navy-600"
              >
                <X size={18} />
              </button>
            </div>
            <div>{children}</div>
            {footer && <div className="mt-6 flex justify-end gap-2">{footer}</div>}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  )
}
