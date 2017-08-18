package eh.msg.service;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import eh.base.constant.DoctorConstant;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.DoctorDAO;
import eh.base.user.UserSevice;
import eh.bus.constant.ConsultConstant;
import eh.bus.constant.MsgTypeEnum;
import eh.bus.service.consult.ConsultMessageService;
import eh.bus.service.meetclinic.MeetClinicService;
import eh.entity.base.Doctor;
import eh.entity.msg.Group;
import eh.msg.dao.GroupDAO;
import eh.msg.dao.GroupMemberDAO;
import eh.util.Easemob;
import eh.wxpay.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by Luphia on 2017/3/15.
 */
@RpcBean
public class GroupService {
    public static final Logger log = LoggerFactory.getLogger(GroupService.class);

    /**
     * 修改群名称
     *
     * @param groupId
     * @param title
     * @param urtId
     * @param doctorName
     */
    @RpcService
    public void updateTitleAndSendMsg(String groupId, String title, int urtId, String doctorName) {
        GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
        groupDAO.updateTitleByGroupId(title, groupId);

        EasemobIMService imService = AppContextHolder.getBean("eh.imService", EasemobIMService.class);
        String prefix = "text://tips?content=";
        String msgContent = "修改医生群的名称为“" + title + "”";
        try {
            msgContent = prefix + URLEncoder.encode(msgContent, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error(e.getMessage());
        }
        Map<String, String> ext = Maps.newHashMap();
        ext.put("busId", "");
        ext.put("busType", "8");    //0转诊  1会诊 2咨询 3在线续方  5专家会诊  6 随访  7 专家解读  8群聊
        ext.put("urtId", String.valueOf(urtId));
        ext.put("doctorName", doctorName);
        ext.put("groupName", title);
        ext.put("hasDoctorPrefix", String.valueOf(true));
        //发送环信消息
        imService.sendMsgToGroupByDoctorUrt(urtId, groupId, msgContent, ext);
    }

    public void createChatGroupMsg(String groupId, int urtId, String names, String doctorName) {
        EasemobIMService imService = AppContextHolder.getBean("eh.imService", EasemobIMService.class);
        String prefix = "text://tips?content=";
        String msgContent = "邀请" + names + "加入了医生群";
        try {
            msgContent = prefix + URLEncoder.encode(msgContent, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error(e.getMessage());
        }
        Map<String, String> ext = Maps.newHashMap();
        ext.put("busId", "");
        ext.put("busType", "8");    //0转诊  1会诊 2咨询 3在线续方  5专家会诊  6 随访  7 专家解读  8群聊
        ext.put("urtId", String.valueOf(urtId));
        ext.put("doctorName", doctorName);
        ext.put("names", names);
        ext.put("hasDoctorPrefix", String.valueOf(true));
        //发送环信消息
        imService.sendMsgToGroupByDoctorUrt(urtId, groupId, msgContent, ext);
    }

    /**
     * 创建群聊群组
     *
     * @param owner
     * @param members
     * @param title
     * @return
     */
    @RpcService
    public Group creatChatGroup(int owner, List<Integer> members, String title) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor o = doctorDAO.get(owner);
        if (o == null) {
            log.error("owner is required!");
            return new Group();
        }
        String ownerName = o.getName();
        Integer ownerUrtId = Util.getUrtForDoctor(o.getMobile());
        if (ownerUrtId == null || ownerUrtId <= 0) {
            log.error("ownerUrtId is required!");
            return new Group();
        }
        ArrayList<String> memberUrtIds = new ArrayList<>();
        StringBuilder names = new StringBuilder();
        for (Integer member : members) {
            Doctor d = doctorDAO.get(member);
            if (d == null) {
                continue;
            }
            Integer memberUrtId = Util.getUrtForDoctor(d.getMobile());
            if (memberUrtId != null && memberUrtId > 0) {
                memberUrtIds.add(Easemob.getDoctor(memberUrtId));
                names.append("“").append(d.getName()).append("”、");
            }
        }
        if (memberUrtIds == null || memberUrtIds.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "memberUrtIds is required!");
        }

        // 防止创建不成功，将参与用户循环注册一遍
        Easemob.registUser(Easemob.getDoctor(ownerUrtId), SystemConstant.EASEMOB_DOC_PWD);
        for (String string : memberUrtIds) {
            Easemob.registUser(string, SystemConstant.EASEMOB_DOC_PWD);
        }

        String nick = String.valueOf(System.currentTimeMillis())
                + String.valueOf(ThreadLocalRandom.current()
                .nextInt(1000, 9999));

        // 创建群组
        ObjectNode node = Easemob.creatChatGroups(
                Easemob.getDoctor(ownerUrtId), memberUrtIds, nick, title, 200,
                true, true);

        Group group = new Group();
        if (node == null || node.get("data") == null
                || node.get("data").get("groupid") == null) {
            return group;
        }
        String groupid = node.get("data").get("groupid").asText();

        GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
        GroupMemberService memberService = AppDomainContext.getBean("eh.groupMemberService", GroupMemberService.class);

        group.setBussId(null);
        group.setBussType(16);
        group.setNick(nick);
        group.setTitle(title);
        group.setGroupId(groupid);
        group.setStatus(1);

        //保存群组信息
        Group savedGroup = groupDAO.save(group);
        //增加成员
        memberService.addGroupMember(groupid, owner, members);
        //推送邀请消息
        createChatGroupMsg(groupid, ownerUrtId, names.substring(0, names.length() - 1), ownerName);

        log.info("创建会诊群组:[{}]", JSONUtils.toString(savedGroup));

        return savedGroup;
    }

