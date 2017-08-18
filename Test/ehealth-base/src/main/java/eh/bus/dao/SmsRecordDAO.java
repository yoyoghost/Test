package eh.bus.dao;


import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.SmsRecord;

import java.util.Date;
import java.util.List;
/**
 * 短信保存服务
 * @author w
 *
 */
public abstract class SmsRecordDAO extends
		HibernateSupportDelegateDAO<SmsRecord> {
	public SmsRecordDAO() {
		super();
		this.setEntityName(SmsRecord.class.getName());
		this.setKeyField("id");
	}
	@RpcService
	public SmsRecord saveSmsRecord(SmsRecord record){
		return this.save(record);
	}
	@RpcService
	public void updateSmsRecord(SmsRecord smsRecord){
		this.update(smsRecord);
	}
	
	@RpcService
	@DAOMethod(sql = "update SmsRecord set status = :status where extendId = :extendId")
	public abstract void updateStatusByExtendId(@DAOParam("status") int status, @DAOParam("extendId") String extendId);
	/**
	 * @date 2015-9-15
	 * @author zjr
	 * @function 根据发送状态获取列表
	 * @param statuss 状态
	 * */
	@DAOMethod(sql="from SmsRecord where status in( :statuss)")
	public abstract List<SmsRecord> findAllByStatus(@DAOParam("statuss") String statuss);
	
	/**
	 * @author zjr
	 * @date 2015-9-15
	 * @function 短信发送后更新短信记录
	 * */
	@DAOMethod(sql = "update SmsRecord set result=:result,status=:status,sendtime=now() where id = :id")
	public abstract void updateById(@DAOParam("id") int id,@DAOParam("result") String result,@DAOParam("status") int status);
	
	@RpcService
	@DAOMethod
	public abstract SmsRecord getById(int id);
	
	/*public SmsRecord saveContent(Integer bussType,SmsContent content){
		//保存短信发送记录
		SmsRecord sr=new SmsRecord();
		sr.setMobile(content.getMobile());
		sr.setContent(JSONUtils.toString(content));
		sr.setCreateTime(new Date());
		sr.setBusstype(bussType);
		sr.setStatus(0);
		SmsRecord smsRecord = save(sr);	
		return smsRecord;
	}*/

	@DAOMethod(sql="from SmsRecord where createTime > :createTime and result like :result " +
			"AND smsTemplateCode <> 'SMS_1455001' AND smsTemplateCode <> 'SMS_60785480' " +
			"AND smsTemplateCode <> 'SMS_8305258' AND smsTemplateCode <> 'SMS_56615297' " +
			"AND smsTemplateCode <> 'SMS_10385982' AND smsTemplateCode <> 'SMS_10465037'" +
			"AND smsTemplateCode <> 'SMS_1560088'",limit = 0)
	public abstract List<SmsRecord> findFailSms(@DAOParam("createTime") Date createTime, @DAOParam("result") String result);

}
