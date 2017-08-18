package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.DoctorBankCard;
import org.apache.log4j.Logger;

import java.util.List;

public abstract class DoctorBankCardDAO extends HibernateSupportDelegateDAO<DoctorBankCard> {
	public static final Logger log = Logger.getLogger(DoctorBankCardDAO.class);

	public DoctorBankCardDAO() {
		super();
		this.setEntityName(DoctorBankCard.class.getName());
		this.setKeyField("id");
	}

	@DAOMethod(sql="from DoctorBankCard where doctorId=:doctorId and status=1 order by createDate desc")
	public abstract List<DoctorBankCard> findBankCards(@DAOParam("doctorId") Integer doctorId);

	@DAOMethod(sql="from DoctorBankCard where doctorId=:doctorId and status=1 order by lastModify desc",limit =1)
	public abstract List<DoctorBankCard> findLastModifyBankCard(@DAOParam("doctorId") Integer doctorId);


	@DAOMethod(sql="from DoctorBankCard where cardNo=:cardNo and status=1")
	public abstract List<DoctorBankCard> findByCardNo(@DAOParam("cardNo")String cardNo);
}
