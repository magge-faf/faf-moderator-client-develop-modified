package com.faforever.moderatorclient.api;

import lombok.Value;

import java.time.LocalTime;

@Value
public class ApiCallRecord {
    LocalTime time;
    String method;
    String url;
    int statusCode;
    long durationMs;
    boolean success;
}
