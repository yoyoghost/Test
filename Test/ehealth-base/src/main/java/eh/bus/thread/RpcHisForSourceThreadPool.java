package eh.bus.thread;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 实时号源线程池管理
 * Created by hwg on 2016/12/19.
 */
public class RpcHisForSourceThreadPool extends BusEventExecutor {

    private Runnable runnable;

    public RpcHisForSourceThreadPool(Runnable runnable){
        if(null == runnable){
            return;
        }
        this.runnable = runnable;
    }

    @Override
    public void execute() {
        ThreadPoolTaskExecutor service = getBusTaskExecutor();
        if(null != service) {
            if (null != runnable) {
                service.execute(runnable);
            }
        }
    }

}
