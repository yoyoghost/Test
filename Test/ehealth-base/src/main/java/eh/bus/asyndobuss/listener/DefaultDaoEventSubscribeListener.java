package eh.bus.asyndobuss.listener;

import ctd.persistence.exception.DAOException;
import eh.bus.asyndobuss.bean.DaoEvent;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by shenhj on 2017/7/10.
 */
public class DefaultDaoEventSubscribeListener implements DaoEventSubscribeListener {

    private final Logger logger = LoggerFactory.getLogger(DefaultDaoEventSubscribeListener.class);

    @Override
    public void doTask(DaoEvent event) throws Exception{
        Method method = this.initMethod(event);
        try {
            method.invoke(event.getDao(),event.getArgs().toArray());
        } catch (IllegalAccessException|InvocationTargetException e) {
            logger.error("method invoke error,methodName:"+event.getMethodName()+",args:"+event.getArgs().toString()+",daoName:"+event.getDao().getClass().getName());
            throw new DAOException(e);
        }finally {
            event.clearParam();
        }
    }
    public Method initMethod(DaoEvent event) throws DAOException{
        assertEvent(event);
        if(event.getMethodName()==null){
            throw new DAOException("methodName is null");
        }
        Method method=null;
        Class clazz= event.getDao().getClass();
        Method[] methods = clazz.getMethods();
        for (Method m:methods) {
            //这里其实可以做方法名是否重复校验
            //checkDuplicateMethod();
            if(event.getMethodName().equals(m.getName())){
                method = m;
                method.setAccessible(true);
                break;
            }
        }
        if(method==null){
            logger.error("can not match any method,methodName:"+event.getMethodName()+",daoName:"+event.getDao().getClass().getName());
            throw new DAOException("can not match any method");
        }
        return method;
    }

    private void assertEvent(DaoEvent e) {
        if(e==null
                || StringUtils.isBlank(e.getMethodName())
                || e.getDao()==null
                || CollectionUtils.isEmpty(e.getArgs())){
            throw new DAOException("DaoEvent miss necessary fields");
        }

    }
}
