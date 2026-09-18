package com.company.tai.user;

import com.company.tai.user.entity.Role;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.RoleRepository;
import com.company.tai.user.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the auth/user/role/permission domain, run against the real dev
 * Postgres database (no H2/test profile configured for this project). Runs on a random
 * port so it does not collide with the already-running dev server on :8080.
 * All rows created here are removed in {@link #cleanup()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthUserSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private static final long TS = System.currentTimeMillis() / 1000;
    private static final String ADMIN_EMAIL = "admin-e2e-" + TS + "@tai.local";
    private static final String STAFF_EMAIL = "staff-e2e-" + TS + "@tai.local";
    private static final String REG_EMAIL = "reg-e2e-" + TS + "@tai.local";

    private static String adminToken;
    private static String staffToken;
    private static Long createdUserId;
    private static Long createdRoleId;
    private static Long createdPermissionId;

    @BeforeAll
    void seedAdmin() {
        // Nothing here; users are created via the API itself in ordered tests below.
    }

    @AfterAll
    void cleanup() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(STAFF_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(REG_EMAIL).ifPresent(userRepository::delete);
        if (createdUserId != null) {
            userRepository.findById(createdUserId).ifPresent(userRepository::delete);
        }
    }

    // ---------- Register ----------

    @Test
    @Order(1)
    void register_publicSelfRegistration_alwaysGetsStaffRole_evenIfAdminRequested() throws Exception {
        String body = """
                {"fullName":"E2E Admin","email":"%s","password":"Passw0rd1","roleName":"ADMIN"}
                """.formatted(ADMIN_EMAIL);

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        assertEquals("STAFF", node.at("/data/roles/0").asText(),
                "Public registration must never grant a self-chosen privileged role (privilege escalation)");
        adminToken = node.at("/data/accessToken").asText();
    }

    @Test
    @Order(2)
    void register_duplicateEmail_returnsConflict() throws Exception {
        String body = """
                {"fullName":"Dup","email":"%s","password":"Passw0rd1"}
                """.formatted(ADMIN_EMAIL);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(3)
    void register_missingRequiredFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(4)
    void register_shortPassword_returnsBadRequest() throws Exception {
        String body = """
                {"fullName":"X","email":"short-%s@tai.local","password":"short"}
                """.formatted(TS);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // ---------- Login ----------

    @Test
    @Order(5)
    void login_wrongPassword_returnsUnauthorized() throws Exception {
        String body = """
                {"email":"%s","password":"WrongPass1"}
                """.formatted(ADMIN_EMAIL);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void login_nonexistentUser_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + TS + "@tai.local\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    void login_success_returnsOtpChallenge_thenVerifyReturnsToken() throws Exception {
        String body = """
                {"email":"%s","password":"Passw0rd1"}
                """.formatted(ADMIN_EMAIL);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeToken").isNotEmpty())
                .andExpect(jsonPath("$.data.debugCode").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        String challengeToken = node.at("/data/challengeToken").asText();
        String code = node.at("/data/debugCode").asText();

        String verifyBody = "{\"challengeToken\":\"" + challengeToken + "\",\"code\":\"" + code + "\"}";
        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @Order(7)
    void verifyOtp_wrongCode_returnsConflict() throws Exception {
        String body = """
                {"email":"%s","password":"Passw0rd1"}
                """.formatted(ADMIN_EMAIL);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        String challengeToken = objectMapper.readTree(response).at("/data/challengeToken").asText();

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + challengeToken + "\",\"code\":\"000000\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(8)
    void login_disabledUser_returnsForbiddenNotServerError() throws Exception {
        User user = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        user.setActive(false);
        userRepository.save(user);
        try {
            String body = """
                    {"email":"%s","password":"Passw0rd1"}
                    """.formatted(ADMIN_EMAIL);
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(result -> assertNotEquals(500, result.getResponse().getStatus(),
                            "A deactivated account attempting login must not blow up as a 500"))
                    .andExpect(status().isForbidden());
        } finally {
            user.setActive(true);
            userRepository.save(user);
        }
    }

    // ---------- Authorization boundaries on /api/users ----------

    @Test
    @Order(9)
    void users_noToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(10)
    void users_malformedToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    // Promote our "admin" test account to real ADMIN via direct repository access (simulating
    // an existing admin having granted the role), matching how the current running system was
    // bootstrapped — since public register can no longer self-grant ADMIN.
    private void promoteAdminAccountToAdminRole() {
        User user = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        user.setRoles(roles);
        userRepository.save(user);
    }

    private String login(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        String challengeToken = node.at("/data/challengeToken").asText();
        String code = node.at("/data/debugCode").asText();

        String verifyBody = "{\"challengeToken\":\"" + challengeToken + "\",\"code\":\"" + code + "\"}";
        String verifyResponse = mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(verifyResponse).at("/data/accessToken").asText();
    }

    @Test
    @Order(11)
    void users_staffToken_forbiddenFromListingUsers() throws Exception {
        // Register a plain STAFF account and confirm it cannot list users.
        String body = """
                {"fullName":"E2E Staff","email":"%s","password":"Passw0rd1"}
                """.formatted(STAFF_EMAIL);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        staffToken = login(STAFF_EMAIL, "Passw0rd1");

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"x\",\"email\":\"x@x.com\",\"password\":\"Passw0rd1\",\"roleName\":\"STAFF\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/roles").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/permissions").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(12)
    void users_adminToken_canListAndGet() throws Exception {
        promoteAdminAccountToAdminRole();
        adminToken = login(ADMIN_EMAIL, "Passw0rd1");

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(13)
    void users_getNonexistent_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/users/999999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(14)
    void users_getWithNonNumericId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/users/not-a-number").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(15)
    void users_createDuplicateEmail_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Dup\",\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"Passw0rd1\",\"roleName\":\"STAFF\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(16)
    void users_createUnknownRole_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\",\"email\":\"" + REG_EMAIL + "\",\"password\":\"Passw0rd1\",\"roleName\":\"NOPE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(17)
    void users_createWithLowercaseRoleName_isNormalizedAndSucceeds() throws Exception {
        String response = mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Lc Role\",\"email\":\"" + REG_EMAIL + "\",\"password\":\"Passw0rd1\",\"roleName\":\"staff\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("STAFF"))
                .andReturn().getResponse().getContentAsString();
        createdUserId = objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Test
    @Order(18)
    void users_updateNonexistent_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/users/999999999").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\",\"roleName\":\"STAFF\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(19)
    void users_updateBlankFullName_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/users/" + createdUserId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"roleName\":\"STAFF\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(20)
    void users_selfDeactivate_isBlocked() throws Exception {
        User self = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        mockMvc.perform(patch("/api/users/" + self.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(21)
    void users_selfDelete_isBlocked() throws Exception {
        User self = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        mockMvc.perform(delete("/api/users/" + self.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(22)
    void users_deactivateAndReactivateOtherUser_succeeds() throws Exception {
        mockMvc.perform(patch("/api/users/" + createdUserId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        mockMvc.perform(patch("/api/users/" + createdUserId + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @Order(23)
    void users_deactivateNonexistent_returnsNotFound() throws Exception {
        mockMvc.perform(patch("/api/users/999999999/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(24)
    void users_deleteNonexistent_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/users/999999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(25)
    void users_deleteOtherUser_succeeds() throws Exception {
        mockMvc.perform(delete("/api/users/" + createdUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        createdUserId = null; // already cleaned up
    }

    // ---------- Roles ----------

    @Test
    @Order(26)
    void roles_createDuplicateName_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/roles").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ADMIN\",\"description\":\"dup\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(27)
    void roles_createMissingName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/roles").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"no name\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(28)
    void roles_createAndAssignPermissions_thenDelete() throws Exception {
        String roleName = "E2E_ROLE_" + TS;
        String response = mockMvc.perform(post("/api/roles").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + roleName + "\",\"description\":\"test\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        createdRoleId = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(put("/api/roles/" + createdRoleId + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"NOPE_CODE_" + TS + "\"]}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/roles/" + createdRoleId + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"USER_VIEW\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("USER_VIEW"));

        mockMvc.perform(delete("/api/roles/" + createdRoleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        createdRoleId = null;
    }

    @Test
    @Order(29)
    void roles_deleteSeededSystemRole_isBlocked() throws Exception {
        Long staffId = roleRepository.findByName("STAFF").orElseThrow().getId();
        mockMvc.perform(delete("/api/roles/" + staffId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(30)
    void roles_getNonexistent_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/roles/999999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ---------- Permissions ----------

    @Test
    @Order(31)
    void permissions_createDuplicateCode_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/permissions").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"USER_VIEW\",\"description\":\"dup\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(32)
    void permissions_createMissingCode_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/permissions").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"no code\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(33)
    void permissions_createUpdateDelete_roundTrip() throws Exception {
        String code = "E2E_PERM_" + TS;
        String response = mockMvc.perform(post("/api/permissions").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"description\":\"test\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        createdPermissionId = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(put("/api/permissions/" + createdPermissionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "_v2\",\"description\":\"updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code + "_v2"));

        mockMvc.perform(delete("/api/permissions/" + createdPermissionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        createdPermissionId = null;
    }

    @Test
    @Order(34)
    void permissions_updateNonexistent_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/permissions/999999999").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"description\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(35)
    void permissions_deleteNonexistent_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/permissions/999999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
