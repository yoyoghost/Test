package eh.op.auth;


import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

public class StatItem implements Serializable {


    //上一次修改时间
    private long lastResetTime;

    //时间周期
    private long interval;

    //当前剩余调用次数
    private AtomicInteger token;

    //阀值
    private int rate;

    public StatItem(int rate, long interval) {
        this.rate = rate;
        this.interval = interval;
        this.lastResetTime = System.currentTimeMillis();
        this.token = new AtomicInteger(rate);
    }

    public boolean isAllowable() {
        long now = System.currentTimeMillis();
        //当前时间 大于上次修改时间加时间周期  则
        if (now > lastResetTime + interval) {
            token.set(rate); //剩余次数等于阀值
            lastResetTime = now; //修改时间设置为当前
        }

        //得到当前次数
        int value = token.get();
        //返回flag
        boolean flag = false;
        while (value > 0 && !flag) {
            //调用一次 剩余次数减1
            flag = token.compareAndSet(value, value - 1);
            value = token.get();
        }

        return flag;
    }

    long getLastResetTime() {
        return lastResetTime;
    }
    
    int getToken() {
        return token.get();
    }
    

}
