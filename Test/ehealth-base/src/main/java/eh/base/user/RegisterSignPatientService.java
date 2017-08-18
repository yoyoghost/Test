package eh.base.user;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class RegisterSignPatientService {
	private static final Log logger = LogFactory.getLog(RegisterSignPatientService.class);

	/**
	 * 健康端患者信息后台Excel数据导入 患者注册及关联签约医生
	 *
	 * @author houxr
	 * @date 2016-4-4 上午10:11:18
	 * @param patient 病人主索引 
	 * @param relationDoctor 关注的医生
	 * @return
	 */
	@RpcService
	public Boolean RegisteredSignPatientAccount(final Patient patient,final RelationDoctor relationDoctor){
		logger.info("===调用eh.RegisteredSignPatientAccount===");
		logger.info("==========rpc病人信息========="+JSONUtils.toString(patient));
		logger.info("==========rpc关注的医生======="+JSONUtils.toString(relationDoctor));
							
		PatientDAO patientDao=DAOFactory.getDAO(PatientDAO.class);
		RelationDoctorDAO relationDoctorDao=DAOFactory.getDAO(RelationDoctorDAO.class);
		//导入签约病人信息  表 mpi_patient mpi_healthcard mpi_address mpi_familymember mpi_relationdoctor
		Patient returnpatient=patientDao.excellRegisterSignPatientUser(patient);

		//签约信息表更新
		relationDoctorDao.addFamilyDoctor(returnpatient.getMpiId(), relationDoctor.getDoctorId(),
				relationDoctor.getRelationDate(), relationDoctor.getStartDate(), relationDoctor.getEndDate());

		logger.info("签约病人导入成功后病人登陆密码:"+patient.getIdcard().substring(patient.getIdcard().length()-6));
		logger.info("===签约患者导入成功====");
		return true;
	}

}
