package edu.cybertrace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end coverage for the security and evidence workflow, using a disposable SQLite database. */
@SpringBootTest
@AutoConfigureMockMvc
class CyberTraceWorkflowTest {
    private static final Path TEST_ROOT = Path.of(System.getProperty("user.dir"), "target", "cybertrace-test-" + UUID.randomUUID()).toAbsolutePath();
    private static final String SETUP_KEY = "test-only-one-time-setup-key";

    static {
        try { Files.createDirectories(TEST_ROOT.resolve("evidence")); }
        catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }

    @DynamicPropertySource
    static void testSettings(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_ROOT.resolve("test.db"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("cybertrace.storage", () -> TEST_ROOT.resolve("evidence").toString());
        registry.add("CYBERTRACE_SETUP_TOKEN", () -> SETUP_KEY);
        registry.add("server.servlet.session.cookie.secure", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Users users;
    @Autowired EvidenceRepo evidence;
    @Autowired EvidenceVersionRepo versions;
    @Autowired VerificationRepo verifications;
    @Autowired AuditRepo audits;
    @Autowired AlertRepo alerts;
    @Autowired CustodyRepo custody;
    @Autowired CaseRepo cases;

    @Test
    void secureBootstrapEvidenceVersionsCustodyAlertsAndAuditWorkflow() throws Exception {
        Files.createDirectories(TEST_ROOT.resolve("evidence"));

        Csrf csrf = csrf(null);
        postJson("/api/auth/register", "{\"username\":\"intruder\",\"password\":\"temporary-password\"}", null, csrf)
                .andExpect(status().isForbidden());
        postJson("/api/auth/register", "{\"username\":\"chief\",\"password\":\"admin-password-123\",\"setupToken\":\"" + SETUP_KEY + "\"}", null, csrf)
                .andExpect(status().isOk());

        // Once bootstrapped, self-service signup can create only the read-only reviewer role.
        postJson("/api/auth/register", "{\"username\":\"outsider\",\"password\":\"recipient-password\"}", null, csrf(null))
                .andExpect(status().isOk());
        assertEquals(RolePolicy.RECIPIENT, users.findByUsername("outsider").orElseThrow().role);
        assertTrue(settingsBootstrapped());

        postJson("/api/auth/login", "{\"username\":\"chief\",\"password\":\"wrong-password\"}", null, csrf(null))
                .andExpect(status().isUnauthorized());
        assertTrue(audits.findAllByOrderByIdAsc().stream().anyMatch(x -> "FAILED".equals(x.outcome)));

        MockHttpSession admin = login("chief", "admin-password-123");
        createAccount(admin, "officer1", "officer-password", RolePolicy.INVESTIGATOR);
        createAccount(admin, "officer2", "officer-password", RolePolicy.INVESTIGATOR);
        createAccount(admin, "court1", "court-password", RolePolicy.COURT);
        createAccount(admin, "auditor1", "auditor-password", RolePolicy.AUDITOR);
        createAccount(admin, "lawyer1", "lawyer-password", RolePolicy.LAWYER);

        MvcResult caseResponse = postJson("/api/cases", "{\"caseNo\":\"CASE-001\",\"title\":\"Incident review\",\"assignedTo\":\"officer1\",\"hearingDate\":\"2030-02-03\"}", admin, csrf(admin))
                .andExpect(status().isOk()).andReturn();
        JsonNode caseResult = json.readTree(caseResponse.getResponse().getContentAsString());
        long caseId = caseResult.get("id").asLong();
        postJson("/api/cases/" + caseId + "/share", "{\"username\":\"court1\"}", admin, csrf(admin)).andExpect(status().isOk());

        MockHttpSession lawyer = login("lawyer1", "lawyer-password");
        MockMultipartFile deniedUpload = new MockMultipartFile("file", "denied.txt", "text/plain", "not authorized".getBytes());
        Csrf deniedCsrf = csrf(lawyer);
        mvc.perform(multipart("/api/evidence").file(deniedUpload).param("category", "Document").param("caseNo", "CASE-001")
                        .param("reason", "This case has not been shared").session(lawyer).cookie(deniedCsrf.cookie).header("X-XSRF-TOKEN", deniedCsrf.token))
                .andExpect(status().isForbidden());
        assertEquals(0, evidence.count(), "An unauthorized case submission must not create an evidence row");
        postJson("/api/cases/" + caseId + "/share", "{\"username\":\"lawyer1\"}", admin, csrf(admin)).andExpect(status().isOk());

        MockMultipartFile original = new MockMultipartFile("file", "evidence.txt", "text/plain", "original signed exhibit".getBytes());
        Csrf uploadCsrf = csrf(lawyer);
        MvcResult upload = mvc.perform(multipart("/api/evidence").file(original).param("category", "Document")
                        .param("caseNo", "CASE-001").param("reason", "Collected during review")
                        .session(lawyer).cookie(uploadCsrf.cookie).header("X-XSRF-TOKEN", uploadCsrf.token))
                .andExpect(status().isOk()).andReturn();
        Evidence item = evidence.findById(json.readTree(upload.getResponse().getContentAsString()).get("id").asLong()).orElseThrow();
        String originalHash = item.referenceHash;
        assertEquals(originalHash, ForensicsService.hash(TEST_ROOT.resolve("evidence").resolve(item.storageName)));
        assertEquals(1, versions.findByEvidenceIdOrderByVersionNumberDesc(item.id).size());

        postJson("/api/evidence/" + item.id + "/verify", "{}", admin, csrf(admin)).andExpect(status().isOk());
        assertEquals("INTACT", verifications.findAllByOrderByIdDesc().get(0).status);

        MockMultipartFile corrected = new MockMultipartFile("file", "corrected.txt", "text/plain", "approved corrected exhibit".getBytes());
        Csrf versionCsrf = csrf(lawyer);
        mvc.perform(multipart("/api/evidence/" + item.id + "/versions").file(corrected).param("note", "Corrected exhibit label")
                        .session(lawyer).cookie(versionCsrf.cookie).header("X-XSRF-TOKEN", versionCsrf.token))
                .andExpect(status().isOk());
        Evidence afterVersion = evidence.findById(item.id).orElseThrow();
        assertEquals(originalHash, afterVersion.referenceHash, "Corrected versions must never replace the original reference hash");
        assertEquals(2, versions.findByEvidenceIdOrderByVersionNumberDesc(item.id).size());
        assertNotEquals(versions.findByEvidenceIdOrderByVersionNumberDesc(item.id).get(0).sha256,
                versions.findByEvidenceIdOrderByVersionNumberDesc(item.id).get(1).sha256);

        Path activeFile = TEST_ROOT.resolve("evidence").resolve(afterVersion.storageName);
        Files.writeString(activeFile, "unauthorized byte change");
        postJson("/api/evidence/" + item.id + "/verify", "{}", admin, csrf(admin)).andExpect(status().isOk());
        Evidence afterTamper = evidence.findById(item.id).orElseThrow();
        assertEquals(originalHash, afterTamper.referenceHash);
        assertEquals("MODIFIED", afterTamper.lastIntegrityStatus);
        assertNotEquals(originalHash, verifications.findAllByOrderByIdDesc().get(0).observedHash);

        MockHttpSession court = login("court1", "court-password");
        Csrf deleteCsrf = csrf(court);
        mvc.perform(delete("/api/evidence/" + item.id).session(court).cookie(deleteCsrf.cookie).header("X-XSRF-TOKEN", deleteCsrf.token))
                .andExpect(status().isForbidden());
        assertTrue(Files.exists(activeFile), "A blocked delete must preserve evidence bytes");
        assertTrue(alerts.findAllByOrderByIdDesc().stream().anyMatch(a -> item.id.equals(a.evidenceId) && "lawyer1".equals(a.owner)));
        assertEquals(0, json.readTree(mvc.perform(get("/api/alerts").session(court)).andReturn().getResponse().getContentAsString()).size(),
                "An unrelated judicial reviewer must not see private alerts owned by the submitter");

        postJson("/api/cases/" + caseId + "/notes", "{\"body\":\"Received for judicial review\"}", court, csrf(court))
                .andExpect(status().isOk());
        assertEquals("JUDICIAL_REVIEW", casesNoteType(caseId, court));

        MockHttpSession officer = login("officer1", "officer-password");
        postJson("/api/custody", "{\"evidenceId\":\"" + item.id + "\",\"recipient\":\"officer2\",\"reason\":\"Second officer review\",\"notes\":\"Sealed handover\"}", officer, csrf(officer))
                .andExpect(status().isOk());
        Custody transfer = custody.findAll().get(0);
        assertEquals("REQUESTED", transfer.status);
        assertEquals("lawyer1", evidence.findById(item.id).orElseThrow().custodian, "Custodian stays unchanged until acceptance");
        MockHttpSession recipient = login("officer2", "officer-password");
        postJson("/api/custody/" + transfer.id + "/respond", "{\"status\":\"ACCEPTED\"}", recipient, csrf(recipient))
                .andExpect(status().isOk());
        assertEquals("officer2", evidence.findById(item.id).orElseThrow().custodian);
        assertEquals("ACCEPTED", custody.findById(transfer.id).orElseThrow().status);

        MockHttpSession auditor = login("auditor1", "auditor-password");
        JsonNode evidenceForAuditor = json.readTree(mvc.perform(get("/api/evidence").session(auditor)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(0, evidenceForAuditor.size(), "Auditors see only evidence in cases explicitly assigned or shared with them");

        JsonNode timeline = json.readTree(mvc.perform(get("/api/evidence/" + item.id + "/timeline").session(court)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(timeline.size() >= 5);
        assertTrue(timeline.get(0).has("actorRole"));

        assertEquals(true, json.readTree(mvc.perform(get("/api/audit/verify").session(admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("valid").asBoolean());
        AuditEntry first = audits.findAllByOrderByIdAsc().get(0);
        first.details = "altered audit history";
        audits.saveAndFlush(first);
        JsonNode broken = json.readTree(mvc.perform(get("/api/audit/verify").session(admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertFalse(broken.get("valid").asBoolean());
        assertEquals(first.id, broken.get("brokenAt").asLong());
    }

    private boolean settingsBootstrapped() throws Exception {
        return users.existsByRole(RolePolicy.ADMIN);
    }

    private String casesNoteType(long caseId, MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(get("/api/cases/" + caseId + "/notes").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get(0).get("noteType").asText();
    }

    private void createAccount(MockHttpSession admin, String username, String password, String role) throws Exception {
        postJson("/api/users", "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"role\":\"" + role + "\"}", admin, csrf(admin))
                .andExpect(status().isOk());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = postJson("/api/auth/login", "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", null, csrf(null))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String body,
                                                                        MockHttpSession session, Csrf csrf) throws Exception {
        MockHttpServletRequestBuilder request = post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                .cookie(csrf.cookie).header("X-XSRF-TOKEN", csrf.token);
        if (session != null) request.session(session);
        return mvc.perform(request);
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode value = json.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie, "The server must issue a CSRF cookie for browser sessions");
        return new Csrf(value.get("token").asText(), cookie);
    }

    private record Csrf(String token, Cookie cookie) {}
}
