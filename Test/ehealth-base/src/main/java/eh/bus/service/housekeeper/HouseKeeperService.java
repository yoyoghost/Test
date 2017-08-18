package eh.bus.service.housekeeper;

import com.google.common.collect.Maps;
import com.ngari.his.sign.mode.SignCommonBeanTO;
import com.ngari.his.sign.service.ISignHisService;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.service.DictionaryLocalService;
import ctd.dictionary.service.DictionarySliceRecordSet;
import ctd.mvc.weixin.exception.WXException;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.schema.Schema;
import ctd.schema.SchemaController;
import ctd.schema.SchemaItem;
import ctd.schema.exception.ValidateException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ConditionOperator;
import eh.base.constant.ErrorCode;
import eh.base.dao.*;
import eh.base.service.DoctorInfoService;
import eh.bus.dao.ConsultSetDAO;
import eh.bus.service.ModuleUseLogService;
import eh.entity.base.*;
import eh.entity.bus.ConsultSet;
import eh.entity.bus.housekeeper.RecommendDoctorBean;
import eh.entity.bus.housekeeper.RecommendOrganBean;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.his.sign.HisSignRecord;
import eh.entity.his.sign.SignCommonBean;
import eh.entity.mpi.*;
import eh.mpi.constant.HealthLogConstant;
import eh.mpi.constant.SignRecordConstant;
import eh.mpi.dao.*;
import eh.mpi.service.PatientService;
import eh.remote.IHisServiceInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import eh.utils.ChinaIDNumberUtil;
import eh.utils.DateConversion;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.formula.functions.T;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * 健康管家模块服务
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/9/29.
 */
public class HouseKeeperService {

    private static final Logger logger = LoggerFactory.getLogger(HouseKeeperService.class);

    /**
     * 获取患者签约信息
     * @param mpiId  患者ID
     * @return  map
     */
    @RpcService
    public Map<String, Object> getPatientSignInfo(String mpiId) {
        Assert.hasLength(mpiId,"getPatientSignInfo mpiId is null");

        Map<String, Object> backMap = new HashMap<>();
        boolean isSign = false;
        //先查询自身是否存在签约信息
        List<Map<String, Object>> signInfo = getDoctorsSignInfoByMpiId(mpiId);
        if(CollectionUtils.isNotEmpty(signInfo)){
            isSign = true;
        }

        //自身不存在信息，再查询家庭成员是否存在签约信息
        if(!isSign) {
            //签约成功的数据
            RelationDoctorDAO relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);
            isSign = relationDoctorDAO.haveSignDoctorForFamily(mpiId);

            if(!isSign){
                //查询申请中的情况
                SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
                isSign = signRecordDAO.haveSigningDoctorForFamily(mpiId);
            }
        }

        if(isSign){
            //先查询家庭成员
            List<HashMap<String, Object>> familyPeople = familyMemberListOrder(mpiId);

            //再查询排名第一的签约医生(其他人的签约医生信息通过其他接口查询)
            if(CollectionUtils.isNotEmpty(familyPeople)){
                Object pObj = familyPeople.get(0).get("patient");
                if(null != pObj && pObj instanceof Patient) {
                    Patient p = (Patient) pObj;
                    if(!mpiId.equals(p.getMpiId())) {
                        signInfo = getDoctorsSignInfoByMpiId(p.getMpiId());
                    }
                }
                familyPeople.get(0).put("signDoctors", signInfo);
            }else{
                familyPeople = new ArrayList<>(0);
            }
            backMap.put("info",familyPeople);
        }else{
            backMap = this.findRecommendDoctorAndHos(mpiId);
        }

        backMap.put("sign",isSign);

