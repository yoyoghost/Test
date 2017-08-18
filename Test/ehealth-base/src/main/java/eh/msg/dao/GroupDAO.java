package eh.msg.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.DoctorDAO;
import eh.base.dao.DoctorGroupDAO;
import eh.base.user.UserSevice;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.EndMeetClinicDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorOrPatientAndUrt;
import eh.entity.bus.Consult;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicResult;
import eh.entity.mpi.FollowChat;
import eh.entity.mpi.Patient;
import eh.entity.msg.Group;
import eh.mpi.dao.FollowChatDAO;
import eh.mpi.dao.PatientDAO;
import eh.util.Easemob;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 群组
 *
 * @author ZX
 */
public abstract class GroupDAO extends HibernateSupportDelegateDAO<Group> {

    public static final Logger log = Logger.getLogger(GroupDAO.class);

    public GroupDAO() {
        super();
        this.setEntityName(Group.class.getName());
        this.setKeyField("groupId");
    }

    /**
     * 创建转诊群组
     *
     * @param bussId
     * @return
     * @author LF
     */
    @RpcService
    public Group createTransferGroup(Integer bussId) {
//        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
//        UserSevice userService = new UserSevice();
//
//        if (!transferDAO.exist(bussId)) {
//            throw new DAOException(609, "不存在这条转诊申请记录");
//        }
//        // 获取会诊申请单信息
//        Transfer transfer = transferDAO.get(bussId);
//        int requestDocId = transfer.getRequestDoctor();
//        int targetDoctorId = transfer.getTargetDoctor();
//
//        // 获取申请医生urtid
//        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
//        Doctor requestDoc = docDao.get(requestDocId);
//        String requestMobile = requestDoc.getMobile();
//        int requestUrtId = userService
//                .getUrtIdByMobile(requestMobile, "doctor");
//
//        // 获取目标医生urtid
//        Doctor targetDoctor = docDao.get(targetDoctorId);
//        String targetMobile = targetDoctor.getMobile();
//        int targetUrtId = userService.getUrtIdByMobile(targetMobile, "doctor");
//
//        // 防止创建不成功，将参与用户都注册一遍
//        Easemob.registUser(Easemob.getDoctor(requestUrtId), SystemConstant.EASEMOB_DOC_PWD);
//        if (targetUrtId > 0) {
//            Easemob.registUser(Easemob.getDoctor(targetUrtId), SystemConstant.EASEMOB_DOC_PWD);
//        }
//
//        // 获取会诊病人信息
//        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
//        Patient pat = patientDao.get(transfer.getMpiId());
//
//        String nick = Easemob.getTransferNick(bussId);
//        String title = pat.getPatientName() + "的转诊讨论";
//
//        ArrayList<String> members = new ArrayList<String>();
//        if (targetUrtId > 0) {
//            members.add(Easemob.getDoctor(targetUrtId));
//        }
//        // 创建群组
//        ObjectNode node = Easemob.creatChatGroups(
//                Easemob.getDoctor(requestUrtId), members, nick, title, 200,
//                true, true);
//
//        Group group = new Group();
//        if (node == null || node.get("data") == null
//                || node.get("data").get("groupid") == null) {
//            return group;
//        }
//
//        String groupid = node.get("data").get("groupid").asText();
//
//        group.setBussId(bussId);
//        group.setBussType(1);
//        group.setNick(nick);
//        group.setTitle(title);
//        group.setGroupId(groupid);
//        group.setStatus(1);
//
//        Group savedGroup = save(group);
//
//        log.info("创建转诊群组:" + JSONUtils.toString(savedGroup));
//
//        return savedGroup;
        return new Group();
    }

