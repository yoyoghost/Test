package eh.util;

import com.easemob.server.example.comm.Constants;
import com.easemob.server.example.comm.HTTPMethod;
import com.easemob.server.example.comm.Roles;
import com.easemob.server.example.httpclient.apidemo.EasemobChatGroups;
import com.easemob.server.example.httpclient.apidemo.EasemobChatMessage;
import com.easemob.server.example.httpclient.apidemo.EasemobIMUsers;
import com.easemob.server.example.httpclient.apidemo.EasemobMessages;
import com.easemob.server.example.httpclient.utils.HTTPClientUtils;
import com.easemob.server.example.httpclient.vo.ClientSecretCredential;
import com.easemob.server.example.httpclient.vo.Credential;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ctd.util.JSONUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Easemob {
    private static final Logger LOGGER = LoggerFactory.getLogger(Easemob.class);
    private static final JsonNodeFactory factory = new JsonNodeFactory(false);
    private static final String APPKEY = Constants.APPKEY;

    // 创建群组时添加前缀
    private static String MODE = "";
    private static String DOCTOR = "doctor_";
    private static String PATIENT = "patient_";
    private static String TRANSFER_NICK = "transfer_";
    private static String CONSULT_NICK = "consult_";
    private static String MEETCLINIC_NICK = "meetClinic_";
    private static String FOLLOW_NICK = "follow_";

    public static String getMODE() {
        return MODE;
    }

    public static void setMODE(String mODE) {
        MODE = mODE;
        DOCTOR = MODE + DOCTOR;
        PATIENT = MODE + PATIENT;
        TRANSFER_NICK = MODE + TRANSFER_NICK;
        CONSULT_NICK = MODE + CONSULT_NICK;
        MEETCLINIC_NICK = MODE + MEETCLINIC_NICK;
        FOLLOW_NICK = MODE + FOLLOW_NICK;
    }

    public static String getDoctorPrefix() {
        return DOCTOR;
    }

    public static String getPatientPrefix() {
        return PATIENT;
    }



    public static String getDoctor(int urtId) {
        return DOCTOR + urtId;
    }

    public static String getPatient(int urtId) {
        return PATIENT + urtId;
    }

    public static String getNick(String nick) {
        return MODE + nick;
    }

    public static String getOwnerOrMember(String om) {
        return MODE + om;
    }

    public static String getTransferNick(int bussId) {
        return TRANSFER_NICK + bussId;
    }

    public static String getConsultNick(int bussId) {
        return CONSULT_NICK + bussId;
    }

    public static String getMeetClinicNick(int bussId) {
        return MEETCLINIC_NICK + bussId;
    }

    public static String getConsultNick(){
        return CONSULT_NICK;
    }

    public static String getFollowNick(int bussId) {
        return FOLLOW_NICK + bussId;
    }

    public static String getFollowNick(){ return FOLLOW_NICK; }

    // 通过app的client_id和client_secret来获取app管理员token
    private static Credential credential = new ClientSecretCredential(
            Constants.APP_CLIENT_ID, Constants.APP_CLIENT_SECRET,
            Roles.USER_ROLE_APPADMIN);

    /**
     * 创建一个用户
     *
     * @param userName
     * @param password
     * @author ZX
     * @date 2015-6-10 下午2:38:31
     */
    public static void registUser(String userName, String password) {
        ObjectNode datanode = JsonNodeFactory.instance.objectNode();
        datanode.put("username", userName);
        datanode.put("password", password);
        ObjectNode createNewIMUserSingleNode = EasemobIMUsers
                .createNewIMUserSingle(datanode);
        if (null != createNewIMUserSingleNode) {
            LOGGER.info("注册IM用户[单个]: " + createNewIMUserSingleNode.toString());
        }
    }

    /**
     * IM用户登录
     *
     * @param userName
     * @param password
     * @author ZX
     * @date 2015-6-10 下午4:31:05
     */
    public static void login(String userName, String password) {
        ObjectNode imUserLoginNode = EasemobIMUsers.imUserLogin(userName,
                password);
        if (null != imUserLoginNode) {
            LOGGER.info("IM用户登录: " + imUserLoginNode.toString());
        }
    }

    /**
     * 创建群组
     *
     * @author ZX
     * @date 2015-6-10 下午4:34:46
     */
    @SuppressWarnings("deprecation")
    public static ObjectNode creatChatGroups(String requestName,
                                             ArrayList<String> members, String nick, String title, int maxusers,
                                             boolean approval, boolean isPublic) {
        ObjectNode dataObjectNode = JsonNodeFactory.instance.objectNode();
        dataObjectNode.put("groupname", nick);
        dataObjectNode.put("desc", title);
        dataObjectNode.put("approval", approval);
        dataObjectNode.put("public", isPublic);
        dataObjectNode.put("maxusers", maxusers);
        dataObjectNode.put("owner", requestName);
        ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();

        // 添加群组成员
        for (String memberName : members) {
            arrayNode.add(memberName);
        }
        dataObjectNode.put("members", arrayNode);
        ObjectNode creatChatGroupNode = null;
        int maxTimes = 5;
        for(int i=0; i<maxTimes; i++){
            creatChatGroupNode = EasemobChatGroups
                    .creatChatGroups(dataObjectNode);
            if (creatChatGroupNode == null || creatChatGroupNode.get("data") == null
                    || creatChatGroupNode.get("data").get("groupid") == null){
                LOGGER.info("IM创建群组 retryTimes[{}], maxTimes[{}], currentResult[{}]", i, maxTimes,creatChatGroupNode==null?"null":creatChatGroupNode.toString());
            } else {
                LOGGER.info("IM创建群组result[{}]", creatChatGroupNode.toString());
                return creatChatGroupNode;
            }
        }
        return creatChatGroupNode;
    }

    /**
     * 删除群组(保留聊天记录)
     *
     * @param toDelChatgroupid
     * @author ZX
     * @date 2015-6-11 下午3:37:20
     * @date 2016-3-8 luf 由于环信接口变化，删除群组便无法查看聊天记录
     */
    public static ObjectNode deleteChatGroups(String toDelChatgroupid) {
        // ObjectNode deleteChatGroupNode = EasemobChatGroups
        // .deleteChatGroups(toDelChatgroupid);
        // LOGGER.info("IM删除群组: " + deleteChatGroupNode.toString());
        return null; // deleteChatGroupNode;
    }

    /**
     * 删除群组
     *
     * @param toDelChatgroupid
     * @return
     */
    public static ObjectNode deleteChatGroupsNoMsg(String toDelChatgroupid) {
        ObjectNode deleteChatGroupNode = EasemobChatGroups
                .deleteChatGroups(toDelChatgroupid);
        LOGGER.info("IM删除群组: " + deleteChatGroupNode.toString());
        return deleteChatGroupNode;
    }

    /**
     * 获取一个用户参与的所有群组
     *
     * @param username
     * @return
     * @author LF
     */
    public static ObjectNode getJoinedChatgroupsAll(String username) {
        ObjectNode getJoinedChatgroupsForIMUserNode = getJoinedChatgroupsForIMUser(username);
        LOGGER.info("IM获取一个用户参与的所有群组: "
                + getJoinedChatgroupsForIMUserNode.toString());
        return getJoinedChatgroupsForIMUserNode;
    }

    /**
     * 获取群组中的所有成员
     *
     * @param groupid
     * @return
     * @author ZX
     * @date 2015-6-15 上午11:56:40
     */
    public static ObjectNode getAllMemberssByGroupId(String groupid) {
        ObjectNode getAllMemberssByGroupIdNode = EasemobChatGroups
                .getAllMemberssByGroupId(groupid);
        LOGGER.info("IM获取群组中的所有成员: " + getAllMemberssByGroupIdNode.toString());
        return getAllMemberssByGroupIdNode;
    }

    public static void main(String[] args) {
        getAllMemberssByGroupId("217931938059518400");
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
    public static ObjectNode addUserToGroup(String addToChatgroupid,
                                            String toAddUsername) {
        ObjectNode addUserToGroupNode = EasemobChatGroups.addUserToGroup(
                addToChatgroupid, toAddUsername);
        LOGGER.info("IM群组加人[单个]: " + addUserToGroupNode.toString());
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
     */
    public static ObjectNode deleteUserFromGroup(String delFromChatgroupid,
                                                 String toRemoveUsername) {
        ObjectNode deleteUserFromGroupNode = EasemobChatGroups
                .deleteUserFromGroup(delFromChatgroupid, toRemoveUsername);
        LOGGER.info("IM群组减人: " + deleteUserFromGroupNode.asText());
        return deleteUserFromGroupNode;
    }

    /**
     * 从环信服务器上获取聊天记录
     *
     * @param startTime
     * @return
     * @author ZX
     * @date 2015-6-18 下午4:02:47
     */
    public static ObjectNode getChatMessages(String startTime,
                                             int limit, String cursor) {
        ObjectNode queryStrNode = factory.objectNode();
        queryStrNode.put("limit", limit);
        queryStrNode.put("ql", "select * where timestamp>" + startTime);
        queryStrNode.put("cursor", cursor);
        ObjectNode messages = EasemobChatMessage.getChatMessages(queryStrNode);
        return messages;
    }

    /**
     * 获取一个用户参与的所有群组
     *
     * @param username
     * @return
     */
    private static ObjectNode getJoinedChatgroupsForIMUser(String username) {
        ObjectNode objectNode = factory.objectNode();

        try {
            URL getJoinedChatgroupsForIMUserUrl = HTTPClientUtils
                    .getURL(Constants.APPKEY.replace("#", "/") + "/users/"
                            + username + "/joined_chatgroups");
            objectNode = sendHTTPRequest(username,
                    getJoinedChatgroupsForIMUserUrl, credential, null,
                    HTTPMethod.METHOD_GET);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }

        return objectNode;
    }

    /**
     * 强制下线
     * 资料地址：http://docs.easemob.com/start/100serverintegration/20users
     *
     * @param userName
     * @return
     */
    public static ObjectNode disconnect(String userName) {
        ObjectNode objectNode = factory.objectNode();
        try {
            URL userPrimaryUrl = HTTPClientUtils
                    .getURL(Constants.APPKEY.replace("#", "/") + "/users/" + userName + "/disconnect");
            objectNode = sendHTTPRequest(userName, userPrimaryUrl, credential, null, HTTPMethod.METHOD_GET);

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }

        LOGGER.info("IM强制下线: " + objectNode.asText());
        return objectNode;
    }

    /**
     * 修改环信密码
     * 资料地址：http://docs.easemob.com/start/100serverintegration/20users
     * @param userName
     * @param newPwd
     * @return
     */
    public static ObjectNode changePwd(String userName,String newPwd){
        ObjectNode objectNode = factory.objectNode();
        try {
            URL userPrimaryUrl = HTTPClientUtils
                    .getURL(Constants.APPKEY.replace("#", "/") + "/users/" + userName + "/password");
            ObjectNode datanode = JsonNodeFactory.instance.objectNode();
            datanode.put("newpassword", newPwd);
            objectNode = sendHTTPRequest(userName, userPrimaryUrl, credential, datanode, HTTPMethod.METHOD_PUT);

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }

        LOGGER.info("IM修改用户["+userName+"]新密码["+newPwd+"]返回结果: " + JSONUtils.toString(objectNode));
        return objectNode;
    }

    /**
     * 转让群组
     * 修改群组 Owner 为同一 APP 下的其他用户。 注意：将群组 Owner 转让给其他用户后，原 Owner 可能被删除。
     * 如果希望原 Owner 作为成员继续留在该群组中，需要再次将该用户添加至群组。
     * 资料地址：http://docs.easemob.com/im/100serverintegration/60groupmgmt
     * @param newOwner 新群主ID
     * @return
     */
    public static ObjectNode changeOwner(String groupId,String newOwner){
        ObjectNode objectNode = factory.objectNode();
        try {
            URL userPrimaryUrl = HTTPClientUtils
                    .getURL(Constants.APPKEY.replace("#", "/") + "/chatgroups/" + groupId);
            ObjectNode datanode = JsonNodeFactory.instance.objectNode();
            datanode.put("newowner", newOwner);
            objectNode = HTTPClientUtils.sendHTTPRequest(userPrimaryUrl, credential, datanode, HTTPMethod.METHOD_PUT);

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
        LOGGER.info("IM转让群组["+groupId+"]新群主["+newOwner+"]返回结果: " + JSONUtils.toString(objectNode));
        return objectNode;
    }

    /**
     * 发送文本消息
     * @param from  发送者
     * @param to    接收者 可以为人 也可以是一个群 以type区分
     * @param msg
     * @param type  users/chatgroups
     * @return
     */
    public static ObjectNode sendSimpleMsg(String from, String to, String msg, String type){
        return sendSimpleMsg(from, to, msg, type, null);
    }

    /**
     * 发送文本消息
     * @param from      发送者
     * @param to        接收者 可以为人 也可以是一个群 以type区分
     * @param msg
     * @param type      users/chatgroups
     * @param extProp   额外属性
     * @return
     */
    public static ObjectNode sendSimpleMsg(String from, String to, String msg, String type, Map<String, String> extProp){
        //接收者 可以多个
        ArrayNode targetusers = factory.arrayNode();
        targetusers.add(to);
        //文本消息
        ObjectNode txtmsg = factory.objectNode();
        txtmsg.put("msg", msg);
        txtmsg.put("type", "txt");
        //扩展信息 可以自定义内容
        ObjectNode ext = factory.objectNode();
        if(extProp != null){
            for(String k:extProp.keySet()){
                ext.put(k, extProp.get(k));
            }
        }
        //发送消息
        ObjectNode node = EasemobMessages.sendMessages(type, targetusers, txtmsg, from, ext);
        return node;
    }

    /**
     * 发送CMD消息
     * @param groupIds   接收者 可以为人 也可以是一个群
     * @param action     发送内容
     * @param extProp    扩展字段
     * @return
     */
    public static ObjectNode sendSystemCmdMsgToChatgroups(List<String> groupIds, String action, Map<String, String> extProp){
        return sendCmdMsg("admin",groupIds,action,"chatgroups",extProp);
    }

    /**
     * 发送Cmd消息
     * @param from      发送者
     * @param tos        接收者 可以为人 也可以是一个群 以type区分
     * @param action
     * @param type      users/chatgroups
     * @param extProp   额外属性
     * @return
     */
    public static ObjectNode sendCmdMsg(String from, List<String> tos, String action, String type, Map<String, String> extProp){
        //接收者 可以多个
        ArrayNode targetusers = factory.arrayNode();
        for(String to:tos){
            targetusers.add(to);
        }
        //文本消息
        ObjectNode txtmsg = factory.objectNode();
        txtmsg.put("action", action);
        txtmsg.put("type", "cmd");
        //扩展信息 可以自定义内容
        ObjectNode ext = factory.objectNode();
        if(extProp != null){
            for(String k:extProp.keySet()){
                ext.put(k, extProp.get(k));
            }
        }
        //发送消息
        ObjectNode node = EasemobMessages.sendMessages(type, targetusers, txtmsg, from, ext);
        return node;
    }

    /**
     * 与环信交互
     *
     * @param userName   用户登录名
     * @param url        访问地址
     * @param credential
     * @param dataBody   交互参数
     * @param method     访问方式(get/post)
     * @return
     */
    public static ObjectNode sendHTTPRequest(String userName, URL url, Credential credential, Object dataBody, String method) {
        ObjectNode objectNode = factory.objectNode();

        // check Constants.APPKEY format
        if (!HTTPClientUtils.match("^(?!-)[0-9a-zA-Z\\-]+#[0-9a-zA-Z]+", Constants.APPKEY)) {
            LOGGER.error("Bad format of Constants.APPKEY: " + Constants.APPKEY);

            objectNode.put("message", "Bad format of Constants.APPKEY");

            return objectNode;
        }

        // check properties that must be provided
        if (StringUtils.isEmpty(userName)) {
            LOGGER.error("The userName that will be used to query must be provided .");

            objectNode.put("message", "The userName that will be used to query must be provided .");

            return objectNode;
        }

        objectNode = HTTPClientUtils.sendHTTPRequest(url, credential, dataBody, method);


        return objectNode;
    }

}
