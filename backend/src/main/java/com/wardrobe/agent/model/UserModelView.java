package com.wardrobe.agent.model;

/** 前端可见的用户形象 DTO。 */
public record UserModelView(String id, String name, String type, String imageUrl, boolean defaultModel, boolean authorized) {
    static UserModelView from(UserModel model) {
        return new UserModelView(model.getId(), model.getName(), model.getType(), model.getImageUrl(), model.isDefaultModel(), model.isAuthorized());
    }
}
