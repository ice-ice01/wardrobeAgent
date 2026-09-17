package com.wardrobe.agent.wardrobe;

import com.wardrobe.agent.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wardrobe/items")
/** 衣橱 CRUD 的 REST 入口；每次调用都从 SecurityContext 获取当前用户以隔离数据。 */
public class WardrobeController {
    private final WardrobeService wardrobe;
    public WardrobeController(WardrobeService wardrobe) { this.wardrobe = wardrobe; }

    @GetMapping
    List<WardrobeItemView> list(@RequestParam(required = false) String category,
                                @RequestParam(required = false) String season,
                                @RequestParam(required = false) String style) {
        return wardrobe.list(CurrentUser.require().id(), category, season, style);
    }

    @GetMapping("/{id}")
    WardrobeItemView get(@PathVariable String id) { return wardrobe.get(CurrentUser.require().id(), id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WardrobeItemView create(@Valid @RequestBody WardrobeItemCommand command) {
        return wardrobe.create(CurrentUser.require().id(), command);
    }

    @PutMapping("/{id}")
    WardrobeItemView update(@PathVariable String id, @Valid @RequestBody WardrobeItemCommand command) {
        return wardrobe.update(CurrentUser.require().id(), id, command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) { wardrobe.delete(CurrentUser.require().id(), id); }
}
