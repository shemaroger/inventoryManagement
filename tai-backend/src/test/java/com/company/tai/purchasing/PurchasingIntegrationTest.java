package com.company.tai.purchasing;

import com.company.tai.inventory.entity.Product;
import com.company.tai.inventory.entity.Warehouse;
import com.company.tai.inventory.repository.ProductRepository;
import com.company.tai.inventory.repository.WarehouseRepository;
import com.company.tai.purchasing.entity.PurchaseOrder;
import com.company.tai.purchasing.entity.Supplier;
import com.company.tai.purchasing.entity.SupplierPayment;
import com.company.tai.purchasing.repository.PurchaseOrderRepository;
import com.company.tai.purchasing.repository.SupplierPaymentRepository;
import com.company.tai.purchasing.repository.SupplierRepository;
import com.company.tai.user.entity.Role;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.RoleRepository;
import com.company.tai.user.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Purchasing domain (Supplier, PurchaseOrder, SupplierPayment
 * controllers), run against the real dev Postgres database on a random port so it does not
 * collide with the already-running dev server on :8080.
 * All rows created here are removed in {@link #cleanup()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PurchasingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private SupplierPaymentRepository supplierPaymentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;

    private static final long TS = System.currentTimeMillis() / 1000;
    private static final String ADMIN_EMAIL = "purch-admin-e2e-" + TS + "@tai.local";
    private static final String STAFF_EMAIL = "purch-staff-e2e-" + TS + "@tai.local";

    private static String adminToken;
    private static String staffToken;
    private static Long warehouseId;
    private static Long productId;

    private static Long supplierId;
    private static Long poId;
    private static Long lineId;
    private static Long paymentPoId;
    private static Long paymentPoLineId;

    @Autowired private com.company.tai.accounting.repository.JournalEntryRepository journalEntryRepository;

    @AfterAll
    void cleanup() {
        supplierPaymentRepository.findAll().stream()
                .filter(p -> (p.getPurchaseOrder() != null && (p.getPurchaseOrder().getId().equals(poId)
                        || p.getPurchaseOrder().getId().equals(paymentPoId))))
                .forEach(supplierPaymentRepository::delete);
        // receive()/payments posted journal entries with created_by pointing at our admin test
        // user; those must go before the user row can be deleted (FK constraint).
        journalEntryRepository.findAll().stream()
                .filter(je -> je.getReference() != null
                        && (je.getReference().equals("PO #" + poId) || je.getReference().equals("PO #" + paymentPoId)))
                .forEach(journalEntryRepository::delete);
        if (poId != null) purchaseOrderRepository.findById(poId).ifPresent(purchaseOrderRepository::delete);
        if (paymentPoId != null) purchaseOrderRepository.findById(paymentPoId).ifPresent(purchaseOrderRepository::delete);
        if (supplierId != null) supplierRepository.findById(supplierId).ifPresent(supplierRepository::delete);
        // Hard-deleting the users isn't possible: receive()/deactivate flows leave them referenced
        // as performed_by/created_by on stock_adjustments and journal_entries rows that belong to
        // other domains and shouldn't be touched from here. Deactivating is enough cleanup — these
        // are throwaway *-e2e-<timestamp> accounts, easily identified and prunable later.
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(u -> { u.setActive(false); userRepository.save(u); });
        userRepository.findByEmail(STAFF_EMAIL).ifPresent(u -> { u.setActive(false); userRepository.save(u); });
    }

    private String register(String email, String role) throws Exception {
        String body = "{\"fullName\":\"E2E\",\"email\":\"" + email + "\",\"password\":\"Passw0rd1\",\"roleName\":\"" + role + "\"}";
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }

    private String login(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = objectMapper.readTree(response);
        String challengeToken = node.at("/data/challengeToken").asText();
        String code = node.at("/data/debugCode").asText();

        String verifyBody = "{\"challengeToken\":\"" + challengeToken + "\",\"code\":\"" + code + "\"}";
        String verifyResponse = mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(verifyResponse).at("/data/accessToken").asText();
    }

    // ---------- Setup ----------

    @Test
    @Order(0)
    void t00_setup() throws Exception {
        // Public self-registration always grants STAFF now (privilege-escalation fix in the auth
        // domain), so an ADMIN test account has to be promoted afterwards via the repository —
        // mirrors AuthUserSecurityIntegrationTest.promoteAdminAccountToAdminRole().
        register(ADMIN_EMAIL, "ADMIN");
        staffToken = register(STAFF_EMAIL, "STAFF");

        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        admin.setRoles(java.util.Set.of(adminRole));
        userRepository.save(admin);
        adminToken = login(ADMIN_EMAIL, "Passw0rd1");

        Warehouse wh = warehouseRepository.findAll().stream().findFirst().orElseThrow();
        warehouseId = wh.getId();
        Product p = productRepository.findAll().stream().findFirst().orElseThrow();
        productId = p.getId();
    }

    // ---------- Supplier ----------

    @Test
    @Order(1)
    void t01_suppliers_noToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/suppliers")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void t02_suppliers_create_asStaff_isForbidden() throws Exception {
        mockMvc.perform(post("/api/suppliers").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void t03_suppliers_create_missingName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/suppliers").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void t04_suppliers_create_valid_succeeds() throws Exception {
        String name = "supplier-e2e-" + TS;
        String response = mockMvc.perform(post("/api/suppliers").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"contactPerson\":\"John\",\"phone\":\"123\",\"email\":\"s@e2e.com\",\"address\":\"Addr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse().getContentAsString();
        supplierId = objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Test
    @Order(5)
    void t05_suppliers_list_asAdmin_containsCreated() throws Exception {
        mockMvc.perform(get("/api/suppliers").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(6)
    void t06_suppliers_update_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/suppliers/999999999").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void t07_suppliers_update_valid_succeeds() throws Exception {
        mockMvc.perform(put("/api/suppliers/" + supplierId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"supplier-e2e-updated-" + TS + "\",\"contactPerson\":\"Jane\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactPerson").value("Jane"));
    }

    @Test
    @Order(8)
    void t08_suppliers_deactivate_asManagerRole_isForbidden() throws Exception {
        // deactivate requires ADMIN specifically; STAFF must be rejected
        mockMvc.perform(patch("/api/suppliers/" + supplierId + "/deactivate")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void t09_suppliers_deactivate_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/suppliers/999999999/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(10)
    void t10_suppliers_deactivate_valid_succeeds() throws Exception {
        mockMvc.perform(patch("/api/suppliers/" + supplierId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        Supplier s = supplierRepository.findById(supplierId).orElseThrow();
        assertEquals(false, s.isActive());
    }

    // ---------- Purchase Orders ----------

    @Test
    @Order(11)
    void t11_po_create_invalidSupplier_returns404() throws Exception {
        mockMvc.perform(post("/api/purchase-orders").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":999999999,\"warehouseId\":" + warehouseId
                                + ",\"lines\":[{\"productId\":" + productId + ",\"quantityOrdered\":10,\"unitCost\":5}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(12)
    void t12_po_create_invalidWarehouse_returns404() throws Exception {
        mockMvc.perform(post("/api/purchase-orders").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":999999999"
                                + ",\"lines\":[{\"productId\":" + productId + ",\"quantityOrdered\":10,\"unitCost\":5}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(13)
    void t13_po_create_invalidProduct_returns404() throws Exception {
        mockMvc.perform(post("/api/purchase-orders").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId
                                + ",\"lines\":[{\"productId\":999999999,\"quantityOrdered\":10,\"unitCost\":5}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(14)
    void t14_po_create_emptyLines_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/purchase-orders").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId + ",\"lines\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(15)
    void t15_po_create_zeroQuantity_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/purchase-orders").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId
                                + ",\"lines\":[{\"productId\":" + productId + ",\"quantityOrdered\":0,\"unitCost\":5}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(16)
    void t16_po_create_negativeUnitCost_returnsBadRequest() throws Exception {
        // Regression test for the fixed bug: PurchaseOrderLineRequest.unitCost previously had no
        // lower-bound validation, letting a negative unit cost slip through into a saved PO.
        mockMvc.perform(post("/api/purchase-orders").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId
                                + ",\"lines\":[{\"productId\":" + productId + ",\"quantityOrdered\":5,\"unitCost\":-10}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(17)
    void t17_po_create_asStaff_isForbidden() throws Exception {
        mockMvc.perform(post("/api/purchase-orders").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId
                                + ",\"lines\":[{\"productId\":" + productId + ",\"quantityOrdered\":5,\"unitCost\":5}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(18)
    void t18_po_create_valid_succeeds() throws Exception {
        String response = mockMvc.perform(post("/api/purchase-orders").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId
                                + ",\"lines\":[{\"productId\":" + productId + ",\"quantityOrdered\":10,\"unitCost\":5000}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        poId = node.at("/data/id").asLong();
        lineId = node.at("/data/lines/0/id").asLong();
    }

    @Test
    @Order(19)
    void t19_po_getById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/purchase-orders/999999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(20)
    void t20_po_getById_valid_succeeds() throws Exception {
        mockMvc.perform(get("/api/purchase-orders/" + poId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(poId));
    }

    @Test
    @Order(21)
    void t21_po_search_byStatus_succeeds() throws Exception {
        mockMvc.perform(get("/api/purchase-orders").header("Authorization", "Bearer " + adminToken)
                        .param("status", "DRAFT").param("supplierId", String.valueOf(supplierId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(22)
    void t22_po_receive_whileDraft_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/receive").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"lineId\":" + lineId + ",\"quantityReceived\":1}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(23)
    void t23_po_update_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/purchase-orders/999999999").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId
                                + ",\"lines\":[{\"productId\":" + productId + ",\"quantityOrdered\":1,\"unitCost\":1}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(24)
    void t24_po_update_whileDraft_succeedsAndReplacesLines() throws Exception {
        String response = mockMvc.perform(put("/api/purchase-orders/" + poId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId + ",\"notes\":\"updated\","
                                + "\"lines\":[{\"productId\":" + productId + ",\"quantityOrdered\":10,\"unitCost\":5000}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes").value("updated"))
                .andReturn().getResponse().getContentAsString();
        // orphanRemoval replaces the line id
        lineId = objectMapper.readTree(response).at("/data/lines/0/id").asLong();
    }

    @Test
    @Order(25)
    void t25_po_submit_asStaff_isForbidden() throws Exception {
        mockMvc.perform(patch("/api/purchase-orders/" + poId + "/submit").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(26)
    void t26_po_submit_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/purchase-orders/999999999/submit").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(27)
    void t27_po_submit_valid_succeeds() throws Exception {
        mockMvc.perform(patch("/api/purchase-orders/" + poId + "/submit").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    @Order(28)
    void t28_po_submit_alreadySubmitted_returnsConflict() throws Exception {
        mockMvc.perform(patch("/api/purchase-orders/" + poId + "/submit").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(29)
    void t29_po_update_afterSubmit_returnsConflict() throws Exception {
        mockMvc.perform(put("/api/purchase-orders/" + poId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId
                                + ",\"lines\":[{\"productId\":" + productId + ",\"quantityOrdered\":10,\"unitCost\":5000}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(30)
    void t30_po_receive_negativeQuantity_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/receive").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"lineId\":" + lineId + ",\"quantityReceived\":-1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(31)
    void t31_po_receive_unknownLine_returns404() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/receive").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"lineId\":999999999,\"quantityReceived\":1}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(32)
    void t32_po_receive_moreThanOrdered_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/receive").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"lineId\":" + lineId + ",\"quantityReceived\":100}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(33)
    void t33_po_receive_partial_movesToPartiallyReceived() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/receive").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"lineId\":" + lineId + ",\"quantityReceived\":4}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.data.lines[0].quantityReceived").value(4));
    }

    @Test
    @Order(34)
    void t34_po_receive_remaining_movesToReceived() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/receive").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"lineId\":" + lineId + ",\"quantityReceived\":6}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"));
    }

    @Test
    @Order(35)
    void t35_po_receive_afterFullyReceived_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/receive").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"lineId\":" + lineId + ",\"quantityReceived\":1}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(36)
    void t36_po_cancel_afterReceived_returnsConflict() throws Exception {
        mockMvc.perform(patch("/api/purchase-orders/" + poId + "/cancel").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(37)
    void t37_po_cancel_draft_succeeds() throws Exception {
        String response = mockMvc.perform(post("/api/purchase-orders").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId
                                + ",\"lines\":[{\"productId\":" + productId + ",\"quantityOrdered\":1,\"unitCost\":100}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long draftPoId = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(patch("/api/purchase-orders/" + draftPoId + "/cancel").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        purchaseOrderRepository.findById(draftPoId).ifPresent(purchaseOrderRepository::delete);
    }

    @Test
    @Order(38)
    void t38_po_cancel_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/purchase-orders/999999999/cancel").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ---------- Supplier Payments ----------
    // PO #poId is now fully RECEIVED with 10 * 5000 = 50000 net value -> invoice total with 18% VAT = 59000.

    @Test
    @Order(39)
    void t39_payments_list_noToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/purchase-orders/" + poId + "/payments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(40)
    void t40_payments_list_empty_succeeds() throws Exception {
        mockMvc.perform(get("/api/purchase-orders/" + poId + "/payments").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @Order(41)
    void t41_payments_record_asStaff_isForbidden() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/payments").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(42)
    void t42_payments_record_negativeAmount_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(43)
    void t43_payments_record_zeroAmount_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(44)
    void t44_payments_record_missingAmount_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(45)
    void t45_payments_record_forNonexistentPo_returns404() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/999999999/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(46)
    void t46_payments_record_exceedingOutstandingBalance_returnsConflict() throws Exception {
        // Regression test for the fixed bug: recording a payment used to be accepted for any
        // amount, with no check against the purchase order's outstanding balance.
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":999999999}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("exceeds the outstanding balance")));
    }

    @Test
    @Order(47)
    void t47_payments_record_partial_succeeds() throws Exception {
        String response = mockMvc.perform(post("/api/purchase-orders/" + poId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":30000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(30000))
                .andReturn().getResponse().getContentAsString();
        paymentPoId = poId;
    }

    @Test
    @Order(48)
    void t48_payments_record_remainderExactly_succeeds() throws Exception {
        // 59000 invoice total - 30000 already paid = 29000 outstanding.
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":29000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(29000));
    }

    @Test
    @Order(49)
    void t49_payments_record_afterFullyPaid_anyAmountIsRejected() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(50)
    void t50_payments_list_afterRecording_returnsBothPayments() throws Exception {
        mockMvc.perform(get("/api/purchase-orders/" + poId + "/payments").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @Order(51)
    void t51_payments_record_onPoWithNothingReceivedYet_isRejected() throws Exception {
        String response = mockMvc.perform(post("/api/purchase-orders").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId
                                + ",\"lines\":[{\"productId\":" + productId + ",\"quantityOrdered\":1,\"unitCost\":100}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long draftPoId = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(post("/api/purchase-orders/" + draftPoId + "/payments").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isConflict());

        purchaseOrderRepository.findById(draftPoId).ifPresent(purchaseOrderRepository::delete);
    }
}
