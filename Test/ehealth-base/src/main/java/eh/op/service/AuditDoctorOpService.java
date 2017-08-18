package eh.op.service;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.DepartmentDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.bus.dao.AuditDoctorLogDAO;
import eh.bus.service.VideoService;
import eh.entity.base.Department;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.Organ;
import eh.entity.bus.AuditDoctorLog;
import eh.entity.bus.AuditDoctorLogListAndDoctor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.ObjectUtils;

import java.util.*;

/**
 * Created by houxr on 2016/5/27.
 * 医生审核相关服务
 */
public class AuditDoctorOpService {
    private static final Log logger = LogFactory.getLog(AuditDoctorOpService.class);


    /**
     * 根据auditDoctorLogId查询审核记录
     *
     * @param auditDoctorLogId
     * @return
     */
    @RpcService
    public AuditDoctorLog getAuditDoctorLogById(int auditDoctorLogId) {
        AuditDoctorLogDAO auditDoctorLogDAO = DAOFactory.getDAO(AuditDoctorLogDAO.class);
        AuditDoctorLog auditDoctorLog = auditDoctorLogDAO.getByAuditDoctorLogId(auditDoctorLogId);
        return auditDoctorLog;
    }


    /**
     * 待审核医生10人（初次提交2人，再次提交8人）
     *
     * @return
     */
    @RpcService
    public Map<String, Long> countPreAuditDoctor() {
        Map<String, Long> map = new HashMap<String, Long>();
        AuditDoctorLogDAO auditDoctorLogDAO = DAOFactory.getDAO(AuditDoctorLogDAO.class);
        Long count = auditDoctorLogDAO.getCountAuditDoctorStatusTwo();
        Long again = auditDoctorLogDAO.getCountAuditDoctorStatusTwoAndAgain();
        map.put("count", count);
        map.put("first", count - again);
        map.put("again", again);
        return map;
    }

    /**
     * 查询审核医生记录
     *
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<AuditDoctorLogListAndDoctor> findAuditDoctorsForOP(int start, int limit) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        AuditDoctorLogDAO auditDoctorLogDAO = DAOFactory.getDAO(AuditDoctorLogDAO.class);
        List<Doctor> doctorLists = doctorDAO.findDoctorsByStatusTwo(start, limit);
        List<AuditDoctorLogListAndDoctor> lists = new ArrayList<AuditDoctorLogListAndDoctor>();
        for (Doctor doctor : doctorLists) {
            List<AuditDoctorLog> auditDoctorLogList = auditDoctorLogDAO.findAuditDoctorLogByDoctorId(doctor.getDoctorId());
            lists.add(new AuditDoctorLogListAndDoctor(doctor, auditDoctorLogList));
        }
        return lists;
    }

    /**
     * 查询被拒绝的医生和日志记录
     *
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<AuditDoctorLogListAndDoctor> queryDeniedDoctors(String docName,String IDNumber,Integer organ,int start, int limit) {
        AuditDoctorLogDAO auditDoctorLogDAO = DAOFactory.getDAO(AuditDoctorLogDAO.class);
        QueryResult<Doctor> doctors = auditDoctorLogDAO.queryDoctorHasLogByStatusAndNameAndIdNumberAndOrgan(docName,IDNumber,organ,0,start,limit);
        List<AuditDoctorLogListAndDoctor> lists = new ArrayList<AuditDoctorLogListAndDoctor>();

        for (Doctor doctor : doctors.getItems()) {
            List<AuditDoctorLog> auditDoctorLogList = auditDoctorLogDAO.findAuditDoctorLogByDoctorId(doctor.getDoctorId());
            lists.add(new AuditDoctorLogListAndDoctor(doctor, auditDoctorLogList));
        }
        return new QueryResult<AuditDoctorLogListAndDoctor>(doctors.getTotal(), (int) doctors.getStart(), (int) doctors.getLimit(), lists);
    }

    /**
     * 审核医生 通过或不通过
     *
     * @param doctorId   医生内码
     * @param status     医生状态
     * @param reason     不通过原因
     * @param memo       详细说明
     * @author houxr
     */
    @RpcService
    public void auditDoctorWithMsg( int doctorId, int status, String reason, String memo) {
        UserRoleToken urt = UserRoleToken.getCurrent();
        if(urt==null){
            throw new DAOException( "userToken is null");
        }
        int userRoleId = urt.getId();
        String userName = urt.getUserName();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        AuditDoctorLogDAO auditDoctorLogDAO = DAOFactory.getDAO(AuditDoctorLogDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        if (doctor == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "no such doctor!");
        }
        if (status >= 2) {
            throw new DAOException(DAOException.VALUE_NEEDED, "status is wrong!");
        }
        doctor.setStatus(status);
        doctor.setLastModify(new Date());
        if (doctorDAO.updateDoctorByDoctorId(doctor)) {
            //保存审核记录
            AuditDoctorLog auditDoctorLog = new AuditDoctorLog();
            auditDoctorLog.setUserRolesId(userRoleId);
            auditDoctorLog.setUserName(userName);
            auditDoctorLog.setDoctorId(doctor.getDoctorId());
            auditDoctorLog.setReason(reason);
            auditDoctorLog.setMemo(memo);
            auditDoctorLog.setAuditDate(new Date());
            auditDoctorLogDAO.addAuditDoctorLog(auditDoctorLog);
            //审核员操作记录
            BusActionLogService.recordBusinessLog("医生审核", doctor.getDoctorId().toString(), "AuditDoctorLog",
                    "[" + doctor.getName() + "](" + doctor.getDoctorId() + ")医生" + (status == 1 ? "审核通过" : ("未审核通过，原因:" + reason)));
            //短信通知医生
            doctorDAO.sendValidateCodeByRole(doctor.getDoctorId(),doctor.getOrgan(),doctor.getMobile(), doctor.getName(), status,reason);
        }
        //视频服务现在用的小鱼，暂时将诚云注册废弃
       /* if (status == 1) {
            //审核通过给医生注册诚云账号
            VideoService videoService = new VideoService();
            videoService.createDoctorVideoUserForNgari(doctorId);
        }*/
    }


