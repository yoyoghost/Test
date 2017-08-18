package eh.remote;

import ctd.controller.exception.ControllerException;
import ctd.persistence.bean.QueryResult;
import ctd.util.annotation.RpcService;
import eh.entity.base.Department;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.bus.AppointDepart;
import eh.entity.bus.AppointSchedule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by zxx on 2017/6/7 0007.
 */
public interface  IYuYueQueryServiceInterface {
    /**
     * 获取机构详情
     * @param organId
     * @return
     */
    @RpcService
    public Organ getOrganDetail(int organId);

    /**
     * 按医院找
     * @param addrArea
     * @param organGrade
     * @param sortType
     * @param start
     * @param num
     * @return
     */
    @RpcService
    public Map<String,Object> screenOrgan(String addrArea, String organGrade, String sortType ,int start ,int num);
    /**
     * 网站首页获取热门医生
     * @param addrArea
     * @param num
     * @return
     */
    @RpcService
    public List<Doctor> getHotDoctors(String addrArea, String organGrade, String profession,int start, int num);

    /**
     * 医生主页  医生详情  预约量
     * @param doctorId
     * @return
     */
    @RpcService
    public Map<String,Object> getDoctorDetail(int doctorId);


    /**
     *获取同一机构下的同一科室下的其他医生
     * @param organId
     * @param profession
     * @param department
     * @return
     */
    @RpcService
    public List<Doctor> getOtherDoctorOfOrgan(Integer doctorId , Integer organId, String profession, Integer department,int max);

    /**
     *  按机构查询一级目录以及其下的专科目录 web显示挂号科室
     * @param organId
     * @return
     */
    @RpcService
    public List<Map<String,Object>> findAppointDepartByOrganId(int organId);

    /**
     * 获取临床科室详情
     * @param deptId
     * @return
     */
    @RpcService
    public Department getDepartmentDetail(int deptId);

    /**
     * 获取预约科室详情
     * @param appointDepartId
     * @return
     */
    @RpcService
    public  Map<String,Object> getAppointDepartmentDetail(int appointDepartId);

    /**
     * 获取热门科室
     * @param area
     * @param num
     * @return
     */
    @RpcService
    public List<AppointDepart> getHotDepartment( String area ,  int num);

    /**
     *找科室页面 科室筛选分页
     *
     * @param area 区域
     * @param grade 医院等级
     * @param sort 排序  0默认 1 医院等级 2 预约量
     * @param professionCode 科室编码
     * @param start 开始
     * @param limit 每页数
     * @return
     */
    @RpcService
    public QueryResult<Map<String,Object>> screenAppointDepart(String area, String grade, String sort, String professionCode, int start , int limit);

    /**
     * 医生在某机构的排班
     * @param organ
     * @param doctorId
     * @return
     */
    @RpcService
    public List<AppointSchedule> getDocSchedule(int organ, int doctorId);

    /**
     * 获取医生所有排班
     * @param doctorId
     * @return
     */
    @RpcService
    public List<Map<String,Object>> getDocAllSchedule(int doctorId) throws ControllerException;

    /**
     * 获取某机构下某专科的所有医生及排班
     * @param organId
     * @param profession
     * @param department
     * @return
     */
    @RpcService
    public List<Doctor> getDoctorScheduleOfDept(Integer organId, String profession, Integer department);

    /**
     * 获取挂号科室下的排班
     * @param organId
     * @param profession
     * @param department
     * @param appointDepart
     * @return
     */
    @RpcService
    public List<Doctor> getDoctorScheduleOfAppointdept(Integer organId, String profession, Integer department,String appointDepart);

    /**
     * 获取找医院总数
     * @param addrArea
     * @param organGrade
     * @return
     */
    @RpcService
    public Long getScreenOrganNum(String addrArea, String organGrade);

    /**
     *找科室筛选总数
     * @param area
     * @param grade
     * @param professionCode
     * @return
     */
    @RpcService
    public Long getScreenAppointDepartNum( String area,  String grade,  String professionCode);

    /**
     * 获取挂号科室号源
     * @param organId
     * @param department
     * @param appointDepartCode
     * @param start
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> findAllDocAndSourcesOfAppointDepart (int organId, String appointDepartCode, int start);

    /**
     * 获取医生号源
     * @param doctorId
     * @param start
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> findAllSourcesOfDoc(int doctorId, int start);
}
