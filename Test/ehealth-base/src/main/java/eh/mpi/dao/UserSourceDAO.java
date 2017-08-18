package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.mpi.UserSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

public abstract class UserSourceDAO extends
		HibernateSupportDelegateDAO<UserSource> {
	private static final Logger log = LoggerFactory.getLogger(UserSourceDAO.class);

	public UserSourceDAO() {
		super();
		this.setEntityName(UserSource.class.getName());
		this.setKeyField("id");
	}

	@DAOMethod(sql="select u.id FROM UserSource u,Patient p where u.mpiId=p.mpiId and u.remindStatus=0 and p.status=1 and u.createDate<=:date")
	public abstract List<Integer> findUnRemindPatient(@DAOParam("date") Date date,@DAOParam(pageStart = true) int start, @DAOParam(pageLimit = true) int limit);

	@DAOMethod
	public abstract void updateRemindStatusById(Integer remindStatus,Integer id);

}
