package eh.bus.asyndobuss.bean;

import com.google.common.collect.Lists;
import ctd.persistence.DAO;
import java.util.ArrayList;

/**
 * Created by shenhj on 2017/7/10.
 */
public class DaoEvent {
    private DAO dao;
    private String methodName;
    private ArrayList<Object> args = Lists.newArrayList();

    /**
     * note:按照目标方法的参数顺序而添加
     */
    public DaoEvent addArgsOrdered(Object param){
        this.args.add(param);
        return this;
    }

    public DaoEvent clearParam(){
        this.args.clear();
        return this;
    }

    public DAO getDao() {
        return dao;
    }

    public void setDao(DAO dao) {
        this.dao = dao;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public ArrayList<Object> getArgs() {
        return args;
    }

    @Override
    public String toString() {
        return "DaoEvent{" +
                "dao=" + dao +
                ", methodName='" + methodName + '\'' +
                ", args=" + args.toString() +
                '}';
    }
}
