package com.wardrobe.agent.model;

import com.wardrobe.agent.common.BusinessException;
import com.wardrobe.agent.media.MediaStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
/** 用户形象应用服务；上传人像前强制校验授权确认和文件所有权。 */
public class UserModelService {
    private final UserModelRepository models;
    private final MediaStorageService media;
    public UserModelService(UserModelRepository models, MediaStorageService media) { this.models = models; this.media = media; }

    @Transactional(readOnly = true)
    public List<UserModelView> list(String userId) { return models.findAllByUserIdAndDeletedFalseOrderByCreatedAtAsc(userId).stream().map(UserModelView::from).toList(); }

    @Transactional
    /** 创建上传形象；用户第一张有效形象会自动成为默认项。 */
    public UserModelView create(String userId, CreateUserModelRequest request) {
        if (!request.authorized()) throw new BusinessException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED", "上传人像前需要确认使用授权");
        media.requireOwned(request.fileId(), userId);
        UserModel model = models.save(new UserModel(userId, request.name().trim(), "UPLOAD", request.fileId(), "/api/files/" + request.fileId(), true));
        if (models.findAllByUserIdAndDeletedFalseOrderByCreatedAtAsc(userId).size() == 1) model.setDefaultModel(true);
        return UserModelView.from(model);
    }

    @Transactional
    /** 在同一事务中取消旧默认项并设置新的唯一默认形象。 */
    public UserModelView makeDefault(String userId, String id) {
        UserModel selected = require(userId, id);
        for (UserModel model : models.findAllByUserIdAndDeletedFalseOrderByCreatedAtAsc(userId)) model.setDefaultModel(model.getId().equals(id));
        return UserModelView.from(selected);
    }

    @Transactional
    public void delete(String userId, String id) {
        UserModel model = require(userId, id);
        if ("PRESET".equals(model.getType())) throw new BusinessException(HttpStatus.CONFLICT, "PRESET_MODEL", "预设模特不能删除");
        model.markDeleted();
    }

    public UserModel require(String userId, String id) {
        return models.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "USER_MODEL_NOT_FOUND", "用户形象不存在"));
    }

    public record CreateUserModelRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String name,
            @jakarta.validation.constraints.NotBlank String fileId,
            boolean authorized
    ) {}
}
