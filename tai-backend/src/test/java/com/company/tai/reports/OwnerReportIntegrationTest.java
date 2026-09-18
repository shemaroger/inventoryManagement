package com.company.tai.reports;

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
class OwnerReportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final long TS = System.currentTimeMillis();
    private static final String ADMIN_EMAIL = "owner-report-admin-e2e-" + TS + "@tai.local";
    private static final String STAFF_EMAIL = "owner-report-staff-e2e-" + TS + "@tai.local";
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
                        .content("{\"fullName\":\"Owner Report Admin\",\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isOk());
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        admin.setRoles(roles);
        userRepository.save(admin);
        adminToken = login(ADMIN_EMAIL, "Passw0rd1");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Owner Report Staff\",\"email\":\"" + STAFF_EMAIL + "\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isOk());
        staffToken = login(STAFF_EMAIL, "Passw0rd1");
    }

    @Order(2)
    @Test
    void ownerSummary_noToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/reports/owner-summary")).andExpect(status().isUnauthorized());
    }

    @Order(3)
    @Test
    void ownerSummary_staffForbidden() throws Exception {
        mockMvc.perform(get("/api/reports/owner-summary").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Order(4)
    @Test
    void ownerSummary_asAdmin_returnsAllSections() throws Exception {
        mockMvc.perform(get("/api/reports/owner-summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.financial.totalRevenue").exists())
                .andExpect(jsonPath("$.data.financial.cashBalance").exists())
                .andExpect(jsonPath("$.data.sales.topProducts").isArray())
                .andExpect(jsonPath("$.data.sales.totalReceivables").exists())
                .andExpect(jsonPath("$.data.inventory.totalStockValue").exists())
                .andExpect(jsonPath("$.data.purchasing.totalPayables").exists());
    }

    @Order(5)
    @Test
    void ownerSummary_customDateRange_isRespected() throws Exception {
        mockMvc.perform(get("/api/reports/owner-summary")
                        .param("startDate", "2020-01-01")
                        .param("endDate", "2020-01-31")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2020-01-01"))
                .andExpect(jsonPath("$.data.endDate").value("2020-01-31"));
    }

    @AfterAll
    void cleanup() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(STAFF_EMAIL).ifPresent(userRepository::delete);
    }
}
