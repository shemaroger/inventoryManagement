// Builds a nested tree from the flat { id, name, parentId }[] the API returns.
// Categories whose parentId doesn't resolve to another category in the list
// (including null/undefined) are treated as roots, so orphaned rows never vanish.
export function buildTree(categories) {
  const nodesById = new Map(categories.map((c) => [c.id, { ...c, children: [] }]))
  const roots = []

  for (const node of nodesById.values()) {
    const parent = node.parentId != null ? nodesById.get(node.parentId) : null
    if (parent) {
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  }

  const sortByName = (a, b) => a.name.localeCompare(b.name)
  const sortRecursive = (nodes) => {
    nodes.sort(sortByName)
    nodes.forEach((n) => sortRecursive(n.children))
  }
  sortRecursive(roots)

  return roots
}

// All ids reachable from `node`, including itself — used to block dropping
// a category onto one of its own descendants (would create a cycle).
export function collectDescendantIds(node, acc = new Set()) {
  acc.add(node.id)
  node.children.forEach((child) => collectDescendantIds(child, acc))
  return acc
}

export function findNode(tree, id) {
  for (const node of tree) {
    if (node.id === id) return node
    const found = findNode(node.children, id)
    if (found) return found
  }
  return null
}

export function flattenTree(tree) {
  const result = []
  const walk = (nodes) => {
    for (const node of nodes) {
      result.push(node)
      walk(node.children)
    }
  }
  walk(tree)
  return result
}

// Ids that would currently be rendered: every root, plus the children of any
// expanded node — used to know which nodes need a lazily-fetched product count.
export function getVisibleIds(tree, expandedIds) {
  const visible = []
  const walk = (nodes) => {
    for (const node of nodes) {
      visible.push(node.id)
      if (expandedIds.has(node.id)) walk(node.children)
    }
  }
  walk(tree)
  return visible
}

export function getAllExpandableIds(tree) {
  const ids = []
  const walk = (nodes) => {
    for (const node of nodes) {
      if (node.children.length > 0) ids.push(node.id)
      walk(node.children)
    }
  }
  walk(tree)
  return ids
}
