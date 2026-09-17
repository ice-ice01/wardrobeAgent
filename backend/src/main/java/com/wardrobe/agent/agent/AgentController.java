package com.wardrobe.agent.agent;

import tools.jackson.databind.ObjectMapper;
import com.wardrobe.agent.security.CurrentUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/agent/conversations")
/** 会话 HTTP 入口；消息接口使用 StreamingResponseBody 输出换行分隔 JSON（NDJSON）。 */
public class AgentController {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final AgentService agent;
    private final AgentRunService runs;
    private final ObjectMapper json;
    public AgentController(AgentService agent, AgentRunService runs, ObjectMapper json) {
        this.agent = agent; this.runs = runs; this.json = json;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ConversationView create(@RequestBody(required = false) CreateConversationRequest request) {
        return agent.createConversation(CurrentUser.require().id(), request == null ? null : request.title());
    }
    @GetMapping List<ConversationView> list() { return agent.list(CurrentUser.require().id()); }
    @GetMapping("/{id}") ConversationView get(@PathVariable String id) { return agent.get(CurrentUser.require().id(), id); }
    @GetMapping("/{id}/runs") List<AgentRunView> runs(@PathVariable String id) {
        return runs.list(CurrentUser.require().id(), id);
    }

    // 前端创建持久化任务
    @PostMapping("/{id}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AgentRunView run(@PathVariable String id, @Valid @RequestBody AgentMessageRequest request) {
        return runs.create(CurrentUser.require().id(), id, request);
    }


//    这个是旧接口，之前返回ndjson给前端的，先不管了，
    @PostMapping(value = "/{id}/messages", produces = "application/x-ndjson")
    /** 把每个 AgentEvent 写成一行 JSON 并立即刷新，使前端可以逐步处理状态和结果。 */
    ResponseEntity<StreamingResponseBody> message(@PathVariable String id, @Valid @RequestBody AgentMessageRequest request) {
        String userId = CurrentUser.require().id();
        StreamingResponseBody body = stream -> agent.process(userId, id, request, event -> {
            try {
                stream.write(json.writeValueAsBytes(event));
                // NDJSON 用换行分隔完整 JSON 对象，前端可按行增量解析。
                stream.write('\n');
                stream.flush();
            } catch (Exception exception) { throw new StreamWriteException(exception); }
        });

        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-ndjson;charset=UTF-8")).body(body);
    }

    public record CreateConversationRequest(String title) {}
    private static class StreamWriteException extends RuntimeException { StreamWriteException(Throwable cause) { super(cause); } }
}
