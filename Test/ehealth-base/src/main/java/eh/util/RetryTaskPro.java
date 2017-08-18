package eh.util;


import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Predicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Created by shenhj on 2017/7/10.
 */
public abstract class RetryTaskPro<T>{

    private static final Logger log = LoggerFactory.getLogger(RetryTaskPro.class);
    private int sleepTime = 2;
    private int attemptCounts = 3;

    private final Retryer<T> retryer;

    public RetryTaskPro(){
        retryer = RetryerBuilder.<T>newBuilder()
                .retryIfResult(Predicates.<T>isNull())
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(attemptCounts))
                .withWaitStrategy(WaitStrategies.fixedWait(sleepTime, TimeUnit.SECONDS))
                .build();
    }

    public RetryTaskPro(int sleepTime,int attemptCounts){
        this.sleepTime = sleepTime;
        this.attemptCounts = attemptCounts;
        retryer = RetryerBuilder.<T>newBuilder()
                .retryIfResult(Predicates.<T>isNull())
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(attemptCounts))
                .withWaitStrategy(WaitStrategies.fixedWait(sleepTime, TimeUnit.SECONDS))
                .build();
    }

    public T retry(){
        try {
            return retryer.call(new Callable<T>() {
                public T call() throws Exception {
                    return action();
                }
            });
        } catch (Exception e) {
            log.error("RetryTaskPro do action failed and retry",e);
            return null;
        }
    }

    protected abstract T action();

}
