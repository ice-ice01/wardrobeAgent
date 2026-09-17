package com.wardrobe.agent.outfit;

import com.wardrobe.agent.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/outfits")
/** 搭配方案查询、保存和确认接口。确认后的完整方案才允许进入试穿流程。 */
public class OutfitController {
    private final OutfitService outfits;
    public OutfitController(OutfitService outfits) { this.outfits = outfits; }
    @GetMapping List<OutfitView> list() { return outfits.list(CurrentUser.require().id()); }
    @GetMapping("/{id}") OutfitView get(@PathVariable String id) { return outfits.get(CurrentUser.require().id(), id); }
    @PostMapping("/{id}/save") OutfitView save(@PathVariable String id) { return outfits.save(CurrentUser.require().id(), id); }
    @PostMapping("/{id}/confirm") OutfitView confirm(@PathVariable String id) { return outfits.confirm(CurrentUser.require().id(), id); }
}
