package eh.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Created by Administrator on 2016/7/14 0014.
 *
 * 此工具通过判断 task()是否抛出异常来判断是否需要重试，抛出异常则重试直到最大重试次数，不抛出异常则不再重试，返回结果
 * 默认重试3次，重试间隔（单位：秒）为：2的0、1、2...次方
 * 泛型<T>值为task返回值类型
 */
public abstract class RetryTask <T> {
    private static final Logger log = LoggerFactory.getLogger(RetryTask.class);
    private int retryMaxTimes;
    private String methodName;

    public RetryTask(){
        this(3, "task");
    }

    public RetryTask(int retryMaxTimes){
        this(retryMaxTimes, "task");
    }

    public RetryTask(String methodName){
        this(3, methodName);
    }

    public RetryTask(int retryMaxTimes, String methodName){
        this.retryMaxTimes = retryMaxTimes;
        this.methodName = methodName;
    }

    public T retryTask(){
        int time = 0;
        while(time <= retryMaxTimes) {
            try {
                return task();
            } catch (Exception e) {
                try {
                    if(time >= retryMaxTimes){
                        log.warn("{} has retryTask maxTimes[{}] but even failed! errorMessage[{}]", methodName, retryMaxTimes, e.getMessage());
                        break;
                    }
                    TimeUnit.SECONDS.sleep((long)Math.pow(2, time));
                    time++;
                    log.info("{} retryTask time[{}] of maxTimes:[{}], errorMessage[{}]", methodName, time, retryMaxTimes, e.getMessage());
                } catch (InterruptedException e1) {
                   log.error("retryTask-->"+e1);
                }
            }
        }
        return null;
    }

    public abstract T task() throws Exception;


    public int getRetryMaxTimes() {
        return retryMaxTimes;
    }

    public void setRetryMaxTimes(int retryMaxTimes) {
        this.retryMaxTimes = retryMaxTimes;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
}
