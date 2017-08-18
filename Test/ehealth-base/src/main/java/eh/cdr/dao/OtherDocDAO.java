package eh.cdr.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.cdr.Otherdoc;

import java.util.List;

public abstract class OtherDocDAO extends HibernateSupportDelegateDAO<Otherdoc> {
	public OtherDocDAO(){
		super();
		this.setEntityName(Otherdoc.class.getName());
		this.setKeyField("otherDocId");
	}

	@DAOMethod(sql = "select docContent from Otherdoc where clinicType=:type and mpiid=:mpiId")
	public abstract List<Integer> findDocIdsByMpiIdAndType(@DAOParam("mpiId") String mpiId, @DAOParam("type")Integer type);
}
