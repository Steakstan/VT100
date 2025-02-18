package org.msv.vt100.ssh;

public record SSHConfig(String user, String host, int port, String privateKeyPath, boolean autoConnect) { }
