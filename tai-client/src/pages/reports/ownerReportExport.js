import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import ExcelJS from 'exceljs'
import axiosClient from '../../api/axiosClient'

const NAVY = [16, 37, 64]
const ORANGE = [226, 105, 10]

function money(n) {
  return Number(n ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.URL.revokeObjectURL(url)
}

// Company profile + logo are fetched fresh for every export rather than passed in, so the
// letterhead always reflects whatever is currently saved on the Company Profile page.
async function loadLetterhead() {
  let company = null
  try {
    const res = await axiosClient.get('/company-settings')
    company = res.data.data
  } catch {
    // Export must still work even if the company profile can't be reached — falls back to
    // just the report content, no letterhead.
  }

  let logoDataUrl = null
  const logoUrl = company?.logoUrl || '/logo.png'
  try {
    const res = await fetch(logoUrl)
    const blob = await res.blob()
    logoDataUrl = await new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result)
      reader.onerror = reject
      reader.readAsDataURL(blob)
    })
  } catch {
    // Missing/unreachable logo shouldn't block the export either.
  }

  return { company, logoDataUrl }
}

function companyAddressLine(company) {
  if (!company) return null
  const parts = [company.addressLine, company.city, company.country].filter(Boolean)
  return parts.length ? parts.join(', ') : null
}

const ALL_SECTIONS = { financial: true, sales: true, inventory: true, purchasing: true, dailyRecords: true }