    /**
     * 医生审核(注册医生)当为[其他]机构时 修改医生第一职业机构为真实存在机构
     *
     * @param doctor  医生信息
     * @param organId 机构内码
     * @return
     * @author houxr 2016-08-15
     */
    @RpcService
    public Doctor changeDoctorPrimaryOrgan(final Doctor doctor, final Integer organId) {
        if (null == doctor) {
            throw new DAOException(DAOException.VALUE_NEEDED, "审核医生不能为空");
        }
        if (ObjectUtils.isEmpty(organId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "目标机构不能为空");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        DepartmentDAO deptDao = DAOFactory.getDAO(DepartmentDAO.class);
        Doctor target = doctorDAO.get(doctor.getDoctorId());
        Organ organ = organDAO.getByOrganId(organId);
        if (null == target) {
            throw new DAOException(DAOException.VALUE_NEEDED, "审核医生不存在");
        }
        if (null == organ) {
            throw new DAOException(DAOException.VALUE_NEEDED, "目标机构不存在");
        }
       /* BeanUtils.map(doctor, target);
        target.setOrgan(organId);
        target = doctorDAO.update(target);*/
        Employment employment = employmentDAO.getPrimaryEmpByDoctorId(doctor.getDoctorId());
        Department dept = deptDao.getDeptByProfessionIdAndOrgan(doctor.getProfession(), organId);
        employment.setOrganId(organId);//新的属机构所
        employment.setDepartment(dept.getDeptId());//新的所属部门
        employment = employmentDAO.update(employment);
        // 通过此方法重新设置医生的第一执业医院，并且刷新缓存
        employmentDAO.updateEmploymentAsPrimary(employment.getEmploymentId());
        //employmentDAO.updateUserCacheEmploymentProperty(employment);//刷新缓存
        //刷新医生缓存
        //new UserSevice().updateUserCache(doctor.getMobile(), SystemConstant.ROLES_DOCTOR, "doctor", doctor);
        return target;
    }


}
