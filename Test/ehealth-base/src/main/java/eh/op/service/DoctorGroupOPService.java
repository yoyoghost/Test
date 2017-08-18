package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.DoctorGroupDAO;
import eh.base.service.BusActionLogService;
import eh.base.service.DoctorGroupService;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorGroup;
import eh.entity.bus.ConsultSet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;

import java.util.*;

/**
 * @author jianghc
 * @create 2016-12-07 11:04
 **/
public class DoctorGroupOPService {

    private static final Log logger = LogFactory.getLog(DoctorOpService.class);


    /**
     * 根据团队管理团队成员(运营专用)
     */
    @RpcService
    public void manageDoctorGroups(final Integer teamId, final HashMap<String, Integer> mapGroups) {
        if (teamId == null) {
//            log.error("doctorId is required");
            throw new DAOException(DAOException.VALUE_NEEDED, "teamId is required");
        }
        if (mapGroups == null || mapGroups.isEmpty()) {
//            log.error("doctorGroups is required");
            throw new DAOException(DAOException.VALUE_NEEDED, "mapGroups is required");
        }

        HibernateStatelessResultAction<Doctor> action = new AbstractHibernateStatelessResultAction<Doctor>() {
            public void execute(StatelessSession ss) throws Exception {
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                Doctor team = doctorDAO.getByDoctorIdAndTeams(teamId);
                if (team == null) {
//                    log.info("该团队不存在,无法添加团队成员");
                    throw new DAOException(609, "该团队不存在,无法添加团队成员");
                }
                DoctorGroupDAO doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
                List<DoctorGroup> groups = doctorGroupDAO.findByDoctorId(teamId);
                int goupType = team.getGroupType().intValue();
                // 如果是第一次添加团队成员，将咨询开关打开
                DoctorGroupService doctorGroupService = AppContextHolder.getBean("eh.doctorGroupService", DoctorGroupService.class);
                if (groups == null || groups.size() <= 0) {
                    ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
                    ConsultSet consultSet = new ConsultSet();
                    consultSet.setDoctorId(teamId);
                    consultSet.setTransferStatus(1);
                    consultSet.setMeetClinicStatus(1);
                    consultSet.setOnLineStatus(1);
                    //运营专用
                    consultSetDAO.addOrupdateConsultSetAdmin(consultSet);
                    Iterator<Map.Entry<String, Integer>> iterator = mapGroups.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, Integer> map = iterator.next();
                        doctorGroupDAO.save(new DoctorGroup(teamId, Integer.valueOf(map.getKey()), map.getValue()));
                        if (goupType == 1) {
                            doctorGroupService.modifyEasemobGroups(teamId, Integer.valueOf(map.getKey()), 1);
                        }
                    }
                } else {
                    HashMap<String, DoctorGroup> oldMap = new HashMap<String, DoctorGroup>();
                    for (DoctorGroup doctorGroup : groups) {
                        oldMap.put(doctorGroup.getMemberId() + "", doctorGroup);
                        if (mapGroups.get(doctorGroup.getMemberId() + "") == null) {//delete
                            doctorGroupDAO.remove(doctorGroup.getDoctorGroupId());
                            if (goupType == 1) {
                                doctorGroupService.modifyEasemobGroups(teamId, doctorGroup.getMemberId(), -1);
                            }
                        }
                    }
                    Iterator<Map.Entry<String, Integer>> iterator = mapGroups.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, Integer> map = iterator.next();
                        DoctorGroup doctorGroup = oldMap.get(map.getKey());
                        if (doctorGroup != null) {//update
                            doctorGroup.setLeader(map.getValue());
                            doctorGroupDAO.update(doctorGroup);
                        } else {//add
                            DoctorGroup d = new DoctorGroup(teamId, Integer.valueOf(map.getKey()), map.getValue());
                            doctorGroupDAO.save(d);
                            if (goupType == 1) {
                                doctorGroupService.modifyEasemobGroups(teamId, d.getMemberId(), 1);
                            }
                        }
                    }
                }

                List<DoctorGroup> doctorGroups = doctorGroupDAO.findByDoctorId(teamId);
                doctorGroups = doctorGroups == null ? new ArrayList<DoctorGroup>() : doctorGroups;
                BusActionLogService.recordBusinessLog("医生团队成员管理", teamId + "", "Doctor",
                        "更新医生团队:" + team.getName() + "成员为:" + JSONUtils.toString(doctorGroups));
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

}
