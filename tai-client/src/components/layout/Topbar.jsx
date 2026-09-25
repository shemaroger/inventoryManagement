import { Menu } from 'lucide-react'

export default function Topbar({ title, onMenuClick }) {
  return (
    <header className="flex h-16 items-center gap-3 border-b-2 border-orange-500 bg-white px-4 sm:px-6">
      <button
        type="button"
        onClick={onMenuClick}
        className="-ml-1 rounded-full p-2 text-navy-600 transition-colors hover:bg-navy-50 lg:hidden"
        aria-label="Open menu"
      >
        <Menu size={22} />
      </button>
      <h1 className="truncate text-lg font-bold text-navy-800 sm:text-xl">{title}</h1>
    </header>
  )
}
