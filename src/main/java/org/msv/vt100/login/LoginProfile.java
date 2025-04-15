package org.msv.vt100.login;

public record LoginProfile(String profileName, String username, String password, boolean autoConnect) { }
