package com.threatdetection.stream;

import com.threatdetection.stream.model.AttackEvent;
import com.threatdetection.stream.model.StatusEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventValidationTest {

    @Test
    void isValidPort_acceptsExpectedBoundaries() throws Exception {
        assertFalse(invokeIsValidPort(-65537));
        assertTrue(invokeIsValidPort(-65536));
        assertTrue(invokeIsValidPort(0));
        assertTrue(invokeIsValidPort(999999));
        assertFalse(invokeIsValidPort(1000000));
    }

    @Test
    void isAttackEventValid_enforcesDevSerialAndPortRange() throws Exception {
        AttackEvent valid = new AttackEvent();
        valid.setDevSerial("DEV-100");
        valid.setResponsePort(0);
        assertTrue(invokeIsAttackEventValid(valid));

        AttackEvent missingDevSerial = new AttackEvent();
        missingDevSerial.setResponsePort(0);
        assertFalse(invokeIsAttackEventValid(missingDevSerial));

        AttackEvent belowLowerBound = new AttackEvent();
        belowLowerBound.setDevSerial("DEV-101");
        belowLowerBound.setResponsePort(-65537);
        assertFalse(invokeIsAttackEventValid(belowLowerBound));

        AttackEvent upperBound = new AttackEvent();
        upperBound.setDevSerial("DEV-102");
        upperBound.setResponsePort(999999);
        assertTrue(invokeIsAttackEventValid(upperBound));

        AttackEvent aboveUpperBound = new AttackEvent();
        aboveUpperBound.setDevSerial("DEV-103");
        aboveUpperBound.setResponsePort(1000000);
        assertFalse(invokeIsAttackEventValid(aboveUpperBound));
    }

    @Test
    void isStatusEventValid_enforcesCountersAndTimestamps() throws Exception {
        StatusEvent valid = new StatusEvent();
        valid.setDevSerial("DEV-200");
        valid.setSentryCount(1);
        valid.setRealHostCount(2);
        valid.setDevStartTime(1_700_000_000L);
        valid.setDevEndTime(-1L);
        assertTrue(invokeIsStatusEventValid(valid));

        StatusEvent missingDevSerial = new StatusEvent();
        missingDevSerial.setSentryCount(1);
        missingDevSerial.setRealHostCount(2);
        missingDevSerial.setDevStartTime(1_700_000_000L);
        missingDevSerial.setDevEndTime(-1L);
        assertFalse(invokeIsStatusEventValid(missingDevSerial));

        StatusEvent negativeSentryCount = new StatusEvent();
        negativeSentryCount.setDevSerial("DEV-201");
        negativeSentryCount.setSentryCount(-1);
        negativeSentryCount.setRealHostCount(2);
        negativeSentryCount.setDevStartTime(1_700_000_000L);
        negativeSentryCount.setDevEndTime(-1L);
        assertFalse(invokeIsStatusEventValid(negativeSentryCount));

        StatusEvent negativeStartTime = new StatusEvent();
        negativeStartTime.setDevSerial("DEV-202");
        negativeStartTime.setSentryCount(1);
        negativeStartTime.setRealHostCount(2);
        negativeStartTime.setDevStartTime(-1L);
        negativeStartTime.setDevEndTime(-1L);
        assertFalse(invokeIsStatusEventValid(negativeStartTime));

        StatusEvent endBeforeStart = new StatusEvent();
        endBeforeStart.setDevSerial("DEV-203");
        endBeforeStart.setSentryCount(1);
        endBeforeStart.setRealHostCount(2);
        endBeforeStart.setDevStartTime(1_700_000_000L);
        endBeforeStart.setDevEndTime(1_699_999_999L);
        assertFalse(invokeIsStatusEventValid(endBeforeStart));
    }

    private static boolean invokeIsValidPort(int port) throws Exception {
        Method method = StreamProcessingJob.class.getDeclaredMethod("isValidPort", int.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, port);
    }

    private static boolean invokeIsAttackEventValid(AttackEvent event) throws Exception {
        Method method = StreamProcessingJob.class.getDeclaredMethod("isAttackEventValid", AttackEvent.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, event);
    }

    private static boolean invokeIsStatusEventValid(StatusEvent event) throws Exception {
        Method method = StreamProcessingJob.class.getDeclaredMethod("isStatusEventValid", StatusEvent.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, event);
    }
}
