package com.wardrobe.agent.tryon;

import com.wardrobe.agent.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController
@RequestMapping("/api/tryon")
/** 试穿确认、任务创建、重试、查询和 SSE 状态订阅接口。 */
public class TryOnController {
    private final TryOnService tryOn;
    public TryOnController(TryOnService tryOn) { this.tryOn = tryOn; }
    @GetMapping("/capabilities")
    TryOnService.CapabilitiesView capabilities(@RequestParam String planId, @RequestParam int planVersion) {
        return tryOn.capabilities(CurrentUser.require().id(), planId, planVersion);
    }
    @PostMapping("/confirmations") TryOnService.ConfirmationView confirmation(@Valid @RequestBody TryOnService.ConfirmationRequest request) { return tryOn.confirmation(CurrentUser.require().id(), request); }
    @PostMapping("/tasks") @ResponseStatus(HttpStatus.CREATED)
    TryOnView create(@Valid @RequestBody TryOnService.CreateTaskRequest request) { return tryOn.create(CurrentUser.require().id(), request); }
    @GetMapping("/tasks") List<TryOnView> list() { return tryOn.list(CurrentUser.require().id()); }
    @GetMapping("/tasks/{id}") TryOnView get(@PathVariable String id) { return tryOn.get(CurrentUser.require().id(), id); }
    @GetMapping(value = "/tasks/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable String id,
                      @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        return tryOn.subscribe(CurrentUser.require().id(), id);
    }
    @PostMapping("/tasks/{id}/retry") TryOnView retry(@PathVariable String id, @Valid @RequestBody TryOnService.RetryRequest request) { return tryOn.retry(CurrentUser.require().id(), id, request); }
}
