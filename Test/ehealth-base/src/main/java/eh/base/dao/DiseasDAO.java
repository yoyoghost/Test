package eh.base.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.base.Diseas;
import eh.entity.base.Organ;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.List;

public abstract class DiseasDAO extends HibernateSupportDelegateDAO<Diseas> {

	public DiseasDAO() {
		super();
		this.setEntityName(Diseas.class.getName());
		this.setKeyField("diseasId");
	}

	/**
	 * 疾病名称检索服务
	 * <p>
	 * 2016-6-20 luf:由于封装方法只能like p%,为前端不做修改，将方法改写为sql语句
	 * zhongzx 由于加了diseasLib 字段 把diseasLib 设为默认的 icd10
	 * @param diseasName 疾病名称
	 * @return List<Diseas>
	 * @author luf
	 */
	@RpcService
	public List<Diseas> findByDiseasNameLike(final String diseasName) {
		HibernateStatelessResultAction<List<Diseas>> action = new AbstractHibernateStatelessResultAction<List<Diseas>>() {
			@Override
			public void execute(StatelessSession statelessSession) throws Exception {
				String hql = "From Diseas where diseasName like :diseasName and diseasLib = :lib";
				Query q = statelessSession.createQuery(hql);
				q.setParameter("diseasName", "%" + diseasName + "%");
				q.setParameter("lib", "icd10");
				q.setMaxResults(10);
				setResult(q.list());
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 如果机构的diseasLib 没有值 默认设置为 icd10-国家标准疾病编码表
	 * @param organId
     */
	private void setDefaultLib(int organId){
		OrganDAO dao = DAOFactory.getDAO(OrganDAO.class);
		Organ o = dao.get(organId);
		if(StringUtils.isEmpty(o.getDiseasLib())){
			o.setDiseasLib("icd10");
			//更新数据库
			dao.update(o);
		}
	}

	/**
	 * 疾病名称检索服务
	 * 
	 * @author luf
	 * @param diseasName
	 *            疾病名称
	 * @return List<Diseas>
	 */
	public List<Diseas> findByDiseasNameLikeNew(final int organId, final String diseasName, final int start, final int limit){

		//判断机构的疾病编码库是否为空 空就使用标准编码库
		setDefaultLib(organId);

		HibernateStatelessResultAction<List<Diseas>> action = new AbstractHibernateStatelessResultAction<List<Diseas>>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				StringBuilder hql = new StringBuilder("select d from Diseas d,Organ o "
						+ "where d.diseasLib = o.diseasLib and o.organId = :organId "
						+ "and (d.diseasName like :diseasName or d.pyCode like :diseasName) order by d.pyCode");
				Query q = ss.createQuery(hql.toString());
				q.setParameter("organId",organId);
				q.setParameter("diseasName","%" + diseasName + "%");
				q.setFirstResult(start);
				q.setMaxResults(limit);
				setResult(q.list());
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 查询医生对应机构 常用诊断 最多显示10条
	 * zhongzx
	 * @param doctor
	 * @param organId
	 * @param start
	 * @param limit
     * @return
     */
	public List<Diseas> findCommonDiseasByDoctorAndOrganId(final int doctor,final int organId,
														   final int start,final int limit){
		HibernateStatelessResultAction<List<Diseas>> action = new AbstractHibernateStatelessResultAction<List<Diseas>>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				StringBuilder hql = new StringBuilder("select a from Diseas a,Recipe b,Organ o where b.doctor=:doctor "
						+ "and b.clinicOrgan=:organId and o.organId=:organId and a.diseasLib=o.diseasLib "
						+ "and b.organDiseaseId=a.icd10 "
						+ "group by b.organDiseaseId order by count(*) desc");
				Query q = ss.createQuery(hql.toString());
				q.setParameter("organId",organId);
				q.setParameter("doctor",doctor);
				q.setFirstResult(start);
				q.setMaxResults(limit);
				setResult(q.list());
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}
}
