package eh.base.dao;

import ctd.account.user.UserController;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.controller.updater.ConfigurableItemUpdater;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.loader.DictionaryItemLoader;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.user.UserSevice;
import eh.bus.dao.AppointDepartDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.Organ;
import eh.entity.bus.AppointDepart;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class EmploymentDAO extends
        HibernateSupportDelegateDAO<Employment> implements DictionaryItemLoader {

    public static final Logger log = Logger.getLogger(EmploymentDAO.class);

    public EmploymentDAO() {
        super();
        setEntityName(Employment.class.getName());
        setKeyField("EmploymentId");
    }

    /**
     * 删除职业点
     *
     * @param employmentId
     * @author LF
     */
    @RpcService
    public Boolean deleteEm(int employmentId) {
        log.info("删除职业点(deleteEm):employmentId=" + employmentId);
        Employment employment = get(employmentId);
        if (employment == null) {
            log.error("employment is required!");
            new DAOException(DAOException.VALUE_NEEDED,
                    "employment is required!");
            return false;
        }
        if (employment.getPrimaryOrgan() != null
                && employment.getPrimaryOrgan()) {
            new DAOException(609, "第一职业点不能删除");
            return false;
        }
        remove(employmentId);
        return true;
    }

    /**
     * 根据医生内码，机构，科室获取唯一执业机构记录
     *
     * @param doctorId
     * @param organId
     * @param department
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod(sql = "From Employment where doctorId=:doctorId and organId=:organId and department=:department")
    public abstract Employment getByDocAndOrAndDep(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("organId") int organId,
            @DAOParam("department") int department);

    /**
     * 供 根据医生内码获取执业机构 调用
     *
     * @param doctorId
     * @return
     */
    @DAOMethod
    @RpcService
    public abstract List<Employment> findByDoctorId(int doctorId);

    /**
     * 查询一个医生的职业机构列表，第一职业点排在最前面，非第一职业点升序排序
     *
     * @param doctorId 医生ID
     * @return
     * @author zhangx
     * @date 2015-11-16 下午8:19:02
     */
    @RpcService
    @DAOMethod(sql = "From Employment where doctorId=:doctorId order by primaryOrgan desc,employmentId asc")
    public abstract List<Employment> findByDoctorIdOrderBy(
            @DAOParam("doctorId") int doctorId);

    /**
     * 根据医生编号获取医生的就诊地址及价格
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findEmploymentList(int doctorId) {
        List<Employment> employments = findByDoctorId(doctorId);
        if (employments == null || employments.size() < 1) {
            return null;
        }

        List<Map<String, Object>> empList = new ArrayList<Map<String, Object>>();
        DictionaryController dicController = DictionaryController.instance();
        try {
            for (Employment employment : employments) {
                Map<String, Object> empMap = new HashMap<>();
                empMap.put("employmentId", employment.getEmploymentId());
                empMap.put("clinicPrice", employment.getClinicPrice());
                empMap.put("profClinicPrice", employment.getProfClinicPrice());
                empMap.put("specClinicPrice", employment.getSpecClinicPrice());
                Integer organId = employment.getOrganId();
                if (organId != null) {
                    empMap.put("organId", organId);
                    empMap.put("organText",
                            dicController.get("eh.base.dictionary.Organ")
                                    .getText(organId));
                }
                Integer departId = employment.getDepartment();
                if (departId != null) {
                    empMap.put("departId", departId);
                    empMap.put("departText",
                            dicController.get("eh.base.dictionary.Depart")
                                    .getText(departId));
                }

                List<Map<String, Object>> addressList = new ArrayList<Map<String, Object>>();
                String appDepartCode1 = employment.getAppointDepartCode1();
                Map<String, Object> address1 = treateAppDepartCode(organId,
                        appDepartCode1);
                address1.put("clinicRoomAddr", employment.getClinicRoomAddr1());
                Integer sourceLevel1 = employment.getSourceLevel1();
                Map<String, Object> map1 = treatSourceLevel(sourceLevel1,
                        employment);
                address1.putAll(map1);
                addressList.add(address1);

                String appDepartCode2 = employment.getAppointDepartCode2();
                Map<String, Object> address2 = treateAppDepartCode(organId,
                        appDepartCode2);
                address2.put("clinicRoomAddr", employment.getClinicRoomAddr2());
                Integer sourceLevel2 = employment.getSourceLevel2();
                Map<String, Object> map2 = treatSourceLevel(sourceLevel2,
                        employment);
                address2.putAll(map2);
                addressList.add(address2);

                String appDepartCode3 = employment.getAppointDepartCode3();
                Map<String, Object> address3 = treateAppDepartCode(organId,
                        appDepartCode3);
                address3.put("clinicRoomAddr", employment.getClinicRoomAddr3());
                Integer sourceLevel3 = employment.getSourceLevel3();
                Map<String, Object> map3 = treatSourceLevel(sourceLevel3,
                        employment);
                address3.putAll(map3);
                addressList.add(address3);

                empMap.put("addressList", addressList);

                empList.add(empMap);
            }
        } catch (ControllerException e) {
            log.error("findEmploymentList() error: "+e);
        }
        return empList;
    }

    /**
     * 根据医生编号获取医生的就诊地址及价格
     *
     * @param doctorId
     * @return 2015-10-23 由于PC端2.0版本没有设置职业点功能，返回给PC端将执业地点为空的列表
     * @author zhangx
     * @date 2015-10-23 下午5:35:27
     */
    @RpcService
    public List<Map<String, Object>> findUnEmptyEmploymentList(int doctorId) {
        List<Employment> employments = findByDoctorIdOrderBy(doctorId);
        if (employments == null || employments.size() < 1) {
            return null;
        }

        List<Map<String, Object>> empList = new ArrayList<Map<String, Object>>();
        DictionaryController dicController = DictionaryController.instance();
        try {
            for (Employment employment : employments) {
                Map<String, Object> empMap = new HashMap<>();
                empMap.put("employmentId", employment.getEmploymentId());
                empMap.put("clinicPrice", employment.getClinicPrice());
                empMap.put("profClinicPrice", employment.getProfClinicPrice());
                empMap.put("specClinicPrice", employment.getSpecClinicPrice());
                Integer organId = employment.getOrganId();
                if (organId != null) {
                    empMap.put("organId", organId);
                    empMap.put("organText",
                            dicController.get("eh.base.dictionary.Organ")
                                    .getText(organId));
                }
                Integer departId = employment.getDepartment();
                if (departId != null) {
                    empMap.put("departId", departId);
                    empMap.put("departText",
                            dicController.get("eh.base.dictionary.Depart")
                                    .getText(departId));
                }

                List<Map<String, Object>> addressList = new ArrayList<Map<String, Object>>();

                if (!StringUtils.isEmpty(employment.getClinicRoomAddr1())) {
                    String appDepartCode1 = employment.getAppointDepartCode1();
                    Map<String, Object> address1 = treateAppDepartCode(organId,
                            appDepartCode1);
                    address1.put("clinicRoomAddr",
                            employment.getClinicRoomAddr1());
                    Integer sourceLevel1 = employment.getSourceLevel1();
                    Map<String, Object> map1 = treatSourceLevel(sourceLevel1,
                            employment);
                    address1.putAll(map1);
                    addressList.add(address1);
                }

                if (!StringUtils.isEmpty(employment.getClinicRoomAddr2())) {
                    String appDepartCode2 = employment.getAppointDepartCode2();
                    Map<String, Object> address2 = treateAppDepartCode(organId,
                            appDepartCode2);
                    address2.put("clinicRoomAddr",
                            employment.getClinicRoomAddr2());
                    Integer sourceLevel2 = employment.getSourceLevel2();
                    Map<String, Object> map2 = treatSourceLevel(sourceLevel2,
                            employment);
                    address2.putAll(map2);
                    addressList.add(address2);
                }

                if (!StringUtils.isEmpty(employment.getClinicRoomAddr3())) {
                    String appDepartCode3 = employment.getAppointDepartCode3();
                    Map<String, Object> address3 = treateAppDepartCode(organId,
                            appDepartCode3);
                    address3.put("clinicRoomAddr",
                            employment.getClinicRoomAddr3());
                    Integer sourceLevel3 = employment.getSourceLevel3();
                    Map<String, Object> map3 = treatSourceLevel(sourceLevel3,
                            employment);
                    address3.putAll(map3);
                    addressList.add(address3);
                }

                empMap.put("addressList", addressList);

                if (addressList != null && addressList.size() > 0) {
                    empList.add(empMap);
                }

            }
        } catch (ControllerException e) {
            log.error("findUnEmptyEmploymentList() error: "+e);
        }
        return empList;
    }

    /**
     * 根据医生编号获取医生的第一职业点的就诊地址及价格
     *
     * @param doctorId
     * @return
     * @author zhangx
     * @date 2015-11-18 下午4:04:34
     */
    @RpcService
    public Map<String, Object> getPrimaryEmployment(int doctorId) {
        Employment employment = getPrimaryEmpByDoctorId(doctorId);
        if (employment == null) {
            return null;
        }

        DictionaryController dicController = DictionaryController.instance();
        Map<String, Object> empMap = new HashMap<>();
        try {
            empMap.put("employmentId", employment.getEmploymentId());
            empMap.put("clinicPrice", employment.getClinicPrice());
            empMap.put("profClinicPrice", employment.getProfClinicPrice());
            empMap.put("specClinicPrice", employment.getSpecClinicPrice());
            Integer organId = employment.getOrganId();
            if (organId != null) {
                empMap.put("organId", organId);
                empMap.put(
                        "organText",
                        dicController.get("eh.base.dictionary.Organ").getText(
                                organId));
            }
            Integer departId = employment.getDepartment();
            if (departId != null) {
                empMap.put("departId", departId);
                empMap.put(
                        "departText",
                        dicController.get("eh.base.dictionary.Depart").getText(
                                departId));
            }

            List<Map<String, Object>> addressList = new ArrayList<Map<String, Object>>();

            if (!StringUtils.isEmpty(employment.getClinicRoomAddr1())) {
                String appDepartCode1 = employment.getAppointDepartCode1();
                Map<String, Object> address1 = treateAppDepartCode(organId,
                        appDepartCode1);
                address1.put("clinicRoomAddr", employment.getClinicRoomAddr1());
                Integer sourceLevel1 = employment.getSourceLevel1();
                Map<String, Object> map1 = treatSourceLevel(sourceLevel1,
                        employment);
                address1.putAll(map1);
                addressList.add(address1);
            }

            if (!StringUtils.isEmpty(employment.getClinicRoomAddr2())) {
                String appDepartCode2 = employment.getAppointDepartCode2();
                Map<String, Object> address2 = treateAppDepartCode(organId,
                        appDepartCode2);
                address2.put("clinicRoomAddr", employment.getClinicRoomAddr2());
                Integer sourceLevel2 = employment.getSourceLevel2();
                Map<String, Object> map2 = treatSourceLevel(sourceLevel2,
                        employment);
                address2.putAll(map2);
                addressList.add(address2);
            }

            if (!StringUtils.isEmpty(employment.getClinicRoomAddr3())) {
                String appDepartCode3 = employment.getAppointDepartCode3();
                Map<String, Object> address3 = treateAppDepartCode(organId,
                        appDepartCode3);
                address3.put("clinicRoomAddr", employment.getClinicRoomAddr3());
                Integer sourceLevel3 = employment.getSourceLevel3();
                Map<String, Object> map3 = treatSourceLevel(sourceLevel3,
                        employment);
                address3.putAll(map3);
                addressList.add(address3);
            }

            empMap.put("addressList", addressList);

        } catch (ControllerException e) {
            log.error("getPrimaryEmployment() error: "+e);
        }
        return empMap;
    }

    /**
     * 根据医生编号获取医生的职业点的就诊地址及价格
     *
     * @param doctorId
     * @return
     * @desc 第一职业点没有，取employmentId最小的为2,2没有，取取employmentId第二小的3,依次类推，直到返回符合要求的
     * @author zhangx
     * @date 2015-11-23 下午5:46:45
     */
    @RpcService
    public Map<String, Object> getUnEmptyEmployment(int doctorId) {
        List<Employment> employments = findByDoctorIdOrderBy(doctorId);
        if (employments == null || employments.size() < 1) {
            return null;
        }

        Employment employment = null;
        for (Employment emp : employments) {
            if (StringUtils.isEmpty(emp.getAppointDepartCode1())
                    && StringUtils.isEmpty(emp.getAppointDepartCode2())
                    && StringUtils.isEmpty(emp.getAppointDepartCode3())) {
                continue;
            } else {
                employment = emp;
                break;
            }
        }

        if (employment == null) {
            return null;
        }

        DictionaryController dicController = DictionaryController.instance();
        Map<String, Object> empMap = new HashMap<>();
        try {
            empMap.put("employmentId", employment.getEmploymentId());
            empMap.put("clinicPrice", employment.getClinicPrice());
            empMap.put("profClinicPrice", employment.getProfClinicPrice());
            empMap.put("specClinicPrice", employment.getSpecClinicPrice());
            Integer organId = employment.getOrganId();
            if (organId != null) {
                empMap.put("organId", organId);
                empMap.put(
                        "organText",
                        dicController.get("eh.base.dictionary.Organ").getText(
                                organId));
            }
            Integer departId = employment.getDepartment();
            if (departId != null) {
                empMap.put("departId", departId);
                empMap.put(
                        "departText",
                        dicController.get("eh.base.dictionary.Depart").getText(
                                departId));
            }

            List<Map<String, Object>> addressList = new ArrayList<Map<String, Object>>();

            if (!StringUtils.isEmpty(employment.getClinicRoomAddr1())) {
                String appDepartCode1 = employment.getAppointDepartCode1();
                Map<String, Object> address1 = treateAppDepartCode(organId,
                        appDepartCode1);
                address1.put("clinicRoomAddr", employment.getClinicRoomAddr1());
                Integer sourceLevel1 = employment.getSourceLevel1();
                Map<String, Object> map1 = treatSourceLevel(sourceLevel1,
                        employment);
                address1.putAll(map1);
                addressList.add(address1);
            }

            if (!StringUtils.isEmpty(employment.getClinicRoomAddr2())) {
                String appDepartCode2 = employment.getAppointDepartCode2();
                Map<String, Object> address2 = treateAppDepartCode(organId,
                        appDepartCode2);
                address2.put("clinicRoomAddr", employment.getClinicRoomAddr2());
                Integer sourceLevel2 = employment.getSourceLevel2();
                Map<String, Object> map2 = treatSourceLevel(sourceLevel2,
                        employment);
                address2.putAll(map2);
                addressList.add(address2);
            }

            if (!StringUtils.isEmpty(employment.getClinicRoomAddr3())) {
                String appDepartCode3 = employment.getAppointDepartCode3();
                Map<String, Object> address3 = treateAppDepartCode(organId,
                        appDepartCode3);
                address3.put("clinicRoomAddr", employment.getClinicRoomAddr3());
                Integer sourceLevel3 = employment.getSourceLevel3();
                Map<String, Object> map3 = treatSourceLevel(sourceLevel3,
                        employment);
                address3.putAll(map3);
                addressList.add(address3);
            }

            empMap.put("addressList", addressList);

        } catch (ControllerException e) {
            log.error("getUnEmptyEmployment() error : "+e);
        }
        return empMap;
    }

    private Map<String, Object> treateAppDepartCode(Integer organId,
                                                    String appDepartCode) throws ControllerException {
        Map<String, Object> address = new HashMap<String, Object>();
        if (StringUtils.isEmpty(appDepartCode)) {
            return address;
        }
        AppointDepartDAO appointDepartDAO = DAOFactory
                .getDAO(AppointDepartDAO.class);
        AppointDepart appointDepart = appointDepartDAO
                .getByOrganIDAndAppointDepartCode(organId, appDepartCode);
        if (appointDepart != null) {
            address.put("appointDepartCode", appDepartCode);
            address.put("appointDepartName",
                    appointDepart.getAppointDepartName());
            String professionCode = appointDepart.getProfessionCode();
            if (!StringUtils.isEmpty(professionCode)) {
                String professionName = DictionaryController.instance()
                        .get("eh.base.dictionary.Profession")
                        .getText(professionCode);
                address.put("professionCode", professionCode);
                address.put("professionName", professionName);
            }
        }
        return address;
    }

    private Map<String, Object> treatSourceLevel(Integer sourceLevel,
                                                 Employment employment) throws ControllerException {
        Map<String, Object> map = new HashMap<String, Object>();
        if (sourceLevel == null) {
            return map;
        }
        Double price = 0.0;// 默认为0
        switch (sourceLevel) {
            case 1:
                price = employment.getClinicPrice();
                break;
            case 2:
                price = employment.getProfClinicPrice();
                break;
            case 3:
                price = employment.getSpecClinicPrice();
                break;
            default:
                break;
        }
        map.put("sourceLevel", sourceLevel);
        map.put("sourceLevelText",
                DictionaryController.instance()
                        .get("eh.base.dictionary.SourceLevel")
                        .getText(sourceLevel));
        map.put("price", price);
        return map;
    }

    /**
     * 根据医生内码获取执业机构
     *
     * @param doctorId
     * @return
     * @author LF
     */
    @RpcService
    public List<Employment> findEmByDoctorId(int doctorId) {
        List<Employment> employments = findByDoctorId(doctorId);
        AppointDepartDAO appointDepartDAO = DAOFactory
                .getDAO(AppointDepartDAO.class);
        for (int i = 0; i < employments.size(); i++) {
            Employment employment = employments.get(i);
            Integer organ = employment.getOrganId();
            String strCode1 = employment.getAppointDepartCode1();
            if (!StringUtils.isEmpty(strCode1)
                    && !StringUtils.isEmpty(strCode1.trim())) {
                AppointDepart appointDepart = appointDepartDAO.getByOrganIDAndAppointDepartCode(organ, strCode1);
                if(null != appointDepart) {
                    employment.setAppointDepartName1(appointDepart.getAppointDepartName());
                }
            }
            String strCode2 = employment.getAppointDepartCode2();
            if (!StringUtils.isEmpty(strCode2)
                    && !StringUtils.isEmpty(strCode2.trim())) {
                AppointDepart appointDepart = appointDepartDAO.getByOrganIDAndAppointDepartCode(organ, strCode2);
                if(null != appointDepart) {
                    employment.setAppointDepartName2(appointDepart.getAppointDepartName());
                }
            }
            String strCode3 = employment.getAppointDepartCode3();
            if (!StringUtils.isEmpty(strCode3)
                    && !StringUtils.isEmpty(strCode3.trim())) {
                AppointDepart appointDepart = appointDepartDAO.getByOrganIDAndAppointDepartCode(organ, strCode3);
                if(null != appointDepart) {
                    employment.setAppointDepartName3(appointDepart.getAppointDepartName());
                }
            }
        }
        return employments;
    }

    @RpcService
    @DAOMethod
    public abstract List<Employment> findByOrganId(Integer organId);

    @RpcService
    @DAOMethod
    public abstract Employment getById(int employmentId);

    /**
     * 医生执业机构更新服务（更新地址和价格）
     *
     * @author LF
     * @param employment
     * @return
     */
    // @RpcService
    // public Employment updateAddrAndPrice(final Employment employment) {
    // if (employment.getDoctorId() == null) {
    // throw new DAOException(DAOException.VALUE_NEEDED,
    // "doctorId is required");
    // }
    // if (employment.getOrganId() == null) {
    // throw new DAOException(DAOException.VALUE_NEEDED,
    // "organId is required");
    // }
    // AbstractHibernateStatelessResultAction<Employment> action = new
    // AbstractHibernateStatelessResultAction<Employment>() {
    // @Override
    // public void execute(StatelessSession ss) throws Exception {
    // // 根据入参organId和doctorId查询出唯一医生执业机构
    // Employment employment1 = getByDoctorIdAndOrganId(
    // employment.getDoctorId(), employment.getOrganId());
    // // 将入参中以下字段不为空的值赋给查询出的医生执业机构
    // if (employment.getClinicRoomAddr1() != null) {
    // employment1.setClinicRoomAddr1(employment
    // .getClinicRoomAddr1());
    // }
    // if (employment.getClinicRoomAddr2() != null) {
    // employment1.setClinicRoomAddr2(employment
    // .getClinicRoomAddr2());
    // }
    // if (employment.getClinicRoomAddr3() != null) {
    // employment1.setClinicRoomAddr3(employment
    // .getClinicRoomAddr3());
    // }
    // if (employment.getClinicPrice() != null) {
    // employment1.setClinicPrice(employment.getClinicPrice());
    // }
    // if (employment.getProfClinicPrice() != null) {
    // employment1.setProfClinicPrice(employment
    // .getProfClinicPrice());
    // }
    // if (employment.getSpecClinicPrice() != null) {
    // employment1.setSpecClinicPrice(employment
    // .getSpecClinicPrice());
    // }
    // // 更新医生执业机构表
    // update(employment1);
    // // 返回更新的对象
    // setResult(employment1);
    // }
    // };
    // HibernateSessionTemplate.instance().execute(action);
    // return action.getResult();
    // }

    /**
     * 返回给前端 price，sourceLevel1，clinicRoomAddr1，appointDepartCode1，
     * 以及这个挂号科室所对应的professionCode
     * <p>
     * 比如传入类型是1，返回给前端 price，sourceLevel1，clinicRoomAddr1，
     * appointDepartCode1，以及这个挂号科室所对应的professionCode(从AppointDepart表中获取),
     * 返回前端的价格根据sourceLevel的值进行定义，sourceLevel=1,
     * 返回clinicPrice；sourceLevel=2，返回profClinicPrice；
     * sourceLevel=3,返回specClinicPrice
     *
     * @param employmentId
     * @param type         选择类型（1/2/3）
     * @return
     * @author Qichengjian
     */
    @RpcService
    public Map<String, Object> getEmploymentMap(int employmentId, int type) {
        Map<String, Object> map = new HashMap<>();
        EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
        Employment employment = dao.getById(employmentId);
        Integer organId = employment.getOrganId();
        AppointDepartDAO appointDepartDAO = DAOFactory
                .getDAO(AppointDepartDAO.class);
        Integer sourceLevel = null;
        String clinicRoomAddr = null;
        String appointDepartCode = null;
        Double price = null;
        String professionCode = null;
        if (type == 1) {
            sourceLevel = employment.getSourceLevel1();
            clinicRoomAddr = employment.getClinicRoomAddr1();
            appointDepartCode = employment.getAppointDepartCode1();
        }
        if (type == 2) {
            sourceLevel = employment.getSourceLevel2();
            clinicRoomAddr = employment.getClinicRoomAddr2();
            appointDepartCode = employment.getAppointDepartCode2();
        }
        if (type == 3) {
            sourceLevel = employment.getSourceLevel3();
            clinicRoomAddr = employment.getClinicRoomAddr3();
            appointDepartCode = employment.getAppointDepartCode3();
        }
        AppointDepart appointDepart = appointDepartDAO
                .getByOrganIDAndAppointDepartCode(organId, appointDepartCode);
        if (appointDepart != null) {
            professionCode = appointDepart.getProfessionCode();
        }
        if (sourceLevel == 1) {
            price = employment.getClinicPrice();
        }
        if (sourceLevel == 2) {
            price = employment.getProfClinicPrice();
        }
        if (sourceLevel == 3) {
            price = employment.getSpecClinicPrice();
        }
        map.put("price", price);
        map.put("sourceLevel", sourceLevel);
        map.put("clinicRoomAddr", clinicRoomAddr);
        map.put("appointDepartCode", appointDepartCode);
        map.put("professionCode", professionCode);
        return map;
    }

    @RpcService
    @DAOMethod(sql = "from Employment where organId=:organId")
    public abstract List<Employment> findAllEmp(
            @DAOParam("organId") int organId,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * （必须保留）
     * 获取该机构下所有审核通过的医生（号源导入时前置机调用）
     * @param organId
     * @return List
     */
    @RpcService
    public List<Employment> findAll(final int organId) {
        AbstractHibernateStatelessResultAction<List<Employment>> action = new AbstractHibernateStatelessResultAction<List<Employment>>() {

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "SELECT e FROM Employment e ,Doctor d  where d.doctorId=e.doctorId AND e.organId=:organId and d.status = 1 ";
                Query query = ss.createQuery(hql);
                query.setParameter("organId", organId);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取医生职业信息服务
     *
     * @param doctorId
     * @param organId
     * @return
     */
    // @RpcService
    // @DAOMethod
    // public abstract Employment getByDoctorIdAndOrganId(int doctorId, int
    // organId);

    /**
     * 获取医生某机构职业信息列表服务
     *
     * @param doctorId
     * @param organId
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod
    public abstract List<Employment> findByDoctorIdAndOrganId(int doctorId,
                                                              int organId);

    @RpcService
    public Employment getDeptNameByDoctorIdAndOrganId(int doctorId, int organId) {
        Employment Employment = getPrimaryEmpByDoctorId(doctorId);
        DepartmentDAO DepartmentDAO = DAOFactory.getDAO(DepartmentDAO.class);
        Employment.setDeptName(DepartmentDAO.getNameById(Employment
                .getDepartment()));
        return Employment;
    }

    @DAOMethod
    @RpcService
    public abstract void updateJobNumberByDoctorIdAndOrganId(String jobNumber,
                                                             int doctorId, int organId);

    /**
     * 根据排班信息，更新平台医生jobnumber用，不作为正式服务使用
     *
     * @param jobNumber
     * @param organId
     * @param doctorName
     * @return
     */
    @RpcService
    public Boolean updateJobnumberByOrganIdAndName(final String jobNumber,
                                                   int organId, final String doctorName) {
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {

            @Override
            public void execute(StatelessSession arg0) throws Exception {
                DoctorDAO dcDao = DAOFactory.getDAO(DoctorDAO.class);
                Doctor dr = dcDao.getByName(doctorName);
                if (dr != null) {
                    updateJobNumberByDoctorIdAndOrganId(jobNumber,
                            dr.getDoctorId(), 1);
                    setResult(true);
                }
            }

        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    @DAOMethod
    @RpcService
    public abstract List<Employment> findByJobNumberAndOrganId(
            String jobNumber, int organId);

    /**
     * 根据医生主键，机构编码查询医生工号
     *
     * @param doctorId
     * @param organId
     */
    @RpcService
    @DAOMethod(sql = "select distinct jobNumber from Employment where doctorId=:doctorId and organId=:organId")
    public abstract List<String> findJobNumberByDoctorIdAndOrganId(
            @DAOParam("doctorId") int doctorId, @DAOParam("organId") int organId);

    /**
     * 根据医生主键,机构编码,科室编号查询医生工号
     *
     * @param doctorId
     * @param organId
     * @param department
     * @return
     * @author Qichengjian
     */
    @RpcService
    @DAOMethod(sql = "select jobNumber from Employment where doctorId= :doctorId and organId= :organId and department= :department")
    public abstract String getJobNumberByDoctorIdAndOrganIdAndDepartment(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("organId") int organId,
            @DAOParam("department") Integer department);

    /**
     * 获取第一职业点
     *
     * @param doctorId
     * @return
     */
    @RpcService
    @DAOMethod(sql = "from Employment where doctorId=:doctorId and primaryOrgan=1")
    public abstract Employment getPrimaryEmpByDoctorId(
            @DAOParam("doctorId") int doctorId);

    @DAOMethod(sql = "from Employment where doctorId in :doctorIdList and primaryOrgan=1")
    public abstract List<Employment> findPrimaryEmpByDoctorIdList(
            @DAOParam("doctorIdList") List<Integer> doctorIdList);

    /**
     * 根据医生工号，机构编码查询第一执业点的医生内部主键
     *
     * @param jobNumber
     * @param organId
     */
    @RpcService
    @DAOMethod(sql = "from Employment where jobNumber=:jobNumber and organId=:organId and primaryOrgan=1")
    public abstract Employment getByJobNumberAndOrganId(
            @DAOParam("jobNumber") String jobNumber,
            @DAOParam("organId") int organId);

    /**
     * 根据医生工号，机构编码查询医生内部主键
     *
     * @param jobNumber
     * @param organId
     */
    @RpcService
    @DAOMethod(sql = "select distinct(doctorId) from Employment where jobNumber=:jobNumber and organId=:organId")
    public abstract int getDoctorIdByJobNumberAndOrganId(
            @DAOParam("jobNumber") String jobNumber,
            @DAOParam("organId") int organId);

    /**
     * 医生执业信息新增服务
     *
     * @param e
     * @author hyj
     */
    @RpcService
    public Employment addEmployment(Employment e) {
        if (StringUtils.isEmpty(e.getJobNumber())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "jobNumber is required");
        }
        if (e.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctorId is required");
        }
        if (e.getOrganId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organId is required");
        }
        if (e.getDepartment() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "department is required");
        }
        // 判断是否存在第一执业点
        Employment em = this.getPrimaryEmpByDoctorId(e.getDoctorId());

        if (em != null) {
            e.setPrimaryOrgan(false);
        } else {
            e.setPrimaryOrgan(true);
        }

        String strJobNumber = this
                .getJobNumberByDoctorIdAndOrganIdAndDepartment(e.getDoctorId(),
                        e.getOrganId(), e.getDepartment());

        if (!StringUtils.isEmpty(strJobNumber)) {
            throw new DAOException(609, "该医生已存在执业信息，请勿重复新增");
        }
        Employment target = save(e);
        return target;
    }

    /**
     * 更新执业信息
     *
     * @param e
     * @return
     * @author yaozh
     */
    @RpcService
    public Employment updateEmployment(final Employment e) {
        log.info("更新执业信息:" + JSONUtils.toString(e));
        //2017-6-6 16:59:27 zhangx android端序列化时填加了一些默认值，比如Integer默认为null，
        // 导致后台接口中将部分关键数据更新掉了，现将doctorid，organid，deptment，primaryOrgan设置为null，不进行更新
        e.setDoctorId(null);
        e.setOrganId(null);
        e.setDepartment(null);
        e.setPrimaryOrgan(null);

        Integer employmentId = e.getEmploymentId();
        if (employmentId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "employmentId is required!");
        }
        final Employment oldEmployment = getById(employmentId);
        if (oldEmployment == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "找不到执业信息");
        }

        BeanUtils.map(e, oldEmployment);
        Employment target = update(oldEmployment);

        // 将修改的医生执业信息set到服务器缓存中
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor d = doctorDAO.getByDoctorId(target.getDoctorId());

        String uid = d.getMobile();
        if (!StringUtils.isEmpty(uid)) {
            if (target.getPrimaryOrgan() != null
                    && target.getPrimaryOrgan()) {
                new UserSevice().updateUserCache(uid, SystemConstant.ROLES_DOCTOR, "employment", target);
            }
        }
        return target;
    }

    /**
     * @param @param doctorId
     * @return void
     * @throws
     * @Title: updatePrimaryOrganFalse
     * @Description: TODO 更新该医生的所有执业机构为非第一执业机构
     * @author AngryKitty
     * @Date 2015-11-18下午2:15:38
     */
    @RpcService
    @DAOMethod(sql = "update Employment  set primaryOrgan=0 where doctorId=:doctorId ")
    public abstract void updatePrimaryOrganFalse(
            @DAOParam("doctorId") int doctorId);

    /**
     * @param @return
     * @return List<Employment>
     * @throws
     * @Title: findByDoctorIdAndDeptId
     * @Description: TODO根据医生ID和科室编码查询机构
     * @author AngryKitty
     * @Date 2015-11-18下午2:19:58
     */
    @RpcService
    @DAOMethod(sql = " from Employment where doctorId=:doctorId and department =:department")
    public abstract Employment getByDoctorIdAndDeptId(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("department") int department);

    /**
     * @param @param docId
     * @param @param organ
     * @param @param deptId
     * @return void
     * @throws
     * @Title: RegisteredEmployment
     * @Description: TODO医生注册时候机构变化
     * @author AngryKitty
     * @Date 2015-11-18上午11:50:17
     */
    public void RegisteredEmployment(int docId, int organ, int deptId) {
        this.updatePrimaryOrganFalse(docId);
        Employment emp = this.getByDoctorIdAndDeptId(docId, deptId);
        if (emp != null) {
            emp.setPrimaryOrgan(true);
            this.update(emp);
        } else {
            emp = new Employment();
            emp.setDoctorId(docId);
            emp.setJobNumber("ZC" + docId);
            emp.setOrganId(organ);
            emp.setDepartment(deptId);
            emp.setPrimaryOrgan(true);
            emp.setConsultEnable(false);
            emp.setConsultPrice(0.0);
            emp.setConsultationEnable(true);
            emp.setConsultationPrice(0.0);
            emp.setClinicEnable(true);
            emp.setClinicPrice(0.0);
            emp.setProfClinicPrice(0.0);
            emp.setSpecClinicPrice(0.0);
            emp.setTransferEnable(true);
            emp.setTransferPrice(0.0);
            emp.setApplyTransferEnable(true);
            this.save(emp);
        }

    }

    public void RegisteredHisEmployment(int docId, int organ, int deptId, String jobNum) {
        this.updatePrimaryOrganFalse(docId);
        Employment emp = this.getByDoctorIdAndDeptId(docId, deptId);
        if (emp != null) {
            emp.setPrimaryOrgan(true);
            emp.setJobNumber(jobNum);
            this.update(emp);
        } else {
            emp = new Employment();
            emp.setDoctorId(docId);
            emp.setJobNumber(jobNum);
            emp.setOrganId(organ);
            emp.setDepartment(deptId);
            emp.setPrimaryOrgan(true);
            emp.setConsultEnable(false);
            emp.setConsultPrice(0.0);
            emp.setConsultationEnable(true);
            emp.setConsultationPrice(0.0);
            emp.setClinicEnable(true);
            emp.setClinicPrice(0.0);
            emp.setProfClinicPrice(0.0);
            emp.setSpecClinicPrice(0.0);
            emp.setTransferEnable(true);
            emp.setTransferPrice(0.0);
            emp.setApplyTransferEnable(true);
            this.save(emp);
        }

    }

    @RpcService
    @DAOMethod(sql = " from Employment where organId=:organId and jobNumber =:jobNumber")
    public abstract Employment getByOrganIdAndJobNumber(
            @DAOParam("organId") Integer organId,
            @DAOParam("jobNumber") String jobNumber);

    /**
     * 获取有效职业点列表
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> findEffEmpWithDrug(int doctorId) {
        List<Employment> ems = findByDoctorIdOrderBy(doctorId);
        List<HashMap<String, Object>> results = new ArrayList<HashMap<String, Object>>();
        if (ems == null || ems.size() <= 0) {
            return results;
        }
//        List<HashMap<String,Object>> unable = new ArrayList<HashMap<String, Object>>();
//        List<HashMap<String,Object>> enable = new ArrayList<HashMap<String, Object>>();
        DrugListDAO dlDao = DAOFactory.getDAO(DrugListDAO.class);
        DepartmentDAO departmentDAO = DAOFactory.getDAO(DepartmentDAO.class);
        for (Employment e : ems) {
            if (e.getOrganId() == null) {
                continue;
            }
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("emp", e);
            Long count = dlDao.getEffDrugNum(e.getOrganId());
            Boolean able = false;
            if (count > 0) {
                able = true;
            }
            map.put("able", able);

            //开处方时增加无法配送时间文案提示
            map.put("unSendTitle", ParamUtils.getParam(ParameterConstant.KEY_RECIPE_UNSEND_TIP));

            //中药处方权限判断
            //判断该医院药品目录中是否有中药药材
            Long count2 = dlDao.getChineseMedicineNum(e.getOrganId());
            able = false;
            if(count2 > 0){
                able = true;
            }

            map.put("chineseMedicineAble", able);

//            if(e.getPrimaryOrgan()) {
            results.add(map);
//            }else if (able) {
//                enable.add(map);
//            }else {
//                unable.add(map);
//            }
        }
//        results.addAll(enable);
//        results.addAll(unable);
        return results;
    }

    /**
     * 更新用户缓存信息中的第一执业点
     *
     * @param employment
     */
    public void updateUserCacheEmploymentProperty(Employment employment) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor d = doctorDAO.getByDoctorId(employment.getDoctorId());
        if (!StringUtils.isEmpty(d.getMobile())) {
            if (employment.getPrimaryOrgan() != null && employment.getPrimaryOrgan()) {
                new UserSevice().updateUserCache(d.getMobile(), SystemConstant.ROLES_DOCTOR, "employment", employment);
            }
        }
    }

    /**
     * 将指定执业点修改为第一执业点，同时有必要的话修改医生的 organ
     *
     * @param employmentId
     * @return
     */
    @RpcService
    public Employment updateEmploymentAsPrimary(Integer employmentId) {
        final Employment targetEmployment = getById(employmentId);
        if (targetEmployment == null) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "找不到执业信息");
        }
        updatePrimaryOrganFalse(targetEmployment.getDoctorId()); // 先将其他设为false
        targetEmployment.setPrimaryOrgan(true);
        update(targetEmployment);

        // 同时更新医生
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(targetEmployment.getDoctorId());
        if (!doctor.getOrgan().equals( targetEmployment.getOrganId())) {
            // 只有在换机构并且有用户的情况下才替换 urt 中的 manageUnit
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            Organ oldOrgan = organDAO.getByOrganId(doctor.getOrgan());
            UserRoleTokenEntity userRoleTokenEntity = (UserRoleTokenEntity) new UserSevice().getUrtRoleToken(doctor.getMobile(), oldOrgan.getManageUnit(), "doctor");
            if (userRoleTokenEntity != null) {
                Organ newOrgan = organDAO.getByOrganId(targetEmployment.getOrganId());
                userRoleTokenEntity.setManageUnit(newOrgan.getManageUnit());
                try {
                    ((ConfigurableItemUpdater) UserController.instance().getUpdater()).updateItem(doctor.getMobile(), userRoleTokenEntity);
                } catch (Exception e) {
                    log.error("updateEmploymentAsPrimary() error :  "+e);
                }
            }

            doctor.setOrgan(targetEmployment.getOrganId());
            doctorDAO.update(doctor);
            // 通知缓存，如果医生有对应用户更新其 doctor 属性
            new UserSevice().updateUserCache(doctor.getMobile(), SystemConstant.ROLES_DOCTOR, "doctor", doctor);
        }
        // 通知缓存，如果医生有对应用户更新其 employment 属性;
        updateUserCacheEmploymentProperty(targetEmployment);
        return targetEmployment;
    }

    /**
     * 更新执业信息
     *
     * @param e
     * @return
     * @author houxr
     */
    @RpcService
    public Employment updateEmploymentForOP(final Employment e) {
        log.info("更新执业信息:" + JSONUtils.toString(e));
        Integer employmentId = e.getEmploymentId();
        if (employmentId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "employmentId is required!");
        }
        Employment target = getById(employmentId);
        if (target == null) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "找不到执业信息");
        }
        boolean organPrimaryOrgan = Boolean.TRUE.equals(target.getPrimaryOrgan());
        BeanUtils.map(e, target);
        target = update(target);

        if (Boolean.TRUE.equals(e.getPrimaryOrgan())) {
            if (!organPrimaryOrgan) {
                updateEmploymentAsPrimary(employmentId);
            } else {
                updateUserCacheEmploymentProperty(target);
            }
        }
        return target;
    }

    @DAOMethod(sql=" select count(*) from Employment where doctorId =:doctorId")
    public abstract long getCountByDoctorId(@DAOParam("doctorId") Integer doctorId);

    @RpcService
    @DAOMethod(sql=" select count(*) from Employment where department =:department")
    public abstract long getCountByDepartment(@DAOParam("department") Integer department);

}
