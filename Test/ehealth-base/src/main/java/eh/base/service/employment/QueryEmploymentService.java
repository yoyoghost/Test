package eh.base.service.employment;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.util.annotation.RpcService;
import eh.base.dao.EmploymentDAO;
import eh.bus.dao.AppointDepartDAO;
import eh.entity.base.Employment;
import eh.entity.bus.AppointDepart;
import eh.op.auth.service.SecurityService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Created by luf on 2016/6/17.
 */

public class QueryEmploymentService {

    public static final Logger log = Logger.getLogger(QueryEmploymentService.class);

    /**
     * 接诊医生执业点信息列表服务
     * <p>
     * 根据EmploymentDAO中的findByDoctorIdAndOrganId修改入参
     *
     * @param inDoctorId   接诊医生内码
     * @param requestOrgan 申请医生机构
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findEmploymentList(int inDoctorId, int requestOrgan) {
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<Employment> employments = employmentDAO.findByDoctorIdAndOrganId(inDoctorId, requestOrgan);
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
            log.error("findEmploymentList() error : " + e);
        }
        return empList;
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
     * 运营平台（权限改造
     * @param doctorId
     * @param organId
     * @return
     */
    @RpcService
    public List<Employment> findByDoctorIdAndOrganId(int doctorId,
                                                     int organId) {
        Set<Integer> o = new HashSet<Integer>();
        o.add(organId);
        if (!SecurityService.isAuthoritiedOrgan(o)) {
            return null;
        }
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        return employmentDAO.findByDoctorIdAndOrganId(doctorId, organId);
    }

}
