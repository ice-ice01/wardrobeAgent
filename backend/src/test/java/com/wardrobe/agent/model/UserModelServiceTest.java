package com.wardrobe.agent.model;

import com.wardrobe.agent.common.BusinessException;
import com.wardrobe.agent.media.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserModelServiceTest {
    @Mock UserModelRepository models;
    @Mock MediaStorageService media;
    @InjectMocks UserModelService service;

    @Test
    void deletingDefaultUploadPromotesEarliestRemainingModel() {
        UserModel deleting = model("model-1", "UPLOAD", true);
        UserModel replacement = model("model-2", "UPLOAD", false);
        when(models.findByIdAndUserIdAndDeletedFalse(deleting.getId(), "user-1")).thenReturn(Optional.of(deleting));
        when(models.findAllByUserIdAndDeletedFalseOrderByCreatedAtAsc("user-1"))
                .thenReturn(List.of(replacement));

        service.delete("user-1", deleting.getId());

        assertThat(deleting.isDeleted()).isTrue();
        assertThat(deleting.isDefaultModel()).isFalse();
        assertThat(replacement.isDefaultModel()).isTrue();
    }

    @Test
    void deletingPresetModelIsRejected() {
        UserModel preset = model("model-1", "PRESET", true);
        when(models.findByIdAndUserIdAndDeletedFalse(preset.getId(), "user-1")).thenReturn(Optional.of(preset));

        assertThatThrownBy(() -> service.delete("user-1", preset.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预设模特不能删除");
        assertThat(preset.isDeleted()).isFalse();
    }

    @Test
    void deletingOnlyDefaultModelLeavesNoDefault() {
        UserModel deleting = model("model-1", "UPLOAD", true);
        when(models.findByIdAndUserIdAndDeletedFalse(deleting.getId(), "user-1")).thenReturn(Optional.of(deleting));
        when(models.findAllByUserIdAndDeletedFalseOrderByCreatedAtAsc("user-1")).thenReturn(List.of());

        service.delete("user-1", deleting.getId());

        assertThat(deleting.isDeleted()).isTrue();
        assertThat(deleting.isDefaultModel()).isFalse();
    }

    private static UserModel model(String id, String type, boolean isDefault) {
        UserModel model = new UserModel("user-1", id, type, null, "/api/files/" + id, true);
        model.setDefaultModel(isDefault);
        return model;
    }
}
