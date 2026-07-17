package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.UserModel;

@Entity
public class User extends BaseEntity {

    public static User fromUserModel(UserModel model) {
        User user = new User();
        user.setJsonData(model.toJson());
        user.setId((model.getId()));
        return user;
    }

    public UserModel toUserModel() {
        UserModel userModel = gson.fromJson(getJsonData(), UserModel.class);
        userModel.setLocalId(getLocalId());
        return userModel;
    }
}