    /**
     * 创建患者申请转诊群组
     *
     * @param bussId
     * @return
     * @author zhangx
     * @date 2015-12-21 下午6:06:30
     */
    @RpcService
    public Group createPatientTransferGroup(Integer bussId) {
//        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
//        UserSevice userService = new UserSevice();
//        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
//
//        if (!transferDAO.exist(bussId)) {
//            throw new DAOException(609, "不存在这条特需预约申请记录");
//        }
//        // 获取会诊申请单信息
//        Transfer transfer = transferDAO.get(bussId);
//        String requestMpiId = transfer.getRequestMpi();
//        int targetDoctorId = transfer.getTargetDoctor();
//
//        // 获取申请者urtid
//        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
//        Patient requestMpi = patientDAO.get(requestMpiId);
//        String requestMobile = requestMpi.getLoginId();
//        int requestUrtId = userService.getUrtIdByMobile(requestMobile,
//                "patient");
//
//        // 获取目标医生urtid
//        Doctor targetDoctor = docDao.get(targetDoctorId);
//        String targetMobile = targetDoctor.getMobile();
//        int targetUrtId = userService.getUrtIdByMobile(targetMobile, "doctor");
//
//        // 防止创建不成功，将参与用户都注册一遍
//        Easemob.registUser(Easemob.getPatient(requestUrtId), SystemConstant.EASEMOB_PATIENT_PWD);
//        if (targetUrtId > 0) {
//            Easemob.registUser(Easemob.getDoctor(targetUrtId), SystemConstant.EASEMOB_DOC_PWD);
//        }
//
//        // 获取会诊病人信息
//        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
//        Patient pat = patientDao.get(transfer.getMpiId());
//
//        String nick = Easemob.getTransferNick(bussId);
//        String title = pat.getPatientName() + "的特需预约讨论";
//
//        ArrayList<String> members = new ArrayList<String>();
//        if (targetUrtId > 0) {
//            members.add(Easemob.getDoctor(targetUrtId));
//        }
//        // 创建群组
//        ObjectNode node = Easemob.creatChatGroups(
//                Easemob.getPatient(requestUrtId), members, nick, title, 200,
//                true, true);
//
//        Group group = new Group();
//        if (node == null || node.get("data") == null
//                || node.get("data").get("groupid") == null) {
//            return group;
//        }
//
//        String groupid = node.get("data").get("groupid").asText();
//
//        group.setBussId(bussId);
//        group.setBussType(1);
//        group.setNick(nick);
//        group.setTitle(title);
//        group.setGroupId(groupid);
//        group.setStatus(1);
//
//        Group savedGroup = save(group);
//
//        log.info("创建特需预约群组:" + JSONUtils.toString(savedGroup));
//
//        return savedGroup;
        return new Group();
    }

