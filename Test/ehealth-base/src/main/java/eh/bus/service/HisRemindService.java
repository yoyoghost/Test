package eh.bus.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.AppointRoadConstant;
import eh.base.constant.ErrorCode;
import eh.base.constant.ServiceType;
import eh.base.constant.SystemConstant;
import eh.base.dao.HisServiceConfigDAO;
import eh.bus.constant.HisRemindConstant;
import eh.bus.dao.HisRemindDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.CheckRequest;
import eh.entity.bus.HisRemind;
import eh.entity.bus.Transfer;
import eh.push.SmsPushService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.List;

public class HisRemindService {
	private static final Log logger = LogFactory.getLog(HisRemindService.class);

	@RpcService
	public List<String> findUnRemindUsersByMaintainOrgan( Integer maintainOrgan){
		HisRemindDAO remindDAO=DAOFactory.getDAO(HisRemindDAO.class);
		return remindDAO.findUnRemindUsersByMaintainOrgan(maintainOrgan);
	}


	@RpcService
	public List<HisRemind> findUnRemindRecordsByUsersAndMaintainOrgan(String operateUser, Integer maintainOrgan){
		HisRemindDAO remindDAO=DAOFactory.getDAO(HisRemindDAO.class);
		return remindDAO.findUnRemindRecordsByUsersAndMaintainOrgan(operateUser,maintainOrgan);
	}


	/**
	 * 更新记录为已经提醒过
	 * @param remindId
	 * @return
	 */
	@RpcService
	public Integer beReminded(final String user,final Integer organ) {
		HisRemindDAO remindDAO=DAOFactory.getDAO(HisRemindDAO.class);
		return remindDAO.beReminded(user,organ);
	}

	/**
	 * 能否申请与his流程相关的业务
	 * @param organ
	 * @return fasle正在维护不能业务，true可以业务
     */
	private Boolean canRequestBuss(Integer organ){
		HisServiceConfigDAO configDAO=DAOFactory.getDAO(HisServiceConfigDAO.class);

		Boolean canRequest=true;

		HisServiceConfig config=configDAO.getByOrganId(organ);
		if(config==null){
			config=new HisServiceConfig();
		}

		if(config.getHisStatus()==null){
			config.setHisStatus(1);
		}

		//0开(his正在维护) 1关(可以走his)
		if(config.getHisStatus()==0){
			canRequest=false;
		}

		return canRequest;
	}

	/**
	 * 普通预约
     */
	public void saveRemindRecordForNormalAppoint(AppointRecord appointRecord){
		saveRemindRecordForAppoint(appointRecord,HisRemindConstant.OPERATE_TYPE_APPOINT);
	}

	/**
	 * 有号转诊
     */
	public void saveRemindRecordForHasSourceTransfer(AppointRecord appointRecord){
		saveRemindRecordForAppoint(appointRecord,HisRemindConstant.OPERATE_TYPE_APPOINT_TRANSFER);
	}

	/**
	 * 非远程门诊预约控制
	 */
	public void saveRemindRecordForAppoint(AppointRecord appointRecord,Integer operateType){
		HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
		HisRemindDAO remindDAO=DAOFactory.getDAO(HisRemindDAO.class);

		Integer organ=appointRecord.getOrganId();
		Boolean canRequest=canRequestBuss(organ);
//		Boolean toHis=hisServiceConfigDao.isServiceEnable(organ,
//				ServiceType.TOHIS);

		//普通预约/有号普通转诊
		if(appointRecord.getAppointRoad() == AppointRoadConstant.NORMAL_APPOINTMENT){
			String appointUser=appointRecord.getAppointUser();
			Date now=new Date();
			/*if(!StringUtils.isEmpty(appointUser) && appointUser.length()<32){*///医生预约
			//预约不需要判断tohis
			if(/*toHis &&*/ !canRequest){//预约经过his 且 his维护中
				HisRemind remind=new HisRemind();
				remind.setOperateUser(appointUser);
				remind.setOperateRole(SystemConstant.ROLES_DOCTOR);
				remind.setMaintainOrgan(organ);
				remind.setOperateDate(now);
				remind.setRemindStatus(HisRemindConstant.REMIND_STATUS_NOREMIND);
				remind.setOperateType(operateType);
				remind.setLastModify(now);
				remindDAO.save(remind);

				throw new DAOException(ErrorCode.SERVICE_ERROR,HisRemindConstant.REMIND_WORD);
			}
		}
	}

