package com.megatech.tcpclient;

public class TcpEvent {
    private final TcpEventType eventType;
    private final Object payload;

    public TcpEvent(TcpEventType eventType, Object payload) {
        this.eventType = eventType;
        this.payload = payload;
    }

    public TcpEventType getTcpEventType() {
        return this.eventType;
    }

    public Object getPayload() {
        return this.payload;
    }
}