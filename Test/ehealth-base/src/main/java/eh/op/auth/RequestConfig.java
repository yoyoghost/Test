package eh.op.auth;

/**
 * Created by Administrator on 2017/4/28 0028.
 */
public class RequestConfig {

    private int rate;
    private long interval;

    public int getRate() {
        return rate;
    }

    public void setRate(int rate) {
        this.rate = rate;
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }
}
