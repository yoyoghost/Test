package eh.task;

import com.google.common.collect.Maps;

import javax.annotation.PreDestroy;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorRegister {
    private static Map<Integer, ExecutorService> registered = Maps.newConcurrentMap();
    private static AtomicInteger numb = new AtomicInteger(0);

    public static ExecutorService register(ExecutorService executorService){
        registered.put(numb.incrementAndGet(), executorService);
        return executorService;
    }

    @PreDestroy
    public static void destoryAll(){
        Iterator<ExecutorService> it = registered.values().iterator();
        while (it.hasNext()){
            ExecutorService es = it.next();
            es.shutdown();
        }
    }

}
