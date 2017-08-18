package eh.mpi.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportWriteDAO;
import eh.base.dao.DoctorDAO;
import eh.entity.mpi.RelationDoctor;

public abstract class RelationDoctorWriteDAO extends HibernateSupportWriteDAO<RelationDoctor>{
	
	public RelationDoctorWriteDAO(){
		super();
		setEntityName(RelationDoctor.class.getName());
		setKeyField("relationDoctorId");
	}
	
	/**
	 * 关注医生添加服务
	 * 保存前判断,
	 */
	@Override
	protected void beforeSave(RelationDoctor relation) throws DAOException{
		String mpiId=relation.getMpiId();
		if(mpiId==null||mpiId.equals("")){
			throw new DAOException(DAOException.VALUE_NEEDED,"mpiId can't  is null or ''");
		}
		
		//判断是否存在这个病人
		PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
		if(!patientDAO.exist(mpiId)){
			throw new DAOException(DAOException.VALUE_NEEDED,"Patient[" + mpiId + "] not exist");
		}
		
		Integer doctorId=relation.getDoctorId();
		if(doctorId==null||doctorId==0){
			throw new DAOException(DAOException.VALUE_NEEDED,"doctorId can't  is null or 0");
		}
		
		//判断是否存在这个医生
		DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
		if(!doctorDao.exist(doctorId)){
			throw new DAOException(DAOException.VALUE_NEEDED,"doctor[" + doctorId + "] not exist");
		}
	}

}