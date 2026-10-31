package com.hify.ai.internal.service;

import com.hify.ai.internal.entity.ProviderHealthObservation;
import com.hify.ai.internal.mapper.ProviderHealthMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 短事务持久化一次逻辑观测，HTTP 探测在调用本服务之前已经完成。 */
@Service
public class ProviderHealthWriter {
    public static final int FAILURE_THRESHOLD = 3;
    private final ProviderHealthMapper mapper;

    public ProviderHealthWriter(ProviderHealthMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void record(ProviderHealthObservation observation) {
        mapper.initializeIfCurrent(observation);
        if (observation.successful()) {
            mapper.recordSuccess(observation);
        } else {
            mapper.recordFailure(observation, FAILURE_THRESHOLD);
        }
    }
}
