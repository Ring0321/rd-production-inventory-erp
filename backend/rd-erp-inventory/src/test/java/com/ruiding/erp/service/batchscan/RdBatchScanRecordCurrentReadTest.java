package com.ruiding.erp.service.batchscan;

import com.baomidou.mybatisplus.core.conditions.SharedString;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingScanRecordMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayBatchScanRecordMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RdBatchScanRecordCurrentReadTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void originalKeyLookupMustBeACurrentLockingRead(boolean loading) {
        AtomicReference<Wrapper<?>> query = new AtomicReference<>();
        if (loading) {
            var mapper = mock(RdLoadingScanRecordMapper.class, CALLS_REAL_METHODS);
            doAnswer(call -> { query.set(call.getArgument(0)); return null; })
                    .when(mapper).selectOne(any(Wrapper.class));
            mapper.selectByTaskAndIdempotencyKey(71L, "original-key");
        } else {
            var mapper = mock(RdPutawayBatchScanRecordMapper.class, CALLS_REAL_METHODS);
            doAnswer(call -> { query.set(call.getArgument(0)); return null; })
                    .when(mapper).selectOne(any(Wrapper.class));
            mapper.selectByTaskAndKey(71L, "original-key");
        }
        SharedString tail = (SharedString) ReflectionTestUtils.getField(query.get(), "lastSql");
        assertEquals("FOR UPDATE", tail.getStringValue().trim());
    }
}
