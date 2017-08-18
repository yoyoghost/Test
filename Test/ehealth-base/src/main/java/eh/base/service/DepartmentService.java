package eh.base.service;

import ctd.account.UserRoleToken;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.constant.DoctorTabConstant;
import eh.base.constant.ServiceType;
import eh.base.dao.DepartmentDAO;
import eh.base.dao.HisServiceConfigDAO;
import eh.bus.dao.AppointDepartDAO;
import eh.entity.base.Department;
import eh.entity.base.Doctor;
import eh.op.auth.service.SecurityService;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.*;

public class DepartmentService {

	public static final Logger log = Logger.getLogger(DepartmentService.class);

	/**
	 * 医院专科类别查询服务（原departmentDAO-findValidProfession）
	 *
	 * @author luf
	 * @param organId
	 * @param deptType
	 *            (1临床科室,2挂号科室)
	 * @param flag 标志--0包含学科团队，1不包含学科团队
	 * @return List<Object>
	 */
	@RpcService
	public List<Object> findValidProfessionTeam(Integer organId, int deptType,int flag) {
		List<String> list = new ArrayList<String>();
		List<Object> result = new ArrayList<Object>();
		switch (deptType) {
			case 1:
				DepartmentDAO departmentDAO = DAOFactory.getDAO(DepartmentDAO.class);
				list = departmentDAO.findProfessionCodeByOrganId(organId);
				break;
			case 2:
				AppointDepartDAO appointdepartdao = DAOFactory
						.getDAO(AppointDepartDAO.class);
				list = appointdepartdao.findProfessionCodeByOrganId(organId);
				break;
		}
		for (int i = 0; i < list.size(); i++) {
			Map<String, String> map = new HashMap<String, String>();
			try {
				String code = list.get(i);
				if(flag==1 && code.trim().equals("00")) {
					continue;
				}
				String professionName = DictionaryController.instance()
						.get("eh.base.dictionary.Profession")
						.getText(code);

				map.put("professionCode", code);
				map.put("professionName", professionName);
				result.add(map);
			} catch (ControllerException e) {
				log.error("findValidProfessionTeam() error: "+e);
			}
		}
		return result;
	}

	/**
	 * 医院专科类别查询服务（原departmentDAO-findValidProfession）
	 *
	 * @author hjh
	 * @param organId
	 * @param deptType
	 *            (1临床科室,2挂号科室)
	 * @param flag 标志--0包含学科团队，1不包含学科团队
	 * @return List<Object>
	 */
	@RpcService
	public List<Object> findValidInHpProfessionTeam(Integer organId, int deptType,int flag) {
		List<String> list = new ArrayList<String>();
		List<Object> result = new ArrayList<Object>();
		DepartmentDAO departmentDAO = DAOFactory.getDAO(DepartmentDAO.class);
		list = departmentDAO.findInProfessionCodeByOrganId(organId);
		for (int i = 0; i < list.size(); i++) {
			Map<String, String> map = new HashMap<String, String>();
			try {
				String code = list.get(i);
				if(flag==1 && code.trim().equals("00")) {
					continue;
				}
				String professionName = DictionaryController.instance()
						.get("eh.base.dictionary.Profession")
						.getText(code);

				map.put("professionCode", code);
				map.put("professionName", professionName);
				result.add(map);
			} catch (ControllerException e) {
				log.error("findValidProfessionTeam() error: "+e);
			}
		}
		return result;
	}

