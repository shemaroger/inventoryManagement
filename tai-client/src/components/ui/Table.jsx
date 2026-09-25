export default function Table({ columns, data, onRowClick, rowKey = 'id' }) {
  return (
    <table className="w-full text-left text-sm">
      <thead className="bg-navy-50 text-navy-500">
        <tr>
          {columns.map((col) => (
            <th
              key={col.key}
              className={`px-4 py-3 font-medium ${col.align === 'right' ? 'text-right' : ''}`}
            >
              {col.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody className="divide-y divide-navy-100">
        {data.map((row, index) => (
          <tr
            key={row[rowKey]}
            onClick={() => onRowClick?.(row)}
            style={{ animationDelay: `${Math.min(index, 10) * 25}ms` }}
            className={`animate-tai-fade-in-up transition-colors duration-150 ${onRowClick ? 'cursor-pointer hover:bg-navy-50/50' : 'hover:bg-navy-50/50'}`}
          >
            {columns.map((col) => (
              <td
                key={col.key}
                className={`px-4 py-3 ${col.align === 'right' ? 'text-right' : ''}`}
              >
                {col.render ? col.render(row) : row[col.key]}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export function TablePagination({ page, totalPages, onPageChange }) {
  return (
    <div className="flex items-center justify-between border-t border-navy-100 px-4 py-3 text-sm text-navy-500">
      <span>
        Page {page + 1} of {Math.max(totalPages, 1)}
      </span>
      <div className="flex gap-2">
        <button
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0}
          className="rounded-full px-3 py-1.5 font-medium transition-all duration-150 hover:bg-navy-50 active:scale-95 disabled:opacity-40 disabled:active:scale-100"
        >
          Previous
        </button>
        <button
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          className="rounded-full px-3 py-1.5 font-medium transition-all duration-150 hover:bg-navy-50 active:scale-95 disabled:opacity-40 disabled:active:scale-100"
        >
          Next
        </button>
      </div>
    </div>
  )
}
