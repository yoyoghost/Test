package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.FollowSchedule;

import java.util.Date;
import java.util.List;

/**
 * @author renzh
 * @date 2016/7/25 0025 上午 10:51
 */
public abstract class FollowScheduleDAO extends HibernateSupportDelegateDAO<FollowSchedule> {

    public FollowScheduleDAO(){
        super();
        this.setEntityName(FollowSchedule.class.getName());
        this.setKeyField("id");
    }

    @RpcService
    public FollowSchedule addFollowSchedule(FollowSchedule followSchedule){
        return save(followSchedule);
    }

    /**
     * 定时发送日程 查询
     * @return
     */
    @DAOMethod(sql = "from FollowSchedule where date(remindDate) = date(now()) and remindFlag <> 1 and scheduleStatus=0", limit = 0)
    public abstract List<FollowSchedule> findShouldPush();

    @DAOMethod(sql = "FROM FollowSchedule WHERE remindFlag <> 1 and mpiId = :mpiId and doctorId = :doctorId and date(followDate)>=date(now()) group by followDate, planNodeId ORDER BY followDate ASC")
    public abstract List<FollowSchedule> findByMpiId(@DAOParam("mpiId") String mpiId,@DAOParam("doctorId")int doctorId,@DAOParam(pageStart = true) int start,@DAOParam(pageLimit = true) int limit);

    /**
     * 取消日程
     * @param id
     */
    @DAOMethod(sql = "update FollowSchedule set scheduleStatus=9,lastModify=current_timestamp() where id = :id")
    public abstract void updateScheduleStatusGoneById(@DAOParam("id")int id);

    /**
     * 查看当前月 每天有无随访
     * @param doctorId
     * @return
     */
    @DAOMethod(sql = "select a.followDate from FollowSchedule a,FollowPlan b where a.remindFlag <> 1 and a.planId = b.planId and date_format(a.followDate,'%Y-%m')=date_format(now(),'%Y-%m') AND a.doctorId = :doctorId GROUP BY date_format(a.followDate,'%Y-%m-%d')")
    public abstract List<FollowSchedule> findMyMonthSchedule(@DAOParam("doctorId") int doctorId);

    /**
     * 查看入参的月份 每天有无随访
     * @param doctorId
     * @param date
     * @return
     */
    @DAOMethod(sql = "select a.followDate from FollowSchedule a,FollowPlan b where a.remindFlag <> 1 and a.planId = b.planId and date_format(a.followDate,'%Y-%m')=date_format(:date,'%Y-%m') and a.doctorId = :doctorId group by date_format(a.followDate,'%Y-%m-%d')")
    public abstract List<FollowSchedule> findScheduleDateByMonth(@DAOParam("doctorId") int doctorId, @DAOParam("date") Date date);

    @DAOMethod(sql = "from FollowSchedule where remindFlag <> 1 and doctorId = :doctorId and date(followDate) = date(:date) group by planNodeId", limit = 0)
    public abstract List<FollowSchedule> getScheduleByDateCount(@DAOParam("doctorId") int doctorId, @DAOParam("date") Date date);

    @DAOMethod(sql = "select a from FollowSchedule a,FollowPlan b where a.remindFlag <> 1 and a.planId = b.planId and a.doctorId = :doctorId and date(a.followDate) = date(:date) group by a.planNodeId order by b.fromType,a.followDate")
    public abstract List<FollowSchedule> findScheduleByDate(@DAOParam("doctorId") int doctorId, @DAOParam("date") Date date, @DAOParam(pageStart = true) int start,@DAOParam(pageLimit = true) int limit);

    /**
     * 查询入参日期 医生所有的要提醒患者的日程
     * @param doctorId
     * @param date
     * @return
     */
    @DAOMethod(sql = "select a from FollowSchedule a,FollowPlan b where a.remindFlag <> 1 and a.planId = b.planId and a.doctorId = :doctorId and a.sendType = 0 and date(a.followDate) = date(:date) group by a.id order by b.fromType,a.followDate", limit = 0)
    public abstract List<FollowSchedule> findAllScheduleByDate(@DAOParam("doctorId") int doctorId, @DAOParam("date") Date date);

    @DAOMethod(sql = "from FollowSchedule where remindFlag <> 1 and doctorId=:doctorId and date(followDate)=date(now()) and readStatus=0 group by planNodeId")
    public abstract List<FollowSchedule> getTodayUnreadScheduleCount(@DAOParam("doctorId") int doctorId);

    @DAOMethod(sql = "FROM FollowSchedule WHERE mpiId = :mpiId and doctorId = :doctorId and scheduleStatus!=9 and date(followDate)>=date(now()) group by followDate, planNodeId ORDER BY followDate desc")
    public abstract List<FollowSchedule> findNearTwoSchedule(@DAOParam("mpiId") String mpiId,@DAOParam("doctorId") int doctorId,@DAOParam(pageStart = true) int start,@DAOParam(pageLimit = true) int limit);

    /**
     * 删除计划下的所有日程
     * @param planId
     */
    @DAOMethod
    public abstract void deleteByPlanId(String planId);

    /**
     * 更新为已提醒
     * @param id
     */
    @RpcService
    @DAOMethod(sql = "update FollowSchedule set scheduleStatus=1,lastModify=current_timestamp() where id = :id")
    public abstract void updateScheduleStatusOverById(@DAOParam("id")int id);

    /**
     * 判断该患者是否有未完成的随访
     * @param doctorId
     * @param mpiId
     * @return
     */
    @DAOMethod(sql = "SELECT count(1) FROM FollowSchedule WHERE remindFlag <> 1 and date(followDate)>=date(now()) and doctorId = :doctorId and mpiId = :mpiId")
    public abstract Long getExisSchedules(@DAOParam("doctorId") int doctorId,@DAOParam("mpiId") String mpiId);

    /**
     * 更新某个日程为已读
     * @param id
     */
    @DAOMethod(sql = "update FollowSchedule set readStatus=1,lastModify=current_timestamp() where id = :id")
    public abstract void updateAfterReadP(@DAOParam("id") int id);

    /**
     *
     * @param planId
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    @DAOMethod(sql = "FROM FollowSchedule WHERE planId = :planId and doctorId = :doctorId group by followDate, planNodeId ORDER BY followDate ASC")
    public abstract List<FollowSchedule> findScheduleListByPlanId(@DAOParam("planId") String planId,@DAOParam("doctorId") int doctorId,@DAOParam(pageStart = true) int start,@DAOParam(pageLimit = true) int limit);

    /**
     * 点击查看日程把当前医生当天所有日程设为已读
     * @param doctorId
     */
    @DAOMethod(sql = "update FollowSchedule set readStatus=1,lastModify=current_timestamp() where doctorId = :doctorId and sendType = 1 and date(followDate) = date(now())")
    public abstract void updateAfterReadC(@DAOParam("doctorId") int doctorId);

    /**
     * 判断随访计划是否带有附件
     * @param planId
     * @return
     */
    @DAOMethod(sql = "SELECT count(1) FROM FollowSchedule WHERE planId = :planId AND ((articleId IS NOT NULL and articleId <> '') OR (formId IS NOT NULL and articleId <> '') or (articleInfo is not null and articleInfo <> '[]') or (formInfo is not null and formInfo <> '[]'))")
    public abstract Long getEnclosureByPlanId(@DAOParam("planId") String planId);

    /**
     * 根据随访计划节点计划Id 查询日程列表
     * @param planNodeId
     * @return
     */
    @DAOMethod(limit = 0)
    public abstract List<FollowSchedule> findByPlanNodeId(Integer planNodeId);
}