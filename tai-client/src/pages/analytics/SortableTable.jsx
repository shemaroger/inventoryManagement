import { useState } from 'react'
import { ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react'

// A small sortable-header variant of the shared Table component, kept local to Analytics since
// no other page currently needs client-side column sorting.
export default function SortableTable({ columns, data, rowKey = 'id', defaultSortKey, defaultSortDir = 'desc' }) {
  const [sortKey, setSortKey] = useState(defaultSortKey ?? columns[0]?.key)
  const [sortDir, setSortDir] = useState(defaultSortDir)

  function toggleSort(col) {
    if (!col.sortable) return
    if (sortKey === col.key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(col.key)
      setSortDir('desc')
    }
  }

  const sorted = [...data].sort((a, b) => {
    const av = a[sortKey]
    const bv = b[sortKey]
    if (av == null && bv == null) return 0
    if (av == null) return 1
    if (bv == null) return -1
    const cmp = typeof av === 'string' ? av.localeCompare(bv) : Number(av) - Number(bv)
    return sortDir === 'asc' ? cmp : -cmp
  })

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="bg-navy-50 text-navy-500">
          <tr>
            {columns.map((col) => (
              <th
                key={col.key}
                className={`px-4 py-3 font-medium ${col.align === 'right' ? 'text-right' : ''} ${col.sortable ? 'cursor-pointer select-none hover:text-navy-700' : ''}`}
                onClick={() => toggleSort(col)}
              >
                <span className={`inline-flex items-center gap-1 ${col.align === 'right' ? 'flex-row-reverse' : ''}`}>
                  {col.label}
                  {col.sortable && (
                    sortKey === col.key
                      ? (sortDir === 'asc' ? <ChevronUp size={12} /> : <ChevronDown size={12} />)
                      : <ChevronsUpDown size={12} className="text-navy-300" />
                  )}
                </span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-navy-100">
          {sorted.map((row) => (
            <tr key={row[rowKey]} className="hover:bg-navy-50/50">
              {columns.map((col) => (
                <td key={col.key} className={`px-4 py-3 ${col.align === 'right' ? 'text-right' : ''}`}>
                  {col.render ? col.render(row) : row[col.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
