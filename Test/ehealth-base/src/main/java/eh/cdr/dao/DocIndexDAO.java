package eh.cdr.dao;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
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
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.DepartmentDAO;
import eh.base.dao.DoctorDAO;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.dao.TransferDAO;
import eh.entity.base.Department;
import eh.entity.base.Doctor;
import eh.entity.bus.Consult;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.Transfer;
import eh.entity.cdr.*;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.entity.his.push.callNum.PushResponseModel;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

public abstract class DocIndexDAO extends HibernateSupportDelegateDAO<DocIndex> {
	private static final Log logger = LogFactory.getLog(DocIndexDAO.class);

	public DocIndexDAO() {
		super();
		this.setEntityName(DocIndex.class.getName());
		this.setKeyField("docIndexId");
	}

	/**
	 * 文档索引查询服务之情况一（根据主索引查一个病人的全部文档索引记录）
	 * 
	 * @param mpiId
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract List<DocIndex> findByMpiid(String mpiId);

	/**
	 * 文档索引查询服务之情况二（根据主索引和就诊序号查一个病人一次看病的文档索引记录）
	 * 
	 * @param mpiId
	 * @param clinicId
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract List<DocIndex> findByMpiidAndClinicId(String mpiId,
			int clinicId);

	/**
	 * 文档索引查询服务之情况三（根据主索引和文档类别查一个病人的某类索引文档）
	 * 
	 * @param mpiId
	 * @param docType
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract List<DocIndex> findByMpiidAndDocType(String mpiId,
			String docType);

	@RpcService
	@DAOMethod(sql = "from DocIndex where mpiid=:Mpiid and clinicId=:ClinicId and docType=:DocType")
	public abstract List<DocIndex> findByMpiidAndDocTypeAndClinicId(
			@DAOParam("Mpiid") String mpiId,
			@DAOParam("DocType") String docType,
			@DAOParam("ClinicId") int clinicId);

	/**
	 * 按病历类别统计文档数服务
	 * 
	 * @param mpiId
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "select new eh.entity.cdr.DocNumType(docType,count(docType)) from DocIndex where mpiid=:mpiId group by docType")
	public abstract List<DocNumType> findDocNumByType(
			@DAOParam("mpiId") String mpiId);

	/**
	 * 文档展示查询服务
	 * 
	 * @param docIndexId
	 *            文档索引序号
	 * @return
	 */
	@RpcService
	public String getDispDocUrl(Integer docIndexId) {
		return "http://121.41.40.2:8089/ehealth-base/showDoc.jsp";
		// return "http://127.0.0.1:8081/ehealth-base/showDoc.jsp";
	}

	/**
	 * 获取文档类型字典
	 */
	@RpcService
	public List<DictionaryItem> getDocType() {
		DictionaryLocalService ser= AppContextHolder.getBean("dictionaryService",DictionaryLocalService.class);
		List<DictionaryItem> list = new ArrayList<DictionaryItem>();
		try {
			DictionarySliceRecordSet var = ser.getSlice(
					"eh.cdr.dictionary.DocType", null, 3, "", 0, 0);
			list = var.getItems();

		} catch (ControllerException e) {
			logger.error(e);
		}
		return list;
	}

	/**
	 * 电子病历文档索引保存服务
	 * 
	 * @author hyj
	 * @param doc
	 */
	@RpcService
	public DocIndex saveDocIndex(DocIndex doc) {
		if (StringUtils.isEmpty(doc.getMpiid())) {
//			logger.error("mpiid is required");
			throw new DAOException(DAOException.VALUE_NEEDED,
					"mpiid is required");
		}
		return save(doc);
	}

	@DAOMethod
	public abstract void updateDocIdByDocIndexId(Integer docId,
			Integer docIndexId);