	/**
	 * 门诊转诊接收
	 */
	public void saveRemindRecordForNormalTransfer(Transfer transfer){
		saveRemindRecordForTransfer(transfer,HisRemindConstant.OPERATE_TYPE_NORMAL_TRANSFER);
	}

	/**
	 * 住院转诊接收
	 */
	public void saveRemindRecordForInHosTransfer(Transfer transfer){
		saveRemindRecordForTransfer(transfer,HisRemindConstant.OPERATE_TYPE_INHOS_TRANSFER);
	}

	/**
	 * 转诊接收(普通转诊/住院转诊接收)
	 */
	public void saveRemindRecordForTransfer(Transfer transfer,Integer operateType){
		HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
		HisRemindDAO remindDAO=DAOFactory.getDAO(HisRemindDAO.class);

		Integer organ=transfer.getConfirmOrgan();
		Boolean canRequest=canRequestBuss(organ);


		Boolean flag=false;
		//住院转诊接收
		if(operateType==HisRemindConstant.OPERATE_TYPE_INHOS_TRANSFER){
			flag=!canRequest;
		}

		//普通转诊接收
		if(operateType==HisRemindConstant.OPERATE_TYPE_NORMAL_TRANSFER){
			//3为远程门诊转诊，远程门诊转诊接收时，接收为门诊转诊(1)
			Integer transferType_request=transfer.getTransferType();
			if (transferType_request != 3) {
				Boolean toHis=hisServiceConfigDao.isServiceEnable(organ,
						ServiceType.TOHIS);
				flag=toHis && !canRequest;
			}
		}

		//转诊接收
		Date now=new Date();
		if(flag){//预约经过his 且 his维护中
			HisRemind remind=new HisRemind();
			remind.setOperateUser(transfer.getAgreeDoctor()+"");
			remind.setOperateRole(SystemConstant.ROLES_DOCTOR);
			remind.setMaintainOrgan(organ);
			remind.setOperateDate(now);
			remind.setRemindStatus(HisRemindConstant.REMIND_STATUS_NOREMIND);
			remind.setOperateType(operateType);
			remind.setLastModify(now);
			remindDAO.save(remind);
			throw new DAOException(ErrorCode.SERVICE_ERROR,HisRemindConstant.REMIND_WORD);
		}
	}

	/**
	 * 预约检查
	 */
	public void saveRemindRecordForCheck(CheckRequest cr){
		HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
		HisRemindDAO remindDAO=DAOFactory.getDAO(HisRemindDAO.class);

		Integer organ=cr.getOrganId();
		Boolean canRequest=canRequestBuss(organ);

		//预约检查
		Date now=new Date();
		if(!canRequest){//预约经过his 且 his维护中
			HisRemind remind=new HisRemind();
			remind.setOperateUser(cr.getRequestDoctorId()+"");
			remind.setOperateRole(SystemConstant.ROLES_DOCTOR);
			remind.setMaintainOrgan(organ);
			remind.setOperateDate(now);
			remind.setRemindStatus(HisRemindConstant.REMIND_STATUS_NOREMIND);
			remind.setOperateType(HisRemindConstant.OPERATE_TYPE_CHECK);//预约检查
			remind.setLastModify(now);
			remindDAO.save(remind);
			throw new DAOException(ErrorCode.SERVICE_ERROR,HisRemindConstant.REMIND_WORD);
		}
	}


	/**
	 * 当系统维护完成后，针对在此期间有向该机构发起过业务且未成功的医生发出推送消息&短信&系统消息
	 */
	public void pushMsgForHisBeNormal(Integer organ){
		//发送消息通知
		Integer clientId = null;
		SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
		smsPushService.pushMsgData2Ons(organ, organ, "SystemRestore", "SystemRestore", clientId);
	}

}
