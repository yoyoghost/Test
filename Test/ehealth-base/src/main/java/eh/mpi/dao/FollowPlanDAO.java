package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.FollowPlan;

import java.util.List;

/**
 * @author renzh
 * @date 2016/7/21 0021 下午 15:31
 */
public abstract class FollowPlanDAO extends HibernateSupportDelegateDAO<FollowPlan> {

    public FollowPlanDAO(){
        super();
        this.setEntityName(FollowPlan.class.getName());
        this.setKeyField("planNodeId");
    }


    @DAOMethod
    public abstract List<FollowPlan> findByPlanId(String planId);

    @RpcService
    @DAOMethod
    public abstract  FollowPlan getByPlanNodeId(Integer planNodeId);

    public FollowPlan addFollowPlan(FollowPlan followPlan){
        return save(followPlan);
    }

    @DAOMethod(sql = "SELECT a.planId from FollowPlan a,FollowSchedule b where a.planId = b.planId and a.planCreator = :planCreator and b.mpiId = :mpiId group by a.planId order by a.createDate desc")
    public abstract List<String> findPlanCreatorNearly(@DAOParam("planCreator") int planCreator,@DAOParam("mpiId") String mpiId);

    @DAOMethod(sql = "SELECT a.planId from FollowPlan a,FollowSchedule b where a.planId = b.planId and a.planCreator = :planCreator and b.mpiId = :mpiId and a.fromType=1 group by a.planId order by a.createDate desc")
    public abstract List<String> findPlanByManual(@DAOParam("planCreator") int planCreator,@DAOParam("mpiId") String mpiId);

    @DAOMethod(sql = "SELECT CASE WHEN date(endDate)>=date(NOW()) THEN 0 ELSE 1 END FROM FollowPlan where planId = :planId")
    public abstract int getIfEnd(@DAOParam("planId") String planId);

    @DAOMethod
    public abstract void deleteByPlanId(String planId);

    @DAOMethod(sql = "SELECT CASE WHEN max(date(endDate)) >= date(NOW()) THEN 0 WHEN max(date(endDate)) < date(NOW()) THEN 1 END FROM FollowPlan WHERE planId = :planId and fromType in (1,3)")
    public abstract Integer getIsCompleteSchedule(@DAOParam("planId") String planId);

    @RpcService
    @DAOMethod(sql = "SELECT planCreator FROM FollowPlan WHERE planId = :planId GROUP BY PlanId")
    public abstract int getPlanCreator(@DAOParam("planId") String planId);

    @DAOMethod
    public abstract List<FollowPlan> findByAppointRecordId(Integer appointRecordId);

    @DAOMethod
    public abstract List<FollowPlan> findByAppointRecordIdAndFromType(Integer appointRecordId, String fromType);

    @DAOMethod(sql = "SELECT a.planId from FollowPlan a,FollowSchedule b where a.planNodeId = b.planNodeId and a.planCreator = :doctorId and b.mpiId = :mpiId group by a.planId")
    public abstract List<String> findByDocAndPa(@DAOParam("doctorId")Integer doctorId,@DAOParam("mpiId")String mpiId);

    @DAOMethod(sql = "SELECT a FROM FollowPlan a,FollowSchedule b WHERE a.planId = b.planId and a.fromType in (1,3) and b.mpiId = :mpiId and a.planCreator = :doctorId and date(a.endDate)>=date(Now()) GROUP BY a.planId order by a.createDate desc")
    public abstract List<FollowPlan> findFollowPlanListByMpiId(@DAOParam("mpiId") String mpiId,@DAOParam("doctorId") int doctorId,@DAOParam(pageStart = true) int start,@DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = "SELECT a FROM FollowPlan a,FollowSchedule b WHERE a.planId = b.planId and b.mpiId = :mpiId and a.planCreator = :doctorId and a.fromType in (1,3) And date(a.endDate)>=date(Now()) GROUP BY a.planId")
    public abstract List<FollowPlan> findUnfinishedFollowPlanByDoctorIdAndMpiId(@DAOParam("mpiId") String mpiId,@DAOParam("doctorId") int doctorId);

    @DAOMethod(sql = "SELECT a FROM FollowPlan a,FollowSchedule b WHERE a.planId = b.planId and b.mpiId = :mpiId and a.planCreator = :doctorId and a.fromType = 3 And date(a.endDate)>=date(Now())")
    public abstract List<FollowPlan> findPatientReportPlan(@DAOParam("mpiId") String mpiId,@DAOParam("doctorId") int doctorId);

    @DAOMethod(sql = "SELECT a FROM FollowPlan a,FollowSchedule b WHERE a.planNodeId = b.planNodeId and b.id = :id and a.fromType=4")
    public  abstract FollowPlan getFollowPlanByScheduleId(@DAOParam("id")int id);
}
