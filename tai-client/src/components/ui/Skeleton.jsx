export default function Skeleton({ className = '' }) {
  return <div className={`animate-pulse rounded bg-navy-100 ${className}`} aria-hidden="true" />
}

export function SkeletonRows({ rows = 5, columns = 4 }) {
  return (
    <tbody className="divide-y divide-navy-100">
      {Array.from({ length: rows }).map((_, rowIdx) => (
        <tr key={rowIdx}>
          {Array.from({ length: columns }).map((_, colIdx) => (
            <td key={colIdx} className="px-4 py-3">
              <Skeleton className="h-4 w-full max-w-[160px]" />
            </td>
          ))}
        </tr>
      ))}
    </tbody>
  )
}
