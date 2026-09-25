import { useEffect, useState } from 'react'

// Client-side pagination for pages that fetch a full list in one call (Suppliers, Customers,
// Brands, Warehouses, Users, Stock) rather than a paginated endpoint — keeps the "10 per page"
// convention consistent everywhere without needing backend changes for lists that are small
// enough to fetch whole but still too many to show on one screen at once.
export default function usePagedList(items, pageSize = 10) {
  const [page, setPage] = useState(0)
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize))

  // Reset to a valid page whenever the underlying (filtered/sorted) list shrinks below the
  // current page — e.g. after a search narrows the results.
  useEffect(() => {
    if (page > 0 && page >= totalPages) setPage(0)
  }, [totalPages, page])

  const pageItems = items.slice(page * pageSize, page * pageSize + pageSize)

  return { page, setPage, totalPages, totalElements: items.length, pageItems }
}
