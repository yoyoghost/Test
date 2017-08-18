package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.context.Context;
import eh.entity.mpi.AutoInvalidLog;
import eh.mpi.constant.AutoInvalidLogConstant;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;

public abstract class AutoInvalidLogDAO extends HibernateSupportDelegateDAO<AutoInvalidLog> {
	private static final Log logger = LogFactory.getLog(AutoInvalidLogDAO.class);

	public AutoInvalidLogDAO() {
		super();
		this.setEntityName(AutoInvalidLog.class.getName());
		this.setKeyField("id");
	}

	@DAOMethod
	public abstract AutoInvalidLog getByInvalidMpiAndKeepMpi(String invalidMpi,String keepMpi);

	/**
	 * 2016-12-8 12:08:44 zhangx wx2.6-插入一条更新日志，用于后期更新业务数据使用
     */
	public void saveInvalidLog(String invalidMpi,String keepMpi){
		Date now= Context.instance().get("date.now",Date.class);

		AutoInvalidLog haslog=getByInvalidMpiAndKeepMpi(invalidMpi,keepMpi);
		if(haslog!=null){
			return;
		}

		AutoInvalidLog log=new AutoInvalidLog();
		log.setInvalidMpi(invalidMpi);
		log.setKeepMpi(keepMpi);
		log.setCreateDate(now);
		log.setLastModify(now);
		log.setStatus(AutoInvalidLogConstant.LOG_STATUS_NO_DEAL);
		save(log);
	}
}
