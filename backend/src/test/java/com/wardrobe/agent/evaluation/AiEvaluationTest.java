package com.wardrobe.agent.evaluation;

import com.wardrobe.agent.wardrobe.WardrobeItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiEvaluationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EvaluationRunRepository runs;
    @Autowired EvaluationCaseResultRepository caseResults;
    @Autowired WardrobeItemRepository wardrobeItems;

    @Test
    void mockRegressionDatasetPassesHardAndStreamingAssertions() throws Exception {
        JsonNode dataset;
        try (var input = getClass().getResourceAsStream("/evaluation/wardrobe-agent-eval-v1.json")) {
            dataset = json.readTree(input);
        }
        assertThat(dataset.path("cases")).hasSizeGreaterThanOrEqualTo(30);
        EvaluationRun run = runs.save(new EvaluationRun(dataset.path("datasetVersion").asString(),
                System.getenv().getOrDefault("GITHUB_SHA", "workspace"), "MOCK_REGRESSION", dataset.path("cases").size()));
        String token = login();
        Set<String> legalItemIds = wardrobeItems.findAll().stream().map(item -> item.getId())
                .collect(java.util.stream.Collectors.toSet());
        List<Outcome> outcomes = new ArrayList<>();

        for (JsonNode testCase : dataset.path("cases")) {
            long started = System.nanoTime();
            Outcome outcome;
            try {
                outcome = evaluate(testCase, token, legalItemIds, started);
            } catch (Exception | AssertionError error) {
                outcome = new Outcome(testCase.path("caseId").asString(), false, elapsed(started),
                        safe(error.getMessage()), "evaluation exception");
            }
            outcomes.add(outcome);
            caseResults.save(new EvaluationCaseResult(run.getId(), outcome.caseId(), outcome.passed(), outcome.durationMs(),
                    outcome.assertionSummary(), outcome.outputSummary()));
        }

        long passed = outcomes.stream().filter(Outcome::passed).count();
        Path reportDir = Path.of("target", "evaluation");
        Files.createDirectories(reportDir);
        writeReports(reportDir, run.getId(), dataset.path("datasetVersion").asString(), outcomes);
        run.complete((int) passed, outcomes.size() - (int) passed, reportDir.resolve("evaluation-report.html").toString());
        runs.save(run);

        assertThat(outcomes).filteredOn(outcome -> !outcome.passed())
                .as("AI evaluation failures; see target/evaluation/evaluation-report.html")
                .isEmpty();
    }

    private Outcome evaluate(JsonNode testCase, String token, Set<String> legalItemIds, long started) throws Exception {
        String conversationId = postJson(token, "/api/agent/conversations", Map.of("title", testCase.path("caseId").asString()))
                .path("id").asString();
        List<JsonNode> events = sendMessage(token, conversationId, Map.of(
                "clientMessageId", UUID.randomUUID().toString(), "content", testCase.path("content").asString()));
        assertThat(events).isNotEmpty();
        for (int index = 0; index < events.size(); index++) {
            assertThat(events.get(index).path("sequence").asInt()).isEqualTo(index + 1);
        }
        String streamed = events.stream().filter(event -> "message.delta".equals(event.path("type").asString()))
                .map(event -> event.path("data").path("content").asString()).collect(java.util.stream.Collectors.joining());
        JsonNode completed = events.stream().filter(event -> "message.completed".equals(event.path("type").asString()))
                .findFirst().orElseThrow();
        assertThat(streamed).isEqualTo(completed.path("data").path("content").asString());

        JsonNode outfitEvent = events.stream().filter(event -> "outfit.created".equals(event.path("type").asString()))
                .findFirst().orElse(null);
        boolean expectedOutfit = testCase.path("mustCreateOutfit").asBoolean();
        assertThat(outfitEvent != null).isEqualTo(expectedOutfit);
        String requiredText = testCase.path("requiredText").asString();
        if (!requiredText.isBlank()) assertThat(streamed).contains(requiredText);
        if (outfitEvent != null) assertOutfit(outfitEvent.path("data"), legalItemIds);
        return new Outcome(testCase.path("caseId").asString(), true, elapsed(started),
                "routing, streaming and hard rules passed", expectedOutfit ? "outfit.created" : "chat-only");
    }

    private void assertOutfit(JsonNode outfit, Set<String> legalItemIds) {
        assertThat(outfit.path("selectedItems")).isNotEmpty();
        Set<String> slots = new HashSet<>();
        boolean dress = false;
        boolean separates = false;
        for (JsonNode item : outfit.path("selectedItems")) {
            assertThat(legalItemIds).contains(item.path("itemId").asString());
            String slot = item.path("slot").asString();
            assertThat(slots.add(slot)).as("duplicate slot " + slot).isTrue();
            dress |= "DRESS".equals(slot);
            separates |= Set.of("INNER_TOP", "OUTER_TOP", "BOTTOM").contains(slot);
        }
        assertThat(dress && separates).isFalse();
        assertThat(outfit.path("recommendationRunId").asString()).isNotBlank();
    }

    private List<JsonNode> sendMessage(String token, String conversationId, Object payload) throws Exception {
        MvcResult pending = mvc.perform(post("/api/agent/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload)))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult completed = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn();
        List<JsonNode> events = new ArrayList<>();
        for (String line : completed.getResponse().getContentAsString().lines().filter(line -> !line.isBlank()).toList()) {
            events.add(json.readTree(line));
        }
        return events;
    }

    private String login() throws Exception {
        return postJson(null, "/api/auth/login", Map.of("username", "demo", "password", "wardrobe123"))
                .path("token").asString();
    }

    private JsonNode postJson(String token, String path, Object payload) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload));
        if (token != null) request.header("Authorization", bearer(token));
        return json.readTree(mvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn()
                .getResponse().getContentAsByteArray());
    }

    private void writeReports(Path dir, String runId, String datasetVersion, List<Outcome> outcomes) throws Exception {
        long passed = outcomes.stream().filter(Outcome::passed).count();
        Map<String, Object> summary = Map.of(
                "runId", runId, "datasetVersion", datasetVersion, "runMode", "MOCK_REGRESSION",
                "generatedAt", Instant.now().toString(), "total", outcomes.size(), "passed", passed,
                "failed", outcomes.size() - passed, "cases", outcomes);
        Files.writeString(dir.resolve("evaluation-summary.json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(summary), StandardCharsets.UTF_8);
        StringBuilder rows = new StringBuilder();
        for (Outcome outcome : outcomes) rows.append("<tr><td>").append(html(outcome.caseId())).append("</td><td>")
                .append(outcome.passed() ? "PASSED" : "FAILED").append("</td><td>").append(outcome.durationMs())
                .append(" ms</td><td>").append(html(outcome.assertionSummary())).append("</td></tr>");
        String report = "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>Wardrobe Agent AI Evaluation</title>"
                + "<style>body{font-family:system-ui;margin:32px;color:#18201d}table{border-collapse:collapse;width:100%}th,td{padding:8px;border:1px solid #ccd5d0;text-align:left}th{background:#edf3ef}</style>"
                + "<h1>Wardrobe Agent AI Evaluation</h1><p>Run: " + html(runId) + " · Dataset: " + html(datasetVersion)
                + " · Passed: " + passed + "/" + outcomes.size() + "</p><table><thead><tr><th>Case</th><th>Status</th><th>Duration</th><th>Assertions</th></tr></thead><tbody>"
                + rows + "</tbody></table></html>";
        Files.writeString(dir.resolve("evaluation-report.html"), report, StandardCharsets.UTF_8);
        StringBuilder suites = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"wardrobe-agent-eval\" tests=\"")
                .append(outcomes.size()).append("\" failures=\"").append(outcomes.size() - passed).append("\">");
        for (Outcome outcome : outcomes) {
            suites.append("<testcase classname=\"ai-evaluation\" name=\"").append(xml(outcome.caseId()))
                    .append("\" time=\"").append(outcome.durationMs() / 1000.0).append("\">");
            if (!outcome.passed()) suites.append("<failure message=\"").append(xml(outcome.assertionSummary())).append("\"/>");
            suites.append("</testcase>");
        }
        Files.writeString(dir.resolve("evaluation-junit.xml"), suites.append("</testsuite>").toString(), StandardCharsets.UTF_8);
    }

    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private String safe(String value) { return value == null ? "unknown failure" : value.substring(0, Math.min(900, value.length())); }
    private String html(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private String xml(String value) { return html(value).replace("\"", "&quot;"); }
    private String bearer(String token) { return "Bearer " + token; }

    record Outcome(String caseId, boolean passed, long durationMs, String assertionSummary, String outputSummary) {}
}
