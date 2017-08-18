package eh.mpi.dao;

import com.alibaba.fastjson.JSONObject;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryItem;
import ctd.dictionary.service.DictionaryLocalService;
import ctd.dictionary.service.DictionarySliceRecordSet;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.schema.exception.ValidateException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.SystemConstant;
import eh.base.dao.OrganConfigDAO;
import eh.base.dao.OrganDAO;
import eh.base.user.UserSevice;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.OrganConfig;
import eh.entity.mpi.FamilyMember;
import eh.entity.mpi.FamilyMemberAndPatient;
import eh.entity.mpi.Patient;
import eh.mpi.constant.FamilyMemberConstant;
import eh.mpi.service.FamilyMemberService;
import eh.util.ChinaIDNumberUtil;
import eh.util.SameUserMatching;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;

public abstract class FamilyMemberDAO extends
		HibernateSupportDelegateDAO<FamilyMember> {
	private static final Logger logger = LoggerFactory.getLogger(FamilyMemberDAO.class);

	public FamilyMemberDAO() {
		super();
		this.setEntityName(FamilyMember.class.getName());
		this.setKeyField("memberId");
	}

	@Override
	public FamilyMember update(FamilyMember member) throws DAOException {
		member.setLastModify(new Date());
		return super.update(member);
	}

	@Override
	public FamilyMember save(FamilyMember member) throws DAOException {
		member.setMemeberStatus(FamilyMemberConstant.MEMBER_STATUS_NO_DEL);
		member.setRelation(member.getRelation()==null?FamilyMemberConstant.MEMBER_RELATION_NO:member.getRelation());
		member.setCreateDt(new Date());
		member.setLastModify(new Date());
		
		if(member.getOrganId()==null){
		
			Integer organId=FamilyMemberConstant.ORGAN_NGARI;
			Map<String,String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
			if(wxAppProperties != null){
				organId=StringUtils.isEmpty(wxAppProperties.get("organId"))?
						FamilyMemberConstant.ORGAN_NGARI:Integer.valueOf(wxAppProperties.get("organId"));

			}
			member.setOrganId(organId);

			OrganConfigDAO configDAO=DAOFactory.getDAO(OrganConfigDAO.class);
			OrganConfig config=configDAO.getByOrganId(organId);
			if(config==null){
				config=new OrganConfig();
			}
			Integer isolationFlag=config.getIsolationFlag()==null?FamilyMemberConstant.ISOLATION_NO:config.getIsolationFlag();

			member.setIsolationFlag(isolationFlag);
		}
		return super.save(member);
	}

	/**
	 * 家庭成员查询服务--hyj
	 *
	 * @param mpiid
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "select new eh.entity.mpi.FamilyMemberAndPatient(a,b) from FamilyMember a,Patient b where a.mpiid=:mpiId and a.memberMpi=b.mpiId")
	public abstract List<FamilyMemberAndPatient> findFamilyMemberAndPatientByMpiid(
			@DAOParam("mpiId") String mpiId);

	/**
	 * 家庭成员查询服务（包括病人自己的信息）
	 *
	 * @author Qichengjian
	 * @param mpiid
	 *            病人ID
	 * @return 家庭成员列表和病人信息
	 */
	@RpcService
	public List<Patient> findPatientsByMpiid(String mpiid) {
		List<Patient> patients;
		List<String> list;
		list = findMemberMpiByMpiid(mpiid);
		list.add(mpiid);
		PatientDAO dao = DAOFactory.getDAO(PatientDAO.class);
		patients = dao.findByMpiIdIn(list);
		return patients;
	}

	/**
	 * 家庭成员增加服务--hyj
	 *
	 * @param familymember
	 */
	@RpcService
	public void addFamilyMember(FamilyMember familymember) {
		if (StringUtils.isEmpty(familymember.getMpiid())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"mpiid is required");
		}
		if (StringUtils.isEmpty(familymember.getMemberMpi())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"memberMpi is required");
		}
		FamilyMember familyMember2 = getByMpiIdAndMemberMpi(
				familymember.getMpiid(), familymember.getMemberMpi());
		if (familyMember2 == null) {
			familymember.setCreateDt(new Date());
			familymember.setLastModify(new Date());
			familymember.setMemeberStatus(FamilyMemberConstant.MEMBER_STATUS_NO_DEL);
			save(familymember);
		} else {
			familyMember2.setRelation(familymember.getRelation());
			familymember.setLastModify(new Date());
			this.update(familyMember2);
		}
	}

	/**
	 * 家庭成员增加服务2（同时保存病人表信息）
	 *
	 * @author Qichengjian
	 * @param mpiId
	 *            病人id
	 * @param patient
	 *            家庭成员详细信息
	 * @param relation
	 *            家庭成员的关系
	 */
	@RpcService
	@SuppressWarnings("rawtypes")
	public Patient addFamilyMemberAndPatient(final String mpiId,
											 final Patient patient, final String relation) {

		logger.info("家庭成员添加：mpiid=" + mpiId + ";patinet="
				+ JSONUtils.toString(patient) + "; realtion=" + relation);

		if (StringUtils.isEmpty(mpiId)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"mpiId is required");
		}

		if (patient.getLoginId() != null) {
			patient.setLoginId(null);
		}

		HibernateStatelessResultAction<Patient> action = new AbstractHibernateStatelessResultAction<Patient>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {
				// 保存病人表信息
				PatientDAO dao = DAOFactory.getDAO(PatientDAO.class);

				// 不能添加自己作为家庭成员
				//2016-12-23 15:46:26 zhangx wx2.7 儿童患者需求：儿童患者时，前端录入的身份证为监护人的身份证
				//判断时需要根据时间情况进行判断
				Boolean guardianFlag=patient.getGuardianFlag()==null?Boolean.valueOf(false):patient.getGuardianFlag();
				if(!guardianFlag){
					Patient familyPatient = dao.getByMpiId(mpiId);
					String idcard = familyPatient.getIdcard();
					if (idcard.equals(patient.getIdcard())) {
						throw new DAOException(609, "不能添加自己作为家庭成员");
					}
				}


				// 使用保存后的patient1对象的主键
				try {
					String idCard18 = ChinaIDNumberUtil.convert15To18(patient
							.getIdcard());
					//wx2.7 儿童患者需求，出生日期和性别都是前端录入的数据，不根据身份证进行判断
					if(!guardianFlag){
						patient.setBirthday(ChinaIDNumberUtil
								.getBirthFromIDNumber(idCard18));
						patient.setPatientSex(ChinaIDNumberUtil
								.getSexFromIDNumber(idCard18));
					}

				} catch (ValidateException e) {
					throw new DAOException(609, "身份证不正确");
				}

				Patient patient1 = dao.getOrUpdate(patient);

				FamilyMember familyMember = new FamilyMember();
				familyMember.setMpiid(mpiId);
				// 使用保存后的patient1对象的主键
				familyMember.setMemberMpi(patient1.getMpiId());
				familyMember.setRelation(relation);
				// 保存家庭成员表信息
				addFamilyMember(familyMember);

				setResult(patient1);
			}
		};
		HibernateSessionTemplate.instance().executeTrans(action);

		//刷新服务器缓存
		Patient p=action.getResult();
		UserSevice.updateUserCache(p.getLoginId(), SystemConstant.ROLES_PATIENT,"patient",p);

		return p;
	}

	/**
	 * 删除家庭成员服务
	 *
	 * @author Qichengjian
	 * @param familymember
	 *            家庭成员
	 */
	@RpcService
	public void delFamilyMember(FamilyMember familymember) {
		if (StringUtils.isEmpty(familymember.getMpiid())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"mpiid is required");
		}
		if (StringUtils.isEmpty(familymember.getMemberMpi())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"memberMpi is required");
		}
		checkCanDelete(familymember);

		FamilyMember member = getByMpiIdAndMemberMpi(
				familymember.getMpiid(), familymember.getMemberMpi());
		if (member != null) {
			//2017-7-1 12:15:59 zhangx 上海六院就诊人优化v1.0，将硬删除改为软删除
			member.setMemeberStatus(FamilyMemberConstant.MEMBER_STATUS_HAS_DEL);
			update(member);
		}
	}
	/**
	 * 判断根据公众号的设置，能否删除就诊人
	 *
	 * @param familymember
	 */
	private void checkCanDelete(FamilyMember familymember) {
		String mpiid = familymember.getMemberMpi();
		HashMap<String, Object> wxPropsMap = DAOFactory.getDAO(OrganDAO.class).getWxOrgansDisplay();
		Object forbidDeleteDays = wxPropsMap.get("forbidDeleteDays");
		if (wxPropsMap != null && forbidDeleteDays != null) {
			if (NumberUtils.isNumber(String.valueOf(forbidDeleteDays)) && Integer.parseInt(String.valueOf(forbidDeleteDays)) > 0) {

				Object type = wxPropsMap.get("type");
				List<Integer> organs = null;
				if (wxPropsMap.get("organs") != null) {
					organs = ((List<Integer>) wxPropsMap.get("organs"));
				}

				if (type != null) {
					String wxAppType = ((String) type);
					Integer mpiAppointRecordCount = 0;
					final Integer fDays = Integer.parseInt(String.valueOf(forbidDeleteDays));
					switch (wxAppType) {
						case "1":
							mpiAppointRecordCount = areaWxAppForbidDelMember(organs, mpiid, fDays);
							break;
						case "2":
							mpiAppointRecordCount = organWxAppForbidDelMember(organs, mpiid,fDays);
							break;
						default:
							mpiAppointRecordCount = ngariWxAppForbidDelMember(mpiid,fDays);
							break;
					}
					;logger.info("患者的预约记录organs[{}],mpiid:[{}]，结果[{}]:",JSONUtils.toString(organs),mpiid,JSONUtils.toString(mpiAppointRecordCount));

					if (mpiAppointRecordCount > 0) {
						throw new DAOException("当前就诊人在" + forbidDeleteDays + "天内不可删除");
					}

				}

			}
		}

	}

	/**
	 * 是否有在平台挂过号
	 */
	private Integer ngariWxAppForbidDelMember(String mpiid,Integer forbidDeleteDays) {
		return organWxAppForbidDelMember(null, mpiid,forbidDeleteDays);
	}

	/**
	 * 是否在机构内挂过号
	 *
	 * @param organs
	 */
	private Integer organWxAppForbidDelMember(List<Integer> organs, String mpiid,Integer forbidDeleteDays) {
		AppointRecordDAO appointRecordDao = DAOFactory.getDAO(AppointRecordDAO.class);
		return appointRecordDao.findByOrganAndMpi(organs, mpiid,forbidDeleteDays).size();
	}

	/**
	 * 区域公众号删除就诊人，需要查询当前就诊人是否在区域机构内预约挂号过
	 *
	 * @param organs
	 */
	private Integer areaWxAppForbidDelMember(List<Integer> organs, String mpiid,Integer forbidDeleteDays) {
		return organWxAppForbidDelMember(organs, mpiid,forbidDeleteDays);
	}
	/**
	 * 根据主键删除家庭成员
	 *
	 * @author zhangx
	 * @date 2016-1-11 上午10:20:03
	 * @param memberId
	 */
	@RpcService
	public void delFamilyMemberByMemberId(Integer memberId) {
		if (memberId == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"memberId is required");
		}

		if (!exist(memberId)) {
			throw new DAOException(609, "不存在这个家庭成员关系");
		}

		this.remove(memberId);
	}

	/**
	 * 根据mpiId和memberMpi查询家庭成员
	 *
	 * @author Qichengjian
	 * @param mpiId
	 * @param memberMpi
	 * @return 家庭成员对象
	 */
	@RpcService
	@DAOMethod(sql = "from FamilyMember where MPIID =:mpiId and MemberMPI =:memberMpi ")
	public abstract FamilyMember getByMpiIdAndMemberMpi(
			@DAOParam("mpiId") String mpiId,
			@DAOParam("memberMpi") String memberMpi);

	/**
	 * 根据病人id查找家庭成员病人id
	 *
	 * @author Qichengjian
	 * @param mpiid
	 *            病人id
	 * @return 家庭成员病人id列表
	 */
	@RpcService
	@DAOMethod(sql = "select memberMpi from FamilyMember where mpiid=:mpiid")
	public abstract List<String> findMemberMpiByMpiid(
			@DAOParam("mpiid") String mpiid);

	/**
	 * 获取加入该患者为家庭成员的患者
	 * @param mpiid
	 * @return
	 */
    @RpcService
	@DAOMethod(sql = "select mpiid from FamilyMember where memberMpi=:mpiid")
	public abstract List<String> findMpiidByMemberMpi(
			@DAOParam("mpiid") String mpiid);

    @DAOMethod(sql = "select mpiid from FamilyMember where memberMpi in :mpiIdList")
    public abstract List<String> findMpiidByMemberMpiList(
            @DAOParam("mpiIdList") List<String> mpiIdList);

	/**
	 * 家庭成员关系字典
	 *
	 * @author ZX
	 * @date 2015-8-7 上午10:47:10
	 * @return
	 */
	@RpcService
	public List<DictionaryItem> getRelation() {
		DictionaryLocalService ser= AppContextHolder.getBean("dictionaryService",DictionaryLocalService.class);
		List<DictionaryItem> list = new ArrayList<DictionaryItem>();
		try {
			DictionarySliceRecordSet var = ser.getSlice(
					"eh.mpi.dictionary.Relation", "", 0, "", 0, 0);
			list = var.getItems();

		} catch (ControllerException e) {
			logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
		}
		return list;
	}

	/**
	 * 供familyMemberList调用
	 *
	 * @author luf
	 * @param mpiId
	 *            当前患者主索引
	 * @return List<FamilyMember>
	 */
	@DAOMethod(orderBy = "createDt desc,memberId desc",limit=0)
	public abstract List<FamilyMember> findByMpiid(String mpiId);

	@DAOMethod(sql = "select memberMpi from FamilyMember where mpiId=:mpiId and memeberStatus=1",orderBy = "memberId desc")
	public abstract List<String> findMemberMpiIdsByMpiId(@DAOParam("mpiId")String mpiId);

	/**
	 * 按照特定顺序排序
	 * @param mpiId
	 * @return
	 * 上海六院就诊人改造-zhangx 添加自己为就诊人，删除改成软删除，不直接删除数据
     */
	@DAOMethod(sql = "FROM FamilyMember WHERE mpiId = :mpiId and memeberStatus=1 and relation>0 ORDER BY FIELD(relation,4,1,2,3)")
	public abstract List<FamilyMember> findFamilyMembersWithField(@DAOParam("mpiId") String mpiId);

	/**
	 * 家庭成员列表(包括自己)
	 *
	 * @author luf
	 * @param mpiId
	 *            当前患者主索引
	 * @return List<Patient>
	 */
	@RpcService
	public List<HashMap<String, Object>> familyMemberList(String mpiId) {
		FamilyMemberService familyMemberService = AppContextHolder.getBean("eh.familyMemberService", FamilyMemberService.class);

		return familyMemberService.familyMemberList(mpiId);


	}

	/**
	 * 获取家庭成员列表[排除与医生身份证相同的相对应的患者]
	 *
	 * @author zhangx
	 * @date 2016-3-5 下午5:44:02
	 * @param mpiId
	 * @param doctorId
	 * @return
	 */
	@RpcService
	public List<HashMap<String, Object>> familyMemberListOutSelf(String mpiId,
																 int doctorId) {
		PatientDAO dao = DAOFactory.getDAO(PatientDAO.class);

		List<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		Patient self = dao.getByMpiId(mpiId);
		HashMap<String, Object> selfmap = new HashMap<String, Object>();

		if (!SameUserMatching.patientAndDoctor(mpiId, doctorId)) {
			selfmap.put("patient", self);
			selfmap.put("relation", new FamilyMember());
			list.add(selfmap);
		}

		List<FamilyMember> members = this.findByMpiid(mpiId);
		for (FamilyMember member : members) {
			if (!SameUserMatching.patientAndDoctor(member.getMemberMpi(),
					doctorId)) {
				HashMap<String, Object> map = new HashMap<String, Object>();
				Patient p = dao.getByMpiId(member.getMemberMpi());
				map.put("patient", p);

				FamilyMember m = new FamilyMember();
				m.setRelation(member.getRelation());

				map.put("relation", m);
				list.add(map);
			}
		}
		return list;
	}

	/**
	 * 内部查询列表使用-不隔离就诊人-获取所有设置成不隔离且未删除就诊人关系
	 * @param mpiid
	 * @return
     */
	public List<FamilyMember> findFamilyMemberStartWithSelf(final String mpiid){
		HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				List<FamilyMember> members=new ArrayList<>();

				String hql1="from FamilyMember where mpiid=:mpiid and mpiid=memberMpi and memeberStatus=1";
				Query query = ss.createQuery(hql1);
				query.setString("mpiid", mpiid);
				members.addAll(query.list());

				String hql2="FROM FamilyMember WHERE mpiid = :mpiid and memeberStatus=1 and isolationFlag=0 and relation>0 order by lastModify desc,memberId desc";
				Query query2 = ss.createQuery(hql2);
				query2.setString("mpiid", mpiid);
				members.addAll(query2.list());

				setResult(members);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (List<FamilyMember>) action.getResult();
	}

