package com.company.tai.company;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompanySettingsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final long TS = System.currentTimeMillis();
    private static final String ADMIN_EMAIL = "cs-admin-e2e-" + TS + "@tai.local";
    private static final String STAFF_EMAIL = "cs-staff-e2e-" + TS + "@tai.local";
    private static String adminToken;
    private static String staffToken;

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        String challengeToken = node.at("/data/challengeToken").asText();
        String code = node.at("/data/debugCode").asText();
        String verifyResponse = mockMvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + challengeToken + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(verifyResponse).at("/data/accessToken").asText();
    }

    @Order(1)
    @Test
    void setup_registerAdminAndStaff() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"CS Admin\",\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isOk());
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        admin.setRoles(roles);
        userRepository.save(admin);
        adminToken = login(ADMIN_EMAIL, "Passw0rd1");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"CS Staff\",\"email\":\"" + STAFF_EMAIL + "\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isOk());
        staffToken = login(STAFF_EMAIL, "Passw0rd1");
    }

    @Order(2)
    @Test
    void get_noToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/company-settings")).andExpect(status().isUnauthorized());
    }

    @Order(3)
    @Test
    void get_staffToken_isAllowed_returnsSeededProfile() throws Exception {
        mockMvc.perform(get("/api/company-settings").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legalName").value("Mapleco S.A.R.L"));
    }

    @Order(4)
    @Test
    void update_staffForbidden() throws Exception {
        mockMvc.perform(put("/api/company-settings").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Nope\"}"))
                .andExpect(status().isForbidden());
    }

    @Order(5)
    @Test
    void update_missingLegalName_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/company-settings").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Order(6)
    @Test
    void update_asAdmin_persistsAndReturnsUpdatedProfile() throws Exception {
        String body = "{\"legalName\":\"Mapleco S.A.R.L\",\"tinNumber\":\"1234567\",\"registrationNumber\":\"RDB-9988\","
                + "\"addressLine\":\"KG 7 Ave\",\"city\":\"Kigali\",\"country\":\"Rwanda\",\"phone\":\"+250788000000\","
                + "\"email\":\"info@mapleco.rw\",\"website\":\"https://mapleco.rw\",\"logoUrl\":\"/logo.png\"}";
        mockMvc.perform(put("/api/company-settings").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tinNumber").value("1234567"))
                .andExpect(jsonPath("$.data.registrationNumber").value("RDB-9988"));

        // Restore the original seeded values so this test suite is repeatable and doesn't
        // leave the live company profile mutated for other domains/manual testing.
        String restore = "{\"legalName\":\"Mapleco S.A.R.L\",\"tradingName\":\"Mapleco\",\"city\":\"Kigali\","
                + "\"country\":\"Rwanda\",\"logoUrl\":\"/logo.png\"}";
        mockMvc.perform(put("/api/company-settings").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(restore))
                .andExpect(status().isOk());
    }

    @AfterAll
    void cleanup() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(STAFF_EMAIL).ifPresent(userRepository::delete);
    }
}
