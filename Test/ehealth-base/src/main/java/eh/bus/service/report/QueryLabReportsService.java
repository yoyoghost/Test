package eh.bus.service.report;

import com.ngari.his.appoint.mode.AppointmentRequestHisTO;
import com.ngari.his.appoint.mode.LabReportHisTO;
import com.ngari.his.cdr.service.ICDRHisService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.Organ;
import eh.entity.bus.AppointmentRequest;
import eh.entity.cdr.LabReport;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.remote.IHisServiceInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Created by zhangz on 2016/7/14.
 */
public class QueryLabReportsService {

    private static final Log logger = LogFactory.getLog(QueryLabReportsService.class);

    //必要数据校验
    private Map<String, String> validateData(Map<String, String> map) {

        String mpiID = map.get("MpiID");
        String cardType = map.get("CardType");
        String cardOrgan = map.get("CardOrgan");
        String cardID = map.get("CardID");
        String organID = map.get("OrganID");
        if(StringUtils.isEmpty(organID)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganId is needed");
        }
        Integer organId = Integer.valueOf(organID);
        if (StringUtils.isEmpty(mpiID)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "MpiID is required!");
        }
        //卡类型,发卡机构,卡号三者是联动关系,一个有值其他必须有值
        if ((!StringUtils.isEmpty(cardType) && (StringUtils.isEmpty(cardOrgan) || StringUtils.isEmpty(cardID))) ||
                (!StringUtils.isEmpty(cardOrgan) && (StringUtils.isEmpty(cardType) || StringUtils.isEmpty(cardID))) ||
                (!StringUtils.isEmpty(cardID) && (StringUtils.isEmpty(cardType) || StringUtils.isEmpty(cardOrgan)))) {
            throw new DAOException(DAOException.VALUE_NEEDED, "CardType or CardOrgan or CardID is required!");
        }

        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Organ organ = organDAO.getByOrganId(organId);
        Patient patient = patientDAO.get(mpiID);
        if (null == organ) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "OrganID 对应找不到机构数据!");
        }
        if (null == patient) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "MpiID 对应找不到患者数据!");
        }
        String organName = organ.getName();
        String patientType = patient.getPatientType();
        String patientName = patient.getPatientName();
        String certID = patient.getRawIdcard();
        map.put("organName", organName);
        map.put("patientType", patientType);
        map.put("patientName", patientName);
        map.put("certID", certID);
        return map;
    }

    /**
     *
     * @param flag 根据flag转化日期 查询标志 0-近一个月数据;1-一年以前到一个月以前的数据
     * @return
     */
    private List<Date> tranDateByFlag(String flag) {
        List<Date> dList = new ArrayList<>();
        Date beginTime;
        Date endTime;
        Calendar ca = Calendar.getInstance();
        ca.setTime(new Date()); //得到当前日期
        if ("1".equals(flag)) {  //一个月以前数据
            ca.add(Calendar.YEAR, -1); //年份减1
            Date resultDate = ca.getTime(); //结果
            beginTime = resultDate;
            ca.setTime(new Date()); //得到当前日期
            ca.add(Calendar.MONTH, -1);//月份减1
            Date endDate = ca.getTime(); //结果
            endTime = endDate;
        } else { //近一个月数据
            ca.add(Calendar.MONTH, -1);//月份减1
            Date resultDate = ca.getTime(); //结果
            beginTime = resultDate;
            endTime = new Date();
        }
        dList.add(beginTime);
        dList.add(endTime);
        return dList;
    }

    /**
     * 包装数据返回前端
     * @param labReport
     * @param map
     * @return
     */
    private Map<String, String> packMap(LabReport labReport, Map<String, String> map) {
        Map<String, String> m = new HashMap();
        Date reportDate = labReport.getRePortDate();
        String reportDateAfter = DateConversion.getDateFormatter(reportDate, "yyyy年M月dd日");
        String reportDateAfterFm = DateConversion.getDateFormatter(reportDate, "yyyy年M月dd日 HH:mm");
        m.put("RePortDate", reportDateAfter);
        m.put("RePortDateFm", reportDateAfterFm);
        m.put("PatientName", map.get("patientName"));
        m.put("PatientType", map.get("patientType"));
        m.put("OrganName", map.get("organName"));
        m.put("TestItemName", labReport.getTestItemName());
        m.put("RePortType", labReport.getTypeCode());
        m.put("ReportSeq", labReport.getClinicId());
        m.put("ReportID", labReport.getReportId());
        m.put("ReportDepartName", labReport.getRePortDepartName());
        m.put("CheckDoctorName", labReport.getCheckDoctorName());
        m.put("ExamItemSummary", labReport.getExamItemSummary());
        return m;
    }

    /**
     * 查询报告列表信息
     * @param map   Flag 查询标志 0-近一个月数据;1-一个月以前数据
     *              MpiID
     *              OrganID
     *              CardType 卡类型（1医院就诊卡  2医保卡3 医院病历号）
     *              CardOrgan 发卡机构 就诊卡：就诊医院组织代码; 医保卡：各地各类医保、农保机构的组织代码
     *              CardID 卡号（门诊号码、就诊卡号、医保号等）
     *              StartNo 起始数 从1开始
     *              Records 记录数
     * @return 返回报告列信息
     */
    @RpcService
    public Map<String,Object> getLableReports(Map<String, String> map){

        //验证传入的参数并且加上患者的信息数据
        map = validateData(map);

        Date beginTime;
        Date endTime;
        String organID = map.get("OrganID");             //医院编号
        Integer organId = Integer.valueOf(organID);
        String cardType = map.get("CardType");         //卡类型（1医院就诊卡  2医保卡3 医院病历号）
        String cardOrgan = map.get("CardOrgan");       //发卡机构 就诊卡：就诊医院组织代码; 医保卡：各地各类医保、农保机构的组织代码
        String cardID = map.get("CardID");             //卡号（门诊号码、就诊卡号、医保号等）
        String flag = map.get("Flag");                 //查询标志 0-近一个月数据;1-一个月以前数据
        String startNo = map.get("StartNo");          //起始数 从1开始
        String records = map.get("Records");          //记录数
        Integer nStartNo = Integer.valueOf(startNo);
        Integer nRecords = Integer.valueOf(records);

        Map<String,Object> resultMap =new HashMap<>();
        List<Map<String, String>> mapList = new ArrayList<>();
        try {
            //根据查询标志 转换日期
            List<Date> dateList = tranDateByFlag(flag);
            beginTime = dateList.get(0);
            endTime = dateList.get(1);

            AppointmentRequest appointment = new AppointmentRequest();
            appointment.setCredentialsType("身份证");// 该数据无从获取
            appointment.setCertID(map.get("certID"));
            appointment.setPatientName(map.get("patientName"));
            appointment.setStartTime(beginTime);
            appointment.setEndTime(endTime);
            appointment.setOrganID(organID);
            appointment.setCardID(cardID);
            appointment.setCardType(cardType);
            appointment.setCardOrgan(cardOrgan);
            String mpiID = map.get("MpiID");
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(mpiID);
            appointment.setGuardianFlag(patient.getGuardianFlag());
            appointment.setGuardianName(patient.getGuardianName());
            appointment.setMobile(patient.getMobile());
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organId);
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".queryLableReports";
            //List<LabReport> listLab = (List<LabReport>)RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId,"getLableReports",appointment);
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
            //给医院返回的报告单 按由近及远 排序
            if(null != listLab && listLab.size() > 0 ){
                logger.info("listLab is :" + listLab.size());
                sort(listLab);
            }

            if("1".equals(flag)) {  //一个月以前数据 需要分页
                if(null == listLab || 0 == listLab.size()){
                    //返回空值
                    logger.info("listLab is null!");
                    resultMap.put("data",mapList);
                    resultMap.put("flag",false);
                }else{
                    logger.info("listLab size is:"+listLab.size());
                    if(listLab.size() > nRecords){//大于限制条数 需要分页

                        int count =(nStartNo + nRecords) -1;
                        if (count > listLab.size()){//防止前台传来的值超过总记录数
                            count = listLab.size();
                        }
                        for (int i = nStartNo - 1; i < count; i++) {
                            LabReport report = listLab.get(i);
                            Map<String, String> m = packMap(report, map);
                            mapList.add(m);
                        }
                        resultMap.put("data", mapList);
                        resultMap.put("flag", true);
                    }else{
                        //重新组装数据返回给前台
                        for (LabReport report : listLab) {
                            Map<String, String> m = packMap(report, map);
                            mapList.add(m);
                        }
                        resultMap.put("data", mapList);
                        resultMap.put("flag", true);
                    }
                }
            }else{
                if(null == listLab || 0 == listLab.size()){
                    //返回空值
                    resultMap.put("data", mapList);
                }else {
                    logger.info("listLab size is:"+listLab.size());
                    //重新组装数据返回给前台
                    for (LabReport report : listLab) {
                        Map<String, String> m = packMap(report, map);
                        mapList.add(m);
                    }
                    resultMap.put("data", mapList);
                }
                //再次发送一个月以前的请求，如果有数据返回true，没有返回false
//                List<Date> dList = tranDateByFlag("1");
//                beginTime = dList.get(0);
//                endTime = dList.get(1);
//                appointment.setStartTime(beginTime);
//                appointment.setEndTime(endTime);
//                List<LabReport> listLableReport1 = (List<LabReport>)RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId,"getLableReports",appointment);
//                if (listLableReport1 != null && listLableReport1.size() > 0){
                    resultMap.put("flag", true);
//                }else{
//                    resultMap.put("flag", false);
//                }
            }
        } catch (Exception e) {
            logger.error("his invoke error:" + e.getMessage());
            //返回空值
            resultMap.put("data", mapList);
            resultMap.put("flag", false);
        }
        return resultMap;
    }

    //按报告日期 由近及远返回 排序
    private void sort(List<LabReport> listLab){

        Collections.sort(listLab, new Comparator<LabReport>() {
            @Override
            public int compare(LabReport o1, LabReport o2) {
                return o2.getRePortDate().compareTo(o1.getRePortDate());
            }
        });
    }
}
