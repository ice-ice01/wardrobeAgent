package com.wardrobe.agent.agent;

import com.wardrobe.agent.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent/runs")
public class AgentRunController {
    private final AgentRunService runs;
    public AgentRunController(AgentRunService runs) { this.runs = runs; }

    @GetMapping("/{id}")
    AgentRunView get(@PathVariable String id) { return runs.get(CurrentUser.require().id(), id); }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable String id,
                      @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        return runs.subscribe(CurrentUser.require().id(), id, lastEventId);
    }
}