    /**
     * 创建咨询群组
     *
     * @param bussId
     * @return
     * @author LF
     */
    @RpcService
    public Group createConsultGroup(Integer bussId) {
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        UserSevice userService = new UserSevice();

        if (!consultDAO.exist(bussId)) {
            throw new DAOException(609, LocalStringUtil.format("不存在这条咨询申请记录consultId[{}]", bussId));
        }

        Group group = new Group();

        // 获取咨询申请单信息
        Consult consult = consultDAO.get(bussId);
        String requestMpi = consult.getRequestMpi();
        Integer groupMode = consult.getGroupMode();
        Integer requestMode = consult.getRequestMode();
        Integer doctorId = consult.getExeDoctor();
        Integer consultDoctor = consult.getConsultDoctor();

        if(groupMode==null || groupMode==0) {
            if (doctorId == null) {
                doctorId = consultDoctor;
            }
        }else {
            doctorId = consultDoctor;
        }

        if (doctorId == null) {
            return group;
        }

        List<Consult> list = new ArrayList<Consult>();
        if(ValidateUtil.nullOrZeroInteger(groupMode)) {
            list = consultDAO.findByRequestMpiAndDoctorIdAndSessionGroup(requestMpi, doctorId, Easemob.getConsultNick() + "%", requestMode);
        }else {
            list = consultDAO.findByRequestMpiAndDoctorIdAndSession(requestMpi, doctorId, Easemob.getConsultNick() + "%", requestMode);
        }

        Integer requestUrtId = consult.getRequestMpiUrt();
        if(requestUrtId==null){
            throw new DAOException(609, "requestUrtId is null");
        }
        List<Integer> targetUrtIds = new ArrayList<Integer>();
        if (ValidateUtil.nullOrZeroInteger(groupMode)) {
            targetUrtIds.add(consult.getConsultDoctorUrt());
        } else {
            List<Integer> members = groupDAO.findMemberIdsByDoctorId(consultDoctor);
            List<String> mobiles = doctorDAO.findMobilesByDoctorIds(members);
            for (String mobile : mobiles) {
                targetUrtIds.add(userService.getUrtIdByUserId(mobile, "doctor"));
            }
        }

        //判断以前是否已存在咨询讨论组
        if (list.size() == 0) {
            // 防止创建不成功，将参与用户都注册一遍
            if (null != requestUrtId && requestUrtId > 0) {
                Easemob.registUser(Easemob.getPatient(requestUrtId), SystemConstant.EASEMOB_PATIENT_PWD);
            }

            for(Integer targetUrtId:targetUrtIds) {
                Easemob.registUser(Easemob.getDoctor(targetUrtId), SystemConstant.EASEMOB_DOC_PWD);
            }

            // 获取咨询病人信息
            PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
            Patient pat = patientDao.get(consult.getRequestMpi());

            String nick = Easemob.getConsultNick(bussId);
            String title = pat.getPatientName() + "的咨询";

            ArrayList<String> members = new ArrayList<String>();
            for(Integer targetUrtId:targetUrtIds) {
                members.add(Easemob.getDoctor(targetUrtId));
            }
            // 创建群组
            ObjectNode node = Easemob.creatChatGroups(
                    Easemob.getPatient(requestUrtId), members, nick, title, 200,
                    true, true);


            if (node == null || node.get("data") == null
                    || node.get("data").get("groupid") == null) {
                return group;
            }

            String groupid = node.get("data").get("groupid").asText();

            group.setBussId(bussId);
            group.setBussType(3);// 1转诊，2会诊，3咨询，4预约
            group.setNick(nick);
            group.setTitle(title);
            group.setGroupId(groupid);
            group.setStatus(1);

            Group savedGroup = save(group);

            log.info("创建咨询群组:" + JSONUtils.toString(savedGroup));
            return savedGroup;
        } else {
            Consult c = list.get(0);
            String groupId=c.getSessionID();
            group.setGroupId(groupId);
            log.info("申请人目标医生存在咨询群组:" + JSONUtils.toString(group));

            //修改BUG-6452,微信端解绑重新绑定导致的urtId改变，接收不到消息
            checkOrUpdateGroupOwner(groupId,Easemob.getPatient(requestUrtId));
            return group;
        }


//		//以管理员身份发送消息[您有一条来自xxx（申请人）的图文咨询申请，请及时回复；]
//		//健康2.1.1版本，去除已管理员身份发送消息
//		String msg="您有一条来自"+pat.getPatientName()+"的图文咨询申请，请及时回复；";
//		DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
//
//		Map<String, String> extProp =new HashMap<String, String>();
//		extProp.put("avatar",pat.getPhoto()==null?"":pat.getPhoto().toString());
//		extProp.put("name",pat.getPatientName());
//		extProp.put("gender",pat.getPatientSex());
//		extProp.put("busId",bussId.toString());
//		extProp.put("groupName",title);
//		extProp.put("busType","2");//1会诊，2咨询
//		extProp.put("appId",consult.getAppId());
//		extProp.put("openId",consult.getOpenId());
//
//		Easemob.sendSimpleMsg("admin", groupid, msg, "chatgroups",extProp);


    }

    /**
     * 创建会话随访群组
     * @param bussId
     * @return
     */
    /*@RpcService
    public Group createFollowChatGroup(Integer bussId) {

        FollowChatDAO followChatDAO = DAOFactory.getDAO(FollowChatDAO.class);

        if (!followChatDAO.exist(bussId)) {
            throw new DAOException(609, "不存在这条会话随访记录");
        }

        FollowChat followChat = followChatDAO.get(bussId);
        int requestUrtId = followChat.getDoctorUrt();

        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor requestDoc = docDao.get(followChat.getChatDoctor());

        List<FollowChat> oldChatList = followChatDAO.findByMpiIdAndDoctorIdAndGroupNick(followChat.getMpiId(),followChat.getChatDoctor(),Easemob.getFollowNick() + "%");
        if(oldChatList.size()==0) {
            Easemob.registUser(Easemob.getDoctor(requestUrtId), SystemConstant.EASEMOB_DOC_PWD);
            Easemob.registUser(Easemob.getPatient(followChat.getMpiUrt()), SystemConstant.EASEMOB_PATIENT_PWD);
            PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
            Patient pat = patientDao.get(followChat.getMpiId());

            String nick = Easemob.getFollowNick(bussId);
            String title = pat.getPatientName() + "的随访";

            ArrayList<String> members = new ArrayList<String>();
            members.add(Easemob.getPatient(followChat.getMpiUrt()));

            ObjectNode node = Easemob.creatChatGroups(
                    Easemob.getDoctor(requestUrtId), members, nick, title, 200,
                    true, true);

            Group group = new Group();
            if (node == null || node.get("data") == null
                    || node.get("data").get("groupid") == null) {
                return group;
            }
            String groupid = node.get("data").get("groupid").asText();

            group.setBussId(bussId);
            group.setBussType(15);
            group.setNick(nick);
            group.setTitle(title);
            group.setGroupId(groupid);
            group.setStatus(1);

            Group savedGroup = save(group);

            log.info("创建会话随访群组:" + JSONUtils.toString(savedGroup));
            return savedGroup;
        }else{
            log.info("取之前会话随访群组:" + JSONUtils.toString(oldChatList.get(0).getSessionID()));
            return new Group(oldChatList.get(0).getSessionID());
        }
    }*/

