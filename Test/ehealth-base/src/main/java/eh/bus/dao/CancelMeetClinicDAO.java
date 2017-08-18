package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.MeetClinic;
import org.apache.log4j.Logger;

public abstract class CancelMeetClinicDAO extends
		HibernateSupportDelegateDAO<MeetClinic> {
	public static final Logger log = Logger
			.getLogger(CancelMeetClinicDAO.class);

	public CancelMeetClinicDAO() {
		super();
		this.setEntityName(MeetClinic.class.getName());
		this.setKeyField("meetClinicId");
	}

	/**
	 * 取消会诊医生服务（取消执行单）
	 * 
	 * @author LF
	 * @param meetClinicId
	 * @param targetDoctor
	 */
	@RpcService
	@DAOMethod(sql = "UPDATE MeetClinicResult SET exeStatus=9,endTime=NOW() WHERE meetClinicId=:meetClinicId AND targetDoctor=:targetDoctor AND exeStatus<2")
	public abstract void updateTargetDoctor(
			@DAOParam("meetClinicId") Integer meetClinicId,
			@DAOParam("targetDoctor") Integer targetDoctor);
}
