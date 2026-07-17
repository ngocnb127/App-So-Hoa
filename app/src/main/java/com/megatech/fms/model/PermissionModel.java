package com.megatech.fms.model;

public class PermissionModel {
    private int userId;
    private String userName;
    private boolean allowNewRefuel;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public boolean isAllowNewRefuel() {
        return allowNewRefuel;
    }

    public void setAllowNewRefuel(boolean allowNewRefuel) {
        this.allowNewRefuel = allowNewRefuel;
    }
}
