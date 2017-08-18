package eh.task.executor;

import com.alibaba.fastjson.JSONObject;
import ctd.net.rpc.async.AsyncTask;
import ctd.net.rpc.async.AsyncTaskRegistry;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.JSONUtils;
import eh.entity.bus.msg.SendSucessCallbackMsg;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by Administrator on 2016/8/31 0031.
 */
public class MsgSendExecutor implements ActionExecutor {
    private static final Logger log = LoggerFactory.getLogger(MsgSendExecutor.class);
    private static ExecutorService executor = ExecutorRegister.register(Executors.newCachedThreadPool());
    private static final int MAX_RETRY_TIMES = 3;
    private static final int TTL = 20;

    private SendSucessCallbackMsg msg;
    private String taskExecutorId;

    public MsgSendExecutor(String taskExecutorId) {
        this.taskExecutorId = taskExecutorId;
    }

    @Override
    public void execute() throws DAOException {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                send();
            }
        });
    }

    public void send() {
        if (msg == null) {
            log.info("[{}] send suspend, msg is null!", this.getClass().getSimpleName());
            return;
        }
        AsyncTaskRegistry asyncTaskRegistry = AppDomainContext.getBean("eh.msgAsyncTaskRegistry", AsyncTaskRegistry.class);
        String id = UUID.randomUUID().toString();
        AsyncTask task = new AsyncTask(id, taskExecutorId, MAX_RETRY_TIMES, TTL);
        task.setParameters(msg);
        try {
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException e) {
            log.info("send SendSucessCallbackMsg AsyncTask[{}], errorMessage[{}], stackTrace[{}]", JSONUtils.toString(task), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
        asyncTaskRegistry.add(task);
        log.info("send SendSucessCallbackMsg AsyncTask[{}]", JSONUtils.toString(task));
    }

    public void destroy() {
        executor.shutdown();
    }

    public SendSucessCallbackMsg getMsg() {
        return msg;
    }

    public void setMsg(SendSucessCallbackMsg msg) {
        this.msg = msg;
    }

    public String getTaskExecutorId() {
        return taskExecutorId;
    }

    public void setTaskExecutorId(String taskExecutorId) {
        this.taskExecutorId = taskExecutorId;
    }
}
