package eh.base.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportWriteDAO;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.bus.service.ConsultSetService;
import eh.entity.base.Employment;

public abstract class EmploymentWriteDAO extends HibernateSupportWriteDAO<Employment>{
	
	public EmploymentWriteDAO(){
		setEntityName(Employment.class.getName());
		setKeyField("EmploymentId");
	}
	
	@Override
	protected void beforeSave(Employment o) throws DAOException{
		int doctorId = o.getDoctorId();
		DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
		if(!doctorDao.exist(doctorId)){
			throw new DAOException("doctor[" + doctorId + "] not exist");
		}
		
		int organId = o.getOrganId();
		OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
		if(!organDao.exist(organId)){
			throw new DAOException("organ[" + doctorId + "] not exist");
		}
	}


	@Override
	protected void afterSave(Employment o) throws DAOException{
		//2017-2-27 14:52:08 wx2.8 当新增职业点/新开医生账户的时候，发现医生所在的职业点中有处方权限，则默认开通【在线续方】功能
		ConsultSetService setService = AppContextHolder.getBean("eh.consultSetService", ConsultSetService.class);
		setService.openRecipeConsultInfo(o.getDoctorId());
	}
}