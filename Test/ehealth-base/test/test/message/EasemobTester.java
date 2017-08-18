package test.message;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import eh.base.constant.SystemConstant;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;

import com.easemob.server.example.jersey.apidemo.EasemobChatGroups;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ctd.util.JSONUtils;

import junit.framework.TestCase;
import eh.util.Easemob;

public class EasemobTester extends TestCase {

	/**
	 * 注册
	 * 
	 * @author ZX
	 * @date 2015-6-10 下午2:45:28
	 */
	public void testRegistUser() {
		String userName = "devpatient_7029";
		String password = "patient123";
		Easemob.registUser(userName, password);
	}

	/**
	 * 登录
	 * 
	 * @author ZX
	 * @date 2015-6-10 下午2:45:49
	 */
	public void testLogin() {
		String userName = "patient_1194";
		String password = "patient222";
		Easemob.login(userName, password);
	}

	/**
	 * 创建群组
	 * 
	 * @author ZX
	 * @date 2015-6-10 下午7:09:17
	 *       {"action":"post","application":"fc9a5e20-08eb-11e5-984f-61b55773769e"
	 *       ,"uri":"https://a1.easemob.com/easygroup/nagri","entities":[],
	 *       "data":{"groupid":"1433988272248457"},"timestamp":1433988272226,
	 *       "duration":60,"organization":"easygroup","applicationName":"nagri",
	 *       "statusCode":200}
	 */
	public void testCreatChatGroups() {
		String requestName = "doctor_17";
		ArrayList<String> members = new ArrayList<String>();
		members.add("doctor_1198");
		members.add("doctor_1178");

		String nick = "meetClickId_3";
		String title = "关于病人【张肖】的会诊";
		ObjectNode creatChatGroupNode = Easemob.creatChatGroups(requestName,
				members, nick, title,200,true,true);
		System.out.println(JSONUtils.toString(creatChatGroupNode));
	}

	/**
	 * 获取一个或者多个群组的详情
	 * 
	 * @author ZX
	 * @date 2015-6-10 下午7:09:54
	 */
	public void testGetGroupDetailsByChatgroupid() {
		ObjectNode chatgroupidsNode = EasemobChatGroups.getAllChatgroupids();
		System.out.println(chatgroupidsNode.toString());
		System.out.println(chatgroupidsNode.get("data").get(0).get("groupid"));

		// String[] chatgroupIDs = {"1433934444869061","1433931229166"};
		// ObjectNode groupDetailNode =
		// EasemobChatGroups.getGroupDetailsByChatgroupid(chatgroupIDs);
		// System.out.println(groupDetailNode.toString());
	}

	/**
	 * 删除群组
	 * curl示例
	 * curl -X DELETE 'https://a1.easemob.com/easemob-playground/test1/chatgroups/1405735927133519' -H 'Authorization: Bearer {token}'
	 */
	public void testdeleteChatGroups() {
		String toDelChatgroupid = "1434355147440688";
		ObjectNode deleteChatGroups=Easemob.deleteChatGroups(toDelChatgroupid) ;
		System.out.println(JSONUtils.toString(deleteChatGroups));
	}
	
	/**
	 * 获取群组中的所有成员
	 * @author ZX
	 * @date 2015-6-15  下午3:25:37
	 */
	public void testGetAllMemberssByGroupId(){
		String groupid="258047660429148584";
		ObjectNode allmember=Easemob.getAllMemberssByGroupId(groupid);
		System.out.println(JSONUtils.toString(allmember));
	}
	
	/**
	 * 群组加人[单个]
	 * @author ZX
	 * @date 2015-6-15  下午3:26:59
	 */
	public void testAddUserToGroup(){
		String groupid="193049663018893752";
		String toAddUsername="devdoctor_1198";
		ObjectNode addUserToGroup=Easemob.addUserToGroup(groupid,toAddUsername);
		System.out.println(JSONUtils.toString(addUserToGroup));
	}
	
	/**
	 * 群组减人
	 * @author ZX
	 * @date 2015-6-15  下午3:35:59
	 */
	public void testDeleteUserFromGroup(){
		String delFromChatgroupid="193049663018893752";
		String toRemoveUsername="devdoctor_1198";
		ObjectNode deleteUserFromGroupNode=Easemob.deleteUserFromGroup(delFromChatgroupid,toRemoveUsername);
		System.out.println(JSONUtils.toString(deleteUserFromGroupNode));
	}
	
	/**
	 * 获取一个用户参与的所有群组
	 * @author ZX
	 * @date 2015-6-17  下午3:43:24
	 */
	public void testGetJoinedChatgroupsAll(){
		String own="doctor_1178";
		ObjectNode getJoinedChatgroupsForIMUserNode=Easemob.getJoinedChatgroupsAll(own);
		System.out.println(getJoinedChatgroupsForIMUserNode.toString());
		
		Iterable<JsonNode> chatGroups=getJoinedChatgroupsForIMUserNode.get("data");
		System.out.println(chatGroups);
		for (JsonNode jsonNode : chatGroups) {
			System.out.println(jsonNode.get("groupname"));
			
		}
	}
	
	/**
	 * 获取聊天记录
	 * @author ZX
	 * @date 2015-6-18  下午5:59:32
	 */
	public void testGetChatMessages(){
//		String endTime=String.valueOf(System.currentTimeMillis());
		Date startTime = DateConversion.getCurrentDate("2016-10-28 17:15:00",
				"yyyy-MM-dd HH:mm:ss");
		int limit=1000;
		ObjectNode messages=Easemob.getChatMessages(String.valueOf(startTime.getTime()), limit,null);
//		System.out.println(messages.toString());

		Integer count =Integer.parseInt( messages.get("count").toString() );
		while(count>0){
			System.out.println(messages.toString());
			Iterable<JsonNode> entities = messages.get("entities");
			for (JsonNode jsonNode : entities) {
//				System.out.println(JSONUtils.toString(jsonNode));
			}
			JsonNode cursor=messages.get("cursor");
			if(cursor==null){
				break;
			}
			messages=Easemob.getChatMessages(String.valueOf(startTime.getTime()), limit,cursor.asText());
			String countString= messages.get("count").toString();
			if(!StringUtils.isEmpty(countString)){
				count =Integer.parseInt(countString );
			}else{
				break;
			}
		}

	}

	/**
	 * 修改密码
	 */
	public void testChangePwd(){
		Integer[] arr = {};
		for (int uid:arr) {
			String userName=Easemob.getPatient(uid);
			String oldPwd="patient222";
			String newPwd= SystemConstant.EASEMOB_PATIENT_PWD;
			Easemob.changePwd(userName,newPwd);
			Easemob.login(userName,newPwd);
		}
	}

	/**
	 * 群组转让,修改群主
	 */
	public void testchangeOwner(){
		String groupId="193049663018893752";
//		String newOwner="devpatient_1197";
		String newOwner="devpatient_7029";
		Easemob.changeOwner(groupId,newOwner);

	}
}
