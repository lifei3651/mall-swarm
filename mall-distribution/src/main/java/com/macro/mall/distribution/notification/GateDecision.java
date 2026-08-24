package com.macro.mall.distribution.notification;

public record GateDecision(boolean allowed, String code) {
    public static GateDecision allow() { return new GateDecision(true, null); }
    public static GateDecision deny(String code) { return new GateDecision(false, code); }
}
