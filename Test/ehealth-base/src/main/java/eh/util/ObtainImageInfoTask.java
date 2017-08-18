package eh.util;

/**
 * Created by Administrator on 2016/12/15.
 */
public class ObtainImageInfoTask {

    private int tryTimes;

    public void addTryTimes(){tryTimes++;}

    public int getTryTimes() {
        return tryTimes;
    }

    public void setTryTimes(int tryTimes) {
        this.tryTimes = tryTimes;
    }

    public Long getDelay(){
        Long delay=5L;
        switch (tryTimes) {
            case 1:
            case 2:
            case 3:
                //break;
            case 4:
                delay=5L;
                break;
            case 5:
                delay=60L;
                break;
            case 6:
                delay=60L;
                break;
            case 7:
                delay=60L;
                break;
            case 8:
                delay=120L;
                break;
            default:
                break;
        }
        return delay;
    }

}
