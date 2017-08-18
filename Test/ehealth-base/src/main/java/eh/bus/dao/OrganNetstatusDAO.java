package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.OrganNetManager;
import eh.entity.bus.OrganNetStatus;
import eh.util.AlidayuSms;

import java.util.HashMap;
import java.util.List;

public abstract class OrganNetstatusDAO extends HibernateSupportDelegateDAO<OrganNetStatus> {
	public OrganNetstatusDAO() {
		super();
		this.setEntityName(OrganNetStatus.class.getName());
		this.setKeyField("organId");
	}
	
	@RpcService
	@DAOMethod(sql = "update OrganNetStatus set statusBase=:statusBase,lasttime=now() where organId=:organId")
	public abstract void updateStatusBase(@DAOParam("statusBase") Integer statusBase,@DAOParam("organId") Integer organId);
	
	@RpcService
	@DAOMethod(sql = "update OrganNetStatus set statusHis=:statusHis,  lasttime=now()  where organId=:organId")
	public abstract void updateStatusHis(@DAOParam("statusHis") Integer statusHis,@DAOParam("organId") Integer organId);

	@RpcService
	public void sendHisMsg(String organName, String url){
		HashMap<String, String> smsParam = new HashMap<>();
		smsParam.put("organname", organName);
		smsParam.put("info", url);
		OrganNetManagerDAO managerDAO = DAOFactory.getDAO(OrganNetManagerDAO.class);
		//去数据库查询启用的网络监听管理员 发送短信
		List<OrganNetManager> list = managerDAO.findByOrgan(1);
		for(OrganNetManager manager:list) {
			AlidayuSms.sendSms(manager.getMobile(), "SMS_1960004", smsParam);
		}
	}

	@RpcService
	public void sendHisMsgByOrgan(int organ ,String organName, String info){
		HashMap<String, String> smsParam = new HashMap<>();
		smsParam.put("organname", organName);
		smsParam.put("info", info);
		OrganNetManagerDAO managerDAO = DAOFactory.getDAO(OrganNetManagerDAO.class);
		//去数据库查询启用的网络监听管理员 发送短信
		List<OrganNetManager> list = managerDAO.findByOrgan(organ);
		for(OrganNetManager manager:list) {
			AlidayuSms.sendSms(manager.getMobile(), "SMS_1960004", smsParam);
		}
	}
}
