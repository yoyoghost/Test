package eh.bus.dao;

import com.google.common.collect.Lists;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.bus.RegPatientInfo;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.List;

public abstract class RegPatientInfoDAO extends
		HibernateSupportDelegateDAO<RegPatientInfo> {
	public RegPatientInfoDAO() {
		super();
		this.setEntityName(RegPatientInfo.class.getName());
		this.setKeyField("id");
	}

	/**
	 * 获取doctorId
	 * 
	 * @param jobNumber
	 * @param organId
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "select doctorId from Employment where jboNumber =:jobNumber and organId =:organId")
	public abstract Integer getDoctorIdByJobAndOr(
			@DAOParam("jobNumber") int jobNumber,
			@DAOParam("organId") int organId);

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@RpcService
	/**
	 *根据idCard and jobNumber and organId 查询病人信息
	 * @return
	 */
	public List<RegPatientInfo> queryPatientInfo( final RegPatientInfo reg) throws DAOException {
		List<RegPatientInfo> asList = Lists.newArrayList();
		//@SuppressWarnings("rawtypes")
		HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				Query query = null;
				StringBuilder hql = new StringBuilder(
						"from RegPatientInfo where idCard=:idCard and jobNumber=:jobNumber and "
								+ "organId=:organId ");
				query = ss.createQuery(hql.toString());

				query.setString("idCard", reg.getIdCard());
				query.setString("jobNumber", reg.getJobNumber());
				query.setInteger("organId", reg.getOrganId());
				List<RegPatientInfo> temp = query.list();

				if (temp == null || temp.size() == 0) {
					// 新增
					RegPatientInfo r = new RegPatientInfo();
					r.setCardNo(reg.getCardNo());
					r.setDoctorName(reg.getDoctorName());
					r.setIdCard(reg.getIdCard());
					r.setJobNumber(reg.getJobNumber());
					r.setMobile(reg.getMobile());
					r.setOrganId(reg.getOrganId());
					r.setPatientName(reg.getPatientName());
					r.setPatientType(reg.getPatientType());
					r.setDoctorId(reg.getDoctorId());
					save(r);
				} else {
					// 更新
					hql = new StringBuilder(
							"update RegPatientInfo r set r.cardNo =:cardNo,r.doctorName =:doctorName,"
									+ "r.mobile =:mobile,r.patientName =:patientName,r.patientType =:patientType,r.doctorId =:doctorId "
									+ "where r.idCard =:idCard and r.jobNumber =:jobNumber and r.organId =:organId");
					
					query = ss.createQuery(hql.toString());

					query.setString("cardNo", reg.getCardNo());
					query.setString("doctorName", reg.getDoctorName());
					query.setString("mobile", reg.getMobile());
					query.setString("patientName", reg.getPatientName());
					query.setString("patientType", reg.getPatientType());
					query.setInteger("doctorId", reg.getDoctorId());
					query.setString("idCard", reg.getIdCard());
					query.setString("jobNumber", reg.getJobNumber());
					query.setInteger("organId", reg.getOrganId());

					query.executeUpdate();
				}

				setResult(temp);
			}
		};
		HibernateSessionTemplate.instance().execute(action);
		asList = (List) action.getResult();
		return asList;
	}

}
