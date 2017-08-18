package eh.bus.service.report;

import com.alibaba.fastjson.JSONObject;
import com.ngari.his.appoint.mode.AppointmentRequestHisTO;
import com.ngari.his.appoint.mode.LabReportHisTO;
import com.ngari.his.cdr.service.ICDRHisService;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.schema.exception.ValidateException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.CloudImageConfigDAO;
import eh.base.dao.DeviceDAO;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganDAO;
import eh.bus.dao.LabReportIdDAO;
import eh.cdr.dao.DocIndexDAO;
import eh.cdr.dao.LabReportDAO;
import eh.cdr.dao.LabReportDetailDAO;
import eh.cdr.dao.PhyFormDAO;
import eh.entity.base.CloudImageConfig;
import eh.entity.base.Device;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.Organ;
import eh.entity.bus.AppointmentRequest;
import eh.entity.cdr.LabReport;
import eh.entity.cdr.LabReportDetail;
import eh.entity.cdr.LabReportId;
import eh.entity.cdr.PhyForm;
import eh.entity.mpi.Patient;
import eh.entity.msg.SmsInfo;
import eh.mpi.dao.PatientDAO;
import eh.push.SmsPushService;
import eh.remote.IHisServiceInterface;
import eh.util.ChinaIDNumberUtil;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import eh.utils.DateConversion;
import eh.utils.MapValueUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by zhangz on 2016/7/14.
 */
public class QueryLabReportsDetailService {

    private static final Logger logger = LoggerFactory.getLogger(QueryLabReportsDetailService.class);
    
