package com.megatech.tcpclient;

public enum TcpClientState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CONNECTION_STARTED,
    FAILED
}