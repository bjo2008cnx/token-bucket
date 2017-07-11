package org.isomorphism.limit.timelimiter;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by lenovo on 2017/7/11.
 */
public class TimeLimiterTest {
    @Test
    public void newProxy() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        TimeLimiter limiter = SimpleTimeLimiter.create(executorService);

        DemoInterface demo = new DemoClass();
        DemoInterface proxy = limiter.newProxy(demo, DemoInterface.class, 50, TimeUnit.MILLISECONDS);
        try {
            proxy.execute();
        } catch (UncheckedTimeoutException e) {
            System.out.println("TIME OUT");
        }
    }

    @Test
    public void callWithTimeout() throws Exception {

    }

    @Test
    public void callWithTimeout1() throws Exception {

    }

    @Test
    public void callUninterruptiblyWithTimeout() throws Exception {

    }

    @Test
    public void runWithTimeout() throws Exception {

    }

    @Test
    public void runUninterruptiblyWithTimeout() throws Exception {

    }

}