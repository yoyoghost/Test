package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.his.TempTable;
import eh.task.executor.SaveHisAppointRecordExecutor;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.List;

public abstract class TempTableDAO extends
		HibernateSupportDelegateDAO<TempTable> {
	public TempTableDAO() {
		super();
		this.setEntityName(TempTable.class.getName());
		this.setKeyField("id");
	}

	@RpcService
	@DAOMethod
	public abstract TempTable getById(int id);

	@RpcService
	public List<TempTable> getAll() throws DAOException {
		HibernateStatelessResultAction<List<TempTable>> action = new AbstractHibernateStatelessResultAction<List<TempTable>>() {
			List<TempTable> list = new ArrayList<TempTable>();

			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws Exception {
				StringBuilder hql = new StringBuilder("from TempTable");
				Query q = ss.createQuery(hql.toString());

				list = (List<TempTable>) q.list();
				setResult(list);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (List<TempTable>) action.getResult();
	}

	@RpcService
	public void saveTempTable(List<TempTable> tmpList) {
		SaveHisAppointRecordExecutor executor = new SaveHisAppointRecordExecutor(
				tmpList);
		executor.execute();
	}

}