	/**
	 * @function 按病人、文档类型查询分页列表,按时间排序
	 * @author zhangjr
	 * @param mpiId
	 *            病人主索引
	 * @param docType
	 *            文档类型 可为null，即可查询全部类型
	 * @param start
	 *            从0开始
	 * @param limit
	 *            每页最大记录数
	 * @date 2015-10-30
	 * @return List<DocIndex>
	 *
	 * 2016-05-27 要将获取电子病历的链接从前端定死写改成后台获取，该接口作废，使用DocIndexService.findByMpiIdAndDocTypeWithPage服务
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@RpcService
	public List<HashMap> findByMpiIdAndDocTypeWithPage(final String mpiId,
			final String docType, final int start, final int limit) {
		if (StringUtils.isEmpty(mpiId)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "病人主索引不能为空!");
		}
		HibernateStatelessResultAction<TreeMap<String, List<DocIndex>>> action = new AbstractHibernateStatelessResultAction<TreeMap<String, List<DocIndex>>>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				// TODO Auto-generated method stub
				StringBuilder hql1 = new StringBuilder(
//						"select date_format(createDate,'%Y-%m-%d') from DocIndex d where d.mpiid=:mpiId ");
						"select createDate from DocIndex d where d.mpiid=:mpiId ");
				if (!StringUtils.isEmpty(docType)) {
					hql1.append("and d.docType=:docType ");
				}
				hql1.append(" order by d.createDate desc ");
				Query query1 = ss.createQuery(hql1.toString());
				query1.setParameter("mpiId", mpiId);
				if (!StringUtils.isEmpty(docType)) {
					query1.setParameter("docType", docType);
				}
				query1.setFirstResult(start);
				query1.setMaxResults(limit);
				List<Date> dateList = query1.list();
				TreeMap<String, List<DocIndex>> treeMap = new TreeMap<>(
						new Comparator<String>() {
							// 排序规则
							public int compare(String o1, String o2) {
								// 指定排序器按照降序排列
								SimpleDateFormat sdf = new SimpleDateFormat(
										"yyyy-MM-dd");
								int d = 0;
								try {
									Date date1 = sdf.parse(o1);
									Date date2 = sdf.parse(o2);
									d = date2.compareTo(date1);
								} catch (ParseException e) {
									logger.error(e);
								}
								return d;
							}
						});
				for (Date date : dateList) {
					List<DocIndex> list = new ArrayList<>();
					String d = DateConversion.getDateFormatter(date, "yyyy-MM-dd");
					if (treeMap.get(d) != null)
						continue;

					treeMap.put(d, list);
				}

				StringBuilder hql = new StringBuilder(
						"select d from DocIndex d where d.mpiid=:mpiId ");
				if (!StringUtils.isEmpty(docType)) {
					hql.append("and d.docType=:docType ");
				}
				hql.append("order by d.createDate desc");

				Query query = ss.createQuery(hql.toString());
				query.setParameter("mpiId", mpiId);
				if (!StringUtils.isEmpty(docType)) {
					query.setParameter("docType", docType);
				}
				query.setFirstResult(start);
				query.setMaxResults(limit);

				List<DocIndex> list = query.list();
				SimpleDateFormat sdfDateFormat = new SimpleDateFormat(
						"yyyy-MM-dd HH:mm:ss");
				OtherDocDAO dao = DAOFactory.getDAO(OtherDocDAO.class);
				for (DocIndex docIndex : list) {
					int docClass = docIndex.getDocClass();
					Integer docId = docIndex.getDocId();
					if (docClass == 99 && docId != null) {
						Otherdoc doc = dao.get(docId);
						if (doc != null) {
							docIndex.setDocContent(doc.getDocContent());
						}
					}
					Date createDate = docIndex.getCreateDate();
					String dateString = sdfDateFormat.format(createDate);
					treeMap.get(dateString.substring(0, 10)).add(docIndex);
				}
				setResult(treeMap);
			}
		};

		HibernateSessionTemplate.instance().executeReadOnly(action);
		TreeMap<String, List<DocIndex>> treeMap = action.getResult();
		List<HashMap> list = new ArrayList<>();
		Iterator iterator = treeMap.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry entry = (Entry) iterator.next();
			String key = (String) entry.getKey();
			List<DocIndex> list2 = (List<DocIndex>) entry.getValue();
			HashMap childMap = new HashMap<>();
			childMap.put("createDate", key);
			childMap.put("docList", list2);
			list.add(childMap);
		}
		return list;
	}

	/**
	 * 整理数据
	 * 
	 * @author zhangx
	 * @date 2015-11-2 下午10:03:52
	 */
	public void cleanData() {
		HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				StringBuilder hql = new StringBuilder(
						"from DocIndex where docClass=99");
				Query query = ss.createQuery(hql.toString());

				// query.setFirstResult(0);
				// query.setMaxResults(6);

				@SuppressWarnings("unchecked")
				List<DocIndex> docList = query.list();

				CdrOtherdocDAO CdrOtherdocDAO = DAOFactory
						.getDAO(CdrOtherdocDAO.class);
				TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
				DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
				DepartmentDAO deptDAO = DAOFactory.getDAO(DepartmentDAO.class);
				DocIndexDAO indexDAO = DAOFactory.getDAO(DocIndexDAO.class);
				MeetClinicDAO clinicDAO = DAOFactory
						.getDAO(MeetClinicDAO.class);
				ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
				PatientDAO patDAO = DAOFactory.getDAO(PatientDAO.class);

				for (DocIndex docIndex : docList) {
					if (docIndex.getDocId() == null) {
						continue;
					}

					Otherdoc doc = CdrOtherdocDAO.get(docIndex.getDocId());
					// 转诊=1
					if (doc != null && doc.getClinicType() == 1) {

						if (!StringUtils.isEmpty(docIndex.getDoctorName())) {
							continue;
						}

						Transfer tran = transferDAO.getById(doc.getClinicId());

						if (tran == null
								|| !tran.getMpiId().equals(doc.getMpiid())) {
							continue;
						}

						docIndex.setCreateDoctor(tran.getRequestDoctor());
						docIndex.setCreateDepart(tran.getRequestDepart());
						docIndex.setCreateOrgan(tran.getRequestOrgan());

						Doctor dcotor = doctorDAO.getByDoctorId(tran
								.getRequestDoctor());
						docIndex.setDoctorName(dcotor.getName());

						Department dept = deptDAO.getById(tran
								.getRequestDepart());
						docIndex.setDepartName(dept.getName());

						String docTypeText = DictionaryController.instance()
								.get("eh.cdr.dictionary.DocType")
								.getText(docIndex.getDocType());

						docIndex.setDocTitle(docTypeText);
						docIndex.setDocSummary(docTypeText);
						docIndex.setDoctypeName(docTypeText);

						indexDAO.update(docIndex);
						continue;
					}

					// 会诊=2，原来代码写错了，写的是3
					if (doc != null && doc.getClinicType() == 2) {

						if (!StringUtils.isEmpty(docIndex.getDoctorName())) {
							continue;
						}

						MeetClinic clinic = clinicDAO.getByMeetClinicId(doc
								.getClinicId());

						if (clinic == null
								|| !clinic.getMpiid().equals(doc.getMpiid())) {
							continue;
						}

						docIndex.setCreateDoctor(clinic.getRequestDoctor());
						docIndex.setCreateDepart(clinic.getRequestDepart());
						docIndex.setCreateOrgan(clinic.getRequestOrgan());

						Doctor dcotor = doctorDAO.getByDoctorId(clinic
								.getRequestDoctor());
						docIndex.setDoctorName(dcotor.getName());

						Department dept = deptDAO.getById(clinic
								.getRequestDepart());
						docIndex.setDepartName(dept.getName());

						String docTypeText = DictionaryController.instance()
								.get("eh.cdr.dictionary.DocType")
								.getText(docIndex.getDocType());

						docIndex.setDocTitle(docTypeText);
						docIndex.setDocSummary(docTypeText);
						docIndex.setDoctypeName(docTypeText);

						indexDAO.update(docIndex);
						continue;
					}

					// 咨询=3
					if (doc != null && doc.getClinicType() == 3) {
						if (!StringUtils.isEmpty(docIndex.getDoctorName())) {
							continue;
						}

						Consult consult = consultDAO.getById(doc.getClinicId());
						if (consult == null
								|| !consult.getMpiid().equals(doc.getMpiid())) {
							continue;
						}

						Patient p = patDAO.getByMpiId(consult.getRequestMpi());

						if (p == null) {
							continue;
						}

						docIndex.setDoctorName(p.getPatientName());
						docIndex.setDepartName("");

						String docTypeText = DictionaryController.instance()
								.get("eh.cdr.dictionary.DocType")
								.getText(docIndex.getDocType());

						docIndex.setDocTitle(docTypeText);
						docIndex.setDocSummary(docTypeText);
						docIndex.setDoctypeName(docTypeText);

						indexDAO.update(docIndex);
						continue;
					}

					// 其他=4
					if (doc != null && doc.getClinicType() == 4) {
						continue;
					}
				}

			}
		};

		HibernateSessionTemplate.instance().execute(action);
	}

	/**
	 * @function 按病人、文档类型查询分页列表,按时间排序(PC版)
	 * @author zhangjr yaozh
	 * @param mpiId
	 *            病人主索引
	 * @param docType
	 *            文档类型 可为null，即可查询全部类型
	 * @param start
	 *            从0开始
	 * @param limit
	 *            每页最大记录数
	 * @date 2015-11-04
	 * @return HashMap<String,Object>
	 *
	 * 2016-05-27 要将获取电子病历的链接从前端定死写改成后台获取，该接口作废，使用DocIndexService.findByMpiIdAndDocTypeWithPageForPc服务
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@RpcService
	public HashMap<String, Object> findByMpiIdAndDocTypeWithPageForPc(
			final String mpiId, final String docType, final int start,
			final int limit) {
		if (StringUtils.isEmpty(mpiId)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "病人主索引不能为空!");
		}
		HibernateStatelessResultAction<TreeMap<String, List<DocIndex>>> action = new AbstractHibernateStatelessResultAction<TreeMap<String, List<DocIndex>>>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				// TODO Auto-generated method stub
				StringBuilder hql1 = new StringBuilder(
//						"select date_format(createDate,'%Y-%m-%d') from DocIndex d where d.mpiid=:mpiId ");
						"select createDate from DocIndex d where d.mpiid=:mpiId ");
				if (!StringUtils.isEmpty(docType)) {
					hql1.append("and d.docType=:docType ");
				}
				hql1.append(" order by d.createDate desc ");
				Query query1 = ss.createQuery(hql1.toString());
				query1.setParameter("mpiId", mpiId);
				if (!StringUtils.isEmpty(docType)) {
					query1.setParameter("docType", docType);
				}
				query1.setFirstResult(start);
				query1.setMaxResults(limit);
				List<Date> dateList = query1.list();
				TreeMap<String, List<DocIndex>> treeMap = new TreeMap<>(
						new Comparator<String>() {
							// 排序规则
							public int compare(String o1, String o2) {
								// 指定排序器按照降序排列
								SimpleDateFormat sdf = new SimpleDateFormat(
										"yyyy-MM-dd");
								int d = 0;
								try {
									Date date1 = sdf.parse(o1);
									Date date2 = sdf.parse(o2);
									d = date2.compareTo(date1);
								} catch (ParseException e) {
									logger.error(e);
								}
								return d;
							}
						});
				for (Date date : dateList) {
					String d = DateConversion.getDateFormatter(date, "yyyy-MM-dd");
					List<DocIndex> list = new ArrayList<>();
					if (treeMap.get(d) != null)
						continue;
					treeMap.put(d, list);
				}

				StringBuilder hql = new StringBuilder(
						"select d from DocIndex d where d.mpiid=:mpiId ");
				if (!StringUtils.isEmpty(docType)) {
					hql.append("and d.docType=:docType ");
				}
				hql.append("order by d.createDate desc");

				Query query = ss.createQuery(hql.toString());
				query.setParameter("mpiId", mpiId);
				if (!StringUtils.isEmpty(docType)) {
					query.setParameter("docType", docType);
				}
				query.setFirstResult(start);
				query.setMaxResults(limit);

				List<DocIndex> list = query.list();
				SimpleDateFormat sdfDateFormat = new SimpleDateFormat(
						"yyyy-MM-dd HH:mm:ss");
				OtherDocDAO dao = DAOFactory.getDAO(OtherDocDAO.class);
				for (DocIndex docIndex : list) {
					int docClass = docIndex.getDocClass();
					Integer docId = docIndex.getDocId();
					if ((docClass == 99 || docClass ==97) && docId != null) {
						Otherdoc doc = dao.get(docId);
						if (doc != null) {
							docIndex.setDocContent(doc.getDocContent());
						}
					}
					Date createDate = docIndex.getCreateDate();
					String dateString = sdfDateFormat.format(createDate);
					treeMap.get(dateString.substring(0, 10)).add(docIndex);
				}
				setResult(treeMap);
			}
		};

		HibernateSessionTemplate.instance().executeReadOnly(action);
		TreeMap<String, List<DocIndex>> treeMap = action.getResult();
		List<HashMap> list = new ArrayList<>();
		Iterator iterator = treeMap.entrySet().iterator();
		Integer count = 0;
		while (iterator.hasNext()) {
			Entry entry = (Entry) iterator.next();
			String key = (String) entry.getKey();
			List<DocIndex> list2 = (List<DocIndex>) entry.getValue();
			HashMap childMap = new HashMap<>();
			childMap.put("createDate", key);
			childMap.put("docList", list2);
			count += list2.size();
			list.add(childMap);
		}
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("recordCount", count);
		if (count > 0) {
			map.put("recordList", list);
		} else {
			map.put("recordList", null);
		}
		return map;
	}

	/**
	 * 保存处方病历
	 * @author zhangx
	 * @date 2015-12-14 下午2:20:03
	 * @param recipe
	 * @return
	 */
	public void saveRecipeDocIndex(Recipe recipe) {
		DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
		DepartmentDAO deptDAO = DAOFactory.getDAO(DepartmentDAO.class);

		DocIndex docIndex = new DocIndex();
		String docType = "3";
        try {
            String docTypeText = DictionaryController.instance().get("eh.cdr.dictionary.DocType").getText(docType);
            docIndex.setDocSummary(docTypeText);
            docIndex.setDoctypeName(docTypeText);
        } catch (ControllerException e) {
            logger.error("saveRecipeDocIndex DocType dictionary error! docType="+docType);
        }
        try {
            String recipeTypeText = DictionaryController.instance().get("eh.cdr.dictionary.RecipeType").getText(recipe.getRecipeType());
            docIndex.setDocTitle(recipeTypeText);
        } catch (ControllerException e) {
            logger.error("saveRecipeDocIndex RecipeType dictionary error! recipeType="+recipe.getRecipeType());
        }
		docIndex.setDocId(recipe.getRecipeId());
        docIndex.setMpiid(recipe.getMpiid());
		docIndex.setCreateOrgan(recipe.getClinicOrgan());
		docIndex.setCreateDepart(recipe.getDepart());
		docIndex.setCreateDoctor(recipe.getDoctor());
        docIndex.setDoctorName(doctorDAO.getNameById(recipe.getDoctor()));
        docIndex.setDepartName(deptDAO.getNameById(recipe.getDepart()));
        saveCommonParameters(docIndex,docType,3);
	}

    /**
     * 保存体检报告病历
     * @param phyForm
     */
    public void savePhyFormDocIndex(PhyForm phyForm) {
        DocIndex docIndex = new DocIndex();
        //体检报告保存在其他里
        String docType = "9";
        try {
            String docTypeText = DictionaryController.instance().get("eh.cdr.dictionary.DocType").getText(docType);
            docIndex.setDocTitle(docTypeText);
            docIndex.setDocSummary(docTypeText);
            docIndex.setDoctypeName(docTypeText);
        } catch (ControllerException e) {
            logger.error("savePhyFormDocIndex DocType dictionary error! docType="+docType);
        }
        docIndex.setDocId(phyForm.getPhyId());
        docIndex.setMpiid(phyForm.getMpiId());
        docIndex.setCreateOrgan(phyForm.getOrganId());
        saveCommonParameters(docIndex,docType,10);
    }

    /**
     * 保存检验检查报告
     * @param labReport
     * @param rePortType
     */
    public void saveLabReportDocIndex(LabReport labReport, String rePortType) {
        DocIndex docIndex = new DocIndex();
        String docType = null;
        Integer docClass = null;
        // 报告类型 1 检验  2  检查  3体检单
        if("1".equals(rePortType)){
            docType = "1";
            docClass = 1;
        }else if("2".equals(rePortType)){
            docType = "2";
            docClass = 2;
        }

        try {
            String docTypeText = DictionaryController.instance().get("eh.cdr.dictionary.DocType").getText(docType);
			String lname = labReport.getTestItemName();
            docIndex.setDocTitle(lname==null?docTypeText:lname);
            docIndex.setDocSummary(docTypeText);
            docIndex.setDoctypeName(docTypeText);
        } catch (ControllerException e) {
            logger.error("saveLabReportDocIndex DocType dictionary error! docType="+docType);
        }
        docIndex.setDocId(labReport.getLabReportId());
        docIndex.setMpiid(labReport.getMpiid());
        docIndex.setCreateOrgan(labReport.getRequireOrgan());
        if(StringUtils.isNotEmpty(labReport.getRePortDepartId())) {
            docIndex.setCreateDepart(Integer.parseInt(labReport.getRePortDepartId()));
        }
        docIndex.setDepartName(labReport.getRePortDepartName());
        if(StringUtils.isNotEmpty(labReport.getRePortDoctorId())) {
            docIndex.setCreateDoctor(Integer.parseInt(labReport.getRePortDoctorId()));
        }
        docIndex.setDoctorName(labReport.getRePortDoctorName());
        saveCommonParameters(docIndex,docType,docClass);
    }

    private void saveCommonParameters(DocIndex docIndex, String docType, Integer docClass){
        Date now = new Date();
        docIndex.setCreateDate(now);
		if(docIndex.getLastModify()==null){
			docIndex.setLastModify(now);
		}
        docIndex.setDocFlag(0);
        docIndex.setDocStatus(0);
        docIndex.setDocType(docType);
        docIndex.setDocClass(docClass);
        save(docIndex);
    }

	/**
	 * 查询患者[电子病历页信息]
	 * @param mpiId
	 * @param docType
	 * @param start
	 * @param limit
     * @return
	 *
	 * 2016-06-18:app3.2 新增医生自主上传电子病历，此类病历可删除，查询时徐过滤已删除的记录 zhangx
     */
	public List<DocIndex> findDocListByMpiAndDocType(final String mpiId,
									  final String docType, final int start, final int limit){
		HibernateStatelessResultAction<List<DocIndex>> action = new AbstractHibernateStatelessResultAction<List<DocIndex>>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				StringBuilder hql1 = new StringBuilder(
						"from DocIndex d where d.mpiid=:mpiId and (docStatus=0 or docStatus is null) ");
				if (!StringUtils.isEmpty(docType)) {
					hql1.append("and d.docType=:docType ");
				}
				hql1.append(" order by d.createDate desc ");
				Query query1 = ss.createQuery(hql1.toString());
				query1.setParameter("mpiId", mpiId);
				if (!StringUtils.isEmpty(docType)) {
					query1.setParameter("docType", docType);
				}
				query1.setFirstResult(start);
				query1.setMaxResults(limit);
				List<DocIndex> list = query1.list();

				setResult(list);
			}
		};

		HibernateSessionTemplate.instance().executeReadOnly(action);

		return action.getResult();
	}


	@DAOMethod
	public abstract List<DocIndex> findByMpiidAndOrganDocId(String mpiId,String organDocId);

    /**
	 * 获取患者端健康档案病例列表（只显示医生和患者上传且不过滤已删除）
	 */
	public List<DocIndex> findHealthProfileDocListByMpiAndDocType(final String mpiId,
													 final String docType, final int start, final int limit){
		HibernateStatelessResultAction<List<DocIndex>> action = new AbstractHibernateStatelessResultAction<List<DocIndex>>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				StringBuilder hql1 = new StringBuilder(
						"select d from DocIndex d,Otherdoc other where d.mpiid=:mpiId and d.docId=other.otherDocId ");
				if (!StringUtils.isEmpty(docType)) {
					hql1.append("and d.docType=:docType ");
				}
				//显示不删除的； 显示被删除的，但是是患者上传的，医生删除的不展示
				hql1.append("and ((d.docStatus=0 AND other.clinicType=6 OR other.clinicType=8 ) OR (d.docStatus=1 AND other.clinicType=8))");


				hql1.append(" order by d.createDate desc ");
				Query query1 = ss.createQuery(hql1.toString());
				query1.setParameter("mpiId", mpiId);
				if (!StringUtils.isEmpty(docType)) {
					query1.setParameter("docType", docType);
				}
				query1.setFirstResult(start);
				query1.setMaxResults(limit);
				List<DocIndex> list = query1.list();

				setResult(list);
			}
		};

		HibernateSessionTemplate.instance().execute(action);

		return action.getResult();
	}
	/**
	 *
	 * */
	@RpcService
	public PushResponseModel saveDocIndexForCdrFile(Integer fileID, PushRequestModel requestModel){
		//文件名称
		try {
			String docType = requestModel.getDocType();
			Map dataMap = (Map)requestModel.getData();
			//文件名称
			String fileName = dataMap.get("fileName").toString();
			logger.info("save cdrFile "+fileName+"  fileID:"+fileID);
			String  certID = (String) dataMap.get("certID");
			String  cdrID = (String) dataMap.get("cdrID");
			int  organID = (int) dataMap.get("organID");
			Patient patient = DAOFactory.getDAO(PatientDAO.class).getByIdCard(certID);
			if(patient==null){
				return new PushResponseModel().setError("-1","该病人不存在");
			}
			OtherDocDAO otherDocDAO = DAOFactory.getDAO(OtherDocDAO.class);
			Otherdoc otherdoc = new Otherdoc();
			otherdoc.setDocType(requestModel.getDocType());
			otherdoc.setDocName(fileName);
			otherdoc.setClinicType(4);//其他
			otherdoc.setMpiid(patient.getMpiId());
			otherdoc.setDocContent(fileID);
			otherdoc.setCreateDate(new Date());
			otherdoc.setDocFormat("12");
			otherdoc = otherDocDAO.save(otherdoc);
			DocIndex docIndex = new DocIndex();
			docIndex.setDocId(otherdoc.getOtherDocId());
			String docTypeText = DictionaryController.instance().get("eh.cdr.dictionary.DocType").getText(docType);
			docIndex.setDocTitle(fileName==null?docTypeText:fileName);
			docIndex.setDocSummary(docTypeText);
			docIndex.setDoctypeName(docTypeText);
			docIndex.setMpiid(patient.getMpiId());
			docIndex.setCreateOrgan(organID);
			Object lastModifyO = dataMap.get("lastModify");
			if(lastModifyO!=null){
				String lastModify = lastModifyO.toString();
				if(!StringUtils.isEmpty(lastModify)){
					Date modifydate = DateConversion.getCurrentDate(lastModify, "yyyy-MM-dd HH:mm:ss");
					if(modifydate!=null){
						docIndex.setLastModify(modifydate);
					}
				}
			}
			if(dataMap.get("rePortDepartName")!=null){
				docIndex.setDepartName(dataMap.get("rePortDepartName").toString());
			}
			if(dataMap.get("rePortDoctorName")!=null) {
				docIndex.setDoctorName(dataMap.get("rePortDoctorName").toString());
			}
			docIndex.setOrganDocId(cdrID);
			saveCommonParameters(docIndex,docType,Integer.parseInt(requestModel.getDocClass()));
		} catch (Exception e) {
			logger.error(e);
			new PushResponseModel().setError("-1",e.getMessage());
		}
		return new PushResponseModel().setSuccess("保存索引成功");
	}

	@RpcService
	public boolean isFileExist(PushRequestModel req){
		List<DocIndex> resList = getCDRFile(req);
		return resList!=null&&!resList.isEmpty();
	}
	public List<DocIndex> getCDRFile(PushRequestModel req){
		Map dataMap = (Map)req.getData();
		//his传过来的文件byte
		String  certID = (String) dataMap.get("certID");
		String  cdrID = (String) dataMap.get("cdrID");
		logger.info("校验文件重复"+cdrID);
		Patient patient = DAOFactory.getDAO(PatientDAO.class).getByIdCard(certID);
		String mpiID = patient.getMpiId();
		List<DocIndex> resList = findByMpiIdAndDocTypeAndDocClassAndOrganAndOrganDocId(mpiID, req.getDocType(),
				Integer.parseInt(req.getDocClass()), Integer.parseInt(req.getOrganId()),cdrID);
		return resList;
	}
	@RpcService
	@DAOMethod(sql = "from DocIndex where mpiid=:Mpiid  and docType=:DocType and docClass=:docClass and createOrgan=:createOrgan and organDocId=:organDocId")
	public abstract List<DocIndex> findByMpiIdAndDocTypeAndDocClassAndOrganAndOrganDocId(
			@DAOParam("Mpiid") String mpiId,
			@DAOParam("DocType") String docType,
			@DAOParam("docClass") Integer docClass,
			@DAOParam("createOrgan") int createOrgan,
			@DAOParam("organDocId") String organDocId
	);
}
