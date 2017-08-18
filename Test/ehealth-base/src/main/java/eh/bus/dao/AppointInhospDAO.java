package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.entity.bus.AppointInhosp;
import eh.entity.bus.TransferAndPatient;
import eh.entity.his.AppointInHosResponse;
import eh.push.SmsPushService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

public abstract class AppointInhospDAO extends HibernateSupportDelegateDAO<AppointInhosp> implements
DBDictionaryItemLoader<AppointInhosp> 
{
	private static final Log logger = LogFactory.getLog(AppointInhospDAO.class);
	public AppointInhospDAO()
	{
		super();
		this.setEntityName(AppointInhosp.class.getName());
		this.setKeyField("appointInHospId");
	}
	
	/**
	 * 根据mpiid获取住院预约表
	 * @author LF
	 * @param mpiid
	 * @return
	 */
	@DAOMethod
	@RpcService
	public abstract List<AppointInhosp> findByMpiid(String mpiid);
	
	@RpcService
	public void saveAppointInhosp(AppointInhosp appointinhosp){
		if(StringUtils.isEmpty(appointinhosp.getMpiid())){
			throw new DAOException(DAOException.VALUE_NEEDED,"mpiid is required");
		}
		appointinhosp.setRequestDate(new Date());
		save(appointinhosp);
	}
	
	@RpcService
	@DAOMethod
	public abstract AppointInhosp getById(int id);
	
	
	@RpcService
	@DAOMethod
	public abstract AppointInhosp getByTransferId(int transferId);

	/**
	 * his转诊住院 成功后 调用更新接口
	 * @param res
	 * @throws DAOException
	 */
	@RpcService
	public void updateOrganAppointInHosId(final AppointInHosResponse res) throws DAOException{
		
		final AppointInhosp ar=this.get(Integer.parseInt(res.getId()));
		if(ar==null){
//			logger.error("can not find the AppointInHosprecord by id:"+res.getId());
			throw new DAOException("can not find the record by id:"+res.getId());
		}
		AbstractHibernateStatelessResultAction<Boolean> action=new AbstractHibernateStatelessResultAction<Boolean>() {
			
			@Override
			public void execute(StatelessSession ss) throws Exception {
				String hql="update AppointInhosp set status=:status,organAppointInhospId=:organAppointInhospId where appointInHospId=:appointInHospId";
				Query query = ss.createQuery(hql.toString());
				query.setParameter("status", 0);
				query.setString("organAppointInhospId", res.getOrganAppointInhospID());
				query.setString("appointInHospId", res.getId());
				query.executeUpdate();
				
				
				TransferDAO transferdao =DAOFactory.getDAO(TransferDAO.class);
				int transId=ar.getTransferId();

				//回写状态
				transferdao.updateTransferFromHosp(transId,"");
				
				//增加医生收入
				transferdao.addTransferIncome(transId);
				setResult(true);
			}			
		};
		HibernateSessionTemplate.instance().executeTrans(action);
		if(action.getResult()){
			//发送住院转诊成功短信

			TransferDAO transferDao = DAOFactory.getDAO(TransferDAO.class);
			
			int transId=ar.getTransferId();
			logger.info("转诊住院成功"+transId);
			TransferAndPatient tp = transferDao.getTransferByID(transId);
			transferDao.sendSmsForTransferInHosp(tp);
		}
	}
	/**
	 * his预约或转诊失败 更新平台状态
	 * @param res
	 * @throws DAOException
	 */
	@RpcService
	public void cancelInHospForHisFail(final AppointInHosResponse res) throws DAOException{	
		logger.error("his转诊或预约失败返回："+JSONUtils.toString(res));
		final AppointInhosp ar=this.get(Integer.parseInt(res.getId()));
		if(ar==null){
//			logger.error("can not find the record by id:"+res.getId());
			throw new DAOException("can not find the record by id:"+res.getId());
		}
		
		AbstractHibernateStatelessResultAction<Boolean> action=new AbstractHibernateStatelessResultAction<Boolean>() {
			
			@Override
			public void execute(StatelessSession ss) throws Exception {
				//更新预约记录表
				String hql="update AppointInhosp set status=:status where appointInHospId=:appointInHospId";
				Query query = ss.createQuery(hql.toString());
				query.setParameter("status", 9);//取消
				query.setString("appointInHospId", res.getId());
				query.executeUpdate();
				
				//处理转诊失败的情况
				TransferDAO transferdao =DAOFactory.getDAO(TransferDAO.class);
				int transId=ar.getTransferId();
					//更新转诊单状态为7 his转诊失败
				transferdao.updateTransferFailed(transId,res.getErrMsg()==null?"":res.getErrMsg());
				setResult(true);
			}			
		};
		HibernateSessionTemplate.instance().executeTrans(action);
		
		if(action.getResult()){
			logger.info("his转诊住院失败，向患者发送短信或系统通知");
			
			//转诊失败
			TransferDAO transferdao =DAOFactory.getDAO(TransferDAO.class);
			int transId=ar.getTransferId();
			
			TransferAndPatient tp = transferdao.getTransferByID(transId);
			
			// 给转诊接收医生新增系统消息，推送消息
			AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(transId, tp.getTransfer().getTargetOrgan(), "DocInHospTransferConfirmHisFailed", "", 0);
		}
	}
	
	
	/**
	 * 根据MpiId更新病人姓名
	 * @author ZX
	 * @date 2015-7-23  上午10:10:10
	 * @param patientName
	 * @param mpiid
	 */
	@RpcService
	@DAOMethod
	public abstract void updatePatientNameByMpiid(String patientName, String mpiid);
	
	
	@RpcService
	@DAOMethod
	public abstract AppointInhosp getByOrganAppointInhospIdAndOrganId(String organAppointInhospId,Integer organId);

	@RpcService
	@DAOMethod
	public abstract List<AppointInhosp> findAppointInhospByStatusAndOrganId(Integer status,Integer organId);
	/**
	 * 住院预约登记确认服务
	 * @author Zhangxq
	 * @param AppointInHosResponse res  确认参数
	 * 
	 * */
	
	//TODO  住院预约登记确认服务
	@RpcService
	public void updateAppointInHosId(final AppointInhosp app) throws DAOException{
		
		final AppointInhosp ar = this.getByOrganAppointInhospIdAndOrganId(app.getOrganAppointInhospId(), app.getOrganId());
		if(ar==null){
//			logger.error("can not find the AppointInHosprecord by OrganAppointInhospId:"+app.getOrganAppointInhospId());
			throw new DAOException("can not find the record by OrganAppointInhospId:"+app.getOrganAppointInhospId());
		}
		AbstractHibernateStatelessResultAction<Boolean> action=new AbstractHibernateStatelessResultAction<Boolean>() {
			
			@Override
			public void execute(StatelessSession ss) throws Exception {
				logger.info("更新住院确认信息");
				if (app.getStatus() != null){
					ar.setStatus(app.getStatus());
				}
				if (app.getOperateDate() != null) {
					ar.setOperateDate(app.getOperateDate());
				}
				if (app.getConfirmUser() != null) {
					ar.setConfirmUser(app.getConfirmUser());
				}
				if (app.getConfirmName() != null) {
					ar.setConfirmName(app.getConfirmName());
				}
				if (app.getClinicDepartCode() != null) {
					ar.setClinicDepartCode(app.getClinicDepartCode());
				}
				if (app.getClinicDepartName() != null) {
					ar.setClinicDepartName(app.getClinicDepartName());
				}
				if (app.getWardCode() != null) {
					ar.setWardCode(app.getWardCode());
				}
				if (app.getWardName() != null) {
					ar.setWardName(app.getWardName());
				}
				if (app.getClinicDoctorCode() != null) {
					ar.setClinicDoctorCode(app.getClinicDoctorCode());
				}
				if (app.getClinicDoctorName() != null) {
					ar.setClinicDoctorName(app.getClinicDoctorName());
				}
				if (app.getInpAddress() != null) {
					ar.setInpAddress(app.getInpAddress());
				}else {
					ar.setInpAddress("指定地点");
				}
				if (app.getNotice() != null) {
					ar.setNotice(app.getNotice());
				}
				if (app.getRemark() != null) {
					ar.setRemark(app.getRemark());
				}
				update(ar);
				
				TransferDAO transferdao =DAOFactory.getDAO(TransferDAO.class);
				int transId=ar.getTransferId();
				//回写状态
				transferdao.updateTransferFromHosp(transId,app.getInpAddress());
				//增加医生收入
//				transferdao.addTransferIncome(transId);
				setResult(true);
			}			
		};
		HibernateSessionTemplate.instance().executeTrans(action);
//		if(action.getResult()){
//			//发送住院转诊成功短信
//			logger.info("转诊住院成功");
//			TransferDAO transferDao = DAOFactory.getDAO(TransferDAO.class);
//			
//			int transId=ar.getTransferId();
//			TransferAndPatient tp = transferDao.getTransferByID(transId);
//			transferDao.sendSmsForTransferInHosp(tp);
//		}
	}
}