    /**
     * 解散群组
     *
     * @param groupId
     * @param doctorId
     */
    @RpcService
    public void deleteChatGroup(String groupId, int doctorId) {
        GroupMemberService memberService = AppDomainContext.getBean("eh.groupMemberService", GroupMemberService.class);
        Boolean isOwner = memberService.isOwner(groupId, doctorId);
        if (!isOwner) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不是群主，没有权限");
        }

        ObjectNode deleteChatGroupNode = Easemob.deleteChatGroupsNoMsg(groupId);

        if (deleteChatGroupNode != null) {
            GroupMemberDAO memberDAO = DAOFactory.getDAO(GroupMemberDAO.class);
            GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);

            groupDAO.updateStatusByGroupId(0, groupId);

            List<Integer> ids = memberDAO.findIdsByGroupId(groupId);
            for (Integer id : ids) {
                memberDAO.remove(id);
            }
        }
    }

    /**
     * 获取群名称
     *
     * @param groupId
     * @return
     */
    @RpcService
    public String getTitleByGroupId(String groupId) {
        if (groupId == null || StringUtils.isEmpty(groupId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "groupId is required!");
        }
        GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
        Group g = groupDAO.get(groupId);
        String title = "";
        if (g != null) {
            title = g.getTitle();
        }
        return title;
    }

    /**
     * 获取群名称列表
     *
     * @param groupIds
     * @return
     */
    @RpcService
    public List<Group> getByGroupIds(List<String> groupIds) {
        List<Group> results = new ArrayList<>();
        if (groupIds == null || groupIds.isEmpty()) {
            return results;
        }
        GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
        for (String groupId : groupIds) {
            Group group = groupDAO.get(groupId);
            if (group != null) {
                results.add(group);
            }
        }
        return results;
    }

    @RpcService
    public void removeMeetClinicGroupMsg(int meetClinicId, List<Integer> doctorIds, String groupId) {
        EasemobIMService imService = AppContextHolder.getBean("eh.imService", EasemobIMService.class);
        ConsultMessageService msgService = AppContextHolder.getBean("eh.consultMessageService", ConsultMessageService.class);
        UserSevice userSevice = AppDomainContext.getBean("eh.userSevice", UserSevice.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        MeetClinicService meetClinicService = AppDomainContext.getBean("eh.meetClinicService", MeetClinicService.class);
        String patientName = meetClinicService.getPatientNameByMeetclinicId(meetClinicId);
        String prefix = "text://tips?content=";
        Map<String, String> ext = Maps.newHashMap();
        ext.put("busId", String.valueOf(meetClinicId));
        ext.put("busType", "1");    //0转诊  1会诊 2咨询 3在线续方  5专家会诊  6 随访  7 专家解读  8群聊
        ext.put("groupName", patientName + "的会诊");
        for (Integer doctorId : doctorIds) {
            Doctor doctor = doctorDAO.getByDoctorId(doctorId);
            if (doctor == null) {
                continue;
            }
            String name = doctor.getName();
            String msgContent = name + "医生已被移出该讨论组";
            try {
                msgContent = prefix + URLEncoder.encode(msgContent, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                log.error(e.getMessage());
                continue;
            }

            try {
                Integer urtId = userSevice.getUrtIdByUserId(doctor.getMobile(), DoctorConstant.DOCTOR);

                //防止删除不成功 先进行注册
                String toDeleteUsername = Easemob.getDoctor(urtId);
                Easemob.registUser(toDeleteUsername, SystemConstant.EASEMOB_DOC_PWD);
                //删除成员
                Easemob.deleteUserFromGroup(groupId, toDeleteUsername);

                ext.put("urtId", urtId + "");
                ext.put("doctorName", name);
                //发送环信消息
                imService.sendMsgToGroupByDoctorUrt(urtId, groupId, msgContent, ext);
                //消息记录至数据库
                msgService.doctorSendMsgWithConsultId("", ConsultConstant.BUS_TYPE_MEET, meetClinicId, String.valueOf(MsgTypeEnum.TEXT.getId()), msgContent);
            } catch (Exception e) {
                log.error("doctor[{}] update MeetClinic[{}] remove doctor send msg to easemob:[{}],errorMsg=[{}]", doctorId, meetClinicId, msgContent, JSONObject.toJSONString(e.getStackTrace()));
                continue;
            }
        }
    }
}