export async function exportOwnerReportPdf(report, sections = ALL_SECTIONS, dailyRecords = null) {
  const { company, logoDataUrl } = await loadLetterhead()
  const doc = new jsPDF()
  const pageWidth = doc.internal.pageSize.getWidth()
  const { financial, sales, inventory, purchasing, startDate, endDate } = report

  // ---------- Letterhead ----------
  let cursorY = 16
  const textX = logoDataUrl ? 32 : 14
  if (logoDataUrl) {
    const formatMatch = /^data:image\/(png|jpeg|jpg|webp);base64,/.exec(logoDataUrl)
    if (formatMatch) {
      try {
        doc.addImage(logoDataUrl, formatMatch[1].toUpperCase(), 14, 10, 16, 16)
      } catch {
        // Unsupported image format — skip the logo rather than fail the whole export.
      }
    }
  }

  doc.setFontSize(15)
  doc.setTextColor(...NAVY)
  doc.setFont(undefined, 'bold')
  doc.text(company?.legalName || 'Business Report', textX, cursorY)
  doc.setFont(undefined, 'normal')

  const address = companyAddressLine(company)
  if (address) {
    cursorY += 5
    doc.setFontSize(9)
    doc.setTextColor(120)
    doc.text(address, textX, cursorY)
  }
  const contactBits = [company?.tinNumber ? `TIN ${company.tinNumber}` : null, company?.phone, company?.email].filter(Boolean)
  if (contactBits.length) {
    cursorY += 4.5
    doc.setFontSize(9)
    doc.setTextColor(120)
    doc.text(contactBits.join('  ·  '), textX, cursorY)
  }

  cursorY = Math.max(cursorY, 22) + 6
  doc.setDrawColor(...ORANGE)
  doc.setLineWidth(0.8)
  doc.line(14, cursorY, pageWidth - 14, cursorY)

  cursorY += 7
  doc.setFontSize(13)
  doc.setTextColor(...NAVY)
  doc.setFont(undefined, 'bold')
  doc.text('Business Report', 14, cursorY)
  doc.setFont(undefined, 'normal')

  cursorY += 5
  doc.setFontSize(9)
  doc.setTextColor(120)
  doc.text(`Period: ${startDate} to ${endDate}   ·   Generated: ${new Date().toLocaleString()}`, 14, cursorY)

  const tableOpts = { theme: 'striped', headStyles: { fillColor: NAVY }, styles: { fontSize: 9 } }
  let nextY = cursorY + 6

  if (sections.financial) {
    autoTable(doc, {
      startY: nextY,
      head: [['Financial summary', 'Amount']],
      body: [
        ['Total revenue', money(financial.totalRevenue)],
        ['Gross margin', money(financial.grossMargin)],
        ['Net profit', money(financial.netIncome)],
        ['Cash on hand', money(financial.cashBalance)],
        ['VAT payable', money(financial.vatPayable)],
        ['Total expenses', money(financial.totalExpenses)],
      ],
      ...tableOpts,
    })
    nextY = doc.lastAutoTable.finalY + 8
  }

  if (sections.sales) {
    autoTable(doc, {
      startY: nextY,
      head: [['Top-selling products', 'Revenue']],
      body: sales.topProducts.map((p) => [p.productName, money(p.revenue)]),
      ...tableOpts,
    })
    nextY = doc.lastAutoTable.finalY + 8

    autoTable(doc, {
      startY: nextY,
      head: [['Top customers', 'Revenue', 'Sales']],
      body: sales.topCustomers.map((c) => [c.customerName, money(c.totalRevenue), c.saleCount]),
      foot: [['Outstanding from customers', money(sales.totalReceivables), '']],
      ...tableOpts,
    })
    nextY = doc.lastAutoTable.finalY + 8
  }

  if (sections.inventory) {
    autoTable(doc, {
      startY: nextY,
      head: [['Stock by warehouse', 'Value']],
      body: inventory.stockByWarehouse.map((w) => [w.warehouseName, money(w.stockValue)]),
      foot: [['Total stock value', money(inventory.totalStockValue)]],
      ...tableOpts,
    })
    nextY = doc.lastAutoTable.finalY + 8

    autoTable(doc, {
      startY: nextY,
      head: [['Needs reordering', 'Warehouse', 'Qty / Reorder level']],
      body: inventory.lowStockItems.map((i) => [i.productName, i.warehouseName, `${i.quantity} / ${i.reorderLevel}`]),
      ...tableOpts,
    })
    nextY = doc.lastAutoTable.finalY + 8
  }

  if (sections.purchasing) {
    autoTable(doc, {
      startY: nextY,
      head: [['Top suppliers', 'Spend']],
      body: purchasing.topSuppliers.map((s) => [s.supplierName, money(s.totalSpend)]),
      foot: [['Owed to suppliers', money(purchasing.totalPayables)]],
      ...tableOpts,
    })
    nextY = doc.lastAutoTable.finalY + 8
  }

  if (sections.dailyRecords && dailyRecords) {
    autoTable(doc, {
      startY: nextY,
      head: [['Daily Records — Sales', 'Cash', 'Credit', 'Total']],
      body: [['This period', money(dailyRecords.sales.cashTotal), money(dailyRecords.sales.creditTotal), money(dailyRecords.sales.grandTotal)]],
      ...tableOpts,
    })
    nextY = doc.lastAutoTable.finalY + 8

    autoTable(doc, {
      startY: nextY,
      head: [['Daily Records — Purchases', 'Cash', 'Credit', 'Total']],
      body: [
        ['This period', money(dailyRecords.purchases.cashTotal), money(dailyRecords.purchases.creditTotal), money(dailyRecords.purchases.grandTotal)],
      ],
      ...tableOpts,
    })
    nextY = doc.lastAutoTable.finalY + 8
  }

  // ---------- Footer: page numbers on every page ----------
  const pageCount = doc.internal.getNumberOfPages()
  for (let i = 1; i <= pageCount; i++) {
    doc.setPage(i)
    doc.setFontSize(8)
    doc.setTextColor(150)
    doc.text(`Page ${i} of ${pageCount}`, pageWidth - 14, doc.internal.pageSize.getHeight() - 8, { align: 'right' })
    doc.text('Generated by Tai', 14, doc.internal.pageSize.getHeight() - 8)
  }

  doc.save(`business-report-${startDate}-to-${endDate}.pdf`)
}