/**
	 * 内部查询列表使用-隔离就诊人-获取指定机构下所有未删除就诊人关系
	 * @param mpiid
	 * @return
	 */
	public List<FamilyMember> findOrganFamilyMemberStartWithSelf(final String mpiid,final Integer organId){
		HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				List<FamilyMember> members=new ArrayList<>();
				
				String hql1="from FamilyMember where mpiid=:mpiid and mpiid=memberMpi and memeberStatus=1";
				Query query = ss.createQuery(hql1);
				query.setString("mpiid", mpiid);
				members.addAll(query.list());
				
				String hql2="FROM FamilyMember WHERE mpiid = :mpiid and memeberStatus=1 and organId=:organId  and relation>0 order by lastModify desc,memberId desc";
				Query query2 = ss.createQuery(hql2);
				query2.setString("mpiid", mpiid);
				query2.setInteger("organId", organId);
				members.addAll(query2.list());
				
				setResult(members);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (List<FamilyMember>) action.getResult();
	}
	
   /**
	 * 内部判断使用-不隔离就诊人-获取所有就诊人关系，包括已删除
	 * @param mpiid
	 * @param organId
     * @return
   */
	public List<FamilyMember> findAllMemberByMpiId(final String mpiid){
		HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				List<FamilyMember> members=new ArrayList<>();
				
				String hql1="from FamilyMember where mpiid=:mpiid and mpiid=memberMpi";
				Query query = ss.createQuery(hql1);
				query.setString("mpiid", mpiid);
				members.addAll(query.list());
				
				String hql3="FROM FamilyMember WHERE mpiid = :mpiid and relation>0 and isolationFlag=0  order by lastModify desc,memberId desc";
				Query query3 = ss.createQuery(hql3);
				query3.setString("mpiid", mpiid);
				members.addAll(query3.list());
				
				setResult(members);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (List<FamilyMember>) action.getResult();
		
	}
	/**
	 * 内部判断使用-隔离就诊人-获取指定机构下所有就诊人关系，包括已删除
	 * @param mpiid
	 * @param organId
     * @return
     */
	public List<FamilyMember> findOrganMemberByMpiId(final String mpiid,final Integer organId){
		HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				List<FamilyMember> members=new ArrayList<>();
				
				String hql1="from FamilyMember where mpiid=:mpiid and mpiid=memberMpi";
				Query query = ss.createQuery(hql1);
				query.setString("mpiid", mpiid);
				members.addAll(query.list());
				
				String hql2="FROM FamilyMember WHERE mpiid = :mpiid and relation>0 and organId=:organId  order by lastModify desc,memberId desc";
				Query query2 = ss.createQuery(hql2);
				query2.setString("mpiid", mpiid);
				query2.setInteger("organId", organId);
				members.addAll(query2.list());
				
				setResult(members);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (List<FamilyMember>) action.getResult();
	}

	/**
	 * 设置机构是否隔离就诊人数据时，需要同步调用这个接口
	 * @param isolationFlag
	 * @param organId
     */
	@DAOMethod
	public abstract void updateIsolationFlagByOrganId(Integer isolationFlag,Integer organId);


	/**
	 * 根据机构查询当天的新增患者
	 * @param organId Integer
	 * @return     	  List
	 * */
	public List<FamilyMember> findNewPatient(final Integer organId){
		Date now = new Date();
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		String date = df.format(now);
		final Date startTime = DateConversion.getCurrentDate(date,"yyyy-MM-dd");
		final Date endTime = DateConversion.getDateAftXDays(startTime,1);

		HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				List<FamilyMember> members=new ArrayList<>();
				String hql="FROM FamilyMember WHERE   organId=:organId  and  createDt>=:startTime and createDt<=:endTime";
				Query query = ss.createQuery(hql);
				query.setInteger("organId", organId);
				query.setDate("startTime",startTime);
				query.setDate("endTime",endTime);
				members.addAll(query.list());
				setResult(members);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (List<FamilyMember>) action.getResult();
	}


	/**
	 * 根据机构查询当天的修改患者
	 * @param organId Integer
	 * @return     	  List
	 * */
	public List<FamilyMember> findModifyFamilyMember(final Integer organId){
		Date now = new Date();
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		String date = df.format(now);
		final Date startTime = DateConversion.getCurrentDate(date,"yyyy-MM-dd");
		final Date endTime = DateConversion.getDateAftXDays(startTime,1);
		HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				List<FamilyMember> members=new ArrayList<>();
				String hql="select  f  FROM Patient p, FamilyMember f " +
						"WHERE p.mpiId = f.memberMpi AND f.organId =:organId " +
						"AND (" +
						"(f.lastModify <=:endTime AND f.lastModify >=:startTime AND f.createDt <=:startTime) " +
						"OR " +
						"( p.lastModify <=:endTime AND p.lastModify >=:startTime AND p.createDate <=:startTime) " +
						")";
				Query query = ss.createQuery(hql);
				query.setInteger("organId", organId);
				query.setDate("startTime",startTime);
				query.setDate("endTime",endTime);
				members.addAll(query.list());
				setResult(members);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (List<FamilyMember>) action.getResult();


	}
}
