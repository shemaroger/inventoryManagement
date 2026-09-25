import { ChevronRight, ChevronDown, Pencil, Trash2, Plus, Loader2 } from 'lucide-react'
import Badge from '../../components/ui/Badge'

const INDENT_PX = 20
const MAX_INDENT_DEPTH = 6 // caps visual indent on narrow viewports so deep trees don't force horizontal scroll

export default function CategoryTreeNode({ node, depth, expandedIds, onToggle, isAdmin, counts, onEdit, onAddChild, onDelete }) {
  const hasChildren = node.children.length > 0
  const expanded = expandedIds.has(node.id)
  const count = counts[node.id]
  const indent = Math.min(depth, MAX_INDENT_DEPTH) * INDENT_PX

  return (
    <>
      <div
        className="flex items-center gap-2 border-b border-navy-100 py-2 pr-3 transition-colors duration-150 hover:bg-navy-50/50"
        style={{ paddingLeft: `${indent + 12}px` }}
      >
        <button
          onClick={() => hasChildren && onToggle(node.id)}
          aria-label={hasChildren ? (expanded ? `Collapse ${node.name}` : `Expand ${node.name}`) : undefined}
          className={`flex h-5 w-5 shrink-0 items-center justify-center rounded text-navy-400 ${
            hasChildren ? 'hover:bg-navy-100 hover:text-navy-600' : 'invisible'
          }`}
        >
          {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        </button>

        <span className="flex-1 truncate text-sm font-medium text-navy-800">{node.name}</span>

        {count === undefined ? (
          <Loader2 size={12} className="animate-spin text-navy-300" aria-label="Loading product count" />
        ) : (
          <Badge variant="neutral">{count} {count === 1 ? 'product' : 'products'}</Badge>
        )}

        {isAdmin && (
          <div className="flex items-center gap-1">
            <button
              onClick={() => onAddChild(node)}
              aria-label={`Add subcategory under ${node.name}`}
              className="rounded-full p-1.5 text-navy-400 hover:bg-navy-100 hover:text-navy-600"
            >
              <Plus size={14} />
            </button>
            <button
              onClick={() => onEdit(node)}
              aria-label={`Edit ${node.name}`}
              className="rounded-full p-1.5 text-navy-400 hover:bg-navy-100 hover:text-navy-600"
            >
              <Pencil size={14} />
            </button>
            <button
              onClick={() => onDelete(node)}
              aria-label={`Delete ${node.name}`}
              className="rounded-full p-1.5 text-red-400 hover:bg-red-50 hover:text-red-600"
            >
              <Trash2 size={14} />
            </button>
          </div>
        )}
      </div>

      {expanded &&
        node.children.map((child) => (
          <CategoryTreeNode
            key={child.id}
            node={child}
            depth={depth + 1}
            expandedIds={expandedIds}
            onToggle={onToggle}
            isAdmin={isAdmin}
            counts={counts}
            onEdit={onEdit}
            onAddChild={onAddChild}
            onDelete={onDelete}
          />
        ))}
    </>
  )
}
