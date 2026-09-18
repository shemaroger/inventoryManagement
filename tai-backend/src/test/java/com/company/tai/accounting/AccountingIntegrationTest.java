package com.company.tai.accounting;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * End-to-end coverage of the Accounting domain (Chart of Accounts, General Ledger, Cash
 * expenditures) via MockMvc against a real Postgres-backed application context. Uses
 * RANDOM_PORT so it never binds 8080, which the shared dev server owns. All rows created here
 * are cleaned up in @AfterAll.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String TS = String.valueOf(System.currentTimeMillis() / 1000);
    private static String adminToken;
    private static String staffToken;
    private static Long expenseAccountId;
    private static Long journalEntryId;
    private static Long cashJournalEntryId;

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode postJson(String url, String body, String token) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) req.header("Authorization", "Bearer " + token);
        MvcResult result = mockMvc.perform(req).andReturn();
        return responseNode(result);
    }

    private JsonNode getJson(String url, String token) throws Exception {
        var req = get(url);
        if (token != null) req.header("Authorization", "Bearer " + token);
        MvcResult result = mockMvc.perform(req).andReturn();
        return responseNode(result);
    }

    private JsonNode patchJson(String url, String token) throws Exception {
        var req = patch(url);
        if (token != null) req.header("Authorization", "Bearer " + token);
        MvcResult result = mockMvc.perform(req).andReturn();
        return responseNode(result);
    }

    private JsonNode putJson(String url, String body, String token) throws Exception {
        var req = put(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) req.header("Authorization", "Bearer " + token);
        MvcResult result = mockMvc.perform(req).andReturn();
        return responseNode(result);
    }

    private JsonNode responseNode(MvcResult result) throws Exception {
        JsonNode node = mapper.readTree(result.getResponse().getContentAsString().isEmpty()
                ? "{}" : result.getResponse().getContentAsString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("__status", result.getResponse().getStatus());
        return node;
    }

    private int status(JsonNode node) {
        return node.path("__status").asInt();
    }

    @BeforeAll
    void setUpUsers() throws Exception {
        String adminEmail = "acct-e2e-admin-junit-" + TS + "@example.com";
        String staffEmail = "acct-e2e-staff-junit-" + TS + "@example.com";

        String adminReg = String.format(
                "{\"fullName\":\"JUnit Admin\",\"email\":\"%s\",\"password\":\"Password123!\",\"roleName\":\"ADMIN\"}",
                adminEmail);
        JsonNode adminResp = postJson("/api/auth/register", adminReg, null);
        assertEquals(200, status(adminResp));
        long adminUserId = adminResp.path("data").path("userId").asLong();

        String staffReg = String.format(
                "{\"fullName\":\"JUnit Staff\",\"email\":\"%s\",\"password\":\"Password123!\",\"roleName\":\"STAFF\"}",
                staffEmail);
        JsonNode staffResp = postJson("/api/auth/register", staffReg, null);
        assertEquals(200, status(staffResp));
        staffToken = staffResp.path("data").path("accessToken").asText();

        // Public self-registration always grants STAFF regardless of the roleName supplied
        // (intentional — see AuthService.register), so elevate this user to ADMIN directly in
        // the database, the same way an admin would via RoleController, then re-login to get a
        // token whose authorities reflect the new role.
        jdbc.update("delete from user_roles where user_id = ?", adminUserId);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'ADMIN'", adminUserId);

        String loginReq = String.format("{\"email\":\"%s\",\"password\":\"Password123!\"}", adminEmail);
        JsonNode loginResp = postJson("/api/auth/login", loginReq, null);
        assertEquals(200, status(loginResp));
        String challengeToken = loginResp.path("data").path("challengeToken").asText();
        String code = loginResp.path("data").path("debugCode").asText();

        String verifyReq = String.format("{\"challengeToken\":\"%s\",\"code\":\"%s\"}", challengeToken, code);
        JsonNode verifyResp = postJson("/api/auth/verify-otp", verifyReq, null);
        assertEquals(200, status(verifyResp));
        adminToken = verifyResp.path("data").path("accessToken").asText();
    }

    @AfterAll
    void cleanUp() {
        if (cashJournalEntryId != null) {
            jdbc.update("delete from journal_entry_lines where journal_entry_id = ?", cashJournalEntryId);
            jdbc.update("delete from journal_entries where id = ?", cashJournalEntryId);
        }
        if (journalEntryId != null) {
            jdbc.update("delete from journal_entry_lines where journal_entry_id = ?", journalEntryId);
            jdbc.update("delete from journal_entries where id = ?", journalEntryId);
        }
        if (expenseAccountId != null) {
            jdbc.update("delete from accounts where id = ?", expenseAccountId);
        }
        jdbc.update("delete from users where email like ?", "acct-e2e-%-junit-" + TS + "@example.com");
    }

    @Test
    @Order(1)
    void listAccounts_requiresAuth() throws Exception {
        assertEquals(401, status(getJson("/api/accounts", null)));
    }

    @Test
    @Order(2)
    void createAccount_asStaff_isForbidden() throws Exception {
        String req = "{\"code\":\"9" + TS + "\",\"name\":\"e2e-junit-expense\",\"accountType\":\"EXPENSE\"}";
        assertEquals(403, status(postJson("/api/accounts", req, staffToken)));
    }

    @Test
    @Order(3)
    void createAccount_missingFields_isBadRequest() throws Exception {
        assertEquals(400, status(postJson("/api/accounts", "{}", adminToken)));
    }

    @Test
    @Order(4)
    void createAccount_asAdmin_happyPath() throws Exception {
        String req = "{\"code\":\"9" + TS + "\",\"name\":\"e2e-junit-expense\",\"accountType\":\"EXPENSE\"}";
        JsonNode resp = postJson("/api/accounts", req, adminToken);
        assertEquals(200, status(resp));
        JsonNode data = resp.path("data");
        expenseAccountId = data.path("id").asLong();
        assertEquals("EXPENSE", data.path("accountType").asText());
    }

    @Test
    @Order(5)
    void createAccount_duplicateCode_isConflict() throws Exception {
        String req = "{\"code\":\"9" + TS + "\",\"name\":\"dup\",\"accountType\":\"ASSET\"}";
        assertEquals(409, status(postJson("/api/accounts", req, adminToken)));
    }

    @Test
    @Order(6)
    void getLedger_forNewAccount_isEmptyAndZeroBalance() throws Exception {
        JsonNode resp = getJson("/api/accounts/" + expenseAccountId + "/ledger", adminToken);
        assertEquals(200, status(resp));
        JsonNode data = resp.path("data");
        assertEquals(0, data.path("lines").size());
        assertEquals(0, data.path("closingBalance").decimalValue().compareTo(java.math.BigDecimal.ZERO));
    }

    @Test
    @Order(7)
    void getLedger_notFound() throws Exception {
        assertEquals(404, status(getJson("/api/accounts/999999999/ledger", adminToken)));
    }

    @Test
    @Order(8)
    void createJournalEntry_unbalanced_isRejected() throws Exception {
        String req = "{\"entryDate\":\"2026-01-15\",\"description\":\"e2e-junit-unbalanced\",\"lines\":["
                + "{\"accountId\":1,\"debitAmount\":10.00,\"creditAmount\":0},"
                + "{\"accountId\":" + expenseAccountId + ",\"debitAmount\":0,\"creditAmount\":5.00}]}";
        assertEquals(409, status(postJson("/api/journal-entries", req, adminToken)));
    }

    @Test
    @Order(9)
    void createJournalEntry_bothDebitAndCreditOnSameLine_isRejected() throws Exception {
        String req = "{\"entryDate\":\"2026-01-15\",\"description\":\"e2e-junit-both\",\"lines\":["
                + "{\"accountId\":1,\"debitAmount\":10.00,\"creditAmount\":10.00},"
                + "{\"accountId\":" + expenseAccountId + ",\"debitAmount\":0,\"creditAmount\":10.00}]}";
        assertEquals(409, status(postJson("/api/journal-entries", req, adminToken)));
    }

    @Test
    @Order(10)
    void createJournalEntry_negativeAmount_isBadRequest() throws Exception {
        String req = "{\"entryDate\":\"2026-01-15\",\"description\":\"e2e-junit-neg\",\"lines\":["
                + "{\"accountId\":1,\"debitAmount\":-5.00,\"creditAmount\":0},"
                + "{\"accountId\":" + expenseAccountId + ",\"debitAmount\":0,\"creditAmount\":-5.00}]}";
        assertEquals(400, status(postJson("/api/journal-entries", req, adminToken)));
    }

    @Test
    @Order(11)
    void createJournalEntry_onlyOneLine_isBadRequest() throws Exception {
        String req = "{\"entryDate\":\"2026-01-15\",\"description\":\"e2e-junit-oneline\",\"lines\":["
                + "{\"accountId\":1,\"debitAmount\":10.00,\"creditAmount\":0}]}";
        assertEquals(400, status(postJson("/api/journal-entries", req, adminToken)));
    }

    @Test
    @Order(12)
    void createJournalEntry_nullLines_isBadRequest() throws Exception {
        String req = "{\"entryDate\":\"2026-01-15\",\"description\":\"e2e-junit-nulllines\"}";
        assertEquals(400, status(postJson("/api/journal-entries", req, adminToken)));
    }

    @Test
    @Order(13)
    void createJournalEntry_nonexistentAccount_isNotFound() throws Exception {
        String req = "{\"entryDate\":\"2026-01-15\",\"description\":\"e2e-junit-noacct\",\"lines\":["
                + "{\"accountId\":999999999,\"debitAmount\":10.00,\"creditAmount\":0},"
                + "{\"accountId\":1,\"debitAmount\":0,\"creditAmount\":10.00}]}";
        assertEquals(404, status(postJson("/api/journal-entries", req, adminToken)));
    }

    @Test
    @Order(14)
    void createJournalEntry_balancedHappyPath() throws Exception {
        String req = "{\"entryDate\":\"2026-01-15\",\"description\":\"e2e-junit-balanced\",\"lines\":["
                + "{\"accountId\":" + expenseAccountId + ",\"debitAmount\":15.00,\"creditAmount\":0},"
                + "{\"accountId\":1,\"debitAmount\":0,\"creditAmount\":15.00}]}";
        JsonNode resp = postJson("/api/journal-entries", req, adminToken);
        assertEquals(200, status(resp));
        JsonNode data = resp.path("data");
        journalEntryId = data.path("id").asLong();
        assertEquals(0, data.path("totalDebit").decimalValue().compareTo(data.path("totalCredit").decimalValue()));
    }

    @Test
    @Order(15)
    void getJournalEntryById_notFound() throws Exception {
        assertEquals(404, status(getJson("/api/journal-entries/999999999", adminToken)));
    }

    @Test
    @Order(16)
    void deactivateAccount_thenPostingToIt_isRejected() throws Exception {
        JsonNode deactResp = patchJson("/api/accounts/" + expenseAccountId + "/deactivate", adminToken);
        assertEquals(200, status(deactResp));

        String req = "{\"entryDate\":\"2026-01-16\",\"description\":\"e2e-junit-inactive-post\",\"lines\":["
                + "{\"accountId\":" + expenseAccountId + ",\"debitAmount\":5.00,\"creditAmount\":0},"
                + "{\"accountId\":1,\"debitAmount\":0,\"creditAmount\":5.00}]}";
        JsonNode resp = postJson("/api/journal-entries", req, adminToken);
        assertEquals(409, status(resp));
        assertTrue(resp.path("message").asText().toLowerCase().contains("inactive"));
    }

    @Test
    @Order(17)
    void cashExpenditure_onInactiveAccount_isRejected() throws Exception {
        String req = "{\"date\":\"2026-01-16\",\"description\":\"e2e-junit-cash-inactive\",\"expenseAccountId\":"
                + expenseAccountId + ",\"amount\":10.00,\"paymentSource\":\"CASH\"}";
        assertEquals(409, status(postJson("/api/cash/expenditures", req, adminToken)));
    }

    @Test
    @Order(18)
    void cashExpenditure_zeroAmount_isBadRequest() throws Exception {
        String req = "{\"date\":\"2026-01-16\",\"description\":\"e2e-junit-cash-zero\",\"expenseAccountId\":"
                + expenseAccountId + ",\"amount\":0,\"paymentSource\":\"CASH\"}";
        assertEquals(400, status(postJson("/api/cash/expenditures", req, adminToken)));
    }

    @Test
    @Order(19)
    void cashExpenditure_nonExpenseAccount_isRejected() throws Exception {
        String req = "{\"date\":\"2026-01-16\",\"description\":\"e2e-junit-cash-wrongtype\",\"expenseAccountId\":1,"
                + "\"amount\":10.00,\"paymentSource\":\"CASH\"}";
        assertEquals(409, status(postJson("/api/cash/expenditures", req, adminToken)));
    }

    @Test
    @Order(20)
    void cashExpenditure_happyPath() throws Exception {
        String createReq = "{\"code\":\"9" + TS + "b\",\"name\":\"e2e-junit-expense-2\",\"accountType\":\"EXPENSE\"}";
        JsonNode createResp = postJson("/api/accounts", createReq, adminToken);
        assertEquals(200, status(createResp));
        long secondAccountId = createResp.path("data").path("id").asLong();

        String req = "{\"date\":\"2026-01-16\",\"description\":\"e2e-junit-cash-happy\",\"expenseAccountId\":"
                + secondAccountId + ",\"amount\":42.00,\"paymentSource\":\"CASH\"}";
        JsonNode resp = postJson("/api/cash/expenditures", req, adminToken);
        assertEquals(200, status(resp));
        JsonNode data = resp.path("data");
        cashJournalEntryId = data.path("id").asLong();
        assertEquals("EXPENSE", data.path("sourceType").asText());
        assertEquals(0, data.path("totalDebit").decimalValue().compareTo(data.path("totalCredit").decimalValue()));

        // Lines referencing this account must go before both the entry and the account itself.
        jdbc.update("delete from journal_entry_lines where journal_entry_id = ?", cashJournalEntryId);
        jdbc.update("delete from journal_entries where id = ?", cashJournalEntryId);
        cashJournalEntryId = null;
        jdbc.update("delete from accounts where id = ?", secondAccountId);
    }

    @Test
    @Order(21)
    void listCashExpenditures_ok() throws Exception {
        assertEquals(200, status(getJson("/api/cash/expenditures", adminToken)));
    }

    @Test
    @Order(22)
    void closePeriod_asStaff_isForbidden() throws Exception {
        var req = post("/api/journal-entries/close-period?startDate=2020-01-01&endDate=2020-01-31")
                .header("Authorization", "Bearer " + staffToken);
        MvcResult result = mockMvc.perform(req).andReturn();
        assertEquals(403, result.getResponse().getStatus());
    }

    @Test
    @Order(23)
    void updateAccount_notFound_is404() throws Exception {
        String req = "{\"code\":\"999zzz\",\"name\":\"x\",\"accountType\":\"EXPENSE\"}";
        assertEquals(404, status(putJson("/api/accounts/999999999", req, adminToken)));
    }
}
