package eh.bus.service.common;

import ctd.net.rpc.async.AsyncTask;
import ctd.net.rpc.async.endpoint.EndPoint;
import ctd.net.rpc.async.endpoint.EndPointFactory;
import ctd.net.rpc.async.exception.AsyncTaskException;
import ctd.net.rpc.async.support.AbstractAsyncTaskExecutor;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by Administrator on 2016/9/9 0009.
 */
public class MsgAsyncTaskExecutor extends AbstractAsyncTaskExecutor {

    public MsgAsyncTaskExecutor(String id) {
        super(id);
    }

    @Override
    public Object execute(AsyncTask task) throws Exception {
        String targetExecuteEndPoint = task.getExecuteEndPoint();
        EndPoint targetEndPoint;
        if(StringUtils.isNotEmpty(targetExecuteEndPoint)) {
            targetEndPoint = EndPointFactory.getEndPoint(targetExecuteEndPoint);
        } else {
            targetEndPoint = this.executeEndPoint;
        }

        if(targetEndPoint == null) {
            throw new AsyncTaskException("taskExecutor[" + this.id + "] execute EndPoint not setup.");
        } else {
            Object result = targetEndPoint.invoke(task.getParameters());
            task.setResult(result);
            return result;
        }
    }

    @Override
    public void resolve(AsyncTask task) throws Exception {
        String taskResolvedEndPoint = task.getResolveEndPoint();
        EndPoint targetEndPoint;
        if(StringUtils.isNotEmpty(taskResolvedEndPoint)) {
            targetEndPoint = EndPointFactory.getEndPoint(taskResolvedEndPoint);
        } else {
            targetEndPoint = this.resolveEndPoint;
        }

        if(targetEndPoint != null) {
            if(task.getResult() == null) {
                targetEndPoint.invoke(task.getParameters());
            } else {
                targetEndPoint.invoke(task.getParameters(), task.getResult());
            }
        }
    }

    @Override
    public void reject(AsyncTask task) throws Exception {
        String taskRejectEndPoint = task.getRejectEndPoint();
        EndPoint targetEndPoint;
        if(StringUtils.isNotEmpty(taskRejectEndPoint)) {
            targetEndPoint = EndPointFactory.getEndPoint(taskRejectEndPoint);
        } else {
            targetEndPoint = this.rejectEndPoint;
        }

        if(targetEndPoint != null) {
            short code = 500;
            if(task.isExpired()) {
                code = 501;
            } else if(task.isMaxRetryExceed()) {
                code = 502;
            }

            AsyncTaskException e = new AsyncTaskException(code, task.getLastException());
            e.setTaskId(task.getId());
            e.setRetryCount(task.getRetryCount());
            targetEndPoint.invoke(new Object[]{task});
        }
    }
}
