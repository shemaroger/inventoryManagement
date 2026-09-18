package com.company.tai.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.company.tai.user.entity.Role;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.RoleRepository;
import com.company.tai.user.repository.UserRepository;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the read-heavy Analytics / Reporting / AI / Operational-Intelligence
 * domain (AnalyticsController, DashboardController, ReportController, AnomalyController,
 * ReorderSuggestionController, AiController), run against the real dev Postgres database on a
 * random port (MockMvc over the loaded application context) so it never collides with the
 * already-running dev server on :8080 — same pattern as InventoryIntegrationTest.
 *
 * All rows this test creates (users, one AI query example) are cleaned up in @AfterAll;
 * everything else exercised here reads existing seeded data or triggers idempotent-safe
 * recompute jobs (anomaly detection, reorder evaluation) which are safe to run repeatedly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalyticsReportingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private static final String TS = String.valueOf(System.currentTimeMillis() / 1000);
    private String adminToken;
    private String staffToken;
    private Long queryExampleId;

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult doGet(String path, String token) throws Exception {
        var req = get(path);
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mockMvc.perform(req).andReturn();
    }

    private int sc(MvcResult r) {
        return r.getResponse().getStatus();
    }

    @BeforeAll
    void setUpUsers() throws Exception {
        String adminEmail = "analytics-e2e-admin-junit-" + TS + "@example.com";
        String staffEmail = "analytics-e2e-staff-junit-" + TS + "@example.com";

        // Public self-registration always grants STAFF regardless of any roleName supplied
        // (AuthService#register ignores it deliberately for security) — so to get an ADMIN
        // token we register normally, then promote the row directly via the repositories,
        // same pattern InventoryIntegrationTest / SalesDomainIntegrationTest use.
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"JUnit Analytics Admin\",\"email\":\"" + adminEmail
                                + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk());
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Set<Role> roles = new java.util.HashSet<>();
        roles.add(adminRole);
        admin.setRoles(roles);
        userRepository.save(admin);
        var adminLogin = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + adminEmail + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk()).andReturn();
        var adminChallenge = body(adminLogin);
        String adminChallengeToken = adminChallenge.path("data").path("challengeToken").asText();
        String adminCode = adminChallenge.path("data").path("debugCode").asText();
        var adminVerify = mockMvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + adminChallengeToken + "\",\"code\":\"" + adminCode + "\"}"))
                .andExpect(status().isOk()).andReturn();
        adminToken = body(adminVerify).path("data").path("accessToken").asText();

        var staffReg = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"JUnit Analytics Staff\",\"email\":\"" + staffEmail
                                + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk()).andReturn();
        staffToken = body(staffReg).path("data").path("accessToken").asText();
    }

    @AfterAll
    void cleanUp() {
        if (queryExampleId != null) {
            jdbc.update("delete from ai_query_examples where id = ?", queryExampleId);
        }
        jdbc.update("delete from users where email like ?", "analytics-e2e-%-junit-" + TS + "@example.com");
    }

    // ---------- AnalyticsController ----------

    @Test
    @Order(1)
    void salesAnalysis_happyPath_returnsPoints() throws Exception {
        var r = doGet("/api/analytics/sales?startDate=2020-01-01&endDate=2030-12-31&groupBy=day", adminToken);
        assertEquals(200, sc(r));
        assertTrue(body(r).path("data").path("points").isArray());
    }

    @Test
    @Order(2)
    void salesAnalysis_emptyRange_returnsZeroGracefully() throws Exception {
        var r = doGet("/api/analytics/sales?startDate=2031-01-01&endDate=2031-01-31", adminToken);
        assertEquals(200, sc(r));
        JsonNode data = body(r).path("data");
        assertEquals(0, data.path("points").size());
        assertEquals(0, data.path("grandTotal").asDouble());
    }

    @Test
    @Order(3)
    void salesAnalysis_endBeforeStart_returnsEmptyNot500() throws Exception {
        var r = doGet("/api/analytics/sales?startDate=2026-09-17&endDate=2026-01-01", adminToken);
        assertEquals(200, sc(r));
        assertEquals(0, body(r).path("data").path("points").size());
    }

    @Test
    @Order(4)
    void salesAnalysis_badDateFormat_is400() throws Exception {
        assertEquals(400, sc(doGet("/api/analytics/sales?startDate=notadate&endDate=2026-09-17", adminToken)));
    }

    @Test
    @Order(5)
    void salesAnalysis_noAuth_is401() throws Exception {
        assertEquals(401, sc(doGet("/api/analytics/sales?startDate=2026-01-01&endDate=2026-09-17", null)));
    }

    @Test
    @Order(6)
    void salesAnalysis_staffRole_is403() throws Exception {
        assertEquals(403, sc(doGet("/api/analytics/sales?startDate=2026-01-01&endDate=2026-09-17", staffToken)));
    }

    @Test
    @Order(7)
    void profitByProduct_emptyRange_isGraceful() throws Exception {
        var r = doGet("/api/analytics/profit-by-product?startDate=2031-01-01&endDate=2031-01-31", adminToken);
        assertEquals(200, sc(r));
        assertEquals(0, body(r).path("data").path("lines").size());
    }

    @Test
    @Order(8)
    void inventoryTurnover_emptyRange_isGraceful() throws Exception {
        var r = doGet("/api/analytics/inventory-turnover?startDate=2031-01-01&endDate=2031-01-31", adminToken);
        assertEquals(200, sc(r));
        assertEquals(0, body(r).path("data").path("lines").size());
    }

    @Test
    @Order(9)
    void customerPerformance_and_supplierPerformance_happyPath() throws Exception {
        assertEquals(200, sc(doGet("/api/analytics/customer-performance?startDate=2020-01-01&endDate=2030-12-31", adminToken)));
        assertEquals(200, sc(doGet("/api/analytics/supplier-performance?startDate=2020-01-01&endDate=2030-12-31", adminToken)));
    }

    @Test
    @Order(10)
    void salesExport_isCsvAndHandlesEmptyRange() throws Exception {
        var r = doGet("/api/analytics/sales/export?startDate=2031-01-01&endDate=2031-01-31", adminToken);
        assertEquals(200, sc(r));
        assertTrue(r.getResponse().getContentAsString().startsWith("Period,Sales Count,Total"));
    }

    // ---------- DashboardController ----------

    @Test
    @Order(20)
    void dashboardSummary_admin_seesFinancials() throws Exception {
        var r = doGet("/api/dashboard/summary", adminToken);
        assertEquals(200, sc(r));
        assertFalse(body(r).path("data").path("monthSalesTotal").isNull());
    }

    @Test
    @Order(21)
    void dashboardSummary_staff_financialsNulled() throws Exception {
        var r = doGet("/api/dashboard/summary", staffToken);
        assertEquals(200, sc(r));
        assertTrue(body(r).path("data").path("monthSalesTotal").isNull());
    }

    @Test
    @Order(22)
    void dashboardSummary_noAuth_is401() throws Exception {
        assertEquals(401, sc(doGet("/api/dashboard/summary", null)));
    }

    @Test
    @Order(23)
    void salesTrend_staffForbidden_adminOk() throws Exception {
        assertEquals(403, sc(doGet("/api/dashboard/sales-trend?days=7", staffToken)));
        var r = doGet("/api/dashboard/sales-trend?days=7", adminToken);
        assertEquals(200, sc(r));
        assertEquals(7, body(r).path("data").path("points").size());
    }

    @Test
    @Order(24)
    void salesTrend_negativeDays_doesNot500() throws Exception {
        var r = doGet("/api/dashboard/sales-trend?days=-5", adminToken);
        assertEquals(200, sc(r));
        assertEquals(0, body(r).path("data").path("points").size());
    }

    @Test
    @Order(25)
    void topProducts_and_slowMoving_and_recentActivity_openToAllRoles() throws Exception {
        assertEquals(200, sc(doGet("/api/dashboard/top-products?limit=5&period=30", staffToken)));
        assertEquals(200, sc(doGet("/api/dashboard/slow-moving?limit=5&period=30", staffToken)));
        assertEquals(200, sc(doGet("/api/dashboard/recent-activity?limit=5", staffToken)));
    }

    @Test
    @Order(26)
    void topProducts_zeroLimit_returnsEmptyList() throws Exception {
        var r = doGet("/api/dashboard/top-products?limit=0", staffToken);
        assertEquals(200, sc(r));
        assertEquals(0, body(r).path("data").size());
    }

    // ---------- ReportController ----------

    @Test
    @Order(30)
    void dailySales_defaultsToToday_andHandlesExplicitDate() throws Exception {
        assertEquals(200, sc(doGet("/api/reports/daily-sales", adminToken)));
        assertEquals(200, sc(doGet("/api/reports/daily-sales?date=2026-09-16", adminToken)));
    }

    @Test
    @Order(31)
    void dailySales_badDate_is400() throws Exception {
        assertEquals(400, sc(doGet("/api/reports/daily-sales?date=bogus", adminToken)));
    }

    @Test
    @Order(32)
    void dailySalesRange_endBeforeStart_isGraceful() throws Exception {
        var r = doGet("/api/reports/daily-sales/range?startDate=2026-09-17&endDate=2026-01-01", adminToken);
        assertEquals(200, sc(r));
        assertEquals(0, body(r).path("data").path("cashSales").size());
    }

    @Test
    @Order(33)
    void vatReport_and_profitLoss_and_balanceSheet_defaultAndEmptyRange() throws Exception {
        assertEquals(200, sc(doGet("/api/reports/vat", adminToken)));
        assertEquals(200, sc(doGet("/api/reports/vat?startDate=2031-01-01&endDate=2031-01-31", adminToken)));
        assertEquals(200, sc(doGet("/api/reports/profit-loss", adminToken)));
        assertEquals(200, sc(doGet("/api/reports/balance-sheet?asOfDate=2000-01-01", adminToken)));
    }

    @Test
    @Order(34)
    void reports_staffForbidden_noAuthUnauthorized() throws Exception {
        assertEquals(403, sc(doGet("/api/reports/vat", staffToken)));
        assertEquals(401, sc(doGet("/api/reports/vat", null)));
    }

    // ---------- AnomalyController ----------

    @Test
    @Order(40)
    void anomalies_search_adminOnly() throws Exception {
        assertEquals(200, sc(doGet("/api/anomalies?page=0&size=5", adminToken)));
        assertEquals(403, sc(doGet("/api/anomalies", staffToken)));
        assertEquals(401, sc(doGet("/api/anomalies", null)));
    }

    @Test
    @Order(41)
    void anomalies_invalidStatusEnum_is400() throws Exception {
        assertEquals(400, sc(doGet("/api/anomalies?status=NOT_A_STATUS", adminToken)));
    }

    @Test
    @Order(42)
    void anomalies_negativePage_is400NotServerError() throws Exception {
        // Regression test: GlobalExceptionHandler now maps IllegalArgumentException (e.g. from
        // PageRequest.of with a negative page index) to 400 instead of leaking a 500.
        assertEquals(400, sc(doGet("/api/anomalies?page=-1", adminToken)));
    }

    @Test
    @Order(43)
    void anomalies_reviewNonexistentId_isBadRequestOrNotFound() throws Exception {
        var r = mockMvc.perform(patch("/api/anomalies/999999/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\",\"reviewNotes\":\"junit\"}"))
                .andReturn();
        int sc = sc(r);
        assertTrue(sc == 404 || sc == 400, "expected 404 or 400 but was " + sc);
    }

    @Test
    @Order(44)
    void anomalies_detectRun_isIdempotentSafe() throws Exception {
        var r = mockMvc.perform(post("/api/anomalies/detect").header("Authorization", "Bearer " + adminToken)).andReturn();
        assertEquals(200, sc(r));
    }

    // ---------- ReorderSuggestionController ----------

    @Test
    @Order(50)
    void reorderSuggestions_search_openToStaff() throws Exception {
        assertEquals(200, sc(doGet("/api/reorder-suggestions?page=0&size=5", staffToken)));
        assertEquals(401, sc(doGet("/api/reorder-suggestions", null)));
    }

    @Test
    @Order(51)
    void reorderSuggestions_negativePage_is400NotServerError() throws Exception {
        assertEquals(400, sc(doGet("/api/reorder-suggestions?page=-1", staffToken)));
    }

    @Test
    @Order(52)
    void reorderSuggestions_summary_isAccessible() throws Exception {
        assertEquals(200, sc(doGet("/api/reorder-suggestions/summary", staffToken)));
    }

    @Test
    @Order(53)
    void reorderSuggestions_dismissNonexistentId_is404() throws Exception {
        var r = mockMvc.perform(patch("/api/reorder-suggestions/999999/dismiss")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        assertEquals(404, sc(r));
    }

    @Test
    @Order(54)
    void reorderSuggestions_refresh_staffForbidden_adminOk() throws Exception {
        var forbidden = mockMvc.perform(post("/api/reorder-suggestions/refresh")
                        .header("Authorization", "Bearer " + staffToken)).andReturn();
        assertEquals(403, sc(forbidden));

        var ok = mockMvc.perform(post("/api/reorder-suggestions/refresh")
                        .header("Authorization", "Bearer " + adminToken)).andReturn();
        assertEquals(200, sc(ok));
    }

    // ---------- AiController ----------

    @Test
    @Order(60)
    void aiQuery_happyPath_and_gibberish_and_blank() throws Exception {
        var happy = mockMvc.perform(post("/api/ai/query").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"show low stock items\"}"))
                .andReturn();
        assertEquals(200, sc(happy));

        var gibberish = mockMvc.perform(post("/api/ai/query").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"zzz qpwoe asdkfj nomatch12345\"}"))
                .andReturn();
        assertEquals(200, sc(gibberish));

        var blank = mockMvc.perform(post("/api/ai/query").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}"))
                .andReturn();
        assertEquals(400, sc(blank));
    }

    @Test
    @Order(61)
    void aiQuery_noAuth_is401() throws Exception {
        var r = mockMvc.perform(post("/api/ai/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hello\"}"))
                .andReturn();
        assertEquals(401, sc(r));
    }

    @Test
    @Order(62)
    void forecast_happyPath_nonexistentProduct_invalidId() throws Exception {
        assertEquals(200, sc(doGet("/api/ai/forecast/1", staffToken)));
        assertEquals(404, sc(doGet("/api/ai/forecast/999999", staffToken)));
        assertEquals(400, sc(doGet("/api/ai/forecast/abc", staffToken)));
    }

    @Test
    @Order(63)
    void seasonalDemand_missingProductId_and_invalidPeriod() throws Exception {
        assertEquals(400, sc(doGet("/api/ai/seasonal-demand", staffToken)));
        assertEquals(404, sc(doGet("/api/ai/seasonal-demand?productId=999999", staffToken)));
        assertEquals(409, sc(doGet("/api/ai/seasonal-demand?productId=1&period=fortnight", staffToken)));
    }

    @Test
    @Order(64)
    void seasonalDemandPredict_outOfRangeTargetMonth_isRejectedNot500() throws Exception {
        // Regression test for the fix in SeasonalDemandService#resolveTargetBucket: an
        // out-of-range targetMonth used to reach Month.of() unguarded and blow up with an
        // uncaught DateTimeException (mapped to 500). It must now be rejected as a client error.
        int sc1 = sc(doGet("/api/ai/seasonal-demand/predict?productId=1&period=month&targetMonth=13", staffToken));
        assertTrue(sc1 >= 400 && sc1 < 500, "expected a 4xx client error but was " + sc1);

        int sc2 = sc(doGet("/api/ai/seasonal-demand/predict?productId=1&period=month&targetMonth=0", staffToken));
        assertTrue(sc2 >= 400 && sc2 < 500, "expected a 4xx client error but was " + sc2);

        int sc3 = sc(doGet("/api/ai/seasonal-demand/predict?productId=1&period=quarter&targetQuarter=5", staffToken));
        assertTrue(sc3 >= 400 && sc3 < 500, "expected a 4xx client error but was " + sc3);
    }

    @Test
    @Order(65)
    void seasonalDemandPredict_validTargetMonth_isOk() throws Exception {
        assertEquals(200, sc(doGet("/api/ai/seasonal-demand/predict?productId=1&period=month&targetMonth=9", staffToken)));
    }

    @Test
    @Order(66)
    void unifiedForecast_badHorizon_and_atRisk() throws Exception {
        assertEquals(409, sc(doGet("/api/ai/forecast?productId=1&horizon=bogus", staffToken)));
        assertEquals(200, sc(doGet("/api/ai/forecast?productId=1&horizon=week", staffToken)));
        assertEquals(200, sc(doGet("/api/ai/forecast/at-risk", staffToken)));
        assertEquals(200, sc(doGet("/api/ai/forecast/at-risk?warehouseId=999999", staffToken)));
    }

    @Test
    @Order(67)
    void queryExamples_managerCanListAndAdd_staffForbidden() throws Exception {
        assertEquals(403, sc(doGet("/api/ai/query-examples", staffToken)));
        assertEquals(200, sc(doGet("/api/ai/query-examples", adminToken)));

        var added = mockMvc.perform(post("/api/ai/query-examples").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionText\":\"analytics-e2e-junit-question-" + TS + "\",\"intent\":\"PRODUCT_SEARCH\"}"))
                .andReturn();
        assertEquals(200, sc(added));
        queryExampleId = body(added).path("data").path("id").asLong();

        var badReq = mockMvc.perform(post("/api/ai/query-examples").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn();
        assertEquals(400, sc(badReq));
    }
}
