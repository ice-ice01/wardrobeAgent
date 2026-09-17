package com.wardrobe.agent.model;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/user-models")
/** 管理虚拟试穿使用的用户形象，包括上传、设为默认和删除。 */
public class UserModelController {
    private final UserModelService models;
    public UserModelController(UserModelService models) { this.models = models; }
    @GetMapping List<UserModelView> list() { return models.list(CurrentUser.require().id()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    UserModelView create(@Valid @RequestBody UserModelService.CreateUserModelRequest request) { return models.create(CurrentUser.require().id(), request); }
    @PutMapping("/{id}/default") UserModelView makeDefault(@PathVariable String id) { return models.makeDefault(CurrentUser.require().id(), id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) { models.delete(CurrentUser.require().id(), id); }
}
