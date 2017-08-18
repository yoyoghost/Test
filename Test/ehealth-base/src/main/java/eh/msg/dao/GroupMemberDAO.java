package eh.msg.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import ctd.util.annotation.RpcSupportDAO;
import eh.entity.msg.GroupMember;

import java.util.List;

/**
 * Created by Luphia on 2017/3/15.
 */
@RpcSupportDAO(serviceId = "groupMember")
public abstract class GroupMemberDAO extends HibernateSupportDelegateDAO<GroupMember> {

    public GroupMemberDAO() {
        super();
        this.setEntityName(GroupMember.class.getName());
        this.setKeyField("id");
    }

    /**
     * 获取当前医生所有群聊ID
     *
     * @param doctorId
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select groupId from GroupMember where doctorId=:doctorId")
    public abstract List<String> findGroupIdsByDoctorId(@DAOParam("doctorId") int doctorId);

    /**
     * 获取群聊所有成员医生内码
     *
     * @param groupId
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select doctorId from GroupMember where groupId=:groupId")
    public abstract List<Integer> findDoctorIdsByGroupId(@DAOParam("groupId") String groupId);

    @DAOMethod(sql = "select id from GroupMember where groupId=:groupId")
    public abstract List<Integer> findIdsByGroupId(@DAOParam("groupId") String groupId);

    @DAOMethod
    public abstract GroupMember getByGroupIdAndDoctorId(String groupId, int doctorId);
}