    /**
     * 通过证件号码与医院ID获取报告信息
     *
     * @param idCard  身份证号码
     * @param organID 医院ID
     * @return
     */
    @RpcService
    public void pushReportInfo(String idCard, String organID) {

        if(StringUtils.isEmpty(idCard) || StringUtils.isEmpty(organID)){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证或者医院Id为空");
        }
        //如果传进来的身份证是 15位的转化成18位
        String idCard18;
        if (idCard.length() == 15) {
            try {
                idCard18 = ChinaIDNumberUtil.convert15To18(idCard.toUpperCase());
            } catch (ValidateException e) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证不正确");
            }
        } else {
            idCard18 = idCard;
        }
        logger.info("idCard18======" + idCard18);

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient p = patientDAO.getByIdCard(idCard18);
        if (null == p) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "患者未注册");
        }
        String mpiId = p.getMpiId();
        Integer organId = Integer.valueOf(organID);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ organ = organDAO.getByOrganId(organId);
        if (null == organ) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "医院未注册");
        }

        //查询的是最近七天完成的报告单
        Calendar ca = Calendar.getInstance();
        ca.setTime(new Date()); //得到当前日期
        ca.add(Calendar.DAY_OF_WEEK, -7);//天数减掉7
        Date startTime = ca.getTime(); //结果
        Date endTime = new Date();

        //组装查询参数
        AppointmentRequest appointment = new AppointmentRequest();
        appointment.setCertID(idCard);
        appointment.setPatientName(p.getPatientName());
        appointment.setStartTime(startTime);
        appointment.setEndTime(endTime);

        //调用his接口进行查询
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organId);
        if(null == cfg){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "医院没有his配置");
        }
        String hisServiceId = cfg.getAppDomainId() + ".queryLableReports";
        logger.info("hisServiceId========" + hisServiceId);
        //首先查询列表
        //List<LabReport> listLab = (List<LabReport>) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getLableReports", appointment);
        
        List<LabReport> listLab = null;
    	if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(appointment.getOrganID()))){ 
    		ICDRHisService iCDRHisService = AppDomainContext.getBean("his.iCDRHisService", ICDRHisService.class);
    		List<LabReport> LabReports = null;
    		List<LabReportHisTO> list = null;
    		AppointmentRequestHisTO reqTO= new AppointmentRequestHisTO();
    		BeanUtils.copy(appointment,reqTO);
    		list = iCDRHisService.getLableReports(reqTO);
    		if(list != null){
    			LabReports = new ArrayList();
    			for(LabReportHisTO labReportHisTO : list){
    				LabReport labReport = new LabReport();
    				BeanUtils.copy(labReportHisTO, labReport);
    				LabReports.add(labReport);
    			}
    		}
    	}else{
    		listLab = (List<LabReport>) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getLableReports", appointment);
    	}
        if (null == listLab) {
            logger.error("listLab is null");
            return;
        }
        //基础信息map
        Map<String, Object> baseMap = new HashMap();
        baseMap.put("OrganID", organId);
        baseMap.put("MpiID", p.getMpiId());

        //获取报告列表后，遍历列表 查询报告单详情 保存数据库
        for (LabReport report : listLab) {
            //判断数据是否在数据库存在，存在跳过
            if (!isExitData(organId, report.getReportId(), report.getTypeCode(), mpiId)) {

                baseMap.put("ReportId", report.getReportId());
                //这两个数据是因为列表中有值 详情中不一定有值 所以传过去
                baseMap.put("ReportSeq", report.getClinicId());
                baseMap.put("TestItemName", report.getTestItemName());
                baseMap.put("ExeDate", report.getExeDate());
                baseMap.put("RePortDate", report.getRePortDate());
                //String result = (String) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getReportDetail", baseMap);
                String result = "";
            	if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(appointment.getOrganID()))){  
            		ICDRHisService iCDRHisService = AppDomainContext.getBean("his.iCDRHisService", ICDRHisService.class);
            		result = iCDRHisService.getReportDetail(baseMap);            		
            	}else{
            		result = (String) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getReportDetail", baseMap);
            	}
                //保存数据库
                Integer labId = saveLableData(result, baseMap);
                String idNumber = createIdNumber(labId, baseMap);
                //推送消息
                //如果微信推送成功 不发短信
                boolean wxFlag = sendWxTemplateMessageSelf(idNumber);
                //微信推送不成功 发短信
                if (!wxFlag) {
                    //发送短信

                    sendSmsMessageNew(organId, report.getLabReportId());
                }
            }
        }
    }

    /**
     * 查询报告详细信息
     *
     * @param paramMap RePortType  报告类型 1 检验  2  检查  3体检单
     *            OrganID
     *            ReportID
     * @return 返回报告列信息
     */
    @RpcService
    public Map<String, Object> getLableReportsDetail(Map<String, Object> paramMap) {
        logger.info("getDetail======="+paramMap);
        //前台数据组装与数据校验
        initParam(paramMap);

        String reportId = MapValueUtil.getString(paramMap, "ReportID");
        String reportType = MapValueUtil.getString(paramMap, "RePortType");
        Integer organId = MapValueUtil.getInteger(paramMap, "OrganID");
        String mpiId = MapValueUtil.getString(paramMap, "MpiID");
        logger.info("reportId:[{}],reportType:[{}],organId:[{}],mpiId:[{}]", reportId, reportType, organId, mpiId);
        //1-检验 2-检查
        if ("1".equals(reportType) || "2".equals(reportType)) {
            //如果数据库存在 去数据库查找
            if (isExitData(organId, reportId, reportType, mpiId)) {
                LabReportDAO dao = DAOFactory.getDAO(LabReportDAO.class);
                LabReport labReport = dao.getByRequreOrganAndReportId(organId, reportId, reportType, mpiId);
                return getLabReportInfo(labReport, paramMap);
            }else{//数据库不存在 去医院查找
                return getDetailFromHis(paramMap);
            }
        } else {//3-体检
            PhyFormDAO dao = DAOFactory.getDAO(PhyFormDAO.class);
            PhyForm phyForm = dao.getByOrganIdAndExamNoAndMpiId(organId, reportId, mpiId);
            if (null != phyForm) {
                return getPhyFormInfo(phyForm, paramMap);
            }else{
                return getDetailFromHis(paramMap);
            }
        }
    }

    /**
     * 从baseMap 添加患者基本信息到 返回集合 resMap
     * @param resMap
     * @param baseMap
     * @return
     */
    private Map<String, Object> addPatientInfo(Map<String, Object> resMap, Map<String, Object> baseMap) {

        resMap.put("age", baseMap.get("Age"));
        resMap.put("sex", baseMap.get("Sex"));
        resMap.put("mpiID", baseMap.get("MpiID"));
        resMap.put("certID", baseMap.get("CertID"));
        resMap.put("mobile", baseMap.get("Mobile"));
        resMap.put("organName", baseMap.get("OrganName"));
        resMap.put("patientName", baseMap.get("PatientName"));
        resMap.put("patientType", baseMap.get("PatientType"));
        resMap.put("patientSex", baseMap.get("PatientSex"));

        return resMap;
    }

    /**
     * 从数据库获取检查检验单详情
     * @param lab
     * @param baseMap
     * @return
     */
    private Map<String, Object> getLabReportInfo(LabReport lab, Map<String, Object> baseMap){
        LabReportDetailDAO detailDAO = DAOFactory.getDAO(LabReportDetailDAO.class);
        List<LabReportDetail> detailList = detailDAO.findByLabReportId(lab.getLabReportId());
        lab.setDetailList(detailList);
        setLabelCountValue(lab, detailList);
        String result = JSONUtils.toString(lab);
        Map<String, Object> resMap = JSONUtils.parse(result, Map.class);

        return addPatientInfo(resMap, baseMap);
    }

    /**
     * zhongzx
     * 从数据库获取体检单详情
     * @param phyForm
     * @param baseMap
     * @return
     */
    private Map<String, Object> getPhyFormInfo(PhyForm phyForm, Map<String, Object> baseMap) {
        LabReportDAO labReportDAO = DAOFactory.getDAO(LabReportDAO.class);
        LabReportDetailDAO detailDAO = DAOFactory.getDAO(LabReportDetailDAO.class);
        //获取检验检查单列表
        List<LabReport> labList = labReportDAO.findByPhyId(phyForm.getPhyId());
        if (null != labList) {
            for (LabReport lab : labList) {
                //获取检查单或者检验单详情列表
                List<LabReportDetail> detailList = detailDAO.findByLabReportId(lab.getLabReportId());
                lab.setDetailList(detailList);
                setLabelCountValue(lab, detailList);
            }
        }
        phyForm.setLabList(labList);
        //把体检单对象转化成Map返回
        String result = JSONUtils.toString(phyForm);
        Map<String, Object> resMap = JSONUtils.parse(result, Map.class);
        Date summaryDate = phyForm.getSummaryDate();
        if(null != summaryDate){
            resMap.put("summaryDate", DateConversion.getDateFormatter(summaryDate, "yyyy-MM-dd HH:mm"));
        }

        return addPatientInfo(resMap, baseMap);
    }

    /**
     * zhongzx
     * 从his查询报告单详情
     * @param baseMap
     * @return
     */
    private Map<String, Object> getDetailFromHis(Map<String, Object> baseMap) {
        Integer organId = MapValueUtil.getInteger(baseMap, "OrganID");
        String reportType = MapValueUtil.getString(baseMap, "RePortType");
        String testItemName = MapValueUtil.getString(baseMap, "TestItemName");
        String reportDate = MapValueUtil.getString(baseMap, "RePortDate");

        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organId);
        //调用服务id 从his查询报告单详情
        String hisServiceId = cfg.getAppDomainId() + ".queryLableReports";
        String mpiID = baseMap.get("MpiID").toString();
        Patient patient = DAOFactory.getDAO(PatientDAO.class).getPatientByMpiId(mpiID);
        if(null == patient){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "患者不存在");
        }
        baseMap.put("guardianFlag",patient.getGuardianFlag());
        baseMap.put("guardianName",patient.getGuardianName());
        baseMap.put("mobile",patient.getMobile());
        logger.info("labReportDetail param : "+baseMap.toString());
        //String result = (String) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getReportDetail", baseMap);
        String result = "";
    	if(DBParamLoaderUtil.getOrganSwich(organId)){  
    		ICDRHisService iCDRHisService = AppDomainContext.getBean("his.iCDRHisService", ICDRHisService.class);
    		result = iCDRHisService.getReportDetail(baseMap);            		
    	}else{
    		result = (String) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getReportDetail", baseMap);
    	}
        Map<String, Object> resMap = JSONUtils.parse(result, Map.class);
        if(null == resMap){
            logger.error("检查或检验单详情为空");
            return null;
        }
        try {
            baseMap.put("RePortDate", DateConversion.getCurrentDate(reportDate, "yyyy-MM-dd"));
        }catch (Exception e){
            logger.error("前端传入日期没有按yyyy-MM-dd形式");
        }
        //先进行保存数据库操作
        if("1".equals(reportType) || "2".equals(reportType)){
            saveLableData(result, baseMap);
            //这是因为 医院传过来的详情页很有可能 这两个字段没有值 需要把前段传入的参数值再传出
            if(StringUtils.isEmpty(MapValueUtil.getString(resMap, "testItemName"))){
                resMap.put("testItemName", testItemName);
            }
            if(StringUtils.isEmpty(MapValueUtil.getString(resMap, "rePortDate"))){
                resMap.put("rePortDate", reportDate);
            }
        }else{
            savePhyFormData(result, baseMap);
            String summaryDate = MapValueUtil.getString(resMap, "summaryDate");
            //体检时间 返回前段格式为 yyyy年M月d日 HH:mm
            if(!StringUtils.isEmpty(summaryDate)){
                Date d = DateConversion.getCurrentDate(summaryDate, "yyyy-MM-dd HH:mm:ss");
                resMap.put("summaryDate", DateConversion.getDateFormatter(d, "yyyy年M月d日 HH:mm"));
            }
        }
        //再把数据转成map返回前段,添加患者信息
        return addPatientInfo(resMap, baseMap);
    }

    /**
     * 通过识别码获取报告详情服务
     * @param
     * @return 返回报告列信息
     */
    @RpcService
    public Map<String, Object> getReportsDetailByIdNumber(String idNumber) {

        LabReportIdDAO labReportIdDAO = DAOFactory.getDAO(LabReportIdDAO.class);
        LabReportDAO labDAO = DAOFactory.getDAO(LabReportDAO.class);
        LabReportId labReportId = labReportIdDAO.getByIdNumber(idNumber);
        if (null == labReportId) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "取单号没有对应的单子");
        }
        LabReport labReport = labDAO.get(labReportId.getLabReportId());
        Map<String, Object> baseMap = new HashMap<>();
        baseMap.put("MpiID", labReport.getMpiid());
        baseMap.put("OrganID", labReport.getRequireOrgan());
        baseMap.put("RePortType", labReport.getTypeCode());
        baseMap.put("ReportID",labReport.getReportId());
        initParam(baseMap);
        return  getLabReportInfo(labReport, baseMap);
    }

    /**
     * 根据主键获取报告详情
     *
     * @param reportId
     * @param reportType
     * @return
     */
    @RpcService
    public Map<String, Object> getReportDetailById(Integer reportId, Integer reportType) {
        if (null == reportId) {
            return null;
        }

        Map<String, Object> resultMap = null;
        LabReportDAO labReportDAO = DAOFactory.getDAO(LabReportDAO.class);
        LabReportDetailDAO labReportDetailDAO = DAOFactory.getDAO(LabReportDetailDAO.class);

        if (1 == reportType || 2 == reportType) {
            LabReport labReport = labReportDAO.getByLabReportId(reportId);
            if (null != labReport) {
                List<LabReportDetail> list = labReportDetailDAO.findByLabReportId(labReport.getLabReportId());
                if (null != list && !list.isEmpty()) {
                    labReport.setDetailList(list);
                    if (1 == reportType) {
                        setLabelCountValue(labReport, list);
                    } else {
                        labReport.setException(0);
                        labReport.setTotal(0);
                    }
                }

                resultMap = JSONUtils.parse(JSONUtils.toString(labReport), Map.class);
            }
        } else if (3 == reportType) {
            PhyFormDAO phyFormDAO = DAOFactory.getDAO(PhyFormDAO.class);
            PhyForm phyForm = phyFormDAO.getByPhyId(reportId);
            if (null != phyForm) {
                //获取检验检查单列表
                List<LabReport> labList = labReportDAO.findByPhyId(phyForm.getPhyId());
                if (null != labList) {
                    for (LabReport lab : labList) {
                        //获取检查单或者检验单详情列表
                        List<LabReportDetail> detailList = labReportDetailDAO.findByLabReportId(lab.getLabReportId());
                        lab.setDetailList(detailList);
                        setLabelCountValue(lab, detailList);
                    }
                }
                phyForm.setLabList(labList);
                resultMap = JSONUtils.parse(JSONUtils.toString(phyForm), Map.class);
            }
        }

        return resultMap;
    }

    @RpcService
    public Map<String, Object> queryReportDetailById(Integer reportId, Integer reportType, String userId, String client){
        if(null == reportId){
            throw new DAOException(DAOException.VALUE_NEEDED, "reportId is needed");
        }
        if(null == reportType){
            throw new DAOException(DAOException.VALUE_NEEDED, "reportType is needed");
        }
        if(StringUtils.isEmpty(userId)){
            throw new DAOException(DAOException.VALUE_NEEDED, "userId is needed");
        }
        if(StringUtils.isEmpty(client)){
            throw new DAOException(DAOException.VALUE_NEEDED, "client is needed");
        }
        LabReportDAO labReportDAO = DAOFactory.getDAO(LabReportDAO.class);
        Map<String, Object> resultMap = getReportDetailById(reportId, reportType);
        if(null == resultMap){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "检查报告不存在");
        }
        boolean cloudImageOpen = false;
        //目前是检查 展示查看影像按钮
        if(2 == reportType) {
            LabReport labReport = labReportDAO.getByLabReportId(reportId);
            if (null == labReport) {
                logger.error("reportId=[{}]检查单不存在", reportId);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "检查报告不存在");
            }
            Integer organId = labReport.getRequireOrgan();
            if (null == organId) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "检查报告的机构为空");
            }
            String repId = labReport.getReportId();
            if(StringUtils.isEmpty(repId)){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "检查报告的reportId为空");
            }
            CloudImageConfigDAO configDAO = DAOFactory.getDAO(CloudImageConfigDAO.class);
            CloudImageConfig config = configDAO.getByOrganIdAndClient(organId, client);
            if (null != config) {
                cloudImageOpen = true;
                Map<String, Object> paramMap = new HashMap<>();
                paramMap.put("userId", userId);
                paramMap.put("reportId", repId);
                paramMap.put("serverIP", ParamUtils.getParam(ParameterConstant.CLOUDIMAGE_SERVERIP));
                paramMap.put("serverPort", ParamUtils.getParam(ParameterConstant.CLOUDIMAGE_SERVERPORT));
                config.setUrl(ParamUtils.processTemplate(config.getUrl(), paramMap));
                resultMap.put("cloudImageConfig", config);
            }
        }
        resultMap.put("cloudImageOpen", cloudImageOpen);
        return resultMap;
    }

    /**
     * 设置报告异常数和总数
     *
     * @param lab
     * @param detailList
     */
    private void setLabelCountValue(LabReport lab, List<LabReportDetail> detailList) {
        if (null == lab || null == detailList) {
            return;
        }
        int total = 0;
        int exception = 0;
        for (int i = 0; i < detailList.size(); i++) {
            LabReportDetail labReportDetail = detailList.get(i);
            if (null != labReportDetail) {
                String resultMessage = labReportDetail.getResultMessage();
                if (!"N".equals(resultMessage)) {
                    exception++;
                }
                total++;
            }
        }
        lab.setException(exception);
        lab.setTotal(total);
    }

    /**
     * 前台数据校验 和必要信息获取
     *
     * @param paramMap 前台数据集合
     * @return
     */
    public void initParam(Map<String, Object> paramMap) {
        String mpiID = MapValueUtil.getString(paramMap, "MpiID");                  //MpiID
        Integer organId = MapValueUtil.getInteger(paramMap, "OrganID");             //医院编号
        String reportType = MapValueUtil.getString(paramMap, "RePortType");    //检验类别代码检验：1        检查 ： 2
        String reportId = MapValueUtil.getString(paramMap, "ReportID");
        //必要字段的非空判断
        if (StringUtils.isEmpty(mpiID)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "MpiID is required!");
        }
        if (null == organId) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganID is required!");
        }
        if (StringUtils.isEmpty(reportType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "RePortType is required!");
        }
        if (StringUtils.isEmpty(reportId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "ReportID is required!");
        }
        /*Integer organId = Integer.valueOf(organID);
        paramMap.put("OrganID", organId);*/
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Organ organ = organDAO.get(organId);

        if (organ == null) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "OrganID 对应找不到机构数据!");
        }
        Patient patient = patientDAO.get(mpiID);
        if (patient == null) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "MpiID 对应找不到患者数据!");
        }
        int age = patient.getBirthday() == null ? 0 : DateConversion
                .getAge(patient.getBirthday());//病人年龄
        String sex = "";
        String patientType = "";
        try {
            sex = DictionaryController.instance().get("eh.base.dictionary.Gender").getText(patient.getPatientSex());//病人性别
            patientType = DictionaryController.instance().get("eh.mpi.dictionary.PatientType").getText(patient.getPatientType());//病人类型
        } catch (ControllerException e) {
            logger.error("获取性别或医保类型出错" + e.getMessage());
        }
        //这些信息是前端返回所需要的必要数据
        paramMap.put("Sex", sex);
        paramMap.put("Age", age);
        paramMap.put("PatientName", patient.getPatientName());
        paramMap.put("OrganName", organ.getName());
        paramMap.put("PatientType", patientType);
        paramMap.put("Mobile", patient.getMobile());
        paramMap.put("CertID", patient.getIdcard());
        paramMap.put("PatientSex", patient.getPatientSex());

    }

    /**
     * 判断该报告与医院在数据库是否存在
     *
     * @param organID  医院代码
     * @param reportID 报告单号
     * @return
     */
    private boolean isExitData(Integer organID, String reportID, String reportType, String mpiId) {
        LabReportDAO labReportDAO = DAOFactory.getDAO(LabReportDAO.class);
        LabReport labReport = labReportDAO.getByRequreOrganAndReportId(organID, reportID, reportType, mpiId);
        if (null == labReport) {
            return false;
        }
        return true;
    }

    /**
     * 保存体检单
     * @param result
     * @param baseMap
     */
    private void savePhyFormData(String result, Map<String, Object> baseMap) {
        String rePortType = MapValueUtil.getString(baseMap, "RePortType");    //报告类型 3-体检单
        String mpiID = MapValueUtil.getString(baseMap, "MpiID");
        Integer organId = MapValueUtil.getInteger(baseMap, "OrganID");
        String reportId = MapValueUtil.getString(baseMap, "ReportID");

        if(StringUtils.isEmpty(result)){
            return;
        }
        PhyFormDAO phyFormDAO = DAOFactory.getDAO(PhyFormDAO.class);
        LabReportDAO labReportDAO = DAOFactory.getDAO(LabReportDAO.class);
        LabReportDetailDAO detailDAO = DAOFactory.getDAO(LabReportDetailDAO.class);
        PhyForm phyForm = JSONUtils.parse(result, PhyForm.class);
        //保存前判断是否已存在改体检单
        String examNo = phyForm.getExamNo();
        if(StringUtils.isEmpty(examNo)){
            phyForm.setExamNo(reportId);
        }
        PhyForm phy = phyFormDAO.getByOrganIdAndExamNoAndMpiId(organId, reportId, mpiID);
        //如果存在 不进行保存
        if (null != phy) {
            return;
        }
        List<LabReport> lablist = phyForm.getLabList();
        phyForm.setMpiId(mpiID);
        phyForm.setOrganId(organId);
        if(null == phyForm.getReportDate()){
            phyForm.setReportDate(MapValueUtil.getDate(baseMap, "RePortDate"));
        }
        phyForm.setCreateTime(new Date());
        phyForm = phyFormDAO.save(phyForm);
        for (LabReport lab : lablist) {
            lab.setPhyId(phyForm.getPhyId());
            lab.setTypeCode(rePortType);
            lab.setTypeName("体检");
            lab.setRequireOrgan(organId);
            lab.setMpiid(mpiID);
            lab = labReportDAO.save(lab);
            List<LabReportDetail> detailList = lab.getDetailList();
            for (LabReportDetail detail : detailList) {
                detail.setLabReportId(lab.getLabReportId());
                detailDAO.save(detail);
            }
        }

        //保存至电子病历
        DocIndexDAO indexDAO = DAOFactory.getDAO(DocIndexDAO.class);
        indexDAO.savePhyFormDocIndex(phyForm);
    }


    /**
     * 保存检查检验单
     * @param result
     * @param baseMap
     * @return
     */
    private Integer saveLableData(String result, Map<String, Object> baseMap) {
        //保存列表信息
        logger.info("start save labReport" + result);
        if(StringUtils.isEmpty(result)){
            logger.error("检查或检验单详情为空");
            return null;
        }
        Integer organId = MapValueUtil.getInteger(baseMap, "OrganID");
        String reportId = MapValueUtil.getString(baseMap, "ReportID");
        String reportType = MapValueUtil.getString(baseMap, "RePortType");
        String mpiId = MapValueUtil.getString(baseMap, "MpiID");
        LabReportDAO dao = DAOFactory.getDAO(LabReportDAO.class);
        LabReport lab = dao.getByRequreOrganAndReportId(organId, reportId, reportType, mpiId);
        if(null != lab) {
            return lab.getLabReportId();
        }
        lab = JSONObject.parseObject(result, LabReport.class);
        if(null == lab){
            return null;
        }
        if(StringUtils.isEmpty(lab.getReportId())){
            lab.setReportId(reportId);
        }
        lab.setMpiid(mpiId);
        lab.setRequireOrgan(organId);
        //如果没有标题 把列表的标题赋值上去
        if(StringUtils.isEmpty(lab.getTestItemName())) {
            lab.setTestItemName(MapValueUtil.getString(baseMap, "TestItemName"));
        }
        lab.setClinicId(MapValueUtil.getString(baseMap, "ReportSeq"));
        if(null == lab.getExeDate()) {
            lab.setExeDate(MapValueUtil.getDate(baseMap, "ExeDate"));
        }
        if(null == lab.getRePortDate()) {
            lab.setRePortDate(MapValueUtil.getDate(baseMap, "RePortDate"));
        }

        lab.setCreateTime(new Date());
        lab = dao.save(lab);
        List<LabReportDetail> detailList = lab.getDetailList();
        LabReportDetailDAO detailDAO = DAOFactory.getDAO(LabReportDetailDAO.class);
        for(LabReportDetail detail:detailList){
            detail.setLabReportId(lab.getLabReportId());
            detailDAO.save(detail);
        }
        //保存至电子病历
        DocIndexDAO indexDAO = DAOFactory.getDAO(DocIndexDAO.class);
        indexDAO.saveLabReportDocIndex(lab, lab.getTypeCode());

        return lab.getLabReportId();
    }

    /**
     * 给检查检验单生成对应的识别码
     * @param labId
     * @param map
     * @return
     */
    private String createIdNumber (Integer labId, Map<String, Object> map) {
        //保存识别码信息
        LabReportIdDAO labReportIdDAO = DAOFactory.getDAO(LabReportIdDAO.class);
        LabReportId labReportId = new LabReportId();
        labReportId.setLabReportId(labId);
        labReportId.setMPIID(MapValueUtil.getString(map, "MpiID"));
        labReportId.setReportId(MapValueUtil.getString(map, "ReportID"));
        labReportId.setOrganId(MapValueUtil.getInteger(map, "OrganID"));
        String idNumber = getIdNumber();
        labReportId.setIdnumber(idNumber);
        labReportIdDAO.save(labReportId);
        return idNumber;
    }

    /**
     * 生成识别码
     * @return
     */
    public String getIdNumber() {
        StringBuilder re = new StringBuilder();
        String arr0[] = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
        String arr1[] = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
        int len0 = arr0.length;
        int len1 = arr1.length;
        Random random = new Random();
        int arrIdx0 = random.nextInt(len0 - 1);
        String s = arr0[arrIdx0];
        re.append(s);
        for (int i = 0; i < 5; i++) {
            int arrIdx1 = random.nextInt(len1 - 1);
            String s1 = arr1[arrIdx1];
            re.append(s1);
        }
        String idNumber = re.toString();
        LabReportIdDAO labReportIdDAO = DAOFactory.getDAO(LabReportIdDAO.class);
        LabReportId oLabReportId = labReportIdDAO.getByIdNumber(idNumber);
        if (oLabReportId != null) {
            return getIdNumber();
        }
        return idNumber;
    }

    /**
     * 推送给患者 如果微信能推送 就不发短信
     *
     * @param idNumber
     */

    private boolean sendWxTemplateMessageSelf(String idNumber) {
        boolean result;
        try {
            LabReportId labReportId = DAOFactory.getDAO(LabReportIdDAO.class).getByIdNumber(idNumber);
            LabReport labReport = DAOFactory.getDAO(LabReportDAO.class).getByLabReportId(labReportId.getLabReportId());
            SmsInfo smsInfo = new SmsInfo();
            smsInfo.setBusId(labReport.getLabReportId());
            smsInfo.setBusType("QueryLabReports");
            smsInfo.setSmsType("QueryLabReports");
            smsInfo.setOrganId(labReport.getRequireOrgan());
            smsInfo.setExtendValue(idNumber);
            smsInfo.setExtendWithoutPersist("wx");
            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2OnsExtendValue(smsInfo);
            result = true;
        }catch (Exception e) {
            logger.error("sendWxTemplateMessageSelf-->"+e);
            result = false;
        }
        return result;
    }

    /**
     * 推送给当前微信登录人
     * @param idNumber
     */
    @RpcService
    public void sendWxTemplateMessage(String idNumber, String openId, String appId) {
        logger.info("appId=====" + appId + "openId======" + openId);
        Device device = new Device();
        device.setAppid("ngari-health");
        device.setOs("WX");
        device.setToken(openId+"@"+appId);
        DeviceDAO deviceDAO = DAOFactory.getDAO(DeviceDAO.class);
        deviceDAO.save(device);
        Integer deviceId = deviceDAO.getDeviceByOsAndTokenAndAppid("WX",openId+"@"+appId,"ngari-health").getId();
        LabReportId labReportId = DAOFactory.getDAO(LabReportIdDAO.class).getByIdNumber(idNumber);
        LabReport labReport = DAOFactory.getDAO(LabReportDAO.class).getByLabReportId(labReportId.getLabReportId());
        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(labReport.getLabReportId());
        smsInfo.setBusType("QueryLabReports");
        smsInfo.setSmsType("QueryLabReports");
        smsInfo.setOrganId(labReport.getRequireOrgan());
        smsInfo.setExtendValue(idNumber+";"+String.valueOf(deviceId));
        smsInfo.setExtendWithoutPersist("wx");
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
    }

    /**
     * SMS_13016268
     * 小纳提醒：您于${organ}做的${item}已出报告，请及时${pub}在公众号首页输入取单码查看报告单：${code}。
     *
     * 小纳提醒：您于2016-05-14（检查时间）在浙大邵逸夫医院（就诊医院）做的腰椎正侧位（项目名称）检查已出报告，
     * 请及时进入纳里健康公众号查看。若未关注，请先关注纳里健康公众号，在公众号首页输入取单码查看报告单：x00000。
     *
     * 【纳里健康】小纳提醒：您于2016-05-14（检查时间）在浙大邵逸夫医院（就诊医院）做的体检已出报告，
     * 请及时进入纳里健康公众号查看。若未关注，请先关注纳里健康公众号，在公众号首页输入取单码查看报告单：x00000。
     *
     *
     */

    public void sendSmsMessageNew(Integer organID, Integer labReportId) {
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2Ons(labReportId, organID, "QueryLabReports", "QueryLabReports", null);
    }

    @RpcService
    public  LabReportId getByIdNumber(String idnumber){
        LabReportIdDAO labReportIdDAO = DAOFactory.getDAO(LabReportIdDAO.class);
        LabReportId labReportId =labReportIdDAO.getByIdNumber(idnumber);
        return labReportId;
    }

    /** 此接口用于查询数据库中对应reportId(医院数据）、organId(医院数据）的报告单详情id
     * 获取数据库报告单详情id
     * @param reportType 医院的报告单类型 与我们平台的报告单类型相同
     * @param reportId   医院的报告单id  不同于我们的报告单id
     * @param organId
     * @return
     */
    public Integer getDbReportId(String reportType, String reportId, Integer organId, String mpiId){
        if ("1".equals(reportType) || "2".equals(reportType)) {
            LabReport labReport = DAOFactory.getDAO(LabReportDAO.class).getByRequreOrganAndReportId(organId, reportId, reportType, mpiId);
            return labReport.getLabReportId();
        } else {//3-体检
            PhyForm phyForm = DAOFactory.getDAO(PhyFormDAO.class).getByOrganIdAndExamNoAndMpiId(organId, reportId, mpiId);
            return phyForm.getPhyId();
        }
    }

}
