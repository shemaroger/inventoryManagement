package com.company.tai.inventory;

import com.company.tai.inventory.repository.BranchRepository;
import com.company.tai.inventory.repository.BrandRepository;
import com.company.tai.inventory.repository.CategoryRepository;
import com.company.tai.inventory.repository.ProductRepository;
import com.company.tai.inventory.repository.StockAdjustmentRepository;
import com.company.tai.inventory.repository.StockItemRepository;
import com.company.tai.inventory.repository.WarehouseRepository;
import com.company.tai.user.entity.Role;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.RoleRepository;
import com.company.tai.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the inventory domain (categories, brands, products, warehouses,
 * stock, branches), run against the real dev Postgres database on a random port so it
 * doesn't collide with the already-running dev server on :8080.
 * All rows created here are removed in {@link #cleanup()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryIntegrationTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private StockItemRepository stockItemRepository;
    @Autowired private StockAdjustmentRepository stockAdjustmentRepository;

    private static final long TS = System.currentTimeMillis() / 1000;
    private static final String ADMIN_EMAIL = "inv-admin-e2e-" + TS + "@tai.local";
    private static final String STAFF_EMAIL = "inv-staff-e2e-" + TS + "@tai.local";

    private static String adminToken;
    private static String staffToken;

    private static Long categoryId;
    private static Long childCategoryId;
    private static Long brandId;
    private static Long productId;
    private static Long warehouseId;
    private static Long warehouse2Id;
    private static Long branchId;

    @AfterAll
    void cleanup() {
        if (productId != null) {
            stockItemRepository.findByProductId(productId).forEach(si -> {
                stockAdjustmentRepository.findAll().stream()
                        .filter(sa -> sa.getProduct().getId().equals(productId))
                        .forEach(stockAdjustmentRepository::delete);
                stockItemRepository.delete(si);
            });
            productRepository.findById(productId).ifPresent(productRepository::delete);
        }
        if (childCategoryId != null) categoryRepository.findById(childCategoryId).ifPresent(categoryRepository::delete);
        if (categoryId != null) categoryRepository.findById(categoryId).ifPresent(categoryRepository::delete);
        if (brandId != null) brandRepository.findById(brandId).ifPresent(brandRepository::delete);
        if (warehouse2Id != null) warehouseRepository.findById(warehouse2Id).ifPresent(warehouseRepository::delete);
        if (warehouseId != null) warehouseRepository.findById(warehouseId).ifPresent(warehouseRepository::delete);
        if (branchId != null) branchRepository.findById(branchId).ifPresent(branchRepository::delete);
        // The admin account may have accumulated journal_entries.created_by rows (posted by
        // JournalService when our stock adjustments/transfers were applied) — those are real
        // accounting audit records with an FK back to users, so deleting the user is blocked.
        // Best-effort delete; if it's referenced, leave the throwaway account in place rather
        // than fail teardown (harmless, same as other *-e2e-* accounts already in this dev DB).
        deleteUserBestEffort(ADMIN_EMAIL);
        deleteUserBestEffort(STAFF_EMAIL);
    }

    private void deleteUserBestEffort(String email) {
        try {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        } catch (Exception e) {
            // referenced by audit/journal data created during the test run; leave it in place
        }
    }

    // ---------- setup helpers ----------

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

    @Order(1)
    @Test
    void setup_registerAdminAndStaff() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Inv Admin\",\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isOk());
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        admin.setRoles(roles);
        userRepository.save(admin);
        adminToken = login(ADMIN_EMAIL, "Passw0rd1");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Inv Staff\",\"email\":\"" + STAFF_EMAIL + "\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isOk());
        staffToken = login(STAFF_EMAIL, "Passw0rd1");
    }

    // ---------- Categories ----------

    @Order(2)
    @Test
    void categories_noToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
    }

    @Order(3)
    @Test
    void categories_create_missingName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Order(4)
    @Test
    void categories_create_staffForbidden() throws Exception {
        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cat-" + TS + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Order(5)
    @Test
    void categories_create_badParentId_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cat-badparent-" + TS + "\",\"parentId\":999999999}"))
                .andExpect(status().isNotFound());
    }

    @Order(6)
    @Test
    void categories_create_success() throws Exception {
        String response = mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cat-e2e-" + TS + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        categoryId = objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Order(7)
    @Test
    void categories_create_duplicateName_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cat-e2e-" + TS + "\"}"))
                .andExpect(status().isConflict());
    }

    @Order(8)
    @Test
    void categories_create_childWithParent_success() throws Exception {
        String response = mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cat-child-e2e-" + TS + "\",\"parentId\":" + categoryId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(categoryId))
                .andReturn().getResponse().getContentAsString();
        childCategoryId = objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Order(9)
    @Test
    void categories_update_selfAsParent_returnsConflict() throws Exception {
        mockMvc.perform(put("/api/categories/" + categoryId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cat-e2e-" + TS + "\",\"parentId\":" + categoryId + "}"))
                .andExpect(status().isConflict());
    }

    @Order(10)
    @Test
    void categories_update_circularParent_returnsConflict() throws Exception {
        // categoryId is currently childCategoryId's parent; making categoryId's parent be
        // childCategoryId would create a 2-node cycle.
        mockMvc.perform(put("/api/categories/" + categoryId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cat-e2e-" + TS + "\",\"parentId\":" + childCategoryId + "}"))
                .andExpect(status().isConflict());
    }

    @Order(11)
    @Test
    void categories_update_notFound_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/categories/999999999").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Order(12)
    @Test
    void categories_delete_withChild_returnsConflict() throws Exception {
        // categoryId has childCategoryId referencing it via parent_id FK.
        mockMvc.perform(delete("/api/categories/" + categoryId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Order(13)
    @Test
    void categories_delete_staffForbidden() throws Exception {
        mockMvc.perform(delete("/api/categories/" + childCategoryId).header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Order(14)
    @Test
    void categories_delete_notFound_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/categories/999999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Order(15)
    @Test
    void categories_listAll_includesCreated() throws Exception {
        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------- Brands ----------

    @Order(16)
    @Test
    void brands_create_missingName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/brands").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Order(17)
    @Test
    void brands_create_success() throws Exception {
        String response = mockMvc.perform(post("/api/brands").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Brand-e2e-" + TS + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        brandId = objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Order(18)
    @Test
    void brands_create_duplicateName_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/brands").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Brand-e2e-" + TS + "\"}"))
                .andExpect(status().isConflict());
    }

    @Order(19)
    @Test
    void brands_update_notFound_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/brands/999999999").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Order(20)
    @Test
    void brands_delete_staffForbidden() throws Exception {
        mockMvc.perform(delete("/api/brands/" + brandId).header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    // ---------- Warehouses / Branches ----------

    @Order(21)
    @Test
    void branches_create_success() throws Exception {
        String response = mockMvc.perform(post("/api/branches").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Branch-e2e-" + TS + "\",\"address\":\"Addr\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        branchId = objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Order(22)
    @Test
    void branches_create_missingName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/branches").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Order(23)
    @Test
    void warehouses_create_badBranchId_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/warehouses").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wh-bad-" + TS + "\",\"branchId\":999999999}"))
                .andExpect(status().isNotFound());
    }

    @Order(24)
    @Test
    void warehouses_create_success() throws Exception {
        String response = mockMvc.perform(post("/api/warehouses").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wh-e2e-" + TS + "\",\"location\":\"Loc\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchId").value(branchId))
                .andReturn().getResponse().getContentAsString();
        warehouseId = objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Order(25)
    @Test
    void warehouses_create_second_success() throws Exception {
        String response = mockMvc.perform(post("/api/warehouses").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wh2-e2e-" + TS + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        warehouse2Id = objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Order(26)
    @Test
    void warehouses_create_staffForbidden() throws Exception {
        mockMvc.perform(post("/api/warehouses").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wh-forbidden-" + TS + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Order(27)
    @Test
    void warehouses_update_notFound_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/warehouses/999999999").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Order(28)
    @Test
    void branches_delete_withWarehouse_returnsConflict() throws Exception {
        mockMvc.perform(delete("/api/branches/" + branchId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Order(29)
    @Test
    void branches_deactivate_success() throws Exception {
        mockMvc.perform(patch("/api/branches/" + branchId + "/deactivate").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------- Products ----------

    @Order(30)
    @Test
    void products_create_missingRequiredFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Order(31)
    @Test
    void products_create_negativeCostPrice_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-e2e-" + TS + "\",\"name\":\"P\",\"costPrice\":-1,\"sellingPrice\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Order(32)
    @Test
    void products_create_badCategoryId_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-e2e-bad-" + TS + "\",\"name\":\"P\",\"costPrice\":1,\"sellingPrice\":2,\"categoryId\":999999999}"))
                .andExpect(status().isNotFound());
    }

    @Order(33)
    @Test
    void products_create_badBrandId_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-e2e-bad2-" + TS + "\",\"name\":\"P\",\"costPrice\":1,\"sellingPrice\":2,\"brandId\":999999999}"))
                .andExpect(status().isNotFound());
    }

    @Order(34)
    @Test
    void products_create_success() throws Exception {
        String response = mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-e2e-" + TS + "\",\"name\":\"Product e2e\",\"costPrice\":100,\"sellingPrice\":150,"
                                + "\"categoryId\":" + categoryId + ",\"brandId\":" + brandId + ",\"reorderLevel\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse().getContentAsString();
        productId = objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Order(35)
    @Test
    void products_create_duplicateSku_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-e2e-" + TS + "\",\"name\":\"Dup\",\"costPrice\":1,\"sellingPrice\":2}"))
                .andExpect(status().isConflict());
    }

    @Order(36)
    @Test
    void products_getById_success() throws Exception {
        mockMvc.perform(get("/api/products/" + productId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("SKU-e2e-" + TS));
    }

    @Order(37)
    @Test
    void products_getById_notFound() throws Exception {
        mockMvc.perform(get("/api/products/999999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Order(38)
    @Test
    void products_search_byCategoryAndBrand() throws Exception {
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("categoryId", String.valueOf(categoryId))
                        .param("brandId", String.valueOf(brandId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sku").value("SKU-e2e-" + TS));
    }

    @Order(39)
    @Test
    void products_update_notFound_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/products/999999999").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"X\",\"name\":\"X\",\"costPrice\":1,\"sellingPrice\":2}"))
                .andExpect(status().isNotFound());
    }

    @Order(40)
    @Test
    void products_deactivate_staffForbidden() throws Exception {
        mockMvc.perform(patch("/api/products/" + productId + "/deactivate").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Order(41)
    @Test
    void products_uploadImage_unsupportedType_returnsConflict() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "test.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/products/" + productId + "/image").file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Order(42)
    @Test
    void products_uploadImage_validPng_succeeds() throws Exception {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "test.png", "image/png", pngBytes);
        mockMvc.perform(multipart("/api/products/" + productId + "/image").file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").isNotEmpty());

        mockMvc.perform(get("/api/products/" + productId + "/image")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Order(43)
    @Test
    void products_uploadImage_notFoundProduct_returnsNotFound() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "test.png", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/products/999999999/image").file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ---------- Stock ----------

    @Order(44)
    @Test
    void stock_adjust_missingFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/stock/adjustments").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Order(45)
    @Test
    void stock_adjust_zeroQuantity_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/stock/adjustments").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"warehouseId\":" + warehouseId
                                + ",\"adjustmentType\":\"INCREASE\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Order(46)
    @Test
    void stock_adjust_negativeQuantity_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/stock/adjustments").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"warehouseId\":" + warehouseId
                                + ",\"adjustmentType\":\"INCREASE\",\"quantity\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Order(47)
    @Test
    void stock_adjust_badProductId_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/stock/adjustments").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":999999999,\"warehouseId\":" + warehouseId
                                + ",\"adjustmentType\":\"INCREASE\",\"quantity\":5}"))
                .andExpect(status().isNotFound());
    }

    @Order(48)
    @Test
    void stock_adjust_decreaseBelowZero_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/stock/adjustments").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"warehouseId\":" + warehouseId
                                + ",\"adjustmentType\":\"DECREASE\",\"quantity\":999999}"))
                .andExpect(status().isConflict());
    }

    @Order(49)
    @Test
    void stock_adjust_increase_success() throws Exception {
        mockMvc.perform(post("/api/stock/adjustments").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"warehouseId\":" + warehouseId
                                + ",\"adjustmentType\":\"INCREASE\",\"quantity\":20,\"reason\":\"e2e\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(20));
    }

    @Order(50)
    @Test
    void stock_lowStock_reflectsReorderLevel() throws Exception {
        // reorderLevel=5, quantity=20 currently -> not low stock; decrease to <=5 to trigger it.
        mockMvc.perform(post("/api/stock/adjustments").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"warehouseId\":" + warehouseId
                                + ",\"adjustmentType\":\"DECREASE\",\"quantity\":16,\"reason\":\"e2e-lowstock\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(4));

        String response = mockMvc.perform(get("/api/stock/low-stock").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).get("data");
        boolean found = false;
        for (JsonNode n : data) {
            if (n.get("productId").asLong() == productId) found = true;
        }
        assertEquals(true, found, "Product at/below reorderLevel must appear in low-stock list");
    }

    @Order(51)
    @Test
    void stock_transfer_sameWarehouse_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/stock/transfers").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"sourceWarehouseId\":" + warehouseId
                                + ",\"destinationWarehouseId\":" + warehouseId + ",\"quantity\":1}"))
                .andExpect(status().isConflict());
    }

    @Order(52)
    @Test
    void stock_transfer_insufficientStock_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/stock/transfers").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"sourceWarehouseId\":" + warehouseId
                                + ",\"destinationWarehouseId\":" + warehouse2Id + ",\"quantity\":999999}"))
                .andExpect(status().isConflict());
    }

    @Order(53)
    @Test
    void stock_transfer_success() throws Exception {
        mockMvc.perform(post("/api/stock/transfers").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"sourceWarehouseId\":" + warehouseId
                                + ",\"destinationWarehouseId\":" + warehouse2Id + ",\"quantity\":4,\"reason\":\"e2e-transfer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source.quantity").value(0))
                .andExpect(jsonPath("$.data.destination.quantity").value(4));
    }

    @Order(54)
    @Test
    void stock_byWarehouse_andByProduct_succeed() throws Exception {
        mockMvc.perform(get("/api/stock/warehouse/" + warehouse2Id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/api/stock/product/" + productId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Order(55)
    @Test
    void stock_adjustmentHistory_filterByProduct() throws Exception {
        mockMvc.perform(get("/api/stock/adjustments").header("Authorization", "Bearer " + adminToken)
                        .param("productId", String.valueOf(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Order(56)
    @Test
    void warehouses_delete_withStock_returnsConflict() throws Exception {
        // warehouse2Id now holds 4 units of productId; FK from stock_items should block delete.
        mockMvc.perform(delete("/api/warehouses/" + warehouse2Id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }
}
