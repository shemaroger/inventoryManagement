import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { MotionConfig } from 'framer-motion'
import { AuthProvider } from './context/AuthContext'
import { ToastProvider } from './components/ui/ToastProvider'
import ProtectedRoute from './routes/ProtectedRoute'
import Layout from './components/layout/Layout'
import Login from './pages/auth/Login'
import Dashboard from './pages/dashboard/Dashboard'
import UsersList from './pages/users/UsersList'
import UserFormPage from './pages/users/UserFormPage'
import UserDetailPage from './pages/users/UserDetailPage'
import CategoriesList from './pages/categories/CategoriesList'
import ProductsList from './pages/products/ProductsList'
import BrandsList from './pages/brands/BrandsList'
import StockPage from './pages/stock/StockPage'
import WarehousesList from './pages/warehouses/WarehousesList'
import SuppliersList from './pages/suppliers/SuppliersList'
import PurchaseOrdersList from './pages/purchaseOrders/PurchaseOrdersList'
import PurchaseOrderFormPage from './pages/purchaseOrders/PurchaseOrderFormPage'
import PurchaseOrderDetailPage from './pages/purchaseOrders/PurchaseOrderDetailPage'
import CustomersList from './pages/customers/CustomersList'
import CustomerStatementPage from './pages/customers/CustomerStatementPage'
import SalesList from './pages/sales/SalesList'
import SaleFormPage from './pages/sales/SaleFormPage'
import SaleDetailPage from './pages/sales/SaleDetailPage'
import AskPage from './pages/ask/AskPage'
import ForecastPage from './pages/forecast/ForecastPage'
import AnomaliesList from './pages/anomalies/AnomaliesList'
import AuditLogList from './pages/audit/AuditLogList'
import CompanyProfilePage from './pages/settings/CompanyProfilePage'
import OwnerReportPage from './pages/reports/OwnerReportPage'
import ReordersList from './pages/reorders/ReordersList'
import DailyRecordsPage from './pages/reports/DailyRecordsPage'
import AccountsList from './pages/accounts/AccountsList'
import AccountLedgerPage from './pages/accounts/AccountLedgerPage'
import JournalEntriesList from './pages/journalEntries/JournalEntriesList'
import JournalEntryFormPage from './pages/journalEntries/JournalEntryFormPage'
import CashManagementPage from './pages/cash/CashManagementPage'
import BankReconciliationPage from './pages/cash/BankReconciliationPage'
import VatReportPage from './pages/vat/VatReportPage'
import ProfitLossPage from './pages/financials/ProfitLossPage'
import BalanceSheetPage from './pages/financials/BalanceSheetPage'
import AnalyticsPage from './pages/analytics/AnalyticsPage'

export default function App() {
  return (
    // "user" honors the OS-level prefers-reduced-motion setting across every Framer Motion
    // animation in the app (Modal, Toast, page transitions, dashboard cards, etc.) without
    // needing to check it in each component individually.
    <MotionConfig reducedMotion="user">
      <ToastProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<Login />} />

            <Route
              element={
                <ProtectedRoute>
                  <Layout />
                </ProtectedRoute>
              }
            >
              <Route path="/" element={<Dashboard />} />
              <Route path="/users" element={<UsersList />} />
              <Route path="/users/new" element={<UserFormPage />} />
              <Route path="/users/:id" element={<UserDetailPage />} />
              <Route path="/users/:id/edit" element={<UserFormPage />} />
              <Route path="/categories" element={<CategoriesList />} />
              <Route path="/products" element={<ProductsList />} />
              <Route path="/brands" element={<BrandsList />} />
              <Route path="/stock" element={<StockPage />} />
              <Route path="/warehouses" element={<WarehousesList />} />
              <Route path="/suppliers" element={<SuppliersList />} />
              <Route path="/purchase-orders" element={<PurchaseOrdersList />} />
              <Route path="/purchase-orders/new" element={<PurchaseOrderFormPage />} />
              <Route path="/purchase-orders/:id" element={<PurchaseOrderDetailPage />} />
              <Route path="/customers" element={<CustomersList />} />
              <Route path="/customers/:id/statement" element={<CustomerStatementPage />} />
              <Route path="/sales" element={<SalesList />} />
              <Route path="/sales/new" element={<SaleFormPage />} />
              <Route path="/sales/:id" element={<SaleDetailPage />} />
              <Route path="/ask" element={<AskPage />} />
              <Route path="/forecast" element={<ForecastPage />} />
              <Route path="/anomalies" element={<AnomaliesList />} />
              <Route path="/audit-log" element={<AuditLogList />} />
              <Route path="/settings/company" element={<CompanyProfilePage />} />
              <Route path="/reports/business" element={<OwnerReportPage />} />
              <Route path="/reorders" element={<ReordersList />} />
              <Route path="/reports/daily-records" element={<DailyRecordsPage />} />
              <Route path="/accounts" element={<AccountsList />} />
              <Route path="/accounts/:id/ledger" element={<AccountLedgerPage />} />
              <Route path="/journal-entries" element={<JournalEntriesList />} />
              <Route path="/journal-entries/new" element={<JournalEntryFormPage />} />
              <Route path="/cash/expenditures" element={<CashManagementPage />} />
              <Route path="/cash/reconciliation" element={<BankReconciliationPage />} />
              <Route path="/reports/vat" element={<VatReportPage />} />
              <Route path="/reports/profit-loss" element={<ProfitLossPage />} />
              <Route path="/reports/balance-sheet" element={<BalanceSheetPage />} />
              <Route path="/analytics" element={<AnalyticsPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
      </ToastProvider>
    </MotionConfig>
  )
}
