package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorGroupDAO;
import eh.base.user.UserSevice;
import eh.bus.dao.ConsultDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorGroup;
import eh.util.Easemob;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;


public class DoctorGroupService {

    public static final Logger log = Logger.getLogger(DoctorGroupService.class);

    /**
     * 获取团队成员列表
     * 医生头像（队长显示队长图标）、姓名、职称；
     * 排序:队长显示在第一个位置，其他成员根据职称由高到低排序，同职称的情况下，根据加入团队时间由远及近排序
     *
     * @param doctorId 团队医生ID
     * @param start 开始页 null则查全部
     * @param limit 每页几条 null则查全部
     * @return
     */
    public List<Doctor> getTeamMembersForHealth(Integer doctorId, Integer start, Integer limit){
        if(doctorId== null){
            return new ArrayList<Doctor>();
        }

        DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
        List<Object[]> list=groupDAO.findMembersByDoctorIdForHealth(doctorId, start, limit);

        List<Doctor> returnList=new ArrayList<Doctor>();
        for (Object[] objList : list) {
            DoctorGroup group=(DoctorGroup)objList[0];
            Doctor doc=(Doctor)objList[1];

            Doctor d=new Doctor();
            d.setDoctorId(doc.getDoctorId());
            d.setName(doc.getName());
            d.setProTitle(doc.getProTitle());
            d.setPhoto(doc.getPhoto());
            d.setGender(doc.getGender());
            d.setLeader(group.getLeader()==null?0:group.getLeader());
            d.setOrgan(doc.getOrgan());
            d.setProfession(doc.getProfession());
            d.setDomain(doc.getDomain()==null?"":doc.getDomain());
            returnList.add(d);
        }

        return returnList;
    }

    /**
     * 分页获取团队成员
     * @param doctorId
     * @param start 开始页
     * @param limit 每页几条
     * @return 队长显示在第一个位置，其他成员根据职称由高到低排序，同职称的情况下，根据加入团队时间由远及近排序
     */
    @RpcService
    public List<Doctor> getTeamMembersForHealthPages(Integer doctorId, Integer start, Integer limit){
        return getTeamMembersForHealth(doctorId, start, limit);
    }

    /**
     * 获取全部团队成员
     * @param doctorId
     * @return 队长显示在第一个位置，其他成员根据职称由高到低排序，同职称的情况下，根据加入团队时间由远及近排序
     */
    @RpcService
    public List<Doctor> getAllTeamMembersForHealth(Integer doctorId){
        return getTeamMembersForHealth(doctorId, null, null);
    }

    /**
     * 循环修改非抢单模式的群组成员
     * @param daytimeTeam 非抢单模式的团队医生iD
     * @param doctorId 要删除或者要添加的医生id
     * @param flag -1删除；1添加
     */
    public void modifyEasemobGroups(Integer daytimeTeam,Integer doctorId,int flag){
        ConsultDAO dao=DAOFactory.getDAO(ConsultDAO.class);
        List<String> sessionList=dao.findAllDaytimeTeamConsultSessionID(daytimeTeam);

        if(sessionList.size()<=0){
            log.info("团队["+daytimeTeam+"]不存在环信群组");
            return;
        }

        UserSevice userService= AppContextHolder.getBean("eh.userSevice",UserSevice.class);
        Integer docUrt=userService.getDoctorUrtIdByDoctorId(doctorId);
        if(docUrt==null || docUrt==0){
            log.info("团队成员["+docUrt+"]无urt信息");
            return;
        }
        String docEasemobName=Easemob.getDoctor(docUrt);
        for(String sessionId:sessionList){

            //添加环信群组成员
            if(flag==1){
                Easemob.addUserToGroup(sessionId,docEasemobName);
            }

            //删除环信群组成员
            if(flag==-1){
                Easemob.deleteUserFromGroup(sessionId,docEasemobName);
            }
        }
    }


}
