package eh.base.service.thirdparty;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.annotation.RpcService;
import eh.base.dao.DepartmentDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.OrganDAO;
import eh.entity.base.Department;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.Organ;
import eh.msg.service.MessagePushService;

import java.util.HashMap;
import java.util.Map;


/**
 * 第三方相关基础服务调用
 * Created by w on 2016/4/21.
 */
public class BasicInfoService {

    /**
     * 艾康点播系统 调用纳里健康服务 获取客户端医生信息
     * @param mobile 医生手机号
     * @return 医生相关信息
     * @throws DAOException
     */
    @RpcService
    public HashMap<String,Object> getDoctorInfoByMobile(String mobile){
        HashMap<String,Object> res=new HashMap<>();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor d=doctorDAO.getByMobile(mobile);
        if(d==null){
            res.put("code", "-1");
            res.put("msg", "cat find the doctorInfo by the mobile:"+"["+mobile+"]");
            return res;
        }
        res.put("code", "200");
        HashMap<String,Object> data=new HashMap<>();
        data.put("doctorId", d.getDoctorId());
        data.put("doctorName", d.getName());
        data.put("mobile", d.getMobile());
        EmploymentDAO empDAO = DAOFactory.getDAO(EmploymentDAO.class);
        DepartmentDAO depDAO = DAOFactory.getDAO(DepartmentDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Employment emp=empDAO.getPrimaryEmpByDoctorId(d.getDoctorId());
        Department depart=depDAO.get(emp.getDepartment());
        Organ organ=organDAO.get(d.getOrgan());
        data.put("department", depart.getName());
        data.put("departmentCode", depart.getCode());
        data.put("organizeCode", organ.getOrganizeCode());
        data.put("organName", organ.getName());
        res.put("data", data);
        return res;
    }

    /**
     * 艾康点播系统 调用纳里健康服务 获取客户端医生信息
     * @param doctorId 医生id
     * @return 医生信息
     * @throws DAOException
     */
    @RpcService
    public HashMap<String,Object> getDoctorInfoByDoctorId(int doctorId){
        HashMap<String,Object> res=new HashMap<>();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor d=doctorDAO.getByDoctorId(doctorId);
        if(d==null){
            res.put("code", "-1");
            res.put("msg", "cat find the doctorInfo by the doctorId:"+"["+doctorId+"]");
            return res;
        }
        res.put("code", "200");
        HashMap<String,Object> data=new HashMap<>();
        data.put("doctorId", d.getDoctorId());
        data.put("doctorName", d.getName());
        data.put("mobile", d.getMobile());
        EmploymentDAO empDAO = DAOFactory.getDAO(EmploymentDAO.class);
        DepartmentDAO depDAO = DAOFactory.getDAO(DepartmentDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Employment emp=empDAO.getPrimaryEmpByDoctorId(doctorId);
        Department depart=depDAO.get(emp.getDepartment());
        Organ organ=organDAO.get(d.getOrgan());
        data.put("department", depart.getName());
        data.put("departmentCode", depart.getCode());
        data.put("organizeCode", organ.getOrganizeCode());
        data.put("organName", organ.getName());

        res.put("data", data);
        return res;
    }

    /**
     * 供点播系统消息推送用
     * @param mobile
     * @param content
     * @param url
     * @return
     */
    @RpcService
    public Map<String,Object> pushTeachMessage(String mobile,String content,String url){

        MessagePushService pushService= AppDomainContext.getBean("messagePushService",MessagePushService.class);
        //自定义参数
        HashMap<String,Object> map=new HashMap<>();
        map.put("action_type","1");// 动作类型，1打开activity或app本身
        map.put("activity","TEACH_DETAIL");//指定点播模块
        HashMap<String,Object> attr=new HashMap<>(); // activity属性，只针对action_type=1的情况
        attr.put("url",url);
        map.put("aty_attr",attr);
        return pushService.pushMsg(mobile,content,map);

    }

}
