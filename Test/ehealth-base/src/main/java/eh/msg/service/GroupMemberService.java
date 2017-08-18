package eh.msg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.msg.Group;
import eh.entity.msg.GroupMember;
import eh.msg.dao.GroupDAO;
import eh.msg.dao.GroupMemberDAO;
import eh.util.Easemob;
import eh.wxpay.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Luphia on 2017/3/15.
 */
@RpcBean
public class GroupMemberService {
    public static final Logger log = Logger.getLogger(GroupMemberService.class);

    public void addGroupMember(String groupId, int doctorId, List<Integer> members) {
        if (groupId == null || StringUtils.isEmpty(groupId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "groupId is required!");
        }
        GroupMemberDAO memberDAO = DAOFactory.getDAO(GroupMemberDAO.class);
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);

        //增加群主
        member.setDoctorId(doctorId);
        member.setOwner(true);
        memberDAO.save(member);

        //增加成员
        member.setOwner(false);
        for (Integer m : members) {
            member.setDoctorId(m);
            memberDAO.save(member);
        }
    }

    /**
     * 当前医生是否是群主
     *
     * @param groupId
     * @param doctorId
     * @return
     */
    @RpcService
    public boolean isOwner(String groupId, int doctorId) {
        if (groupId == null || StringUtils.isEmpty(groupId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "groupId is required!");
        }
        GroupMemberDAO memberDAO = DAOFactory.getDAO(GroupMemberDAO.class);
        GroupMember member = memberDAO.getByGroupIdAndDoctorId(groupId, doctorId);
        if (member != null && member.getOwner() != null && member.getOwner()) {
            return true;
        }
        return false;
    }

    /**
     * 群组加人
     *
     * @param groupId
     * @param members
     * @param doctorId
     */
    @RpcService
    public void addMembersToGroup(String groupId, List<Integer> members, int doctorId) {
        if (groupId == null || StringUtils.isEmpty(groupId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "groupId is required!");
        }
        if (members == null || members.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "members is required!");
        }
        GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
        Group group = groupDAO.get(groupId);
        if (group == null || group.getStatus() == null || group.getStatus().equals(0)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "group is required!");
        }

        GroupMemberDAO memberDAO = DAOFactory.getDAO(GroupMemberDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        List<Integer> inMembers = memberDAO.findDoctorIdsByGroupId(groupId);
        int size = members.size();
        if (inMembers != null && !inMembers.isEmpty()) {
            size += inMembers.size();
        }
        if (size > 100) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "人数已到达上限，无法邀请成员加入");
        }
        StringBuffer names = new StringBuffer();

        GroupMember groupMember = new GroupMember();
        groupMember.setGroupId(groupId);
        groupMember.setOwner(false);

        for (Integer member : members) {
            Doctor d = doctorDAO.get(member);
            if (d == null) {
                continue;
            }
            Integer mUrtId = Util.getUrtForDoctor(d.getMobile());
            if (mUrtId == null || mUrtId <= 0) {
                continue;
            }
            //防止添加不成功 先进行注册
            String toAddUsername = Easemob.getDoctor(mUrtId);
            Easemob.registUser(toAddUsername, SystemConstant.EASEMOB_DOC_PWD);
            //添加成员
            ObjectNode addUserToGroupNode = Easemob.addUserToGroup(groupId, toAddUsername);
            if (addUserToGroupNode != null) {
                groupMember.setDoctorId(member);

                if (!inMembers.contains(member)) {
                    memberDAO.save(groupMember);
                    names.append("“").append(d.getName()).append("”、");
                }
            }
        }

        if (names != null && !StringUtils.isEmpty(names)) {
            GroupService groupService = AppDomainContext.getBean("eh.groupService", GroupService.class);
            Doctor addDoc = doctorDAO.get(doctorId);
            if (addDoc != null) {
                groupService.createChatGroupMsg(groupId, Util.getUrtForDoctor(addDoc.getMobile()), names.substring(0, names.length() - 1), addDoc.getName());
            }
        }
    }

    /**
     * 群组减人
     *
     * @param groupId
     * @param members
     * @param doctorId
     */
    @RpcService
    public void removeMembers(String groupId, List<Integer> members, int doctorId) {
        if (groupId == null || StringUtils.isEmpty(groupId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "groupId is required!");
        }
        if (members == null || members.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "members is required!");
        }
        GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
        Group group = groupDAO.get(groupId);
        if (group == null || group.getStatus() == null || group.getStatus().equals(0)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "group is required!");
        }

        Boolean isOwner = this.isOwner(groupId, doctorId);
        if (!isOwner) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不是群主，没有权限");
        }

        GroupMemberDAO memberDAO = DAOFactory.getDAO(GroupMemberDAO.class);
        if (members.contains(doctorId)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不能删除群主");
        }

        List<Integer> urts = getUrtList(groupId);
        for (Integer m : members) {
            Integer mUrtId = Util.getUrtByDoctorId(m);
            if (mUrtId == null || mUrtId <= 0) {
                continue;
            }
            //防止删除不成功 先进行注册
            String toDeleteUsername = Easemob.getDoctor(mUrtId);
            Easemob.registUser(toDeleteUsername, SystemConstant.EASEMOB_DOC_PWD);
            //删除成员
            ObjectNode deleteUserFromGroupNode = Easemob.deleteUserFromGroup(groupId, toDeleteUsername);

            GroupMember gm = memberDAO.getByGroupIdAndDoctorId(groupId, m);
            boolean isMove = false;
            if (gm != null && gm.getId() != null) {
                if (deleteUserFromGroupNode != null) {
                    isMove = true;
                } else {
                    if (urts != null && !urts.isEmpty()) {
                        isMove = !urts.contains(mUrtId);
                    }
                }
            }
            if (isMove) {
                memberDAO.remove(gm.getId());
            }
        }
    }

    /**
     * 退出群聊
     *
     * @param groupId
     * @param doctorId
     * @param flag     标志-是否要推退群提示
     */
    @RpcService
    public void exitGroupChat(String groupId, int doctorId, boolean flag) {
        if (groupId == null || StringUtils.isEmpty(groupId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "groupId is required!");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        if (doctor == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctor is required!");
        }
        Integer urtId = Util.getUrtForDoctor(doctor.getMobile());
        if (urtId == null || urtId <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "urtId is required!");
        }

        GroupMemberDAO memberDAO = DAOFactory.getDAO(GroupMemberDAO.class);
        GroupMember gm = memberDAO.getByGroupIdAndDoctorId(groupId, doctorId);
        ObjectNode deleteUserFromGroupNode = null;
        if (gm == null || gm.getOwner() == null || !gm.getOwner()) {
            deleteUserFromGroupNode = Easemob.deleteUserFromGroup(groupId, Easemob.getDoctor(urtId));
        }
        boolean isMove = false;
        if (gm != null && gm.getOwner() != null && !gm.getOwner()) {
            if (deleteUserFromGroupNode == null) {
                List<Integer> urts = getUrtList(groupId);
                if (urts != null && !urts.isEmpty()) {
                    isMove = !urts.contains(urtId);
                }
            } else {
                isMove = true;
            }
        }
        if (isMove) {
            memberDAO.remove(gm.getId());

            if (flag) {
                EasemobIMService imService = AppContextHolder.getBean("eh.imService", EasemobIMService.class);
                String prefix = "text://tips?content=";
                String msgContent = new String(doctor.getName() + "退出了医生群");
                try {
                    msgContent = prefix + URLEncoder.encode(msgContent, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    log.error(e.getMessage());
                }
                Map<String, String> ext = Maps.newHashMap();
                ext.put("busId", "");
                ext.put("busType", "8");    //0转诊  1会诊 2咨询 3在线续方  5专家会诊  6 随访  7 专家解读  8群聊
                ext.put("hasDoctorPrefix", false + "");
                //发送环信消息
                imService.sendMsgToGroupByDoctorUrt(urtId, groupId, msgContent, ext);
            }
        }
    }

    /**
     * 获取医生信息列表
     *
     * @param groupId
     * @return
     */
    @RpcService
    public List<Doctor> findDoctorsByGroupId(String groupId) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        GroupMemberDAO memberDAO = DAOFactory.getDAO(GroupMemberDAO.class);
        List<Integer> ids = memberDAO.findDoctorIdsByGroupId(groupId);
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<Doctor>();
        }
        List<Doctor> ds = doctorDAO.findEffectiveDocByDoctorIdIn(ids);

        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<Doctor> results = new ArrayList<Doctor>();
        for (Doctor d : ds) {
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(d.getDoctorId());
            if (employment != null) {
                d.setDepartment(employment.getDepartment());
            }
            results.add(d);
        }
        return results;
    }

    /**
     * 获取群成员urt列表
     *
     * @param groupId
     * @return
     */
    @RpcService
    public List<Integer> getUrtList(String groupId) {
        ObjectNode objectNode = Easemob.getAllMemberssByGroupId(groupId);
        List<Integer> urts = new ArrayList<Integer>();
        if (objectNode != null && objectNode.get("data") != null) {
            ArrayNode nodes = (ArrayNode) objectNode.get("data");
            if (nodes != null) {
                for (int i = 0; i < nodes.size(); i++) {
                    JsonNode node = nodes.get(i);
                    if (node != null) {
                        String member = null;
                        if (node.get("member") != null) {
                            member = node.get("member").asText();
                        } else if (node.get("owner") != null) {
                            member = node.get("owner").asText();
                        }
                        if (member != null) {
                            String m = null;
                            String docPre = Easemob.getDoctorPrefix();
                            String patPre = Easemob.getPatientPrefix();
                            if (member.contains(docPre)) {
                                m = member.replace(docPre, "");
                            } else if (member.contains(patPre)) {
                                m = member.replace(patPre, "");
                            }
                            if (m != null && !StringUtils.isEmpty(m)) {
                                urts.add(Integer.valueOf(m));
                            }
                        }
                    }
                }
            }
        }
        return urts;
    }
}