    /**
     * zhongzx
     * 新建的会话随访 获取groupId
     * 如果之前有过记录 沿用以前 没有记录新建一个group
     * @return
     */
    public String getGroupIdForFollowChat(FollowChat followChat){
        if(null == followChat){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "followChat is null");
        }
        String gId;
        Integer bussId = followChat.getId();
        Integer doctorUrt = followChat.getDoctorUrt();
        Integer mpiUrt = followChat.getMpiUrt();
        String mpiId = followChat.getMpiId();
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        FollowChatDAO followChatDAO = DAOFactory.getDAO(FollowChatDAO.class);
        Integer doctorId = followChat.getChatDoctor();

        /**
         * 如果之前有过会话随访记录 沿用以前会话随访里的groupId
         */
        List<FollowChat> oldChatList = followChatDAO.findFollowChatWithTime(mpiId, doctorId, null);
        if(null == oldChatList || oldChatList.size() == 0) {
            String easemobDoctor = Easemob.getDoctor(doctorUrt);
            String easemobPatient = Easemob.getPatient(mpiUrt);

            Easemob.registUser(easemobDoctor, SystemConstant.EASEMOB_DOC_PWD);
            Easemob.registUser(easemobPatient, SystemConstant.EASEMOB_PATIENT_PWD);

            String patientName = patientDAO.getPatientNameByMpiId(mpiId);

            String nick = Easemob.getFollowNick(bussId);
            String title = patientName + "的随访";

            ArrayList<String> members = new ArrayList();
            members.add(easemobPatient);

            ObjectNode node = Easemob.creatChatGroups(easemobDoctor, members, nick, title, 200, true, true);

            Group group = new Group();
            if (node == null || node.get("data") == null || node.get("data").get("groupid") == null) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "创建group失败");
            }
            String groupId = node.get("data").get("groupid").asText();

            group.setBussId(bussId);
            group.setBussType(15);
            group.setNick(nick);
            group.setTitle(title);
            group.setGroupId(groupId);
            group.setStatus(1);

            Group savedGroup = save(group);

            if (null == savedGroup) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "saveGroup is null");
            }
            gId = savedGroup.getGroupId();
        }else{
            gId = oldChatList.get(0).getSessionID();
        }
        return gId;
    }

    /**
     * 创建会诊群组
     *
     * @param bussId
     * @author ZX
     * @date 2015-6-10 下午7:45:46
     */
    @RpcService
    public Group creatMeetClinckGroup(Integer bussId) {

        MeetClinicDAO meetDao = DAOFactory.getDAO(MeetClinicDAO.class);
        UserSevice userService = new UserSevice();

        if (!meetDao.exist(bussId)) {
            throw new DAOException(609, "不存在这条会诊申请记录");
        }
        // 获取会诊申请单信息
        MeetClinic meetClinic = meetDao.get(bussId);
        int requestDocId = meetClinic.getRequestDoctor();

        // 获取申请医生urtid
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor requestDoc = docDao.get(requestDocId);
        String requestMobile = requestDoc.getMobile();
        int requestUrtId = userService
                .getUrtIdByUserId(requestMobile, "doctor");

        // 获取执行单信息
        EndMeetClinicDAO recordDao = DAOFactory.getDAO(EndMeetClinicDAO.class);
        List<MeetClinicResult> list = recordDao.findByMeetClinicId(bussId);

        List<Object> idList = new ArrayList<Object>();
        if (list.size() <= 0) {
            return new Group();
        }

        for (MeetClinicResult result : list) {
            int docId = result.getTargetDoctor();
            idList.add(docId);
        }

        List<DoctorOrPatientAndUrt> doctorOrPatientAndUrtList = userService
                .getUrtId(idList, "doctor");
        ArrayList<String> urtIdList = new ArrayList<String>();

        // 获取执行单医生UrtId
        for (DoctorOrPatientAndUrt doctorOrPatientAndUrt : doctorOrPatientAndUrtList) {
            Integer urtId = doctorOrPatientAndUrt.getUrtId();
            if (urtId == null || urtId <= 0) {
                continue;
            }
            urtIdList.add(Easemob.getDoctor(urtId));
        }

        // 防止创建不成功，将参与用户循环注册一遍
        Easemob.registUser(Easemob.getDoctor(requestUrtId), SystemConstant.EASEMOB_DOC_PWD);
        for (String string : urtIdList) {
            Easemob.registUser(string, SystemConstant.EASEMOB_DOC_PWD);
        }

        // 获取会诊病人信息
        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        Patient pat = patientDao.get(meetClinic.getMpiid());

        String nick = Easemob.getMeetClinicNick(bussId);
        String title = pat.getPatientName() + "的会诊讨论";

        // 创建群组
        ObjectNode node = Easemob.creatChatGroups(
                Easemob.getDoctor(requestUrtId), urtIdList, nick, title, 200,
                true, true);

        Group group = new Group();
        if (node == null || node.get("data") == null
                || node.get("data").get("groupid") == null) {
            return group;
        }
        String groupid = node.get("data").get("groupid").asText();

        group.setBussId(bussId);
        group.setBussType(2);
        group.setNick(nick);
        group.setTitle(title);
        group.setGroupId(groupid);
        group.setStatus(1);

        Group savedGroup = save(group);

        log.info("创建会诊群组:" + JSONUtils.toString(savedGroup));

        return savedGroup;
    }

    /**
     * 根据业务号获取群组信息
     *
     * @param bussType
     * @param bussId
     * @return
     * @author ZX
     * @date 2015-6-11 下午4:12:50
     */
    @RpcService
    @DAOMethod
    public abstract Group getByBussTypeAndBussId(Integer bussType,
                                                 Integer bussId);

    /**
     * 根据昵称查询群组信息
     *
     * @param nick
     * @return
     * @author ZX
     * @date 2015-6-17 下午3:27:19
     */
    @DAOMethod
    public abstract Group getByNick(String nick);

    /**
     * 根据主键跟新状态
     *
     * @param status
     * @param groupId
     * @return
     * @author ZX
     * @date 2015-6-11 下午5:44:44
     */
    @RpcService
    @DAOMethod
    public abstract void updateStatusByGroupId(int status, String groupId);

    /**
     * 关闭群组
     *
     * @param bussId
     * @author ZX
     * @date 2015-6-11 下午4:13:03
     */
    public void closeMeetClinckGroup(Integer bussId) {
        Group group = getByBussTypeAndBussId(2, bussId);
        if (group != null) {
            //String groupId = group.getGroupId();
            //Easemob.deleteChatGroups(groupId);
            //updateStatusByGroupId(0, groupId);
        }

        log.info("关闭群组-更新状态");
    }

    /**
     * 获取一个用户参与的所有群组
     *
     * @param urtId
     * @return
     * @author LF
     */
    @RpcService
    public ObjectNode getAllChatgroupsOfOne(Integer urtId) {
        String username = Easemob.getDoctor(urtId);
        ObjectNode AllChatGroupOfOne = Easemob.getJoinedChatgroupsAll(username);
        return AllChatGroupOfOne;
    }

    /**
     * 获取群组中的所有成员
     *
     * @param bussType
     * @param bussId
     * @return
     * @author ZX
     * @date 2015-6-15 上午11:59:46
     * @date 2016-3-3 luf 修改异常code
     */
    @RpcService
    public ObjectNode getAllMemberssByGroupId(Integer bussType, Integer bussId) {
        Group group = getByBussTypeAndBussId(bussType, bussId);
        if (group == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "不存在业务类型为："
                    + bussType + ",业务号为" + bussId + "的群组");
        }
        String groupId = group.getGroupId();
        ObjectNode getAllMemberssByGroupIdNode = Easemob
                .getAllMemberssByGroupId(groupId);
        return getAllMemberssByGroupIdNode;
    }

    /**
     * 群组加人[单个]
     *
     * @param addToChatgroupid
     * @param toAddUsername
     * @return
     * @author ZX
     * @date 2015-6-15 下午2:17:17
     */
    @RpcService
    public ObjectNode addUserToGroup(Integer bussType, Integer bussId,
                                     Integer toAddDoctorId) {
        Group group = getByBussTypeAndBussId(bussType, bussId);
        if (group == null) {
            if (bussType == 2) {
                group = creatMeetClinckGroup(bussId);
            } else if (bussType == 1) {
                // 2017-2-7 luf:关闭转诊创建群聊入口
//                group = createTransferGroup(bussId);
            } else if (bussType == 3) {
                group = createConsultGroup(bussId);
            }else{

            }
        }
        if(group==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"group is not find");
        }

        String addToChatgroupid = null;
        if (group != null && group.getGroupId() != null) {
            addToChatgroupid = group.getGroupId();
        }

        // 获取被添加医生urtid
        UserSevice userService = new UserSevice();
        int toAddUrtId = userService.getDoctorUrtIdByDoctorId(toAddDoctorId);

        String toAddUsername = Easemob.getDoctor(toAddUrtId);
        Easemob.registUser(toAddUsername, SystemConstant.EASEMOB_DOC_PWD);

        ObjectNode addUserToGroupNode = Easemob.addUserToGroup(
                addToChatgroupid, toAddUsername);

        return addUserToGroupNode;
    }

    /**
     * 群组减人
     *
     * @param delFromChatgroupid
     * @param toRemoveUsername
     * @return
     * @author ZX
     * @date 2015-6-15 下午2:23:19
     * @date 2016-3-3 luf 修改异常code
     */
    @RpcService
    public ObjectNode deleteUserFromGroup(Integer bussType, Integer bussId,
                                          Integer toDelDoctorId) {
        Group group = getByBussTypeAndBussId(bussType, bussId);
        if (group == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "不存在业务类型为："
                    + bussType + ",业务号为" + bussId + "的群组");
        }
        String delFromChatgroupid = group.getGroupId();

        // 获取被删除医生urtid
        UserSevice userService = new UserSevice();
        int toDelUrtId = userService.getDoctorUrtIdByDoctorId(toDelDoctorId);
        String toRemoveUsername = Easemob.getDoctor(toDelUrtId);

        ObjectNode deleteUserFromGroupNode = Easemob.deleteUserFromGroup(
                delFromChatgroupid, toRemoveUsername);
        return deleteUserFromGroupNode;
    }

    /**
     * 前端主动发起创建群组请求
     *
     * @param groupInfo
     * @return
     * @author ZX
     * @date 2015-6-17 下午3:27:45
     */
    @SuppressWarnings("unchecked")
    @RpcService
    public Object createIMGroup(Map<String, Object> groupInfo) {

        String nick = Easemob.getNick(groupInfo.get("groupname").toString());
        Group group = getByNick(nick);

        // 本地数据库中存在群组信息，将查询到的信息返回给前端
        if (group != null) {
            return group;
        }

        // 如果本地服务数据库中不存在群组信息，去环信服务器查询是否存在
        String owner = Easemob.getOwnerOrMember(groupInfo.get("owner")
                .toString());
        Easemob.registUser(owner, SystemConstant.EASEMOB_DOC_PWD);

        // 获取用户参与的所有群组信息
        ObjectNode AllChatGroupOfOne = Easemob.getJoinedChatgroupsAll(owner);
        Iterable<JsonNode> chatGroups = AllChatGroupOfOne.get("data");

        if (chatGroups != null) {
            // 循环对比，查出是否环信服务器上已经存在该群组信息，如果存在，赋值，返回
            for (JsonNode jsonNode : chatGroups) {
                String groupname = jsonNode.get("groupname").asText();
                String groupid = jsonNode.get("groupid").asText();
                if (nick.equals(groupname)) {
                    group = new Group();
                    group.setGroupId(groupid);
                    group.setNick(groupname);
                    break;
                }
            }
        }

        // 环信服务器上的不存在,环信上新增群组
        if (group == null) {
            String requestName = Easemob.getOwnerOrMember(groupInfo
                    .get("owner").toString());
            ArrayList<String> members = new ArrayList<String>();
            ArrayList<String> memberList = (ArrayList<String>) groupInfo
                    .get("members");
            for (String member : memberList) {
                members.add(Easemob.getOwnerOrMember(member));
                Easemob.registUser(Easemob.getOwnerOrMember(member),
                        SystemConstant.EASEMOB_DOC_PWD);
            }

            String chatGroupNick = Easemob.getNick(groupInfo.get("groupname")
                    .toString());
            String title = groupInfo.get("desc").toString();
            int maxusers = Integer.parseInt(groupInfo.get("maxusers")
                    .toString());
            Boolean isPublic = (Boolean) groupInfo.get("public");
            Boolean approval = (Boolean) groupInfo.get("approval");

            // 环信服务器创建群组
            ObjectNode creatChatGroupNode = Easemob.creatChatGroups(
                    requestName, members, chatGroupNick, title, maxusers,
                    approval, isPublic);

            group = new Group();
            group.setGroupId(creatChatGroupNode.get("data").get("groupid")
                    .asText());
            group.setNick(chatGroupNick);
        }

        // 保存数据库
        String[] sourceStrArray = nick.split("_");
        if (sourceStrArray.length == 0) {
            log.info("groupname[" + nick + "]不符合规定");
        }
        if (Easemob.getMODE().equals("")) {
            group.setBussId(Integer.parseInt(sourceStrArray[1]));
            if (sourceStrArray[0].equals("meetClinic")) {
                group.setBussType(2);
            }
        } else {
            group.setBussId(Integer.parseInt(sourceStrArray[2]));
            if (sourceStrArray[1].equals("meetClinic")) {
                group.setBussType(2);
            }
        }
        group.setStatus(1);
        group.setTitle(groupInfo.get("desc").toString());
        Group savedGroup = save(group);

        log.info("创建会诊群组:" + JSONUtils.toString(savedGroup));
        return savedGroup;
    }

    /**
     * 添加群组成员
     *
     * @author ZX
     * @date 2015-6-17 下午5:14:56
     */
    @RpcService
    public ObjectNode addIMGroupMember(String groupId, String memberId) {
        // 将需要添加的成员先在环信服务器上注册一下
        Easemob.registUser(Easemob.getOwnerOrMember(memberId), SystemConstant.EASEMOB_DOC_PWD);

        // 添加群组成员
        ObjectNode addUserToGroupNode = Easemob.addUserToGroup(groupId,
                Easemob.getOwnerOrMember(memberId));

        return addUserToGroupNode;
    }

    /**
     * 删除群组成员
     *
     * @author ZX
     * @date 2015-6-17 下午5:14:56
     */
    @RpcService
    public ObjectNode delIMGroupMember(String groupId, String memberId) {
        // 删除群组成员
        ObjectNode deleteUserFromGroupNode = Easemob.deleteUserFromGroup(
                groupId, Easemob.getOwnerOrMember(memberId));
        return deleteUserFromGroupNode;
    }

    /**
     * 关闭群组
     *
     * @param groupId
     * @return
     * @author ZX
     * @date 2015-6-17 下午8:06:25
     */
    @RpcService
    public ObjectNode closeIMGroup(String groupId) {
        ObjectNode deleteChatGroupNode = Easemob.deleteChatGroups(groupId);
        //updateStatusByGroupId(0, groupId);
        return deleteChatGroupNode;
    }

    /**
     * 获取群组所有成员信息
     *
     * @param groupId
     * @return
     * @author ZX
     * @date 2015-6-17 下午8:12:51
     */
    @RpcService
    public ObjectNode getIMMemberForGroup(String groupId) {
        ObjectNode getAllMemberssByGroupIdNode = Easemob
                .getAllMemberssByGroupId(groupId);
        return getAllMemberssByGroupIdNode;
    }

    /**
     * 检验群主是否正确
     * @param groupId
     * @param newOwner
     */
    private void checkOrUpdateGroupOwner(String groupId,String newOwner){
        Boolean bool=true;
        ObjectNode allmember=Easemob.getAllMemberssByGroupId(groupId);
        JsonNode members=allmember.get("data");

        String oldOwner=newOwner;
        for (JsonNode node:members) {
            JsonNode onwer=node.get("owner");
            if(onwer!=null){
                oldOwner=onwer.asText();
                break;
            }
        }

        //当前群的群主并不是申请人
        if(!StringUtils.isEmpty(newOwner) && !newOwner.equals(oldOwner)){
            //修改群主
            Easemob.changeOwner(groupId,newOwner);
        }
    }

    @DAOMethod
    public abstract void updateTitleByGroupId(String title,String groupId);

}
