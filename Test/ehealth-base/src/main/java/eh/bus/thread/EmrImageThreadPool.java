package eh.bus.thread;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 病人影像服务线程池管理
 * Created by hwg on 2017/1/4.
 */
public class EmrImageThreadPool extends BusEventExecutor {

    private Runnable runnable;

    public EmrImageThreadPool(Runnable runnable){
        if(null == runnable){
            return;
        }
        this.runnable = runnable;
    }

    @Override
    public void execute(){
        ThreadPoolTaskExecutor service = getBusTaskExecutor();
        if(null != service) {
            if (null != runnable) {
                service.execute(runnable);
            }
        }
    }
}
