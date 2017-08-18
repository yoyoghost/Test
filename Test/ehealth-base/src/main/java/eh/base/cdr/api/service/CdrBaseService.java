package eh.base.cdr.api.service;

import ctd.util.annotation.RpcService;
import eh.entity.base.Department;
import eh.entity.base.Doctor;
import eh.entity.base.DrugList;
import eh.entity.base.Employment;

import java.util.List;

/**
 * Created by jiangtingfeng on 2017/7/28.
 */
public interface CdrBaseService
{
    @RpcService
    public abstract List<Integer> findOrganIdsByDoctorId(int doctorId);

    @RpcService
    public abstract Department getDepartmentById(Integer deptId);

    @RpcService
    Doctor getByMobile(String mobile);

    @RpcService
    Doctor getByDoctorId(int doctorId);

    @RpcService
    Doctor get(int doctorId);

    @RpcService
    DrugList getDrugListById(int drugId);

    @RpcService
    List<DrugList> findAll();

    @RpcService
    Employment getByJobNumberAndOrganId(String jobNumber,int organId);

    @RpcService
    Employment getPrimaryEmpByDoctorId(Integer doctorId);

    @RpcService
    String getJobNumberByDoctorIdAndOrganIdAndDepartment(int doctorId,int organId,Integer department);

}
