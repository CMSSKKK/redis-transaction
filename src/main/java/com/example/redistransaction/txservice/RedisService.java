package com.example.redistransaction.txservice;

public interface RedisService {

    void incr(String key, boolean isException);

    RedisDto incrAndCopy(String originkey, String newkey, int count);
}
