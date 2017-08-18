package eh.cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import eh.entity.base.Device;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestCache {

    private static ExecutorService executorService = Executors.newCachedThreadPool();
    private static LoadingCache<String, Device> cache = CacheBuilder.newBuilder().build(new CacheLoader<String, Device>() {
        @Override
        public Device load(String s) throws Exception {
            return null;
        }
    });

    public static void main(String[] args) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                String key = "15869197716";
                while (true){
                    Device device = new Device(key, "WX", "1.0", "123@xyz");
                    device.setStatus("1");
                    device.setAppid("ngari-health");
                    device.setCreateDt(new Date());
                    device.setLastModify(new Date());
                    device.setUrt(7003);
                    device.setAccesstoken("1234-5678-abcd-efgh");
                    device.setId(12306);
                    cache.put(key, device);
                }
            }
        });
        try {
            TimeUnit.SECONDS.sleep(1l);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (int i = 0; i < 10; i++) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    String key = "15869197716";
                    while (true){
                        if (null == cache.getIfPresent(key)){
                            throw new NullPointerException("it is fucked");
                        }
                    }
                }
            });
        }
    }
}
