package com.company.tai.sales;

import com.company.tai.inventory.repository.ProductRepository;
import com.company.tai.inventory.repository.StockItemRepository;
import com.company.tai.inventory.repository.WarehouseRepository;
import com.company.tai.sales.repository.CustomerRepository;
import com.company.tai.sales.repository.SalePaymentRepository;
import com.company.tai.sales.repository.SaleRepository;
import com.company.tai.user.entity.Role;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.RoleRepository;
import com.company.tai.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Sales domain (CustomerController, SaleController,
 * SalePaymentController), run against the real dev Postgres database on a random port so it
 * does not collide with the already-running dev server on :8080.
 *
 * <p>Uses an existing product/warehouse pair with ample stock (looked up at runtime rather than
 * hardcoded, so this doesn't break if seed data ids differ), and drives the full
 * QUOTATION -> CONFIRMED -> COMPLETED lifecycle plus payments through the real HTTP endpoints.
 * All rows created here are removed in {@link #cleanup()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SalesDomainIntegrationTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SalePaymentRepository salePaymentRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private StockItemRepository stockItemRepository;

    private static final long TS = System.currentTimeMillis() / 1000;
    private static final String ADMIN_EMAIL = "sales-e2e-admin-" + TS + "@tai.local";
    private static final String STAFF_EMAIL = "sales-e2e-staff-" + TS + "@tai.local";
    private static final String MANAGER_EMAIL = "sales-e2e-mgr-" + TS + "@tai.local";

    private static String adminToken;
    private static String staffToken;
    private static String managerToken;

    private static Long productId;
    private static Long warehouseId;

    private static Long customerId;
    private static Long saleId;          // CASH quotation -> completed
    private static Long creditSaleId;    // CREDIT sale used for payments
    private static Long cleanupSaleId2;  // insufficient-stock sale, cancelled

    @AfterAll
    void cleanup() {
        if (creditSaleId != null) salePaymentRepository.findBySaleIdOrderByPaymentDateDesc(creditSaleId)
                .forEach(salePaymentRepository::delete);
        if (saleId != null) saleRepository.findById(saleId).ifPresent(saleRepository::delete);
        if (creditSaleId != null) saleRepository.findById(creditSaleId).ifPresent(saleRepository::delete);
        if (cleanupSaleId2 != null) saleRepository.findById(cleanupSaleId2).ifPresent(saleRepository::delete);
        if (customerId != null) customerRepository.findById(customerId).ifPresent(customerRepository::delete);
        // The admin account is left in place: completing sales above posted stock_adjustments
        // rows (performed_by = this user) via StockService, and those rows are inventory-domain
        // data this test must not delete — deleting the user would violate that FK. The account
        // is a harmless timestamped throwaway (sales-e2e-admin-<ts>@tai.local).
        userRepository.findByEmail(STAFF_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(MANAGER_EMAIL).ifPresent(userRepository::delete);
    }

    // ---------- setup helpers ----------

    @Test
    @org.junit.jupiter.api.Order(1)
    void setup_registerUsersAndPickFixtures() throws Exception {
        adminToken = registerAndLogin(ADMIN_EMAIL, "ADMIN");
        staffToken = registerAndLogin(STAFF_EMAIL, null);
        managerToken = registerAndLogin(MANAGER_EMAIL, "MANAGER");

        // Pick a product/warehouse pair with plenty of stock so quantity-based tests are stable.
        var stockItem = stockItemRepository.findAll().stream()
                .filter(si -> si.getQuantity().compareTo(BigDecimal.valueOf(50)) > 0)
                .findFirst().orElseThrow(() -> new IllegalStateException("No stock item with enough quantity to test against"));
        productId = stockItem.getProduct().getId();
        warehouseId = stockItem.getWarehouse().getId();
    }

    private String registerAndLogin(String email, String extraRole) throws Exception {
        String body = "{\"fullName\":\"Sales E2E\",\"email\":\"" + email + "\",\"password\":\"Passw0rd1\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        if (extraRole != null) {
            User user = userRepository.findByEmail(email).orElseThrow();
            Role role = roleRepository.findByName(extraRole).orElseThrow();
            Set<Role> roles = new HashSet<>();
            roles.add(role);
            user.setRoles(roles);
            userRepository.save(user);
        }
        String loginBody = "{\"email\":\"" + email + "\",\"password\":\"Passw0rd1\"}";
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = objectMapper.readTree(response);
        String challengeToken = node.at("/data/challengeToken").asText();
        String code = node.at("/data/debugCode").asText();

        String verifyBody = "{\"challengeToken\":\"" + challengeToken + "\",\"code\":\"" + code + "\"}";
        String verifyResponse = mockMvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(verifyResponse).at("/data/accessToken").asText();
    }

    // ---------- Customers ----------

    @Test
    @org.junit.jupiter.api.Order(10)
    void customers_create_missingName_badRequest() throws Exception {
        mockMvc.perform(post("/api/customers").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"creditLimit\":1000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    void customers_create_negativeCreditLimit_badRequest() throws Exception {
        mockMvc.perform(post("/api/customers").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"neg-" + TS + "\",\"creditLimit\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    void customers_create_noAuth_unauthorized() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"noauth-" + TS + "\",\"creditLimit\":1000}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    void customers_create_staffForbidden() throws Exception {
        mockMvc.perform(post("/api/customers").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"staff-" + TS + "\",\"creditLimit\":1000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    void customers_create_success() throws Exception {
        String body = "{\"name\":\"cust-e2e-" + TS + "\",\"creditLimit\":50000,\"customerCategory\":\"RETAIL\"}";
        String response = mockMvc.perform(post("/api/customers").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance").value(0))
                .andReturn().getResponse().getContentAsString();
        customerId = objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    void customers_update_nonexistent_notFound() throws Exception {
        mockMvc.perform(put("/api/customers/999999999").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"creditLimit\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @org.junit.jupiter.api.Order(16)
    void customers_statement_nonexistent_notFound() throws Exception {
        mockMvc.perform(get("/api/customers/999999999/statement").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @org.junit.jupiter.api.Order(17)
    void customers_statement_freshCustomer_isEmpty() throws Exception {
        mockMvc.perform(get("/api/customers/" + customerId + "/statement").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ---------- Sales: validation & FK checks ----------

    @Test
    @org.junit.jupiter.api.Order(20)
    void sales_create_invalidCustomer_notFound() throws Exception {
        mockMvc.perform(post("/api/sales").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(999999999L, warehouseId, "CASH", productId, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @org.junit.jupiter.api.Order(21)
    void sales_create_invalidWarehouse_notFound() throws Exception {
        mockMvc.perform(post("/api/sales").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(customerId, 999999999L, "CASH", productId, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @org.junit.jupiter.api.Order(22)
    void sales_create_invalidProduct_notFound() throws Exception {
        mockMvc.perform(post("/api/sales").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(customerId, warehouseId, "CASH", 999999999L, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @org.junit.jupiter.api.Order(23)
    void sales_create_zeroQuantity_badRequest() throws Exception {
        mockMvc.perform(post("/api/sales").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(customerId, warehouseId, "CASH", productId, 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Order(24)
    void sales_create_emptyLines_badRequest() throws Exception {
        String body = "{\"customerId\":" + customerId + ",\"warehouseId\":" + warehouseId
                + ",\"paymentType\":\"CASH\",\"lines\":[]}";
        mockMvc.perform(post("/api/sales").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Order(25)
    void sales_create_staffForbidden() throws Exception {
        mockMvc.perform(post("/api/sales").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(customerId, warehouseId, "CASH", productId, 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @org.junit.jupiter.api.Order(26)
    void sales_create_noAuth_unauthorized() throws Exception {
        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(customerId, warehouseId, "CASH", productId, 1)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Sales: full CASH lifecycle ----------

    @Test
    @org.junit.jupiter.api.Order(30)
    void sales_create_success_isQuotation() throws Exception {
        String response = mockMvc.perform(post("/api/sales").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(customerId, warehouseId, "CASH", productId, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUOTATION"))
                .andReturn().getResponse().getContentAsString();
        saleId = objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Test
    @org.junit.jupiter.api.Order(31)
    void sales_complete_beforeConfirm_conflict() throws Exception {
        mockMvc.perform(patch("/api/sales/" + saleId + "/complete").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @org.junit.jupiter.api.Order(32)
    void sales_confirm_success() throws Exception {
        mockMvc.perform(patch("/api/sales/" + saleId + "/confirm").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sale.status").value("CONFIRMED"));
    }

    @Test
    @org.junit.jupiter.api.Order(33)
    void sales_confirm_alreadyConfirmed_conflict() throws Exception {
        mockMvc.perform(patch("/api/sales/" + saleId + "/confirm").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @org.junit.jupiter.api.Order(34)
    void sales_complete_success_decrementsStock() throws Exception {
        BigDecimal before = stockItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow().getQuantity();

        mockMvc.perform(patch("/api/sales/" + saleId + "/complete").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        BigDecimal after = stockItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow().getQuantity();
        assertEquals(0, before.subtract(BigDecimal.valueOf(2)).compareTo(after),
                "Completing a sale must decrement warehouse stock by the sold quantity");
    }

    @Test
    @org.junit.jupiter.api.Order(35)
    void sales_complete_alreadyCompleted_conflict() throws Exception {
        mockMvc.perform(patch("/api/sales/" + saleId + "/complete").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @org.junit.jupiter.api.Order(36)
    void sales_cancel_completedSale_conflict() throws Exception {
        mockMvc.perform(patch("/api/sales/" + saleId + "/cancel").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // ---------- Sales: insufficient stock ----------

    @Test
    @org.junit.jupiter.api.Order(40)
    void sales_complete_insufficientStock_rejectedAndStockUnchanged() throws Exception {
        BigDecimal available = stockItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow().getQuantity();
        BigDecimal tooMany = available.add(BigDecimal.valueOf(1_000_000));

        String response = mockMvc.perform(post("/api/sales").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(customerId, warehouseId, "CASH", productId, tooMany.intValue())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        cleanupSaleId2 = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(patch("/api/sales/" + cleanupSaleId2 + "/confirm").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockWarnings").isNotEmpty());

        mockMvc.perform(patch("/api/sales/" + cleanupSaleId2 + "/complete").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        BigDecimal unchanged = stockItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow().getQuantity();
        assertEquals(0, available.compareTo(unchanged), "A rejected completion must not move any stock");

        mockMvc.perform(patch("/api/sales/" + cleanupSaleId2 + "/cancel").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ---------- Credit limit enforcement ----------

    @Test
    @org.junit.jupiter.api.Order(50)
    void sales_creditSale_exceedingLimit_rejectedThenAdminOverrideSucceeds() throws Exception {
        // Customer's credit limit is 50000; derive a quantity whose VAT-inclusive total exceeds
        // it from the actual product price, rather than a hardcoded quantity — the specific
        // product/price picked in setUp() varies with whatever stock happens to be seeded.
        var sellingPrice = productRepository.findById(productId).orElseThrow().getSellingPrice();
        int exceedingQty = sellingPrice.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(60000).divide(sellingPrice, 0, java.math.RoundingMode.UP).intValue() + 1
                : 60;

        String response = mockMvc.perform(post("/api/sales").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(customerId, warehouseId, "CREDIT", productId, exceedingQty)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        creditSaleId = objectMapper.readTree(response).at("/data/id").asLong();
        mockMvc.perform(patch("/api/sales/" + creditSaleId + "/confirm").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Without override: rejected for exceeding credit limit.
        mockMvc.perform(patch("/api/sales/" + creditSaleId + "/complete").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // Manager cannot override.
        mockMvc.perform(patch("/api/sales/" + creditSaleId + "/complete?override=true").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict());

        // Admin can override.
        mockMvc.perform(patch("/api/sales/" + creditSaleId + "/complete?override=true").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    // ---------- Payments ----------

    @Test
    @org.junit.jupiter.api.Order(60)
    void payments_onNonCompletedSale_rejected() throws Exception {
        // sales_complete_insufficientStock_rejectedAndStockUnchanged cancelled cleanupSaleId2, a
        // non-COMPLETED sale — recording a payment against it must be refused.
        mockMvc.perform(post("/api/sales/" + cleanupSaleId2 + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @org.junit.jupiter.api.Order(61)
    void payments_invalidSale_notFound() throws Exception {
        mockMvc.perform(post("/api/sales/999999999/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @org.junit.jupiter.api.Order(62)
    void payments_zeroOrNegativeAmount_badRequest() throws Exception {
        mockMvc.perform(post("/api/sales/" + creditSaleId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/sales/" + creditSaleId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-5,\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Order(63)
    void payments_staffForbidden() throws Exception {
        mockMvc.perform(post("/api/sales/" + creditSaleId + "/payments").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @org.junit.jupiter.api.Order(64)
    void payments_overpayment_rejected() throws Exception {
        // The credit sale's total is well over 50000 (its own credit limit) but nowhere near
        // this absurd figure, so this must always exceed the remaining balance owed.
        mockMvc.perform(post("/api/sales/" + creditSaleId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":999999999,\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @org.junit.jupiter.api.Order(65)
    void payments_partialThenFull_succeedsAndReducesBalance() throws Exception {
        String saleResponse = mockMvc.perform(get("/api/sales/" + creditSaleId).header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        BigDecimal total = new BigDecimal(objectMapper.readTree(saleResponse).at("/data/totalAmount").asText());
        BigDecimal partial = total.divide(BigDecimal.valueOf(2));

        mockMvc.perform(post("/api/sales/" + creditSaleId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + partial + ",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isOk());

        // Overpaying the now-smaller remaining balance must still be rejected.
        mockMvc.perform(post("/api/sales/" + creditSaleId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + total + ",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isConflict());

        BigDecimal remaining = total.subtract(partial);
        mockMvc.perform(post("/api/sales/" + creditSaleId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + remaining + ",\"paymentMethod\":\"BANK_TRANSFER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sales/" + creditSaleId + "/payments").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/customers/" + customerId + "/statement").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[-1].runningBalance").value(0.0));
    }

    private static String saleJson(Long custId, Long whId, String paymentType, Long prodId, Number qty) {
        return "{\"customerId\":" + custId + ",\"warehouseId\":" + whId
                + ",\"paymentType\":\"" + paymentType + "\",\"lines\":[{\"productId\":" + prodId
                + ",\"quantity\":" + qty + "}]}";
    }
}
