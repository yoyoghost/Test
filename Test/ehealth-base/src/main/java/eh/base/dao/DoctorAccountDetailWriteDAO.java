package eh.base.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportWriteDAO;
import eh.entity.base.DoctorAccount;
import eh.entity.base.DoctorAccountDetail;

import java.math.BigDecimal;

public abstract class DoctorAccountDetailWriteDAO extends HibernateSupportWriteDAO<DoctorAccountDetail>{
	
	public DoctorAccountDetailWriteDAO(){
		setEntityName(DoctorAccountDetail.class.getName());
		setKeyField("accountDetailId");
	}
	
	@Override
	protected void beforeSave(DoctorAccountDetail o) throws DAOException{
		if(o.getInout()==2){
			BigDecimal payMoney = o.getMoney();
			DoctorAccountDAO dao=DAOFactory.getDAO(DoctorAccountDAO.class);
			DoctorAccount d=dao.getByDoctorId(o.getDoctorId());
			BigDecimal accountMoney=d.getInCome().add(d.getPayOut().negate());
			if(accountMoney.compareTo(payMoney)<0){
				throw new DAOException(609,"账户余额不足，无法提现");
			}
		}
	}
	
}