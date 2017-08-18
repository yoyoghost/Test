package eh.bus.homepage.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.bus.PendingTask;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.List;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/20.
 */
public abstract class PendingTaskDAO extends HibernateSupportDelegateDAO<PendingTask> {

    public PendingTaskDAO() {
        super();
        this.setEntityName(PendingTask.class.getName());
        this.setKeyField("id");
    }

    /**
     * 医生忽略团队业务
     * @param bussType
     * @param bussId
     * @param doctorId
     */
    @DAOMethod(sql = "update PendingTask set status=2 where bussType=:bussType and bussId=:bussId and doctorId=:doctorId")
    public abstract void updateIgnoreBuss(@DAOParam("bussType") int bussType,@DAOParam("bussId") int bussId,
                                                  @DAOParam("doctorId") int doctorId);

    /**
     * 接收业务发起时，更新业务可见状态
     * @param bussType
     * @param bussId
     * @param doctorId
     */
    @DAOMethod(sql = "update PendingTask set status=2 where bussType=:bussType and bussId=:bussId and doctorId!=:doctorId")
    public abstract void updateExceptAcceptDoctor(@DAOParam("bussType") int bussType,@DAOParam("bussId") int bussId,
                                                   @DAOParam("doctorId") int doctorId);

    /**
     * 接收业务发起时，更新业务可见状态，接收医生的任务可能处于其他状态，则改成可见状态
     * @param bussType
     * @param bussId
     * @param doctorId
     */
    @DAOMethod(sql = "update PendingTask set status=1 where bussType=:bussType and bussId=:bussId and doctorId=:doctorId")
    public abstract void updateAcceptDoctor(@DAOParam("bussType") int bussType,@DAOParam("bussId") int bussId,
                                                  @DAOParam("doctorId") int doctorId);

    /**
     * 取消业务处理
     * @param bussType
     * @param bussId
     */
    @DAOMethod(sql = "update PendingTask set status=3 where bussType=:bussType and bussId=:bussId")
    public abstract void updateCancelBuss(@DAOParam("bussType") int bussType,@DAOParam("bussId") int bussId);

    /**
     * 完成业务处理
     * @param bussType
     * @param bussId
     */
    @DAOMethod(sql = "update PendingTask set status=4 where bussType=:bussType and bussId=:bussId")
    public abstract void updateFinishBuss(@DAOParam("bussType") int bussType,@DAOParam("bussId") int bussId);

    /**
     * 获取医生待处理任务总数
     * @param doctorId
     */
    @DAOMethod(sql = "select count(1) from PendingTask where doctorId=:doctorId and status=1")
    public abstract long getCountByDoctorId(@DAOParam("doctorId") int doctorId);

    /**
     * 获取医生待处理任务列表 (10级任务)
     * @param doctorId
     * @return
     */
    @DAOMethod(sql = "from PendingTask where doctorId=:doctorId and id<:taskId and status=1 and grade=10 order by id desc ")
    public abstract List<PendingTask> findByDoctorId( @DAOParam("doctorId") int doctorId,@DAOParam("taskId") Integer taskId,
                                                      @DAOParam(pageStart = true) int start,@DAOParam(pageLimit = true) int limit);

    /**
     * 获取首页待处理项
     *
     * @param bussType
     * @param bussId
     * @param doctorId
     * @return
     */
    @DAOMethod(sql = "from PendingTask where bussType=:bussType and bussId=:bussId and doctorId=:doctorId")
    public abstract PendingTask getByBussTypeAndBussTypeAndDoctorId(@DAOParam("bussType") Integer bussType,
                                                                    @DAOParam("bussId") Integer bussId,
                                                                    @DAOParam("doctorId") Integer doctorId);

    public void delAll(){
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder sql = new StringBuilder("delete from bus_pendingtask");
                Query q = ss.createSQLQuery(sql.toString());

                int flag = q.executeUpdate();
                setResult(flag == 1);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
    }

    public void savePendingWithSelect(PendingTask pendingTask) {
        PendingTask task = this.getByBussTypeAndBussTypeAndDoctorId(pendingTask.getBussType(), pendingTask.getBussId(), pendingTask.getDoctorId());
        if (task != null && task.getId() != null) {
            this.update(pendingTask);
        } else {
            this.save(pendingTask);
        }
    }
}