        return backMap;
    }

    /**
     * 获取某患者的签约医生信息
     * @param mpiId 患者ID
     * @return list
     */
    @RpcService
    public List<Map<String, Object>> getPatientSignDoctors(String mpiId) {
        Assert.hasLength(mpiId,"getPatientSignDoctors mpiId is null");

        return getDoctorsSignInfoByMpiId(mpiId);
    }

    /**
     * 获取推荐医生和医院
     * @param mpiId 患者ID
     * @return  map
     */
    @RpcService
    public Map<String, Object> findRecommendDoctorAndHos(String mpiId) {
        Assert.hasLength(mpiId,"findRecommendDoctorAndHos mpiId is null");

        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Integer> organList = organDAO.findOrgansByUnitForHealth();

        Map<String, Object> backMap = new HashMap<>();
        backMap.put("doctor", Array.newInstance(Object.class,0));
        backMap.put("hospital", Array.newInstance(Object.class,0));

        if(null != organList && organList.isEmpty()){
            logger.error("findRecommendDoctorAndHos useful organ is empty!");
            return backMap;
        }

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        String homeArea = patientDAO.getHomeAreaByMpiId(mpiId);
        int docLimit = 4;//推荐医生限制为4个
        List<RecommendDoctorBean> backDoctors = new ArrayList<>(5);
        List<RecommendOrganBean> backOrgans = new ArrayList<>(5);
        if(StringUtils.isNotEmpty(homeArea)){
            List<Integer> doctors = new ArrayList<>(10);//需要排序的医生ID
            if(4 == homeArea.length()){
                //说明配置的是全市区域，具体取哪几位推荐医生则需要根据条件再过滤
                doctors = doctorDAO.findRecommendDoctorId(organList,homeArea,null,ConditionOperator.LIKE,null);
                if(null != doctors && !doctors.isEmpty()){
                    backDoctors = doctorDAO.sortRecommendDoctorBySignPatientCount(doctors,docLimit);
                    if(null == backDoctors){
                        backDoctors = new ArrayList<>(5);
                    }
                    //有可能某个医生一个签约病人也没有，则返回的数据与传入的医生ID长度不同，则需要逐一添加
                    if(backDoctors.size() < docLimit){
                        setAllCityDoctors(doctors,backDoctors,docLimit-backDoctors.size());
                    }
                }

                //推荐医院
                backOrgans = organDAO.findRecommendOrgan(organList,homeArea,null,ConditionOperator.LIKE,null);
            }else if(6 == homeArea.length()){
                //配置的是具体区域
                //查找区域内推荐医生，已按照职级排序
                doctors = doctorDAO.findRecommendDoctorId(organList,homeArea,null,ConditionOperator.EQUAL,null);
                if(CollectionUtils.isNotEmpty(doctors)){
                    List<RecommendDoctorBean> sortedList = doctorDAO.sortRecommendDoctorBySignPatientCount(doctors,docLimit);
                    if(CollectionUtils.isNotEmpty(sortedList)) {
                        backDoctors.addAll(sortedList);
                    }else{
                        sortedList = new ArrayList<>(0);
                    }
                    //有可能某个医生一个签约病人也没有，则返回的数据与传入的医生ID长度不同，则需要逐一添加
                    if(sortedList.size() != docLimit){
                        setAllCityDoctors(doctors,backDoctors,docLimit-sortedList.size());
                    }
                }

                //区域内推荐医生足够的话不需要再从全市选择
                if(backDoctors.size() < docLimit){
                    //需要扩大查询范围至全市，查询时注意将之前的区域条件去掉，否则结果可能会重复
                    String _homeArea = homeArea.substring(0,4);
                    doctors = doctorDAO.findRecommendDoctorId(organList,_homeArea,homeArea,ConditionOperator.LIKE,null);
                    if(CollectionUtils.isNotEmpty(doctors)){
                        int needCount = docLimit-backDoctors.size();
                        List<RecommendDoctorBean> sortedList = doctorDAO.sortRecommendDoctorBySignPatientCount(doctors,needCount);
                        if(CollectionUtils.isNotEmpty(sortedList)){
                            backDoctors.addAll(sortedList);
                        }else{
                            sortedList = new ArrayList<>(0);
                        }
                        //有可能某个医生一个签约病人也没有，则返回的数据与传入的医生ID长度不同，则需要逐一添加
                        if(sortedList.size() != needCount){
                            setAllCityDoctors(doctors,backDoctors,needCount-sortedList.size());
                        }
                    }
                }

                //推荐医院
                List<RecommendOrganBean> recommendOrg = organDAO.findRecommendOrgan(organList,homeArea,null,ConditionOperator.EQUAL,null);
                if(CollectionUtils.isNotEmpty(recommendOrg)){
                    backOrgans.addAll(recommendOrg);
                }

                String _homeArea = homeArea.substring(0,4);
                List<RecommendOrganBean> cityOrg = organDAO.findRecommendOrgan(organList,_homeArea,homeArea,ConditionOperator.LIKE,null);
                if(CollectionUtils.isNotEmpty(cityOrg)){
                    backOrgans.addAll(cityOrg);
                }
            }

            if(CollectionUtils.isNotEmpty(backDoctors)){
                backMap.put("doctor", backDoctors);
            }

            if(CollectionUtils.isNotEmpty(backOrgans)){
                backMap.put("hospital", backOrgans);
            }
        }

        return backMap;
    }

    /**
     * 根据机构ID获取可签约的医生信息
     * @param organId 机构ID
     * @return 签约医生集合
     */
    @RpcService
    public List<RecommendDoctorBean> findSignDoctorsByOrganId(Integer organId){
        Assert.notNull(organId,"findSignDoctorsByOrganId organId is null");

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        List<RecommendDoctorBean> list = doctorDAO.findSignDoctorsByOrganId(organId);
        if(CollectionUtils.isNotEmpty(list)){
            for (RecommendDoctorBean bean : list){
                bean.setOrganId(organId);
            }
        }
        return list;
    }


    /**
     * 从某一块医生数据中获取需要数量的医生对象
     * @param doctors 候选人ID
     * @param sortedDocList  已排序好的数据
     * @param limitNum  还差多少推荐医生数
     */
    private void setAllCityDoctors(List<Integer> doctors, List<RecommendDoctorBean> sortedDocList,
                                   int limitNum){
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        List<Integer> needSearchDocId = new ArrayList<>(5);
        for(Integer oDocId : doctors){
            boolean isAdd = false;
            for(RecommendDoctorBean sDoc : sortedDocList){
                if(sDoc.getDoctorId().equals(oDocId)){
                    isAdd = true;
                    break;
                }
            }

            if(!isAdd){
                needSearchDocId.add(oDocId);
            }

            //获取到推荐医生数量则停止循环
            if(needSearchDocId.size() == limitNum){
                break;
            }
        }

        //将 needSearchDocId 查询出整个推荐医生对象
        if(!needSearchDocId.isEmpty()){
            List<RecommendDoctorBean> recDoc = doctorDAO.findRecommendDoctorById(needSearchDocId);
            if(CollectionUtils.isNotEmpty(recDoc)){
                sortedDocList.addAll(recDoc);
            }
        }
    }

    /**
     * 获取指定医生与该患者的签约信息 (包含申请中的签约信息)
     * @param mpiId 患者ID
     * @return  map
     */
    private List<Map<String, Object>> getDoctorsSignInfoByMpiId(String mpiId){
        RelationDoctorDAO relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        OrganConfigDAO organConfigDAO=DAOFactory.getDAO(OrganConfigDAO.class);
        List<Map<String, Object>> backList = new ArrayList<>(5);

        DoctorInfoService service = AppContextHolder.getBean("eh.doctorInfoService", DoctorInfoService.class);

        //先查询续签申请中的签约医生
        List<SignRecord> renewRecords = signRecordDAO.findRenewSignRecords(mpiId);
        if(null != renewRecords && !renewRecords.isEmpty()){
            for(SignRecord signRecord : renewRecords){
                if(null != signRecord){
                    Map<String, Object> map = new HashMap<>();
                    map.put("renew", signRecord);
                    Doctor doctor = doctorDAO.getByDoctorId(signRecord.getDoctor());
                    if(null != doctor){
                        map.put("doctor", doctor);
                    }
                    Patient patient = patientDAO.getByMpiId(signRecord.getRequestMpiId());
                    //非预签约模式下不显示已完成预签约按钮
                    //2017-4-11 15:03:17 zhangx为 兼容健康APP老版本，保存前将是否预签约标记设置为不预签约
                    int preSign=signRecord.getPreSign()==null? SignRecordConstant.SIGN_IS_NOT_PRESIGN:signRecord.getPreSign();

                    if (preSign==1) {
                        map.put("disFlag", getOfHisIsSigned(patient, doctor));
                    }
                    backList.add(map);
                }
            }
        }

        //如没有续签的再查询签约中的签约医生
        List<Doctor> signedDoctors = relationDoctorDAO.findDoctorsByMpi(mpiId);
        if(!CollectionUtils.isEmpty(signedDoctors)) {
            for (Doctor doctor : signedDoctors) {
            	List<SignRecord> signRecordList = signRecordDAO.findDoctorAndMpiId(doctor.getDoctorId(), mpiId);
            	boolean flag = false;
            	if (CollectionUtils.isNotEmpty(signRecordList)) {
            		for (SignRecord signRecord : signRecordList) {
            			Integer recordStatus = signRecord.getRecordStatus();
            			//签约确认中
            			if (null != recordStatus && recordStatus.equals(SignRecordConstant.RECORD_STATUS_CONFIRMATION)) {
            				flag = true;
            				break;
            			}
            			for(SignRecord entity : renewRecords){
                            if(null != signRecord){
                            	Integer  renewSignRecordId = entity.getSignRecordId();
                            	Integer signRecordId = signRecord.getSignRecordId();
                            	if (renewSignRecordId.equals(signRecordId)) {
                            		flag = true;
                    				break;
                            	}
                            }
                        }
					}
            	}
            	if (flag) {
            		continue;
            	}
                if (null != doctor) {
                    Map<String, Object> map = service.getDoctorInfoForHealth(doctor.getDoctorId(), mpiId);
                    RelationDoctor relationDoctor = relationDoctorDAO.getSignByMpiAndDoc(mpiId, doctor.getDoctorId());
                    if (null != relationDoctor) {
                        relationDoctor.setRemainDates(DateConversion.getDaysBetween(Calendar.getInstance().getTime(), relationDoctor.getEndDate()) + "");
                        try {
                            OrganConfig organConfig=organConfigDAO.getByOrganId(doctor.getOrgan());
                            Integer months=organConfig.getSigningAhead();
	                        int monthFlag = relationDoctorDAO.getMonthFlag(months,mpiId, doctor.getDoctorId());
                            if (monthFlag == 1) {
                            	ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
                            	ConsultSet consultSet = consultSetDAO.getDefaultConsultSet(doctor.getDoctorId());
                            	if (consultSet.getSignStatus()) {
                            		map.put("canRenew", 1);
                            	}else{
                            		map.put("canRenew", 0);
                            	}
                          }
                        } catch (Exception e) {
                            map.put("canRenew", 0);
                            logger.debug("to query if left one month of sign NullPointer and mpiId:[{}] and doctorId:[{}]", mpiId, doctor.getDoctorId());
                        }
                        map.put("signed", relationDoctor);
                    }
                    backList.add(map);
                }
            }
        }

        //最后查询申请中的签约医生
        List<SignRecord> signingRecords = signRecordDAO.findEffectiveSignRecords(mpiId);
        if(null != signingRecords && !signingRecords.isEmpty()){
            for(SignRecord signRecord : signingRecords){
                if(null != signRecord){
                    Map<String, Object> map = new HashMap<>();
                    map.put("signing", signRecord);
                    Doctor doctor = doctorDAO.getByDoctorId(signRecord.getDoctor());
                    if(null != doctor){
                        map.put("doctor", doctor);
                    }

                    //2017-4-11 15:03:17 zhangx为 兼容健康APP老版本，保存前将是否预签约标记设置为不预签约
                    int preSign=signRecord.getPreSign()==null?SignRecordConstant.SIGN_IS_NOT_PRESIGN:signRecord.getPreSign();

                    if(preSign==1) {
                        Patient patient = patientDAO.getByMpiId(signRecord.getRequestMpiId());
                        map.put("disFlag", getOfHisIsSigned(patient, doctor));
                    }
                    backList.add(map);
                }
            }
        }

        return backList;
    }

    /**
     * 内部方法，调his判断是否已经签约(0没接his，1已签，2未签)
     * @param patient
     * @param doctor
     * @return
     */
    public static int getOfHisIsSigned(Patient patient,Doctor doctor){
        Integer organ = doctor.getOrgan();
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig hisServiceConfig = hisServiceConfigDao.getByOrganId(organ);
        try {
            String jobNum = DAOFactory.getDAO(EmploymentDAO.class).getPrimaryEmpByDoctorId(doctor.getDoctorId()).getJobNumber();
            if(hisServiceConfig!=null&&hisServiceConfig.getSigntohis()!=null&&hisServiceConfig.getSigntohis().intValue()==1){
                SignCommonBean signCommonBean = new SignCommonBean();
                signCommonBean.setPatientID(patient.getMpiId());
                signCommonBean.setPatientName(patient.getPatientName());
                signCommonBean.setCertID(patient.getIdcard());
    //                signCommonBean.setCardType(patient.getHealthCard().getCardType());
    //                signCommonBean.setCardID(patient.getHealthCard().getCardId());
                signCommonBean.setMobile(patient.getMobile());
                signCommonBean.setDoctor(doctor.getDoctorId());
                signCommonBean.setDoctorName(doctor.getName());
                signCommonBean.setOrgan(organ);
                signCommonBean.setJobNum(jobNum);
                String hisServiceId = hisServiceConfig.getAppDomainId() + ".signService";
                //Object resObj = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getSignList", signCommonBean);
                Object resObj = null;
                if(DBParamLoaderUtil.getOrganSwich(signCommonBean.getOrgan())){
                	ISignHisService iSignHisService = AppDomainContext.getBean("his.iSignHisService",ISignHisService.class);
                	SignCommonBeanTO reqTO= new SignCommonBeanTO();
            		BeanUtils.copy(signCommonBean,reqTO);
            		resObj = iSignHisService.getSignList(reqTO);
            	}else{
            		resObj = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getSignList", signCommonBean);
            	}
                if(resObj!=null){
                    List<SignCommonBean> signCommonBeenList = (List<SignCommonBean>)resObj;
                    if(!signCommonBeenList.isEmpty()){
                        return 1;
                    }else{
                        return 2;
                    }
                }else{
                    return 2;
                }
            }else{
                return 0;
            }
        } catch (Exception e) {
            logger.debug("getOfHisIsSigned faild and patient is [{}] and doctor is [{}]",patient.getMpiId(),doctor.getDoctorId());
        }
        return 0;
    }
    
    
    public static SignCommonBean getHisIsSigned(Patient patient,Doctor doctor){
        Integer organ = doctor.getOrgan();
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig hisServiceConfig = hisServiceConfigDao.getByOrganId(organ);
        try {
            String jobNum = DAOFactory.getDAO(EmploymentDAO.class).getPrimaryEmpByDoctorId(doctor.getDoctorId()).getJobNumber();
            if(hisServiceConfig!=null&&hisServiceConfig.getSigntohis()!=null&&hisServiceConfig.getSigntohis().intValue()==1){
                SignCommonBean signCommonBean = new SignCommonBean();
                signCommonBean.setPatientID(patient.getMpiId());
                signCommonBean.setPatientName(patient.getPatientName());
                signCommonBean.setCertID(patient.getIdcard());
                signCommonBean.setMobile(patient.getMobile());
                signCommonBean.setDoctor(doctor.getDoctorId());
                signCommonBean.setDoctorName(doctor.getName());
                signCommonBean.setOrgan(organ);
                signCommonBean.setJobNum(jobNum);
                String hisServiceId = hisServiceConfig.getAppDomainId() + ".signService";
                Object resObj = null;
                if(DBParamLoaderUtil.getOrganSwich(signCommonBean.getOrgan())){
                	ISignHisService iSignHisService = AppDomainContext.getBean("his.iSignHisService",ISignHisService.class);
                	SignCommonBeanTO reqTO= new SignCommonBeanTO();
            		BeanUtils.copy(signCommonBean,reqTO);
            		resObj = iSignHisService.getSignList(reqTO);
            	}else{
            		resObj = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getSignList", signCommonBean);
            	}
                if(resObj!=null){
                    List<SignCommonBean> signCommonBeenList = (List<SignCommonBean>)resObj;
                    if(!signCommonBeenList.isEmpty()){
                        return signCommonBeenList.get(0);
                    }else{
                        return null;
                    }
                }else{
                    return null;
                }
            }else{
                return null;
            }
        } catch (Exception e) {
            logger.debug("getOfHisIsSigned faild and patient is [{}] and doctor is [{}]",patient.getMpiId(),doctor.getDoctorId());
        }
        return null;
    }

    /**
     * 特定顺序查询家庭成员
     * @param mpiId
     * @return
     */
    public List<HashMap<String, Object>> familyMemberListOrder(String mpiId) {
        PatientDAO dao = DAOFactory.getDAO(PatientDAO.class);
        List<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();
        FamilyMemberDAO familyMemberDAO = DAOFactory.getDAO(FamilyMemberDAO.class);
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        RelationDoctorDAO relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);
        Integer isSign = (!CollectionUtils.isEmpty(signRecordDAO.findOwnInOrOutRecords(mpiId))||
                !CollectionUtils.isEmpty(relationDoctorDAO.findSignByMpi(mpiId)))?0:1;
        Patient self = dao.get(mpiId);
        if(null != self) {
            HashMap<String, Object> selfmap = new HashMap<String, Object>();
            selfmap.put("patient", self);
            selfmap.put("relation", new FamilyMember());
            selfmap.put("comparaFlag", isSign);
            list.add(selfmap);
        }

        List<FamilyMember> members = familyMemberDAO.findFamilyMembersWithField(mpiId);
        for (FamilyMember member : members) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            Patient p = dao.get(member.getMemberMpi());
            if(null != p) {
                if (StringUtils.isEmpty(p.getFullHomeArea())) {
                    p.setFullHomeArea(dao.getFullHomeArea(p.getHomeArea()));
                }
                map.put("patient", p);

                FamilyMember m = new FamilyMember();
                m.setRelation(member.getRelation());
                map.put("relation", m);

                Integer isSignFamily = (!CollectionUtils.isEmpty(signRecordDAO.findOwnInOrOutRecords(member.getMemberMpi())) ||
                        !CollectionUtils.isEmpty(relationDoctorDAO.findSignByMpi(member.getMemberMpi()))) ? 0 : 1;
                map.put("comparaFlag", isSignFamily);

                list.add(map);
            }
        }

        Collections.sort(list, new Comparator<HashMap<String, Object>>() {
            @Override
            public int compare(HashMap<String, Object> o1, HashMap<String, Object> o2) {
                Integer flagO1 = Integer.parseInt(o1.get("comparaFlag").toString());
                Integer flagO2 = Integer.parseInt(o2.get("comparaFlag").toString());
                return flagO1.compareTo(flagO2);
            }
        });

        return list;
    }

    /**
    * @Author:gey
    * @Description:根据传入的patient档案信息，保存该记录到patient表中。完善保存成功后saveSuccess为true，默认为false
    * @Date:11:01 2017-04-13
    */
    @RpcService
    public boolean updatePatientHealthRecord(final Patient patient) throws Exception {
        logger.info("updatePatientHealthRecord:" + JSONUtils.toString(patient));
        PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
        PatientService patientService = AppContextHolder.getBean("eh.patientService",PatientService.class);
        //保存健康档案标志
        boolean healthProfileFlag = false;
        //判断姓名，性别，年龄，身份证号是否传入进来
        patientService.isValidPatientData(patient);
        //根据患者主键mpi查询
        String mpiId = patient.getMpiId();
        Patient dbp = patDao.get(mpiId);
        if (dbp == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者不存在");
        }
        //患者未完善身份证信息
        if(StringUtils.isEmpty(dbp.getIdcard())) {
            //完善信息
            dbp = patientService.perfectPatientUserInfo(patient);
        }else{
            dbp.setMarry(patient.getMarry());
            dbp.setHeight(patient.getHeight());
            dbp.setWeight(patient.getWeight());
            dbp.setBlood(patient.getBlood());
            dbp.setEducation(patient.getEducation());
            dbp.setNation(patient.getNation());
            dbp.setCountry(patient.getCountry());
            dbp.setCountryItem(patient.getCountryItem());
            dbp.setJob(patient.getJob());
            dbp.setHouseHold(patient.getHouseHold());
            dbp.setAddress(patient.getAddress());
            dbp.setBirthPlace(patient.getBirthPlace());
            dbp.setFullBirthPlace(patDao.getFullHomeArea(patient.getBirthPlace()));
            dbp.setBirthPlaceDetail(patient.getBirthPlaceDetail());
            dbp.setResident(patient.getResident());
            dbp.setDocPayType(patient.getDocPayType());
            dbp.setSynHealthRecordFlag(true);
            isPatientInfo(dbp);
            dbp = patDao.update(dbp);
        }
        updateHealthOthers(patient,dbp,mpiId);
        healthProfileFlag = true;
        patDao.updateHealthProfileFlag(healthProfileFlag,dbp.getMpiId());
        logger.info("健康档案更新:" + JSONUtils.toString(dbp));
        return healthProfileFlag;
    }

    /**
     *
     * @param patient his或前端传入的原始数据
     * @param dbp   本地数据
     * @param mpiId 患者id
     */
    private void updateHealthOthers(Patient patient, Patient dbp, String mpiId) {
        LifeHabitDAO lifeHabitDAO = DAOFactory.getDAO(LifeHabitDAO.class);
        HealthLogDAO healthLogDAO = DAOFactory.getDAO(HealthLogDAO.class);
        ActionHabitDAO actionHabitDAO = DAOFactory.getDAO(ActionHabitDAO.class);
        LifeHabit lifeHabit = patient.getLifeHabit();
        HealthLog healthLog = patient.getHealthLog();
        ActionHabit actionHabit = patient.getActionHabit();
        //当传入的生活习惯不为空
        if(lifeHabit!=null){
            lifeHabit.setMpiId(dbp.getMpiId());
            if(lifeHabitDAO.getLifeHabitByMpiId(mpiId)==null){
                //生活习惯表中没有该患者的生活习惯记录
                lifeHabitDAO.addLifeHabit(lifeHabit);
            }else{
                //有生活习惯更新时
                lifeHabit.setLifeHabitId(lifeHabitDAO.getLifeHabitByMpiId(mpiId).getLifeHabitId());
                lifeHabitDAO.update(lifeHabit);
            }
        }

        //当传入的健康信息不为空
        if(healthLog!=null){
            healthLog.setMpiId(dbp.getMpiId());
            HealthLog dbHealthLog=healthLogDAO.getHealthLogByMpiId(mpiId);
            healthLog = getHealthLogInfo(healthLog);

            if(dbHealthLog==null){
                //健康信息表中没有该患者的健康信息记录
                healthLogDAO.addHealthLog(healthLog);
            }else{
                //有健康信息更新时
                BeanUtils.map(healthLog,dbHealthLog);
                isHealthLogInfo(dbHealthLog);
                healthLogDAO.update(dbHealthLog);
            }
        }

        //当传入的行为习惯不为空
        if(actionHabit!=null){
            actionHabit.setMpiId(dbp.getMpiId());
            if(actionHabitDAO.getActionHabitByMpiId(mpiId)==null){
                //行为习惯表中没有该患者的行为习惯记录
                actionHabitDAO.addActionHabit(actionHabit);
            }else{
                //有行为习惯更新时
                actionHabit.setActionHabitId(actionHabitDAO.getActionHabitByMpiId(mpiId).getActionHabitId());
                isActionHabitInfo(actionHabit);
                actionHabitDAO.update(actionHabit);
            }
        }
    }
    /* *
        * @Author:gey
        * @Description:根据传入的patient档案信息，保存该记录到patient表中。完善保存成功后healthProfileFlag为true，默认为false
        * @Date:11:01 2017-04-13
        */
    @RpcService
    public Map<String,Object> getHealthRecordByMpiId(String mpiId,Integer moduleId){
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        LifeHabitDAO lifeHabitDAO = DAOFactory.getDAO(LifeHabitDAO.class);
        HealthLogDAO healthLogDAO = DAOFactory.getDAO(HealthLogDAO.class);
        ActionHabitDAO actionHabitDAO = DAOFactory.getDAO(ActionHabitDAO.class);
        Patient patient = patientDAO.get(mpiId);
        LifeHabit lifeHabit = lifeHabitDAO.getLifeHabitByMpiId(mpiId);
        HealthLog healthLog = healthLogDAO.getHealthLogByMpiId(mpiId);
        ActionHabit actionHabit = actionHabitDAO.getActionHabitByMpiId(mpiId);
        if(healthLog!=null){
            patient.setHealthLog(healthLog);
        }
        if(actionHabit!=null){
            patient.setActionHabit(actionHabit);
        }
        if(lifeHabit!=null){
            patient.setLifeHabit(lifeHabit);
        }
        if (patient == null) {
            throw new DAOException(609, "患者数据不存在");
        }
        final ModuleUseLogService moduleUseLogService = AppContextHolder.getBean("eh.moduleUseLogService",ModuleUseLogService.class);
        HashMap returnMap = new HashMap();
        if(moduleId!=null){
            returnMap.put("isFirstUseModule",moduleUseLogService.isFirstUseModule(mpiId,moduleId));
        }
        returnMap.put("patient",patient);
       return returnMap;
    }

    /**
     * 2017-6-23 11:53:36 zhangx
     * 由于前端编辑界面要判断原来保存的时候，如何选择选项，且只有患者端编辑，
     * 目前需要的字段flag处理方式为患者端每次查询都进行处理
     * 后期如果其他地方都需要，则可以考虑保存到数据库
     *
     * hasExposeFlag暴露史，
     * hasAllergyFlag过敏史，
     * hasDisabilityFlag残疾情况，
     * hasFamilySickFatherFlag我的父亲，
     * hasFamilySickMotherFlag我的母亲，
     * hasFamilySickSiblingsFlag兄弟，
     * hasFamilySickChildrenFlag子女
     * @param healthLog
     * @return
     */
    private HealthLog getHealthLogInfo(HealthLog healthLog){
        healthLog.setHasExposeFlag(getHasFlag(healthLog.getExpose(),HealthLogConstant.EXPOSE_NO));
        healthLog.setHasAllergyFlag(getHasFlag(healthLog.getHasAllergy(),HealthLogConstant.HASALLERGY_NO));
        healthLog.setHasDisabilityFlag(getHasFlag(healthLog.getHasDisability(),HealthLogConstant.HASDISABILITY_NO));
        healthLog.setHasFamilySickFatherFlag(getHasFlag(healthLog.getHasFamilySickFather(),HealthLogConstant.HASFAMILYSICK_NO));
        healthLog.setHasFamilySickMotherFlag(getHasFlag(healthLog.getHasFamilySickMother(),HealthLogConstant.HASFAMILYSICK_NO));
        healthLog.setHasFamilySickSiblingsFlag(getHasFlag(healthLog.getHasFamilySickSiblings(),HealthLogConstant.HASFAMILYSICK_NO));
        healthLog.setHasFamilySickChildrenFlag(getHasFlag(healthLog.getHasFamilySickChildren(),HealthLogConstant.HASFAMILYSICK_NO));

        return healthLog;
    }

    private void isActionHabitInfo(ActionHabit actionHabit){
        if (actionHabit.getSmoke().equals("2")) {
            actionHabit.setSmokeTime("");
            actionHabit.setStartSmokeTime("");
            actionHabit.setSmokeFrequency("");
            actionHabit.setEndSmokeTime("");
        }
        if (actionHabit.getSmoke().equals("1")) {
            actionHabit.setEndSmokeTime("");
        }
        if (actionHabit.getAlcohol().equals("2")) {
            actionHabit.setAlcoholFrequency("");
            actionHabit.setAlcoholType("");
            actionHabit.setAlcoholRate("");
            actionHabit.setEndAlcoholTime("");
        }
        if (actionHabit.getAlcohol().equals("1")) {
            actionHabit.setEndAlcoholTime("");
        }
        if (!actionHabit.getTraining().equals("1")) {
            actionHabit.setTrainingType("");
            actionHabit.setTrainingTime("");
            actionHabit.setTrainingDetail("");
        }
    }
    private void isPatientInfo(Patient patient){
        if (!patient.getResident().equals("1")) {
            patient.setAddress("");
        }
    }

    private void isHealthLogInfo(HealthLog healthLog){
        if (healthLog.getHasAllergy().equals("1") || !splitItem(healthLog.getHasAllergy(),"5")) {
            healthLog.setAllergyDetail("");
        }
        if (healthLog.getHasDisability().equals("8") || !splitItem(healthLog.getHasDisability(),"7")) {
            healthLog.setDisabilityDetail("");
        }
        if (healthLog.getHasHeredopathia().equals("0")) {
            healthLog.setHeredopathiaDetail("");
        }
        if (healthLog.getHasFamilySickFather().equals("1") || !splitItem(healthLog.getHasFamilySickFather(),"12")) {
            healthLog.setFamilySickFatherDetail("");
        }
        if (healthLog.getHasFamilySickMother().equals("1") || !splitItem(healthLog.getHasFamilySickMother(),"12")) {
            healthLog.setFamilySickMotherDetail("");
        }
        if (healthLog.getHasFamilySickSiblings().equals("1") || !splitItem(healthLog.getHasFamilySickSiblings(),"12")) {
            healthLog.setFamilySickSiblingsDetail("");
        }
        if (healthLog.getHasFamilySickChildren().equals("1") || !splitItem(healthLog.getHasFamilySickChildren(),"12")) {
            healthLog.setFamilySickChildrenDetail("");
        }
    }
    private boolean splitItem(String s, String value) {
        boolean result = false;
        String [] strings = s.split(",");
        for (int i = 0;i<strings.length;i++) {
            if (strings[i].equals(value)) {
                result = true;
            }
        }
        return result;
    }
    private int getHasFlag(String healthLogData,String healthLogData_no){
        int hasFalg=HealthLogConstant.HEALTHLOG_SELECT_NO;
        String[] healthLogDataArray=healthLogData.split(",");
        List<String> healthLogDataList=Arrays.asList(healthLogDataArray);

        if(!StringUtils.isEmpty(healthLogData) && !healthLogDataList.contains(healthLogData_no)){
            hasFalg=HealthLogConstant.HEALTHLOG_SELECT_HAS;
        }
        return hasFalg;
    }

    //获取字典
    @RpcService
    public Map<String, DictionarySliceRecordSet> getHealthProfileDictionary(String parentKey, int sliceType) throws ControllerException {
        DictionaryLocalService ser = AppContextHolder.getBean("dictionaryService",DictionaryLocalService.class);
        Map<String, DictionarySliceRecordSet> mMap = new HashMap<>();

        mMap.put("Marry",ser.getSlice ("eh.base.dictionary.Marry", parentKey,sliceType,"", 0,0));
        mMap.put("Blood",ser.getSlice("eh.base.dictionary.Blood", parentKey,sliceType,"", 0,0));
        mMap.put("Smoke",ser.getSlice("eh.mpi.dictionary.Smoke", parentKey,sliceType,"", 0,0));
        mMap.put("Education",ser.getSlice("eh.mpi.dictionary.Educations", parentKey,sliceType,"", 0,0));
        mMap.put("Nation",ser.getSlice ("eh.mpi.dictionary.Nation", parentKey,sliceType,"", 0,0));
        mMap.put("Country",ser.getSlice("eh.mpi.dictionary.Country", parentKey,sliceType,"", 0,0));
        mMap.put("Job",ser.getSlice("eh.mpi.dictionary.Job", parentKey,sliceType,"", 0,0));
        mMap.put("Household",ser.getSlice("eh.mpi.dictionary.Household", parentKey,sliceType,"", 0,0));
        mMap.put("Resident",ser.getSlice ("eh.mpi.dictionary.Resident", parentKey,sliceType,"", 0,0));
        mMap.put("PayType",ser.getSlice("eh.mpi.dictionary.PayType", parentKey,sliceType,"", 0,0));
        mMap.put("BirthPlace",ser.getSlice("eh.base.dictionary.AddrArea", parentKey,sliceType,"", 0,0));

        mMap.put("RhBlood",ser.getSlice ("eh.mpi.dictionary.RhBlood", parentKey,sliceType,"", 0,0));
        mMap.put("Expose",ser.getSlice("eh.mpi.dictionary.Expose", parentKey,sliceType,"", 0,0));
        mMap.put("HasAllergy",ser.getSlice("eh.mpi.dictionary.HasAllergy", parentKey,sliceType,"", 0,0));
        mMap.put("HasDisability",ser.getSlice("eh.mpi.dictionary.HasDisability", parentKey,sliceType,"", 0,0));
        mMap.put("HasFamilySick",ser.getSlice ("eh.mpi.dictionary.HasFamilySick", parentKey,sliceType,"", 0,0));
        mMap.put("History",ser.getSlice ("eh.mpi.dictionary.History", parentKey,sliceType,"", 0,0));

        mMap.put("SmokeFrequency",ser.getSlice("eh.mpi.dictionary.SmokeFrequency", parentKey,sliceType,"", 0,0));
        mMap.put("AlcoholType",ser.getSlice("eh.mpi.dictionary.AlcoholType", parentKey,sliceType,"", 0,0));
        mMap.put("AlcoholFrequency",ser.getSlice("eh.mpi.dictionary.AlcoholFrequency", parentKey,sliceType,"", 0,0));
        mMap.put("AlcoholRate",ser.getSlice("eh.mpi.dictionary.AlcoholRate", parentKey,sliceType,"", 0,0));
        mMap.put("Training",ser.getSlice("eh.mpi.dictionary.Alcohol", parentKey,sliceType,"", 0,0));
        mMap.put("TrainingDetail",ser.getSlice("eh.mpi.dictionary.TrainingDetail", parentKey,sliceType,"", 0,0));
        mMap.put("TrainingTime",ser.getSlice("eh.mpi.dictionary.TrainingTime", parentKey,sliceType,"", 0,0));
        mMap.put("TrainingType",ser.getSlice("eh.mpi.dictionary.TrainingType", parentKey,sliceType,"", 0,0));
        mMap.put("EatingType",ser.getSlice ("eh.mpi.dictionary.EatingType", parentKey,sliceType,"", 0,0));
        mMap.put("SleepTime",ser.getSlice("eh.mpi.dictionary.SleepTime", parentKey,sliceType,"", 0,0));
        mMap.put("SleepingNormal",ser.getSlice("eh.mpi.dictionary.SleepingNormal", parentKey,sliceType,"", 0,0));

        mMap.put("KitchenExhaust",ser.getSlice("eh.mpi.dictionary.KitchenExhaust", parentKey,sliceType,"", 0,0));
        mMap.put("FuelType",ser.getSlice ("eh.mpi.dictionary.FuelType", parentKey,sliceType,"", 0,0));
        mMap.put("DrinkWater",ser.getSlice("eh.mpi.dictionary.DrinkWater", parentKey,sliceType,"", 0,0));
        mMap.put("Toilet",ser.getSlice("eh.mpi.dictionary.Toilet", parentKey,sliceType,"", 0,0));
        mMap.put("Bird",ser.getSlice("eh.mpi.dictionary.Bird", parentKey,sliceType,"", 0,0));
        return mMap;
    }

    //获取类中属性的类型
    @RpcService
    public Map<String, Object> getAnnoType(String strName) throws InvocationTargetException, IllegalAccessException, ControllerException {
        Schema schema = SchemaController.instance().get(strName);
        List<SchemaItem> list = schema.getItems();
        Map<String, Object> map = new HashMap();
        for (SchemaItem item : list) {
            if (item.isCodedValue()) {
                map.put(item.getId(),item.getDefaultValue());

            }
        }
        return map;
    }
    public void changeFieldType(T t) throws InvocationTargetException, IllegalAccessException {
        Class c = (Class) t.getClass();
        Field[] field = c.getDeclaredFields();
        for(Field f : field) {
            f.setAccessible(true);
            Object value = f.get(t);
            String type = f.getType().toString();
            if (!type.endsWith("String") && !type.endsWith("int") && !type.endsWith("Inter")) {
                f.set(t,"");
            }
        }
    }

    @RpcService
    public HisResponse getHisHealthRecord(Patient patient){
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patientdb = new Patient();
        HisResponse hisResponse = new HisResponse();
        String idCard18;
        try {
            idCard18 = ChinaIDNumberUtil.convert15To18(patient.getRawIdcard());
            patient.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
            patient.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
            patient.setIdcard(idCard18);
            patient.setRawIdcard(patient.getRawIdcard());
        } catch (ValidateException e) {
            hisResponse.setMsgCode("0");
            hisResponse.setMsg("身份证格式错误");
            return hisResponse;
        }
        try {
            BeanUtils.copy(patient,patientdb);
            patientDAO.update(patientdb);
        }catch (Exception e){
            hisResponse.setMsgCode("0");
            hisResponse.setMsg("同步失败");
        }
        return hisResponse;
    }

    //医生端签约时同步下载公卫健康档案
    @RpcService
    public Map<String, Object> synHealthRecord(String mpiId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.get(mpiId);
        HisHealthRecord hisHealthRecord = new HisHealthRecord();
        hisHealthRecord.setPatientIDCard(patient.getRawIdcard());
        hisHealthRecord.setPatientName(patient.getPatientName());
        //调用服务id
        String hisServiceId = "sbys" + ".healthRecordService";
        HisResponse hisResponse = (HisResponse)RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId, "synHealthRecord", hisHealthRecord);
        Map<String, Object> resultMap = Maps.newHashMap();
        if (hisResponse != null) {
            Patient p = (Patient)hisResponse.getData();
            if (!p.getMpiId().equals(mpiId)) {
                throw new DAOException(609, "患者数据不匹配");
            }
            //同步处理
            patient.setMarry(p.getMarry());
            patient.setHeight(p.getHeight());
            patient.setWeight(p.getWeight());
            patient.setEducation(p.getEducation());
            patient.setNation(p.getNation());
            patient.setCountry(p.getCountry());
            patient.setCountryItem(p.getCountryItem());
            patient.setJob(p.getJob());
            patient.setHouseHold(p.getHouseHold());
            patient.setAddress(p.getAddress());
            patient.setBirthPlace(p.getBirthPlace());
            patient.setFullBirthPlace(patientDAO.getFullHomeArea(p.getBirthPlace()));
            patient.setBirthPlaceDetail(p.getBirthPlaceDetail());
            patient.setResident(p.getResident());
            patient.setDocPayType(p.getDocPayType());
            patient.setBlood(p.getBlood());
            updateHealthOthers(p,patient,mpiId);
            if(hisResponse.isSuccess()) {
                resultMap.put("code", "200");
            }else {
                resultMap.put("code", "0");
            }
            resultMap.put("msg", hisResponse.getMsg());
        }
        return resultMap;
    }

    //患者更新健康档案时医生端同步到公卫
    @RpcService
    public void updateHealthRecordForPublic(String mpiId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getByMpiId(mpiId);
        if (patient == null) {
            throw new DAOException(609, "患者信息不存在");
        }
        Map map = getHealthRecordByMpiId(patient.getMpiId(),null);
        Patient p = (Patient) map.get("patient");
        String hisServiceId = "sbys" + ".healthRecordService";
        HisResponse hisResponse = (HisResponse)RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId, "uploadHealthRecordToHis", p);
        patient.setSynHealthRecordFlag(false);
        patientDAO.update(patient);
        if (hisResponse != null) {
            logger.info("健康档案同步到公卫 code: " + hisResponse.getMsgCode() + "msg：" + hisResponse.getMsg());
        }
    }

    //验证该患者是否与医生有签约关系
    @RpcService
    public boolean isSignRaliton(String mpiId, Integer doctorId) {
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        boolean result = true;
        List<SignRecord> list = signRecordDAO.findByDoctorAndMpiIdAndRecordStatus(doctorId, mpiId, 1);
        if (CollectionUtils.isEmpty(list)) {
            result = false;
        }
        return result;
    }

    //公卫返回是否同步成功
    @RpcService
    public String isSynHealth(String code, String mpiId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getByMpiId(mpiId);
        if (patient.getSynHealthRecordFlag() && code.equals("200")){
            return "200";
        }
        return "500";
    }


    /**
     * 供前置机调用，将his的签约记录导入到平台中
     * */
    @RpcService
    public HisResponse saveHisSignRecords(HisSignRecord record){
        String paramStr = JSONUtils.toString(record);
        logger.info("his签约记录参数："+paramStr);
        try {
            HisResponse f = validateParam(record);
            if(!f.isSuccess()){
                return f;
            }
            SignRecord signRecord = getSignRecord(record);
            if(signRecord==null){
                return new HisResponse("-1","transfer error!");
            }
            SignRecordDAO signRecordDao = DAOFactory.getDAO(SignRecordDAO.class);
            Date start = signRecord.getStartDate();
            Date end = signRecord.getEndDate();
            List<SignRecord> records = signRecordDao.findByMpiIdAndDoctorID(signRecord.getOrgan(), signRecord.getRequestMpiId(), signRecord.getDoctor(), start, end);
            if(!CollectionUtils.isEmpty(records)){
                return new HisResponse("-1","该病人已存在签约记录！");
            }
            signRecordDao.save(signRecord);
            return f.setSuccess();
        }catch (Exception e){
            e.printStackTrace();
            return new HisResponse("-1","系统处理异常！"+e.getMessage());
        }
    }

    private SignRecord getSignRecord(HisSignRecord record) {
        SignRecord r = new SignRecord();
        r.setOrgan(record.getOrgan());
        r.setDoctor(record.getDoctor());
        r.setRequestMpiId(record.getMpiID());
        r.setSignCost(record.getSignCost());
        r.setSignPrice(record.getSignPrice());
        r.setSignSubsidyPrice(record.getSignSubsidyPrice());
        r.setStartDate(record.getStartDate());
        r.setEndDate(record.getEndDate());
        r.setRequestDate(record.getRequestDate());
        r.setSignTime(record.getSignTime());
        String status = record.getRecordStatus();
        r.setRecordStatus(Integer.parseInt(status));
        r.setFromSign(record.getFromSign());
        r.setPayFlag(record.getPayFlag());
        r.setSignCost(record.getSignCost());
        r.setSignPrice(record.getSignPrice());
        r.setSignSubsidyPrice(record.getSignSubsidyPrice());
        r.setRenew(record.getRenew());
        r.setPreSign(record.getPreSign());
        r.setOrganSignId(record.getSignRecordId());
        String organizeCode = record.getOrganizeCode();
        if(StringUtils.isNoneBlank(organizeCode)){
            Organ organ = DAOFactory.getDAO(OrganDAO.class).getByOrganizeCode(organizeCode);
            if(organ!=null){
                r.setRequestOrgan(organ.getOrganId());
            }
        }
        r.setFromFlag(1);
        return r;
    }

    private HisResponse validateParam(HisSignRecord record) {
        if(record.getOrgan()==null){
            return new HisResponse("-1","organ is null");
        }

        if(StringUtils.isEmpty(record.getPatientName())||StringUtils.isEmpty(record.getCertID())){
            return new HisResponse("-1","patient is empty");
        }

        if(StringUtils.isEmpty(record.getJobNum())&&record.getDoctor()==null){
            return new HisResponse("-1","doctor is empty");
        }
        if(record.getDoctor()==null){
            String jobNum = record.getJobNum();
            Employment emp = DAOFactory.getDAO(EmploymentDAO.class).getByJobNumberAndOrganId(jobNum, record.getOrgan());
            if(emp==null){
                return new HisResponse("-1","can't find the doctor!");
            }else{
                record.setDoctor(emp.getDoctorId());
            }
        }
        if(record.getStartDate()==null||record.getEndDate()==null){
            return new HisResponse("-1","signDate is empty");
        }
        String certID = record.getCertID();
        if(StringUtils.isEmpty(certID)){
            return new HisResponse("-1","certID is empty");
        }
        Patient patient = DAOFactory.getDAO(PatientDAO.class).getByIdCard(certID);
        if(patient==null){
            //找不到患者，新建患者
            try {
                patient = new Patient();
                patient.setPatientName(record.getPatientName());
                patient.setIdcard(certID);
                String idCard18 = eh.util.ChinaIDNumberUtil.convert15To18(certID);
                patient.setBirthday(eh.util.ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
                patient.setPatientSex(eh.util.ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
                patient.setPatientType("1");
                patient.setMobile(record.getMobile());
                Patient p = DAOFactory.getDAO(PatientDAO.class).getOrUpdate(patient);
                record.setMpiID(p.getMpiId());
            } catch (ValidateException e) {
                e.printStackTrace();
            }

//            getOrUpdateAndConcered
//            return new HisResponse("-1","can't find the patient!");
        }else{
            record.setMpiID(patient.getMpiId());
        }
        return new HisResponse().setSuccess();

    }
}
