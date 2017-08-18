package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.Bank;
import org.apache.log4j.Logger;

import java.util.List;

public abstract class BankDAO extends HibernateSupportDelegateDAO<Bank> {
	public static final Logger log = Logger.getLogger(BankDAO.class);

	public BankDAO() {
		super();
		this.setEntityName(Bank.class.getName());
		this.setKeyField("bankId");
	}

	@DAOMethod(sql="from Bank where bankStatus=1 order by orderNum")
	public abstract List<Bank> findEffBanks();

	@DAOMethod(sql="select bankIcon from Bank where bankName=:bankName")
	public abstract String getBankIconByBankName(@DAOParam("bankName") String bankName);
}
