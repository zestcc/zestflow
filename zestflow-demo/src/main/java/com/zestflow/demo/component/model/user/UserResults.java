package com.zestflow.demo.component.model.user;

/** 用户域元件返回值。 */
public final class UserResults {
    private UserResults() {}

    public record ValidateUserResult(String userId, boolean valid) {}
    public record SendNotifyResult(boolean sent, String channel, String userId) {}
}
