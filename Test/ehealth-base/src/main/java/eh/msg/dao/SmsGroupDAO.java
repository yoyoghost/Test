package eh.msg.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.msg.SmsGroup;
import eh.util.AlidayuSms;

import java.util.HashMap;
import java.util.List;

public abstract class SmsGroupDAO extends HibernateSupportDelegateDAO<SmsGroup> {
	public SmsGroupDAO() {
		super();
		this.setEntityName(SmsGroup.class.getName());
		this.setKeyField("id");
	}

	@RpcService
	@DAOMethod(sql = " from SmsGroup")
	public abstract List<SmsGroup> findAllSmsGroups();

	@RpcService
	public void sendSmsToGroups() {

		final String smsConstant = "SMS_2135326";
		HashMap<String, String> smsParam = new HashMap<String, String>();
		List<SmsGroup> arr = this.findAllSmsGroups();
		if (arr != null) {
			for (SmsGroup item : arr) {
				AlidayuSms.sendSms(item.getPhone(), smsConstant, smsParam);
				item.setSendFlag(true);
				update(item);
			}
		}

	}

}
