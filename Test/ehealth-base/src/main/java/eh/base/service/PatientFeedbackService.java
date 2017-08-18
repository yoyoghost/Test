package eh.base.service;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.PatientFeedbackDAO;
import eh.bus.dao.ConsultDAO;
import eh.bus.service.consult.ConsultMessageService;
import eh.entity.base.PatientFeedback;
import eh.entity.bus.Consult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;

public class PatientFeedbackService {
	private static final Log logger = LogFactory.getLog(PatientFeedbackService.class);

	/**
	 * 患者端给医生点赞(团队咨询单给执行医生，和所在团队点赞；个人咨询单给目标医生点赞)
	 * @param consultId
	 * @return
     */
	@RpcService
	public Boolean evaluationConsultForHealth(Integer consultId){

		ConsultDAO consultDAO= DAOFactory.getDAO(ConsultDAO.class);
		PatientFeedbackDAO feedbackDAO=DAOFactory.getDAO(PatientFeedbackDAO.class);

		Consult consult=consultDAO.getById(consultId);
		if(consult==null){
			throw new DAOException(ErrorCode.SERVICE_ERROR,"不存在该咨询单");
		}
		Integer exeDocId=consult.getExeDoctor();
		if(consult.getConsultStatus()!=2 && consult.getConsultStatus()!=3){
			logger.error("咨询单["+consultId+"]不是完成/拒绝状态,不能点赞");
			return false;
		}

		PatientFeedback sigleFeed=initThumbUpData();

		if(sigleFeed==null){
			return false;
		}

		//给目标医生(团队医生/个人医生)点赞
		sigleFeed.setServiceType("3");
		sigleFeed.setServiceId(consultId.toString());
		sigleFeed.setMpiid(consult.getMpiid());
		sigleFeed.setDoctorId(consult.getConsultDoctor());
		feedbackDAO.addFeedBackByGood(sigleFeed);

		//判断是否为患者图文咨询结束后对医生的点赞
		ConsultMessageService msgService = new ConsultMessageService();
//		msgService.updateSystemNotificationMessage(consultId);

		Boolean teams=consult.getTeams();
		if(teams==null){
			teams=false;
		}

		//是团队咨询单 且 有执行医生,给执行医生点赞
		if(exeDocId!=null && teams){
			PatientFeedback teamsFeed=sigleFeed;
			teamsFeed.setDoctorId(exeDocId);
			feedbackDAO.addFeedBackByGood(teamsFeed);
		}

		return true;
	}

	/**
	 * 初始化[点赞]数据(供evaluationConsultForHealth使用)
	 */
	private PatientFeedback initThumbUpData(){
		UserRoleToken urt = UserRoleToken.getCurrent();
		if(urt==null){
			return null;
		}
		Integer urtId=urt.getId();
		String roleId=urt.getRoleId();
		if(!SystemConstant.ROLES_PATIENT.equals(roleId) && !SystemConstant.ROLES_DOCTOR.equals(roleId)){
			return null;
		}

		PatientFeedback feedback=new PatientFeedback();
		feedback.setEvaDate(new Date());
		feedback.setGood(1);
		feedback.setUserId(urtId);
		feedback.setUserType(roleId);
		return feedback;
	}

}