	/**
	 * 机构会诊中心科室查询（剔除无会诊中心）
	 * zhangsl 2017-05-31 18:31:51
	 * @param organId
	 * @param professionCode
	 * @return
	 */
	public List<Department> findValidDepartmentForMeetCenter(final Integer organId, final String professionCode) {
		List<Department> result=new ArrayList<Department>();
		UserRoleToken ur = (UserRoleToken) ContextUtils
				.get(Context.USER_ROLE_TOKEN);
		Doctor doctor = (Doctor) ur.getProperty("doctor");
		if (doctor == null) {
			return result;
		}
		final int doctorId = doctor.getDoctorId();
		HibernateStatelessResultAction<List<Department>> action = new AbstractHibernateStatelessResultAction<List<Department>>() {
			List<Department> list = new ArrayList<Department>();
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws Exception {
				StringBuilder hql = new StringBuilder("select distinct d from Department d,Employment e,ConsultSet c " +
						"where d.organId=:organId and d.status=0 and d.professionCode like :professionCode and d.organId=e.organId and d.deptId=e.department " +
						"and exists(from DoctorTab dt,Doctor r where c.doctorId=r.doctorId and dt.paramType=:paramType and dt.paramValue=:paramValue and r.doctorId=dt.doctorId and r.doctorId<>:doctorId and r.status=1) " +
						"and e.doctorId=c.doctorId and c.meetClinicStatus=1");
				Query q = ss.createQuery(hql.toString());
				q.setParameter("professionCode", professionCode + "%");
				q.setParameter("organId", organId);
				q.setParameter("doctorId", doctorId);
				q.setParameter("paramType", DoctorTabConstant.ParamType_MEETCENTER);
				q.setParameter("paramValue", DoctorTabConstant.ParamValue_TRUE);
				list = (List<Department>) q.list();
				setResult(list);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		result = action.getResult();
		return result;
	}

	/**
	 * 机构云门诊科室查询（剔除无号源）
	 * zhangsl 2017-06-02 14:28:59
	 * @param organId
	 * @param professionCode
	 * @return
	 */
	public List<Department> findValidDepartmentForCloud(final Integer organId, final String professionCode) {
		List<Department> result=new ArrayList<Department>();
		UserRoleToken ur = (UserRoleToken) ContextUtils
				.get(Context.USER_ROLE_TOKEN);
		Doctor doctor = (Doctor) ur.getProperty("doctor");
		if (doctor == null) {
			return result;
		}
		final int doctorId = doctor.getDoctorId();
		final Boolean canAppointToday=DAOFactory.getDAO(HisServiceConfigDAO.class).isServiceEnable(organId, ServiceType.CANAPPOINTTODAY);
		HibernateStatelessResultAction<List<Department>> action = new AbstractHibernateStatelessResultAction<List<Department>>() {
			List<Department> list = new ArrayList<Department>();
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws Exception {
				StringBuilder hql = new StringBuilder("select distinct d from Department d,Employment e " +
						"where d.organId=:organId and d.status=0 and d.professionCode like :professionCode and e.department=d.deptId and e.doctorId<>:doctorId " +
						"and exists(FROM AppointSource s WHERE s.doctorId=e.doctorId AND s.cloudClinic=1 AND s.cloudClinicType=1 AND s.stopFlag=0 ");
				hql.append("AND s.organId=:organId ");//要有当前机构下的号源
				if(canAppointToday){//当天号源可约
					hql.append("AND ((s.workDate>NOW() AND (s.sourceNum-s.usedNum)>0) OR (s.workDate=DATE(NOW()) and sourceNum>=usedNum))) ");
				}else{
					hql.append("AND s.workDate>NOW() AND (s.sourceNum-s.usedNum)>0) ");
				}
				Query q = ss.createQuery(hql.toString());
				q.setParameter("professionCode", professionCode + "%");
				q.setParameter("organId", organId);
				q.setParameter("doctorId", doctorId);
				list = (List<Department>) q.list();
				setResult(list);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		result = action.getResult();
		return result;
	}

	/**
	 * 按筛选入口类型查询有效科室
	 * zhangsl 2017-05-31 19:04:56
	 * @param organId
	 * @param professionCode
	 * @param deType 1-会诊中心，2云门诊预约
	 * @return
	 */
	@RpcService
	public List<Department> findValidDepartmentByDetailType(Integer organId, String professionCode, int deType) {
		List<Department> result = new ArrayList<Department>();
		switch (deType) {
			case 1://会诊中心
				result = this.findValidDepartmentForMeetCenter(organId, professionCode);
				break;
			case 2://云门诊预约
				result = this.findValidDepartmentForCloud(organId, professionCode);
				break;
			default:
				break;

		}
		return result;
	}

	/**
	 * 运营平台（权限改造）
	 * @param organId
	 * @return
	 */
	@RpcService
	public List<Department> findAllByOrganIdForOp(Integer organId){
		Set<Integer> o = new HashSet<Integer>();
		o.add(organId);
		if(!SecurityService.isAuthoritiedOrgan(o)){
			return null;
		}
		DepartmentDAO departmentDAO = DAOFactory.getDAO(DepartmentDAO.class);
		return departmentDAO.findAllByOrganId(organId);
	}


}