export async function exportOwnerReportExcel(report, sections = ALL_SECTIONS, dailyRecords = null) {
  const { company, logoDataUrl } = await loadLetterhead()
  const { financial, sales, inventory, purchasing, startDate, endDate } = report
  const workbook = new ExcelJS.Workbook()
  workbook.creator = company?.legalName || 'Tai'
  workbook.created = new Date()

  let logoImageId = null
  if (logoDataUrl) {
    try {
      const match = /^data:image\/(png|jpeg|jpg);base64,/.exec(logoDataUrl)
      if (match) {
        logoImageId = workbook.addImage({ base64: logoDataUrl, extension: match[1] === 'jpg' ? 'jpeg' : match[1] })
      }
    } catch {
      // Unsupported image format — the sheet still gets a text header without a logo.
    }
  }

  const headerFill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF102540' } }
  const headerFont = { color: { argb: 'FFFFFFFF' }, bold: true }

  function styleHeaderRow(row) {
    row.eachCell((cell) => {
      cell.fill = headerFill
      cell.font = headerFont
    })
  }

  // Every sheet gets the same 4-row letterhead block (logo + company name + report title +
  // period/generated line) before its data table starts, so a sheet opened on its own in
  // Excel still reads as a proper report rather than a bare grid of numbers.
  function addLetterhead(sheet, columnCount) {
    if (logoImageId != null) {
      sheet.addImage(logoImageId, { tl: { col: 0, row: 0 }, ext: { width: 40, height: 40 } })
    }
    sheet.getRow(1).height = 30
    const nameCell = sheet.getCell('B1')
    nameCell.value = company?.legalName || 'Business Report'
    nameCell.font = { bold: true, size: 14, color: { argb: 'FF102540' } }

    const addressLine = companyAddressLine(company)
    if (addressLine) {
      sheet.getCell('B2').value = addressLine
      sheet.getCell('B2').font = { size: 9, color: { argb: 'FF4A6B95' } }
    }

    sheet.getCell('B3').value = `Business Report — ${startDate} to ${endDate}`
    sheet.getCell('B3').font = { bold: true, size: 11 }
    sheet.getCell('B4').value = `Generated ${new Date().toLocaleString()}`
    sheet.getCell('B4').font = { size: 8, italic: true, color: { argb: 'FF7E9BBD' } }

    if (columnCount > 1) {
      sheet.mergeCells(1, 2, 1, columnCount)
      sheet.mergeCells(2, 2, 2, columnCount)
      sheet.mergeCells(3, 2, 3, columnCount)
      sheet.mergeCells(4, 2, 4, columnCount)
    }
    sheet.addRow([])
  }

  if (sections.financial) {
    const financialSheet = workbook.addWorksheet('Financial')
    financialSheet.columns = [{ width: 6 }, { header: 'Metric', key: 'k', width: 28 }, { header: 'Amount', key: 'v', width: 18 }]
    addLetterhead(financialSheet, 2)
    const financialHeaderRow = financialSheet.addRow({ k: 'Metric', v: 'Amount' })
    styleHeaderRow(financialHeaderRow)
    financialSheet.addRows([
      { k: 'Total revenue', v: financial.totalRevenue },
      { k: 'Gross margin', v: financial.grossMargin },
      { k: 'Net profit', v: financial.netIncome },
      { k: 'Cash on hand', v: financial.cashBalance },
      { k: 'VAT payable', v: financial.vatPayable },
      { k: 'Total expenses', v: financial.totalExpenses },
    ])
  }

  if (sections.sales) {
    const salesSheet = workbook.addWorksheet('Sales & Customers')
    salesSheet.columns = [
      { width: 6 },
      { header: 'Top products', key: 'product', width: 28 },
      { header: 'Revenue', key: 'revenue', width: 16 },
      { header: '', key: 'gap', width: 4 },
      { header: 'Top customers', key: 'customer', width: 28 },
      { header: 'Revenue', key: 'customerRevenue', width: 16 },
      { header: 'Sales', key: 'saleCount', width: 10 },
    ]
    addLetterhead(salesSheet, 6)
    styleHeaderRow(
      salesSheet.addRow({ product: 'Top products', revenue: 'Revenue', customer: 'Top customers', customerRevenue: 'Revenue', saleCount: 'Sales' })
    )
    const salesRowCount = Math.max(sales.topProducts.length, sales.topCustomers.length)
    for (let i = 0; i < salesRowCount; i++) {
      const p = sales.topProducts[i]
      const c = sales.topCustomers[i]
      salesSheet.addRow({
        product: p?.productName ?? '',
        revenue: p?.revenue ?? '',
        customer: c?.customerName ?? '',
        customerRevenue: c?.totalRevenue ?? '',
        saleCount: c?.saleCount ?? '',
      })
    }
    salesSheet.addRow({})
    salesSheet.addRow({ product: 'Total sales this period', revenue: sales.totalSales })
    salesSheet.addRow({ product: 'Outstanding from customers', revenue: sales.totalReceivables })
  }

  if (sections.inventory) {
    const inventorySheet = workbook.addWorksheet('Inventory')
    inventorySheet.columns = [{ width: 6 }, { header: 'Warehouse', key: 'warehouse', width: 26 }, { header: 'Stock value', key: 'value', width: 18 }]
    addLetterhead(inventorySheet, 2)
    styleHeaderRow(inventorySheet.addRow({ warehouse: 'Warehouse', value: 'Stock value' }))
    inventory.stockByWarehouse.forEach((w) => inventorySheet.addRow({ warehouse: w.warehouseName, value: w.stockValue }))
    inventorySheet.addRow({})
    inventorySheet.addRow({ warehouse: 'Total stock value', value: inventory.totalStockValue })
    inventorySheet.addRow({})
    const lowStockHeaderRow = inventorySheet.addRow({ warehouse: 'Needs reordering', value: 'Qty / Reorder level' })
    lowStockHeaderRow.font = { bold: true }
    inventory.lowStockItems.forEach((item) =>
      inventorySheet.addRow({ warehouse: `${item.productName} (${item.warehouseName})`, value: `${item.quantity} / ${item.reorderLevel}` })
    )
  }

  if (sections.purchasing) {
    const purchasingSheet = workbook.addWorksheet('Purchasing')
    purchasingSheet.columns = [{ width: 6 }, { header: 'Supplier', key: 'supplier', width: 26 }, { header: 'Spend', key: 'spend', width: 18 }]
    addLetterhead(purchasingSheet, 2)
    styleHeaderRow(purchasingSheet.addRow({ supplier: 'Supplier', spend: 'Spend' }))
    purchasing.topSuppliers.forEach((s) => purchasingSheet.addRow({ supplier: s.supplierName, spend: s.totalSpend }))
    purchasingSheet.addRow({})
    purchasingSheet.addRow({ supplier: 'Total purchases this period', spend: purchasing.totalPurchases })
    purchasingSheet.addRow({ supplier: 'Owed to suppliers', spend: purchasing.totalPayables })
  }

  if (sections.dailyRecords && dailyRecords) {
    const dailySheet = workbook.addWorksheet('Daily Records')
    dailySheet.columns = [{ width: 6 }, { header: 'Record', key: 'record', width: 22 }, { header: 'Cash', key: 'cash', width: 16 }, { header: 'Credit', key: 'credit', width: 16 }, { header: 'Total', key: 'total', width: 16 }]
    addLetterhead(dailySheet, 4)
    styleHeaderRow(dailySheet.addRow({ record: 'Record', cash: 'Cash', credit: 'Credit', total: 'Total' }))
    dailySheet.addRow({
      record: 'Sales (statutory)',
      cash: dailyRecords.sales.cashTotal,
      credit: dailyRecords.sales.creditTotal,
      total: dailyRecords.sales.grandTotal,
    })
    dailySheet.addRow({
      record: 'Purchases (statutory)',
      cash: dailyRecords.purchases.cashTotal,
      credit: dailyRecords.purchases.creditTotal,
      total: dailyRecords.purchases.grandTotal,
    })
    dailySheet.addRow({})
    dailySheet.addRow({ record: 'Per Rwanda Tax Procedures Law — see Daily Records page for full per-transaction detail.' })
  }

  const buffer = await workbook.xlsx.writeBuffer()
  downloadBlob(
    new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
    `business-report-${startDate}-to-${endDate}.xlsx`
  )
}
