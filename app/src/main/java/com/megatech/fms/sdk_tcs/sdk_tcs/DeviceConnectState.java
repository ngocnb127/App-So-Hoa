package com.megatech.fms.sdk_tcs.sdk_tcs;

public enum DeviceConnectState {
    NONE,
    CONNECTING_NETWORK,
    CONNECTING_DEVICE,
    HANDSHAKING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING,
    DISCONNECTED,
    REMOVING,
    CLEANING,
    REMOVED,
    ERROR
}