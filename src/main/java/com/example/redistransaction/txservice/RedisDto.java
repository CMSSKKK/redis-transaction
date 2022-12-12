package com.example.redistransaction.txservice;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class RedisDto {

    private final String key;
    private final String value;
}
